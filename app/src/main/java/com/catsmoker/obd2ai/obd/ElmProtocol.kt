package com.catsmoker.obd2ai.obd

/** What the caller should do about an [ElmStatus]: nothing, retry once, or re-init the adapter. */
enum class ElmRecovery { NONE, RETRY, REINIT }

/**
 * Adapter states decoded from raw ELM327 text. The mechanism is studied from
 * LTSupportAutomotive's input filter and ObdMetrics' AdapterErrorType, then
 * reimplemented here in this codebase's defensive style. The check order in
 * [ElmSanitizer.classifyStatus] is load-bearing: specific states first,
 * generic ERROR and "?" last.
 */
enum class ElmStatus(val recovery: ElmRecovery) {
    NONE(ElmRecovery.NONE),
    OK(ElmRecovery.NONE),
    NO_DATA(ElmRecovery.NONE),
    SEARCHING(ElmRecovery.RETRY),
    STOPPED(ElmRecovery.RETRY),
    UNABLE_TO_CONNECT(ElmRecovery.REINIT),
    BUS_BUSY(ElmRecovery.RETRY),
    BUS_ERROR(ElmRecovery.REINIT),
    CAN_ERROR(ElmRecovery.REINIT),
    LOW_VOLTAGE_RESET(ElmRecovery.REINIT),
    GENERIC_ERROR(ElmRecovery.RETRY);
}

/**
 * Clone-proofing for raw ELM327 text: cheap adapters inject control bytes,
 * high bytes and chatter around real frames. All functions are pure (no
 * Android imports) so they run in JVM tests.
 */
object ElmSanitizer {
    /**
     * Drops non-printable bytes (keeping tab/CR/LF as separators), splits
     * into lines and drops empties. "410D1F\\r\\r>\\nSEARCHING..." becomes
     * ["410D1F", ">", "SEARCHING..."].
     */
    fun sanitizeLines(raw: String): List<String> =
        raw.filter { c -> c == '\t' || c == '\n' || c == '\r' || c.code in 0x20..0x7E }
            .split('\r', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** True for frames that are only hex digits and spaces ("41 0D 1F"). */
    fun isValidPidLine(line: String): Boolean =
        line.isNotEmpty() && line.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' || it == ' ' }

    /**
     * Names the adapter state in raw text. Specific states win over generic
     * markers ("CAN ERROR" is CAN_ERROR, not GENERIC_ERROR; "?" never
     * swallows a real state), and a bare OK token wins over prose that
     * merely contains those letters ("BROKEN" is NONE).
     */
    fun classifyStatus(raw: String): ElmStatus {
        val text = raw.uppercase()
        fun has(vararg needles: String) = needles.any { it in text }
        val tokens = text.split(Regex("\\s+"))
        return when {
            has("NO DATA", "NODATA") -> ElmStatus.NO_DATA
            has("SEARCHING") -> ElmStatus.SEARCHING
            has("STOPPED") -> ElmStatus.STOPPED
            has("UNABLE TO CONNECT", "UNABLETOCONNECT") -> ElmStatus.UNABLE_TO_CONNECT
            has("BUS BUSY", "BUSBUSY") -> ElmStatus.BUS_BUSY
            has("BUS ERROR", "BUSERROR", "BUSINIT", "BUS INIT") -> ElmStatus.BUS_ERROR
            has("CAN ERROR", "CANERROR") -> ElmStatus.CAN_ERROR
            has("LVRESET", "LOW VOLTAGE", "LOWVOLTAGE") -> ElmStatus.LOW_VOLTAGE_RESET
            has("ERROR") -> ElmStatus.GENERIC_ERROR
            "?" in text -> ElmStatus.GENERIC_ERROR
            tokens.any { it == "OK" } -> ElmStatus.OK
            else -> ElmStatus.NONE
        }
    }
}

/**
 * Per-PID failure budget (AndrOBD's MAX_ERROR_COUNT idea, reimplemented):
 * a PID that fails repeatedly is dropped from the poll loop instead of
 * wasting bus time and risking invented values. A single success clears the
 * streak. Pure logic, tested.
 */
class PidHealthTracker(private val maxErrors: Int = 3) {
    private val badStreaks = mutableMapOf<Int, Int>()
    private val disabled = mutableSetOf<Int>()

    fun recordSuccess(pid: Int) {
        badStreaks.remove(pid)
    }

    /** Records one failed read; returns true when the PID just got disabled. */
    fun recordFailure(pid: Int): Boolean {
        if (pid in disabled) return true
        val streak = (badStreaks[pid] ?: 0) + 1
        return if (streak >= maxErrors) {
            disabled.add(pid)
            badStreaks.remove(pid)
            true
        } else {
            badStreaks[pid] = streak
            false
        }
    }

    fun isDisabled(pid: Int): Boolean = pid in disabled

    fun disabledPids(): Set<Int> = disabled.toSet()

    fun reset() {
        badStreaks.clear()
        disabled.clear()
    }
}
