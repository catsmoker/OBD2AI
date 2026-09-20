package com.catsmoker.obd2ai.obd

import android.util.Log
import com.catsmoker.obd2ai.diagnostics.DtcSource
import com.catsmoker.obd2ai.diagnostics.FreezeFrame
import com.catsmoker.obd2ai.diagnostics.MilStatus
import com.github.eltonvs.obd.command.BusInitException
import com.github.eltonvs.obd.command.NoDataException
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdRawResponse
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.command.StoppedException
import com.github.eltonvs.obd.command.UnSupportedCommandException
import com.github.eltonvs.obd.command.UnableToConnectException
import com.github.eltonvs.obd.command.at.AdapterVoltageCommand
import com.github.eltonvs.obd.command.at.DescribeProtocolNumberCommand
import com.github.eltonvs.obd.command.control.DTCNumberCommand
import com.github.eltonvs.obd.command.control.DistanceSinceCodesClearedCommand
import com.github.eltonvs.obd.command.control.MILOnCommand
import com.github.eltonvs.obd.command.control.ModuleVoltageCommand
import com.github.eltonvs.obd.command.control.PendingTroubleCodesCommand
import com.github.eltonvs.obd.command.control.PermanentTroubleCodesCommand
import com.github.eltonvs.obd.command.control.ResetTroubleCodesCommand
import com.github.eltonvs.obd.command.control.TimeSinceCodesClearedCommand
import com.github.eltonvs.obd.command.control.TroubleCodesCommand
import com.github.eltonvs.obd.command.control.VINCommand
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Owns the OBD connection and commands for all three transports: Bluetooth
 * SPP ([setupObd]), WiFi TCP ([setupWifi]) and simulated [setupDemo].
 * Sends the fixed ELM327 init sequence `ATZ, ATE0, ATL0, ATS0, ATH0, ATSP0,
 * ATAT1` — `ATS0`/`ATH0` make spaces/headers deterministic across clones,
 * which the parsers assume; don't drop them. All reads branch on `demoMode`.
 */
class ObdHelper(private val bluetoothHelper: BluetoothHelper) {
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var obdConnection: ObdDeviceConnection? = null
    private var wifiSocket: java.net.Socket? = null

    /** When true, all reads come from [DemoObdSource] instead of hardware. */
    var demoMode: Boolean = false

    /**
     * Per-PID failure budget for the fast loop: a PID that keeps failing is
     * skipped so one dead sensor stops costing bus time. Reset on connect.
     */
    val pidHealth = PidHealthTracker()

    /**
     * Serializes every byte on the adapter streams. The library serializes
     * its own run() calls, but raw paths (console, init, batch) share the
     * same socket — without this, a slow poll colliding with a console send
     * garbles both frames into STOPPED soup.
     */
    private val ioMutex = Mutex()

    val isConnected: Boolean
        get() = demoMode || obdConnection != null

    suspend fun setupObd(deviceAddress: String) {
        demoMode = false
        pidHealth.reset()
        val (iStream, oStream) = bluetoothHelper.connectToDevice(deviceAddress)
        this.inputStream = iStream
        this.outputStream = oStream
        obdConnection = ObdDeviceConnection(iStream, oStream)
    }

    /** WiFi adapters (e.g. ELM327 clones at 192.168.0.10:35000) speak the same
     * serial protocol over TCP, so only the transport differs from Bluetooth. */
    suspend fun setupWifi(host: String, port: Int, timeoutMs: Int = 5000) = withContext(Dispatchers.IO) {
        demoMode = false
        pidHealth.reset()
        disconnectTransports()
        val socket = java.net.Socket()
        try {
            socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw IOException("Failed to connect to $host:$port.", e)
        }
        wifiSocket = socket
        inputStream = socket.getInputStream()
        outputStream = socket.getOutputStream()
        obdConnection = ObdDeviceConnection(inputStream!!, outputStream!!)
    }

    /** Enters demo mode: no transport, simulated data only. */
    fun setupDemo() {
        disconnectTransports()
        pidHealth.reset()
        demoMode = true
        obdConnection = null
    }

    suspend fun initializeObd() = withContext(Dispatchers.IO) {
        if (demoMode) return@withContext
        ioMutex.withLock {
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
            sendRawCommand("ATS0")
            sendRawCommand("ATH0")
            sendRawCommand("ATSP0")
            sendRawCommand("ATAT1")

            delay(1000)
            if (`in`.available() > 0) {
                val buffer = ByteArray(`in`.available())
                `in`.read(buffer)
                Log.d("ObdHelper", "Initialization buffer cleared. Read: ${String(buffer)}")
            }
        }
    }

