package com.catsmoker.obd2ai.obd

import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdRawResponse

// Custom OBD commands with corrected parsing logic. Every handler is
// defensive: clones return non-hex garbage, truncated frames and chatter,
// so handlers return blank (never throw, never invent 0) and callers keep
// the last good value instead.

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
            // Clones can return non-hex garbage (e.g. "410DZZ"); never throw from a handler.
            // Blank = unreadable so callers can keep the last good value instead of
            // inventing a 0 (a moving car is never at 0 km/h with the engine running).
            runCatching { Integer.parseInt(speedHex, 16).toString() }.getOrDefault("")
        } else {
            ""
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

            val a = runCatching { Integer.parseInt(aHex, 16) }.getOrNull()
            val b = runCatching { Integer.parseInt(bHex, 16) }.getOrNull()

            // Either byte unreadable (or the frame truncated) -> blank, never
            // an invented 0 RPM: 0 with the ignition on does not exist.
            if (a == null || b == null) "" else (((a * 256) + b) / 4).toString()
        } else {
            ""
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
            runCatching { (Integer.parseInt(tempHex, 16) - 40).toString() }.getOrDefault("")
        } else {
            ""
        }
    }
}

class MyEngineLoadCommand : ObdCommand() {
    override val tag = "ENGINE_LOAD"
    override val name = "Calculated Engine Load"
    override val mode = "01"
    override val pid = "04"
    override val defaultUnit = "%"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "4104"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 2) {
            val loadHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            // Empty string = unsupported/missing so callers can skip silently.
            runCatching { (Integer.parseInt(loadHex, 16) * 100 / 255).toString() }.getOrDefault("")
        } else {
            ""
        }
    }
}

class MyFuelLevelCommand : ObdCommand() {
    override val tag = "FUEL_LEVEL"
    override val name = "Fuel Tank Level"
    override val mode = "01"
    override val pid = "2F"
    override val defaultUnit = "%"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "412F"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 2) {
            val fuelHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            runCatching { (Integer.parseInt(fuelHex, 16) * 100 / 255).toString() }.getOrDefault("")
        } else {
            ""
        }
    }
}

/** Shared single-byte-percentage parsing (throttlenioskich 0x11): A * 100 / 255. Blank on failure. */
private fun parseSingleBytePercent(rawValue: String, identifier: String): String {
    val index = rawValue.indexOf(identifier)
    if (index != -1 && rawValue.length >= index + identifier.length + 2) {
        val hex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
        return runCatching { (Integer.parseInt(hex, 16) * 100 / 255).toString() }.getOrDefault("")
    }
    return ""
}

class MyThrottleCommand : ObdCommand() {
    override val tag = "THROTTLE_POSITION"
    override val name = "Throttle Position"
    override val mode = "01"
    override val pid = "11"
    override val defaultUnit = "%"

    override val handler = { it: ObdRawResponse ->
        parseSingleBytePercent(it.processedValue, "4111")
    }
}

class MyIntakeTempCommand : ObdCommand() {
    override val tag = "INTAKE_TEMP"
    override val name = "Intake Air Temperature"
    override val mode = "01"
    override val pid = "0F"
    override val defaultUnit = "°C"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "410F"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 2) {
            val tempHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            runCatching { (Integer.parseInt(tempHex, 16) - 40).toString() }.getOrDefault("")
        } else {
            ""
        }
    }
}

class MyMafCommand : ObdCommand() {
    override val tag = "MAF"
    override val name = "Mass Air Flow"
    override val mode = "01"
    override val pid = "10"
    override val defaultUnit = "g/s"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "4110"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 4) {
            val aHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            val bHex = rawValue.substring(index + identifier.length + 2, index + identifier.length + 4)
            val a = runCatching { Integer.parseInt(aHex, 16) }.getOrNull()
            val b = runCatching { Integer.parseInt(bHex, 16) }.getOrNull()
            // One decimal, US locale so clones in any locale still parse downstream.
            if (a == null || b == null) "" else "%.1f".format(java.util.Locale.US, (a * 256 + b) / 100.0)
        } else {
            ""
        }
    }
}

class MyTimingCommand : ObdCommand() {
    override val tag = "TIMING_ADVANCE"
    override val name = "Timing Advance"
    override val mode = "01"
    override val pid = "0E"
    override val defaultUnit = "°"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "410E"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 2) {
            val hex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            // (A / 2) - 64: negative values are valid (retarded timing).
            runCatching {
                "%.1f".format(java.util.Locale.US, Integer.parseInt(hex, 16) / 2.0 - 64.0)
            }.getOrDefault("")
        } else {
            ""
        }
    }
}

/** Shared fuel-trim parsing (0x06-0x09): (A - 128) * 100 / 128, one decimal. Blank on failure. */
private fun parseFuelTrim(rawValue: String, identifier: String): String {
    val index = rawValue.indexOf(identifier)
    if (index != -1 && rawValue.length >= index + identifier.length + 2) {
        val hex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
        return runCatching {
            "%.1f".format(java.util.Locale.US, (Integer.parseInt(hex, 16) - 128) * 100.0 / 128.0)
        }.getOrDefault("")
    }
    return ""
}

class MyShortFuelTrimCommand : ObdCommand() {
    override val tag = "SHORT_FUEL_TRIM_1"
    override val name = "Short Term Fuel Trim (Bank 1)"
    override val mode = "01"
    override val pid = "06"
    override val defaultUnit = "%"

    override val handler = { it: ObdRawResponse ->
        parseFuelTrim(it.processedValue, "4106")
    }
}

class MyLongFuelTrimCommand : ObdCommand() {
    override val tag = "LONG_FUEL_TRIM_1"
    override val name = "Long Term Fuel Trim (Bank 1)"
    override val mode = "01"
    override val pid = "08"
    override val defaultUnit = "%"

    override val handler = { it: ObdRawResponse ->
        parseFuelTrim(it.processedValue, "4108")
    }
}

/**
 * Mode 01 PID-support probe (0100/0120/0140): returns the raw bitmask payload
 * after the "41XX" identifier, or blank when the ECU answers NO DATA. Parsed
 * by [PidRegistry.parseSupportBitmask].
 */
class MySupportedPidsCommand(private val rangePid: String) : ObdCommand() {
    override val tag = "SUPPORTED_PIDS_$rangePid"
    override val name = "Supported PIDs ($rangePid)"
    override val mode = "01"
    override val pid = rangePid
    override val defaultUnit = ""

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        // processedValue strips whitespace, so "41 00 BE 1F" arrives as "4100BE1F".
        val identifier = "41$rangePid"
        val index = rawValue.indexOf(identifier)
        if (index != -1) {
            rawValue.substring(index + identifier.length).filter { c -> c.isLetterOrDigit() }
        } else {
            ""
        }
    }
}
