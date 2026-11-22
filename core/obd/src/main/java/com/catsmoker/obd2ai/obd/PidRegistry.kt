package com.catsmoker.obd2ai.obd

/** One live-data sensor the app knows how to read, display and test. */
data class PidDefinition(
    /** Mode 01 PID number, e.g. 0x0D for vehicle speed. */
    val pid: Int,
    /** Plausible display range; readings outside it are treated as garbage, never shown. */
    val min: Double,
    val max: Double,
    val unit: String,
    val decimals: Int = 0,
)

/**
 * Central registry of the Mode 01 PIDs the app supports (idea borrowed from
 * OBDvis's PidRegistry and ObdMetrics' PID groups, reimplemented here in the
 * codebase's own defensive-parsing style). The fast gauge loop stays small;
 * everything else is polled on demand or in slow telemetry, gated by
 * [ObdDataHolder.isPidSupported] after [ObdHelper.discoverSupportedPids] runs.
 */
object PidRegistry {
    const val PID_ENGINE_LOAD = 0x04
    const val PID_COOLANT_TEMP = 0x05
    const val PID_SHORT_TRIM_1 = 0x06
    const val PID_LONG_TRIM_1 = 0x08
    const val PID_RPM = 0x0C
    const val PID_SPEED = 0x0D
    const val PID_TIMING = 0x0E
    const val PID_INTAKE_TEMP = 0x0F
    const val PID_MAF = 0x10
    const val PID_THROTTLE = 0x11
    const val PID_FUEL_LEVEL = 0x2F
    const val PID_VOLTAGE = 0x42

    val all: List<PidDefinition> = listOf(
        PidDefinition(PID_ENGINE_LOAD, 0.0, 100.0, "%"),
        PidDefinition(PID_COOLANT_TEMP, -40.0, 215.0, "°C"),
        PidDefinition(PID_SHORT_TRIM_1, -100.0, 99.2, "%", 1),
        PidDefinition(PID_LONG_TRIM_1, -100.0, 99.2, "%", 1),
        PidDefinition(PID_RPM, 0.0, 8000.0, "RPM"),
        PidDefinition(PID_SPEED, 0.0, 255.0, "Km/h"),
        PidDefinition(PID_TIMING, -64.0, 63.5, "°", 1),
        PidDefinition(PID_INTAKE_TEMP, -40.0, 215.0, "°C"),
        PidDefinition(PID_MAF, 0.0, 655.35, "g/s", 1),
        PidDefinition(PID_THROTTLE, 0.0, 100.0, "%"),
        PidDefinition(PID_FUEL_LEVEL, 0.0, 100.0, "%"),
        PidDefinition(PID_VOLTAGE, 0.0, 30.0, "V", 1),
    )

    fun forPid(pid: Int): PidDefinition? = all.find { it.pid == pid }

    /** True when a parsed reading is inside the plausible range (else: garbage, don't show). */
    fun isPlausible(pid: Int, value: Double): Boolean {
        val def = forPid(pid) ?: return true
        return value in def.min..def.max
    }

    /**
     * Decodes one 0100/0120/0140 support bitmask into the set of supported
     * PID numbers. [payloadHex] is the raw hex after the "41XX" identifier
     * (whitespace/case tolerated). [rangeBase]
     * is 0x00/0x20/0x40 for the 0100/0120/0140 ranges. Bit N (MSB-first) of
     * byte B means PID (rangeBase + B * 8 + N + 1) is supported. Garbage in,
     * empty set out — never throws.
     */
    fun parseSupportBitmask(payloadHex: String, rangeBase: Int): Set<Int> {
        val clean = payloadHex.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.uppercase()
        // Real bitmasks are whole bytes; an odd nibble count means the input
        // was prose/adapter chatter ("NO DATA" filters down to "DAA"), not data.
        if (clean.length < 2 || clean.length % 2 != 0) return emptySet()
        val supported = mutableSetOf<Int>()
        val bytes = clean.chunked(2)
        for ((byteIndex, pair) in bytes.withIndex()) {
            if (pair.length < 2) break
            val byte = runCatching { Integer.parseInt(pair, 16) }.getOrNull() ?: return emptySet()
            for (bit in 0 until 8) {
                if (byte and (0x80 shr bit) != 0) {
                    supported.add(rangeBase + byteIndex * 8 + bit + 1)
                }
            }
        }
        return supported
    }
}
