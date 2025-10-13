package com.catsmoker.obd2ai

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdRawResponse
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.command.control.PendingTroubleCodesCommand
import com.github.eltonvs.obd.command.control.PermanentTroubleCodesCommand
import com.github.eltonvs.obd.command.control.TroubleCodesCommand
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

// =================================================================================
// MODELS
// =================================================================================

data class BluetoothDeviceDTO(
    val name: String,
    val address: String
)

data class DtpCodeDTO(
    val errorCode: String,
    val severity: ErrorSeverity,
    val title: String,
    val detail: String,
    val implications: String,
    val suggestedActions: List<String>
)

enum class ErrorSeverity {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromInt(value: Int) = when (value) {
            0 -> LOW
            1 -> MEDIUM
            2 -> HIGH
            else -> throw IllegalArgumentException("Invalid severity level: $value")
        }

        fun getColor(severity: ErrorSeverity) = when (severity) {
            MEDIUM -> Color.rgb(255, 165, 0)
            HIGH -> Color.RED
            else -> Color.GRAY
        }
    }
}

object ObdDataHolder {
    var dtpResults: List<DtpCodeDTO> = emptyList()
    val isMonitoring = AtomicBoolean(false)
    val speedFlow = MutableStateFlow("-- km/h")
    val rpmFlow = MutableStateFlow("-- RPM")
    val coolantTempFlow = MutableStateFlow("-- °C")
}


// =================================================================================
// CUSTOM OBD COMMANDS WITH CORRECTED PARSING LOGIC
// =================================================================================

class MySpeedCommand : ObdCommand() {
    override val tag = "SPEED"
    override val name = "Vehicle Speed"
    override val mode = "01"
    override val pid = "0D"
    override val defaultUnit = "Km/h"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "410D"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 2) {
            val speedHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            Integer.parseInt(speedHex, 16).toString()
        } else {
            "0"
        }
    }
}

class MyRPMCommand : ObdCommand() {
    override val tag = "ENGINE_RPM"
    override val name = "Engine RPM"
    override val mode = "01"
    override val pid = "0C"
    override val defaultUnit = "RPM"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "410C"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 4) {
            val aHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            val bHex = rawValue.substring(index + identifier.length + 2, index + identifier.length + 4)

            val a = Integer.parseInt(aHex, 16)
            val b = Integer.parseInt(bHex, 16)

            ((a * 256) + b) / 4
        } else {
            0
        }.toString()
    }
}

class MyCoolantTempCommand : ObdCommand() {
    override val tag = "COOLANT_TEMP"
    override val name = "Engine Coolant Temperature"
    override val mode = "01"
    override val pid = "05"
    override val defaultUnit = "°C"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "4105"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 2) {
            val tempHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            (Integer.parseInt(tempHex, 16) - 40).toString()
        } else {
            "0"
        }
    }
}


// =================================================================================
// HELPERS
// =================================================================================

class BluetoothHelper(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter?
    private var bluetoothSocket: BluetoothSocket? = null
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _isBluetoothPermissionGranted = MutableLiveData<Boolean>()
    val isBluetoothPermissionGranted: LiveData<Boolean> = _isBluetoothPermissionGranted

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 101
    }

    private val requiredPermissions: Array<String>
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            } else {
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_ADMIN,
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled ?: false

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (checkBluetoothPermissions()) {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
            bluetoothAdapter?.startDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (checkBluetoothPermissions() && bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
    }

    fun checkBluetoothPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(activity, requiredPermissions, REQUEST_CODE_PERMISSIONS)
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceDTO> {
        if (!checkBluetoothPermissions()) throw SecurityException("Permissions not granted")
        val adapter = bluetoothAdapter ?: throw IOException("Bluetooth adapter is not available.")
        return adapter.bondedDevices.map { convertToDeviceDTO(it) }
    }

    @Throws(IOException::class, SecurityException::class)
    suspend fun connectToDevice(deviceAddress: String): Pair<InputStream, OutputStream> = withContext(Dispatchers.IO) {
        if (!checkBluetoothPermissions()) throw SecurityException("Permissions not granted")
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            ?: throw IOException("Device not found")
        bluetoothSocket = device.createRfcommSocketToServiceRecord(sppUuid).apply {
            try {
                bluetoothAdapter.cancelDiscovery()
                connect()
            } catch (e: IOException) {
                close()
                throw IOException("Failed to connect to device.", e)
            }
        }
        val socket = bluetoothSocket ?: throw IOException("Bluetooth socket connection failed.")
        return@withContext Pair(socket.inputStream, socket.outputStream)
    }

    fun disconnectFromDevice() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothHelper", "Error closing Bluetooth socket", e)
        } finally {
            bluetoothSocket = null
        }
    }

    fun resolvePermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            _isBluetoothPermissionGranted.postValue(granted)
        }
    }

    @SuppressLint("MissingPermission")
    fun convertToDeviceDTO(bluetoothDevice: BluetoothDevice): BluetoothDeviceDTO {
        return BluetoothDeviceDTO(
            name = bluetoothDevice.name ?: "Unknown Device",
            address = bluetoothDevice.address
        )
    }
}

class ObdHelper(private val bluetoothHelper: BluetoothHelper) {
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var obdConnection: ObdDeviceConnection? = null

    suspend fun setupObd(deviceAddress: String) {
        val (iStream, oStream) = bluetoothHelper.connectToDevice(deviceAddress)
        this.inputStream = iStream
        this.outputStream = oStream
        obdConnection = ObdDeviceConnection(iStream, oStream)
    }

