package com.catsmoker.obd2ai.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the OBD-only trip computer. */
class TripComputerTest {

    @Test
    fun `trip integrates distance from speed over time`() {
        val trip = TripComputer()
        trip.start(0L)
        // 100 km/h held for 36 s = exactly 1 km.
        for (s in 1..36) trip.sample(100.0, 2500, s * 1000L)
        val snap = trip.snapshot(36_000L)
        assertEquals(1.0, snap.distanceKm, 0.01)
        assertEquals(100.0, snap.avgKmh, 0.5)
        assertEquals(100.0, snap.maxKmh, 0.0)
        assertEquals(2500, snap.maxRpm)
    }

    @Test
    fun `trip splits idle and drive time`() {
        val trip = TripComputer()
        trip.start(0L)
        for (s in 1..10) trip.sample(0.0, 800, s * 1000L)
        for (s in 11..20) trip.sample(60.0, 2000, s * 1000L)
        val snap = trip.snapshot(20_000L)
        assertEquals(10_000L, snap.idleMs)
        assertEquals(10_000L, snap.driveMs)
        assertEquals(20_000L, snap.durationMs)
    }

    @Test
    fun `trip stop freezes totals and reset clears`() {
        val trip = TripComputer()
        trip.start(0L)
        trip.sample(50.0, 2000, 10_000L)
        trip.stop(10_000L)
        trip.sample(50.0, 2000, 20_000L)
        assertEquals(10_000L, trip.snapshot(30_000L).durationMs)
        trip.reset()
        val snap = trip.snapshot(30_000L)
        assertEquals(0L, snap.durationMs)
        assertEquals(0.0, snap.distanceKm, 0.0)
        assertEquals(0, snap.maxRpm)
    }

    @Test
    fun `trip tolerates nulls and ignores samples before start`() {
        val trip = TripComputer()
        trip.sample(80.0, 3000, 5_000L)
        trip.start(10_000L)
        trip.sample(null, null, 11_000L)
        val snap = trip.snapshot(11_000L)
        assertEquals(0.0, snap.distanceKm, 0.0)
        assertEquals(0, snap.maxRpm)
        assertEquals(1_000L, snap.durationMs)
    }

    @Test
    fun `trip formats durations as h-mm-ss`() {
        assertEquals("0:00:00", TripComputer.formatDuration(0L))
        assertEquals("0:01:05", TripComputer.formatDuration(65_000L))
        assertEquals("2:10:00", TripComputer.formatDuration(7_800_000L))
    }
}
