package com.catsmoker.obd2ai.obd

import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.diagnostics.MilStatus
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-memory live telemetry flows plus the latest DTC results. GPS speed
 * stays here too (never persisted). UI collects these lifecycle-aware;
 * unknown reads keep their "--" placeholders — never invented values.
 */
object ObdDataHolder {
    var dtpResults: List<DtpCodeDTO> = emptyList()
    var lastMil: MilStatus? = null
    val isMonitoring = AtomicBoolean(false)
    val speedFlow = MutableStateFlow("-- km/h")
    val rpmFlow = MutableStateFlow("-- RPM")
    val coolantTempFlow = MutableStateFlow("-- °C")
    val engineLoadFlow = MutableStateFlow("-- %")
    val voltageFlow = MutableStateFlow("-- V")
    val fuelFlow = MutableStateFlow("-- %")
    /**
     * PIDs the vehicle confirmed via the 0100/0120/0140 support bitmasks
     * (see PidRegistry). Null = not probed yet — callers must treat every
     * PID as potentially supported until discovery runs.
     */
    var supportedPids: Set<Int>? = null

    /** Null (unknown) counts as supported; only an explicit probe can rule a PID out. */
    fun isPidSupported(pid: Int): Boolean = supportedPids?.contains(pid) ?: true
}

/**
 * Slow background telemetry for the Online AI (polled ~every 30 s, never in
 * the fast gauge loop): stored fault codes, battery voltage, fuel level.
 * Null = unsupported/unreadable; callers must skip silently.
 */
data class SlowTelemetry(
    val codes: List<String>,
    val voltageV: Float?,
    val fuelPct: Int?
)

/**
 * Simulated adapter (inspired by the SwiftOBD2 MOCKComm and AndrOBD demo ideas
 * in the reference projects): lets users explore the whole app with no car.
 */
object DemoObdSource {
    val storedCodes = listOf("P0301", "P0455")
    val pendingCodes = listOf("P0420")
    val permanentCodes = emptyList<String>()
    val milStatus = MilStatus(milOn = true, storedCount = 2)
    const val VOLTAGE = "13.8"
    const val ADAPTER_VOLTAGE = "13.5"
    const val PROTOCOL = "ISO 15765-4 CAN"
    const val VIN = "1M8GDM9AXKP042788"
    const val SINCE_KM = "145 Km"
    const val SINCE_MIN = "320 min"

    /** Drive-cycle-like waves so gauges move realistically. */
    fun liveValuesAt(second: Int): Triple<Int, Int, Int> {
        val t = second.toDouble()
        val speed = (62 + 38 * kotlin.math.sin(t / 9.0)).toInt().coerceIn(0, 220)
        val rpm = (2300 + 1700 * kotlin.math.sin(t / 5.0 + 1.0)).toInt().coerceIn(800, 5200)
        val coolant = (89 + 3 * kotlin.math.sin(t / 25.0)).toInt()
        return Triple(speed, rpm, coolant)
    }

    /** Calculated engine load wave (%) for the demo dashboard. */
    fun engineLoadAt(second: Int): Int {
        val t = second.toDouble()
        return (42 + 25 * kotlin.math.sin(t / 7.0)).toInt().coerceIn(5, 95)
    }

    /**
     * Correlated demo waves for the extended PID set. They deliberately share
     * phases with [liveValuesAt]/[engineLoadAt] so the fake vehicle behaves
     * coherently: throttle follows load, MAF follows RPM × load, intake air
     * sits near ambient, timing retards under load, trims oscillate mildly.
     */
    fun throttleAt(second: Int): Int {
        val t = second.toDouble()
        return (35 + 28 * kotlin.math.sin(t / 7.0 + 0.4)).toInt().coerceIn(0, 100)
    }

    fun intakeTempAt(second: Int): Int {
        val t = second.toDouble()
        return (31 + 4 * kotlin.math.sin(t / 31.0)).toInt().coerceIn(-40, 120)
    }

    fun mafAt(second: Int): Double {
        val rpm = liveValuesAt(second).second.toDouble()
        val load = engineLoadAt(second).toDouble()
        // Toy correlation: airflow scales with RPM × load, normalized to g/s.
        return ((rpm / 1000.0) * (load / 100.0) * 28.0).coerceIn(1.0, 220.0)
    }

    fun timingAt(second: Int): Double {
        val t = second.toDouble()
        val load = engineLoadAt(second).toDouble()
        return (28.0 - load * 0.28 + 4.0 * kotlin.math.sin(t / 11.0)).coerceIn(-20.0, 50.0)
    }

    fun shortTrimAt(second: Int): Double {
        val t = second.toDouble()
        return (3.5 * kotlin.math.sin(t / 13.0)).coerceIn(-15.0, 15.0)
    }

    fun longTrimAt(second: Int): Double {
        val t = second.toDouble()
        return (2.0 + 1.5 * kotlin.math.sin(t / 47.0)).coerceIn(-15.0, 15.0)
    }

    const val FUEL_PCT = 64
}
