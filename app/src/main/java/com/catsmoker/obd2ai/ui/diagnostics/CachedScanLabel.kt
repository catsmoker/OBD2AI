package com.catsmoker.obd2ai.ui.diagnostics

/**
 * Wording for the reopened overview: a list restored from disk is a *saved*
 * scan, never a live one, and a legacy file without a timestamp must not
 * invent a date. Pure logic, tested.
 */
object CachedScanLabel {

    /** True when the saved scan carries a real timestamp to print. */
    fun hasDate(savedAtMs: Long?): Boolean = savedAtMs != null && savedAtMs > 0L
}
