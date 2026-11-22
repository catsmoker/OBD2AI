package com.catsmoker.obd2ai.ui.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cache without a timestamp must not be dressed up with a date: the label
 * falls back to the undated wording instead of inventing when the scan ran.
 */
class CachedScanLabelTest {

    @Test
    fun `a real timestamp is printed`() {
        assertTrue(CachedScanLabel.hasDate(1_700_000_000_000L))
    }

    @Test
    fun `missing, zero or legacy values have no date`() {
        assertFalse(CachedScanLabel.hasDate(null))
        assertFalse(CachedScanLabel.hasDate(0L))
        assertFalse(CachedScanLabel.hasDate(-1L))
    }
}
