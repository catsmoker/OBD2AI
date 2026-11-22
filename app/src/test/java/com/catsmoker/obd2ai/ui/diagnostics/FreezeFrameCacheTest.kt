package com.catsmoker.obd2ai.ui.diagnostics

import com.catsmoker.obd2ai.diagnostics.FreezeFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The freeze frame belongs to one scan: it is read once, reused while the
 * user triages codes, and dropped when a new scan or clear starts.
 */
class FreezeFrameCacheTest {

    private val frame = FreezeFrame(dtc = "P0301", rpm = 2100, speedKmh = 64, coolantC = 91, loadPct = 43)

    @Before
    fun reset() = FreezeFrameCache.invalidate()

    @Test
    fun `nothing is loaded before the first read`() {
        assertFalse(FreezeFrameCache.isLoaded())
        assertNull(FreezeFrameCache.get())
    }

    @Test
    fun `a read is reused instead of being repeated`() {
        FreezeFrameCache.put(frame)
        assertTrue(FreezeFrameCache.isLoaded())
        assertEquals(frame, FreezeFrameCache.get())
        // Second lookup must not ask the adapter again.
        assertEquals(frame, FreezeFrameCache.get())
    }

    @Test
    fun `an empty answer is cached too`() {
        FreezeFrameCache.put(null)
        assertTrue(FreezeFrameCache.isLoaded())
        assertNull(FreezeFrameCache.get())
    }

    @Test
    fun `a new scan forgets the previous frame`() {
        FreezeFrameCache.put(frame)
        FreezeFrameCache.invalidate()
        assertFalse(FreezeFrameCache.isLoaded())
        assertNull(FreezeFrameCache.get())
    }
}