    suspend fun initializeObd() = withContext(Dispatchers.IO) {
        val out = outputStream ?: throw IOException("Output stream is not available.")
        val `in` = inputStream ?: throw IOException("Input stream is not available.")

        suspend fun sendRawCommand(command: String) {
            out.write((command + "\r").toByteArray())
            out.flush()
            delay(400)
        }

        sendRawCommand("ATZ")
        sendRawCommand("ATE0")
        sendRawCommand("ATL0")
        sendRawCommand("ATSP0")

        delay(1000)
        if (`in`.available() > 0) {
            val buffer = ByteArray(`in`.available())
            `in`.read(buffer)
            Log.d("ObdHelper", "Initialization buffer cleared. Read: ${String(buffer)}")
        }
    }

    private suspend fun runCommand(command: ObdCommand): ObdResponse {
        val connection = obdConnection ?: throw IOException("OBD connection not established.")
        val `in` = inputStream ?: throw IOException("Input stream is not available.")

        if (`in`.available() > 0) {
            val buffer = ByteArray(`in`.available())
            `in`.read(buffer)
        }

        val response = connection.run(command)
        Log.d("ObdHelper", "Command: ${command.name}, Raw: ${response.rawResponse.value}, Parsed: ${response.value} ${response.unit}")
        return response
    }

    suspend fun getDtpCodes(): List<String> = withContext(Dispatchers.IO) {
        val result = runCommand(TroubleCodesCommand()).value
        splitErrors(result)
    }

    suspend fun getPendingDtpCodes(): List<String> = withContext(Dispatchers.IO) {
        val result = runCommand(PendingTroubleCodesCommand()).value
        splitErrors(result)
    }

    suspend fun getPermanentDtpCodes(): List<String> = withContext(Dispatchers.IO) {
        val result = runCommand(PermanentTroubleCodesCommand()).value
        splitErrors(result)
    }

    fun disconnectFromObdDevice() {
        bluetoothHelper.disconnectFromDevice()
        obdConnection = null
        inputStream = null
        outputStream = null
    }

    private fun splitErrors(errors: String): List<String> {
        if (errors.isBlank() || errors.equals("NO DATA", ignoreCase = true)) {
            return emptyList()
        }
        return errors.split(Regex("\\s+|,")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    suspend fun startLiveDataMonitoring() = withContext(Dispatchers.IO) {
        ObdDataHolder.isMonitoring.set(true)
        var errorCount = 0

        while (ObdDataHolder.isMonitoring.get()) {
            try {
                val speedResponse = runCommand(MySpeedCommand())
                ObdDataHolder.speedFlow.value = "${speedResponse.value} ${speedResponse.unit}"

                val rpmResponse = runCommand(MyRPMCommand())
                ObdDataHolder.rpmFlow.value = "${rpmResponse.value} ${rpmResponse.unit}"

                val coolantTempResponse = runCommand(MyCoolantTempCommand())
                ObdDataHolder.coolantTempFlow.value = "${coolantTempResponse.value} ${coolantTempResponse.unit}"

                errorCount = 0
                delay(800)

            } catch (e: Exception) {
                errorCount++
                Log.e("ObdHelper", "Error during live data monitoring (Attempt $errorCount):", e)

                if (errorCount >= 3) {
                    ObdDataHolder.isMonitoring.set(false)
                    ObdDataHolder.speedFlow.value = "ERROR"
                    ObdDataHolder.rpmFlow.value = "ERROR"
                    ObdDataHolder.coolantTempFlow.value = "ERROR"
                    break
                }
                delay(1000)
            }
        }
    }

    fun stopLiveDataMonitoring() {
        ObdDataHolder.isMonitoring.set(false)
    }
}

class OpenAIService(private val context: Context) {
    private val openAI: OpenAI
    private var modelId: String = "gpt-5-mini"

    init {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("openai_api_key", "")
        val token = if (apiKey.isNullOrEmpty()) BuildConfig.OPENAI_API_KEY else apiKey
        modelId = prefs.getString("openai_model_id", "gpt-5-mini") ?: "gpt-5-mini"

        openAI = OpenAI(
            token = token,
            timeout = Timeout(socket = 60.seconds)
        )
    }

    suspend fun getDtpCodeAssessment(dtpCode: String): DtpCodeDTO {
        val response = getResponse(dtpCode)
        return parseErrorInfo(response)
    }

    private suspend fun getResponse(query: String): String {
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(modelId),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = "You are an expert mechanic. Given a plain OBD2 error code, provide a resolution in a structured JSON format with fields: 'errorCode', 'severity' (0-Low, 1-Medium, 2-High), 'title' (max 60 chars), 'detail' (~300 chars), 'implications' (~300 chars), and 'suggestedActions' (array of strings)."
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = query
                )
            )
        )
        val completion: ChatCompletion = openAI.chatCompletion(chatCompletionRequest)
        return completion.choices.first().message.content ?: "{}"
    }

    private fun parseErrorInfo(jsonString: String): DtpCodeDTO {
        return try {
            val jsonObject = JSONObject(jsonString)
            val errorCode = jsonObject.getString("errorCode")
            val severity = ErrorSeverity.fromInt(jsonObject.getInt("severity"))
            val title = jsonObject.getString("title")
            val detail = jsonObject.getString("detail")
            val implications = jsonObject.getString("implications")
            val actionsArray = jsonObject.getJSONArray("suggestedActions")
            val suggestedActions = (0 until actionsArray.length()).map { actionsArray.getString(it) }
            DtpCodeDTO(errorCode, severity, title, detail, implications, suggestedActions)
        } catch (e: Exception) {
            Log.e("OpenAIService", "Failed to parse JSON response: $jsonString", e)
            DtpCodeDTO("Error", ErrorSeverity.LOW, "Parsing Error", "Could not parse server response.", "Invalid data.", listOf("Try again."))
        }
    }
}