package com.catsmoker.obd2ai.diagnostics

/**
 * One-entry cache for the parsed assessment file.
 *
 * The detail screens re-read `dtc_results.json` on every visit, and parsing a
 * JSON array per tap is wasted work for a document that only changes when this
 * process saves it. The cache is invalidated by [DtcStore.save], so what it
 * holds is always what was last written — never a stale file nobody refreshed.
 *
 * Pure logic, tested.
 */
internal class ScanCache {

    @Volatile
    private var entry: SavedScan? = null

    fun get(): SavedScan? = entry

    fun put(scan: SavedScan) {
        entry = scan
    }

    fun clear() {
        entry = null
    }
}