    private suspend fun runCommand(command: ObdCommand): ObdResponse = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val connection = obdConnection ?: throw IOException("OBD connection not established.")
            val `in` = inputStream ?: throw IOException("Input stream is not available.")

            if (`in`.available() > 0) {
                val buffer = ByteArray(`in`.available())
                `in`.read(buffer)
            }

            val response = connection.run(command)
            Log.d("ObdHelper", "Command: ${command.name}, Raw: ${response.rawResponse.value}, Parsed: ${response.value} ${response.unit}")
            return@withLock response
        }
    }

    /**
     * Raw console: sends one free-text line (AT command or PID like "010C")
     * and returns everything up to the ">" prompt. Power-user debugging aid
     * for clone adapters; shares the connection, so avoid it while Live Data
     * is polling. Throws on empty input, oversize input, or reply timeout.
     */
    suspend fun sendRaw(command: String, timeoutMs: Long = 3000): String = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val cmd = command.trim()
            require(cmd.isNotEmpty()) { "Empty command." }
            require(cmd.length <= 64) { "Command too long." }
            if (demoMode) return@withLock "(demo) $cmd\r\nOK\r\n>"
            val out = outputStream ?: throw IOException("Output stream is not available.")
            val `in` = inputStream ?: throw IOException("Input stream is not available.")
            out.write((cmd + "\r").toByteArray())
            out.flush()
            val reply = StringBuilder()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (`in`.available() > 0) {
                    val chunk = ByteArray(`in`.available())
                    val read = `in`.read(chunk)
                    if (read > 0) {
                        reply.append(String(chunk, 0, read))
                        if (reply.contains('>')) break
                    }
                } else {
                    delay(50)
                }
            }
            val text = reply.toString()
            if (text.isBlank()) throw IOException("No reply from adapter (timeout).")
            return@withLock text
        }
    }

    suspend fun getDtpCodes(): List<String> = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(600)
            return@withContext DemoObdSource.storedCodes
        }
        val result = runCommand(TroubleCodesCommand()).value
        splitErrors(result)
    }

    suspend fun getPendingDtpCodes(): List<String> = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(300)
            return@withContext DemoObdSource.pendingCodes
        }
        val result = runCommand(PendingTroubleCodesCommand()).value
        splitErrors(result)
    }

    suspend fun getPermanentDtpCodes(): List<String> = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(300)
            return@withContext DemoObdSource.permanentCodes
        }
        val result = runCommand(PermanentTroubleCodesCommand()).value
        splitErrors(result)
    }

    /** Mode 01 PID 01: malfunction-indicator lamp state + ECU's stored-code count. */
    suspend fun getMilStatus(): MilStatus = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(200)
            return@withContext DemoObdSource.milStatus
        }
        val mil = runCommand(MILOnCommand()).value
        val count = runCommand(DTCNumberCommand()).value
        parseMilStatus(mil, count)
    }

    /** Mode 04: ask the ECU to erase stored codes and switch the MIL off. */
    suspend fun clearTroubleCodes(): String = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(600)
            return@withContext "OK"
        }
        runCommand(ResetTroubleCodesCommand()).value
    }

    /** ECU voltage (mode 01-42) + adapter supply voltage (ATRV) + protocol, for the health line. */
    suspend fun getAdapterHealth(): Triple<String, String, String> = withContext(Dispatchers.IO) {
        if (demoMode) return@withContext Triple(DemoObdSource.VOLTAGE, DemoObdSource.ADAPTER_VOLTAGE, DemoObdSource.PROTOCOL)
        val ecuVoltage = runCatching { runCommand(ModuleVoltageCommand()) }
            .map { "${it.value} ${it.unit}".trim() }.getOrDefault("?")
        // ATRV reads the adapter's own supply; a healthy adapter sits near
        // battery voltage. "?" = unreadable, never an invented number.
        val adapterVoltage = runCatching { runCommand(AdapterVoltageCommand()).value }
            .map { parseVoltage(it)?.let { volts -> "$volts V" } ?: "?" }.getOrDefault("?")
        val protocol = runCatching { runCommand(DescribeProtocolNumberCommand()) }
            .map { it.value.trim() }.getOrDefault("?")
        Triple(ecuVoltage, adapterVoltage, protocol)
    }

    /** VIN + distance/time since codes were last cleared (mode 01/09). */
    suspend fun getVehicleInfo(): Triple<String, String, String> = withContext(Dispatchers.IO) {
        if (demoMode) return@withContext Triple(DemoObdSource.VIN, DemoObdSource.SINCE_KM, DemoObdSource.SINCE_MIN)
        val vin = runCatching { runCommand(VINCommand()).value.trim() }.getOrDefault("?")
        val dist = runCatching { runCommand(DistanceSinceCodesClearedCommand()) }
            .map { "${it.value} ${it.unit}".trim() }.getOrDefault("?")
        val time = runCatching { runCommand(TimeSinceCodesClearedCommand()) }
            .map { "${it.value} ${it.unit}".trim() }.getOrDefault("?")
        Triple(vin, dist, time)
    }

    /**
     * Probes the 0100/0120/0140 support bitmasks and caches the result in
     * [ObdDataHolder.supportedPids] (same idea as OBDvis's bitmask discovery,
     * reimplemented on this codebase's defensive parsing). Runs once per
     * process; unknown stays unknown (treated as supported) when the ECU
     * answers NO DATA or the read fails. In demo every registry PID is
     * reported supported.
     */
    suspend fun discoverSupportedPids(): Set<Int> = withContext(Dispatchers.IO) {
        ObdDataHolder.supportedPids?.let { return@withContext it }
        if (demoMode) {
            val all = PidRegistry.all.map { it.pid }.toSet()
            ObdDataHolder.supportedPids = all
            return@withContext all
        }
        val found = mutableSetOf<Int>()
        val ranges = listOf("00" to 0x00, "20" to 0x20, "40" to 0x40)
        for ((rangePid, base) in ranges) {
            val payload = runCatching { runCommand(MySupportedPidsCommand(rangePid)).value }.getOrNull()
            if (payload.isNullOrBlank()) break
            val pids = PidRegistry.parseSupportBitmask(payload, base)
            if (pids.isEmpty()) break
            found.addAll(pids)
            // Last bit of the range = "another range follows" (0x20/0x40/0x60).
            if (!pids.contains(base + 0x20)) break
        }
        if (found.isNotEmpty()) ObdDataHolder.supportedPids = found
        return@withContext ObdDataHolder.supportedPids ?: emptySet()
    }

    private fun disconnectTransports() {
        bluetoothHelper.disconnectFromDevice()
        runCatching { wifiSocket?.close() }
        wifiSocket = null
        inputStream = null
        outputStream = null
    }

    fun disconnectFromObdDevice() {
        // NOTE: demoMode is intentionally NOT reset here. Leaving Live Data
        // (or rotating the phone) destroys the view and calls this; resetting
        // would silently drop demo mode and every later read would fail with
        // "OBD connection not established". setupObd/setupWifi/setupDemo own it.
        disconnectTransports()
        obdConnection = null
    }

    companion object {
        /**
         * Words an ELM327 adapter emits instead of codes ("NO DATA", "SEARCHING...",
         * "STOPPED", "UNABLE TO CONNECT", "?", …). They are filtered so they are
         * never shown as fault codes or sent to the AI backend. No real DTC
         * (P/C/B/U + hex digits) can equal one of these.
         */
        private val ELM_STATUS_TOKENS = setOf(
            "NO", "DATA", "NODATA", "SEARCHING", "STOPPED", "BUS",
            "ERROR", "UNKNOWN", "UNABLE", "TO", "CONNECT", "OK",
            // Clone chatter beyond the classic set: bus/CAN states, voltage
            // resets and timeouts. No real DTC (P/C/B/U + hex) equals these.
            "CAN", "CANERROR", "BUSY", "INIT", "BUSINIT",
            "LOW", "VOLTAGE", "LVRESET", "TIMEOUT", "FCRX"
        )

        fun splitErrors(errors: String): List<String> {
            if (errors.isBlank()) return emptyList()
            return errors.split(Regex("[\\s,>]+"))
                .map { it.trim().trim('.', ':') }
                .filter { it.isNotEmpty() && !isElmStatusToken(it) }
        }

        private fun isElmStatusToken(token: String): Boolean {
            val compact = token.filter { it.isLetterOrDigit() }.uppercase()
            return compact.isEmpty() || compact in ELM_STATUS_TOKENS
        }

        fun parseMilStatus(milValue: String, countValue: String): MilStatus {
            val milOn = milValue.toBooleanStrictOrNull() ?: false
            val count = countValue.toIntOrNull()?.coerceAtLeast(0) ?: 0
            return MilStatus(milOn, count)
        }

        /**
         * Tags every code with the read modes that reported it, so the report
         * can show Stored/Pending/Permanent pills instead of one merged list.
         * Codes are normalized to uppercase; blanks are skipped. Pure logic.
         */
        fun mergeCodeSources(
            stored: List<String>,
            pending: List<String>,
            permanent: List<String>
        ): Map<String, Set<DtcSource>> {
            val merged = linkedMapOf<String, MutableSet<DtcSource>>()
            fun add(codes: List<String>, source: DtcSource) {
                for (code in codes) {
                    val key = code.trim().uppercase()
                    if (key.isEmpty()) continue
                    merged.getOrPut(key) { mutableSetOf() }.add(source)
                }
            }
            add(stored, DtcSource.STORED)
            add(pending, DtcSource.PENDING)
            add(permanent, DtcSource.PERMANENT)
            return merged
        }

        /** First decimal number in adapter text ("13.8 V", "12.6V", …) or null. */
        fun parseVoltage(raw: String): Float? =
            Regex("""[-+]?\d+(\.\d+)?""").find(raw)?.value?.toFloatOrNull()

        /**
         * Maps a read failure to the ElmRecovery action. Bus-level failures
         * (lost connection, failed init) deserve a re-init; STOPPED and
         * unknown I/O just retry; NO DATA and unsupported PIDs are healthy
         * unknowns, not errors. Pure logic, tested.
         */
        fun recoveryFor(error: Throwable): ElmRecovery = when (error) {
            is UnableToConnectException, is BusInitException -> ElmRecovery.REINIT
            is StoppedException -> ElmRecovery.RETRY
            is NoDataException, is UnSupportedCommandException -> ElmRecovery.NONE
            else -> ElmRecovery.RETRY
        }

        /**
         * Splits one concatenated fast-loop reply ("010C0D0504" asked once)
         * back into per-PID values by reusing the tested single-PID handlers,
         * which find their own identifier anywhere in the frame. Missing
         * frames yield blanks, never exceptions.
         */
        fun parseFastBatch(raw: String): Map<Int, String> {
            val response = ObdRawResponse(raw, 0L)
            return mapOf(
                PidRegistry.PID_RPM to MyRPMCommand().handler(response),
                PidRegistry.PID_SPEED to MySpeedCommand().handler(response),
                PidRegistry.PID_COOLANT_TEMP to MyCoolantTempCommand().handler(response),
                PidRegistry.PID_ENGINE_LOAD to MyEngineLoadCommand().handler(response)
            )
        }
    }

    /**
     * Slow background telemetry for the Online AI (polled ~every 30 s, never in
     * the fast gauge loop): stored fault codes, battery voltage, fuel level.
     * Null = unsupported/unreadable; callers must skip silently.
     */
    suspend fun readSlowTelemetry(): SlowTelemetry = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(300)
            return@withContext SlowTelemetry(DemoObdSource.storedCodes, 13.8f, DemoObdSource.FUEL_PCT)
        }
        val codes = runCatching { splitErrors(runCommand(TroubleCodesCommand()).value) }
            .getOrDefault(emptyList())
        val voltage = runCatching { runCommand(ModuleVoltageCommand()).value }
            .getOrNull()?.let { parseVoltage(it) }
        val fuel = runCatching { runCommand(MyFuelLevelCommand()).value.toIntOrNull() }
            .getOrNull()
        SlowTelemetry(codes, voltage, fuel)
    }

    /**
     * Mode 02 freeze frame: the sensor snapshot the ECU stored when a fault
     * was set. Each PID is read independently; unreadable ones stay null so
     * the UI can say "not available" instead of inventing values.
     */
    suspend fun getFreezeFrame(): FreezeFrame = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(300)
            return@withContext FreezeFrame(dtc = "P0301", rpm = 2100, speedKmh = 64, coolantC = 91, loadPct = 43)
        }
        suspend fun read(cmd: String): String? = runCatching { sendRaw(cmd, 2500) }.getOrNull()
        FreezeFrame.parse(
            dtcRaw = read("020200"),
            rpmRaw = read("020C00"),
            speedRaw = read("020D00"),
            coolantRaw = read("020500"),
            loadRaw = read("020400")
        )
    }

    suspend fun startLiveDataMonitoring() = withContext(Dispatchers.IO) {
        ObdDataHolder.isMonitoring.set(true)
        if (demoMode) {
            // Demo values come from the user's sliders (LiveDataFragment), not
            // from a loop — seed the starting point and return.
            val (speed, rpm, coolant) = DemoObdSource.liveValuesAt(0)
            ObdDataHolder.speedFlow.value = "$speed Km/h"
            ObdDataHolder.rpmFlow.value = "$rpm RPM"
            ObdDataHolder.coolantTempFlow.value = "$coolant °C"
            ObdDataHolder.engineLoadFlow.value = "${DemoObdSource.engineLoadAt(0)} %"
            return@withContext
        }
        var errorCount = 0
        // Best effort: learn which PIDs exist so later screens can mark the
        // rest unsupported instead of showing invented values. Never fatal.
        runCatching { discoverSupportedPids() }

        while (ObdDataHolder.isMonitoring.get()) {
            try {
                var attempted = 0
                var failed = 0
                var lastError: Exception? = null

                // One PID never kills the cycle: failures are counted per PID
                // (3 strikes disables it via pidHealth) and only a cycle where
                // EVERYTHING failed still trips the global error path below.
                suspend fun poll(pid: Int, command: ObdCommand, publish: (String, String) -> Unit) {
                    if (pidHealth.isDisabled(pid)) return
                    attempted++
                    try {
                        val response = runCommand(command)
                        // Blank = unreadable: keep the last good value (never
                        // invent 0) and count it against the PID's budget.
                        if (response.value.isNotBlank()) {
                            pidHealth.recordSuccess(pid)
                            publish(response.value, response.unit)
                        } else {
                            pidHealth.recordFailure(pid)
                        }
                    } catch (e: Exception) {
                        failed++
                        lastError = e
                        pidHealth.recordFailure(pid)
                        Log.e("ObdHelper", "Live read failed for PID ${pid.toString(16)}:", e)
                    }
                }

                // Fast path: all four gauges in one round-trip ("010C0D0504").
                // Adapters that reject concatenated PIDs just fail here and
                // the single reads below run instead. Disabled PIDs are
                // skipped in both paths.
                val batchValues = runCatching { sendRaw("010C0D0504", 1500) }
                    .mapCatching { parseFastBatch(it) }.getOrNull()
                var batchGood = 0
                if (batchValues != null) {
                    fun takeBatch(pid: Int, unit: String, publish: (String) -> Unit) {
                        if (pidHealth.isDisabled(pid)) return
                        val value = batchValues[pid].orEmpty()
                        if (value.isNotBlank()) {
                            pidHealth.recordSuccess(pid)
                            publish("$value $unit")
                            batchGood++
                        } else {
                            pidHealth.recordFailure(pid)
                        }
                    }
                    takeBatch(PidRegistry.PID_SPEED, MySpeedCommand().defaultUnit) {
                        ObdDataHolder.speedFlow.value = it
                    }
                    takeBatch(PidRegistry.PID_RPM, MyRPMCommand().defaultUnit) {
                        ObdDataHolder.rpmFlow.value = it
                    }
                    takeBatch(PidRegistry.PID_COOLANT_TEMP, MyCoolantTempCommand().defaultUnit) {
                        ObdDataHolder.coolantTempFlow.value = it
                    }
                    takeBatch(PidRegistry.PID_ENGINE_LOAD, MyEngineLoadCommand().defaultUnit) {
                        ObdDataHolder.engineLoadFlow.value = it
                    }
                }
                if (batchGood == 0) {
                    poll(PidRegistry.PID_SPEED, MySpeedCommand()) { value, unit ->
                        ObdDataHolder.speedFlow.value = "$value $unit"
                    }
                    poll(PidRegistry.PID_RPM, MyRPMCommand()) { value, unit ->
                        ObdDataHolder.rpmFlow.value = "$value $unit"
                    }
                    poll(PidRegistry.PID_COOLANT_TEMP, MyCoolantTempCommand()) { value, unit ->
                        ObdDataHolder.coolantTempFlow.value = "$value $unit"
                    }
                    poll(PidRegistry.PID_ENGINE_LOAD, MyEngineLoadCommand()) { value, unit ->
                        ObdDataHolder.engineLoadFlow.value = "$value $unit"
                    }
                }

                if (attempted > 0 && failed == attempted) {
                    throw lastError ?: IOException("OBD read failed.")
                }

                errorCount = 0
                delay(800)

            } catch (e: Exception) {
                errorCount++
                Log.e("ObdHelper", "Error during live data monitoring (Attempt $errorCount):", e)

                // Bus-level failures get one best-effort re-init so the next
                // cycle does not fail the same way; the 3-strike rule below
                // still gives up on a truly dead link.
                if (!demoMode && isConnected && recoveryFor(e) == ElmRecovery.REINIT) {
                    runCatching { initializeObd() }
                        .onFailure { Log.w("ObdHelper", "Recovery re-init failed", it) }
                }

                if (errorCount >= 3) {
                    ObdDataHolder.isMonitoring.set(false)
                    ObdDataHolder.speedFlow.value = "ERROR"
                    ObdDataHolder.rpmFlow.value = "ERROR"
                    ObdDataHolder.coolantTempFlow.value = "ERROR"
                    ObdDataHolder.engineLoadFlow.value = "ERROR"
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
