package com.catsmoker.obd2ai.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The cache exists to skip re-parsing, not to remember anything the app did not
 * write: a fresh put replaces the entry and clear() leaves nothing behind.
 */
class ScanCacheTest {

    private fun scan(code: String, savedAtMs: Long?) = SavedScan(
        listOf(DtpCodeDTO(code, ErrorSeverity.LOW, "T", "D", "I", emptyList())),
        savedAtMs
    )

    @Test
    fun `empty until something is stored`() {
        assertNull(ScanCache().get())
    }

    @Test
    fun `a stored scan comes back unchanged`() {
        val cache = ScanCache()
        val saved = scan("P0301", 1_700_000_000_000L)
        cache.put(saved)
        assertEquals(saved, cache.get())
    }

    @Test
    fun `a later write replaces the entry instead of piling up`() {
        val cache = ScanCache()
        cache.put(scan("P0301", 1L))
        cache.put(scan("P0420", 2L))
        assertEquals("P0420", cache.get()?.codes?.single()?.errorCode)
        assertEquals(2L, cache.get()?.savedAtMs)
    }

    @Test
    fun `clearing forgets everything`() {
        val cache = ScanCache()
        cache.put(scan("P0301", 1L))
        cache.clear()
        assertNull(cache.get())
    }
}
