package com.catsmoker.obd2ai.ui.trip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hint is about polling, not failure: only a running real-adapter trip is
 * not being measured, so only that case earns a note.
 */
class TripNoticeTest {

    @Test
    fun `a running real trip explains the standing distance`() {
        assertTrue(TripNotice.showDistanceHint(running = true, demo = false))
    }

    @Test
    fun `demo keeps moving, so it needs no hint`() {
        assertFalse(TripNotice.showDistanceHint(running = true, demo = true))
    }

    @Test
    fun `an idle trip says nothing`() {
        assertFalse(TripNotice.showDistanceHint(running = false, demo = false))
    }
}
