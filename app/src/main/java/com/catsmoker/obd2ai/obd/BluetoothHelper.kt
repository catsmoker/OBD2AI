package com.catsmoker.obd2ai.obd

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

data class BluetoothDeviceDTO(
    val name: String,
    val address: String
)

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
    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(deviceAddress: String): Pair<InputStream, OutputStream> = withContext(Dispatchers.IO) {
        if (!checkBluetoothPermissions()) throw SecurityException("Permissions not granted")
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            ?: throw IOException("Device not found")
        bluetoothSocket = try {
            device.createRfcommSocketToServiceRecord(sppUuid).apply {
                try {
                    bluetoothAdapter.cancelDiscovery()
                    connect()
                } catch (e: IOException) {
                    close()
                    throw IOException("Failed to connect to device.", e)
                }
            }
        } catch (e: IOException) {
            // Many ELM327 clones reject the secure socket; retry once insecurely.
            Log.w("BluetoothHelper", "Secure RFCOMM failed, trying insecure socket", e)
            try {
                device.createInsecureRfcommSocketToServiceRecord(sppUuid).apply { connect() }
            } catch (e2: IOException) {
                throw IOException("Failed to connect to device (secure and insecure).", e2)
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
