package com.catsmoker.obd2ai.ui.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A stopped monitor means unknown input: the trip may not integrate a stale
 * reading into distance it never observed.
 */
class TripSampleTest {

    @Test
    fun `a live monitor passes readings through unchanged`() {
        assertEquals("62 Km/h", TripSample.speedText(true, "62 Km/h"))
        assertEquals("1800 RPM", TripSample.rpmText(true, "1800 RPM"))
        assertEquals("--", TripSample.speedText(true, "--"))
    }

    @Test
    fun `demo counts as live data`() {
        assertTrue(TripSample.dataAllowed(monitoring = false, demo = true))
        assertTrue(TripSample.dataAllowed(monitoring = true, demo = false))
        assertFalse(TripSample.dataAllowed(monitoring = false, demo = false))
    }

    @Test
    fun `no monitor means unknown, not the last reading`() {
        assertEquals(TripSample.UNKNOWN, TripSample.speedText(false, "62 Km/h"))
        assertEquals(TripSample.UNKNOWN, TripSample.rpmText(false, "1800 RPM"))
    }

    @Test
    fun `unknown input adds no distance`() {
        val computer = com.catsmoker.obd2ai.vehicle.TripComputer()
        val start = 1_000_000L
        computer.start(start)
        // Ten seconds of unknown speed must leave distance at zero.
        for (tick in 1..10) {
            computer.sample(
                TripSession.parseSpeed(TripSample.speedText(false, "62 Km/h")),
                TripSession.parseRpm(TripSample.rpmText(false, "1800 RPM")),
                start + tick * 1_000L
            )
        }
        assertEquals(0.0, computer.snapshot(start + 10_000L).distanceKm, 0.0001)
    }
}
