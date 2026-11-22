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

    @Test
    fun `a sampling gap is not turned into invented distance`() {
        val trip = TripComputer()
        trip.start(0L)
        trip.sample(100.0, 2000, 1_000L)
        // Sampler paused for 30 minutes (backgrounded/screen off), vehicle
        // then reported 100 km/h again: only the trusted 5 s window counts.
        trip.sample(100.0, 2000, 30 * 60_000L)
        val snap = trip.snapshot(30 * 60_000L)
        val trusted = (1_000L + TripComputer.MAX_SAMPLE_GAP_MS) / 3_600_000.0 * 100.0
        assertEquals(trusted, snap.distanceKm, 1e-6)
        // Wall-clock duration is still the real elapsed time.
        assertEquals(30 * 60_000L, snap.durationMs)
    }

    @Test
    fun `export and restore keep every total`() {
        val trip = TripComputer()
        trip.start(1_000L)
        for (s in 1..10) trip.sample(80.0, 2400, 1_000L + s * 1_000L)
        trip.stop(11_000L)
        val state = trip.exportState()

        val restored = TripComputer()
        restored.restoreState(state)
        val a = trip.snapshot(11_000L)
        val b = restored.snapshot(11_000L)
        assertEquals(a, b)
    }

    @Test
    fun `a trip resumed moments after a restart keeps running`() {
        val state = TripState(
            startMs = 1_000L,
            lastMs = 10_000L,
            elapsedMs = 0L,
            distanceKm = 0.2,
            maxKmh = 80.0,
            maxRpm = 2400,
            idleMs = 2_000L,
            driveMs = 7_000L
        )
        val resumed = state.afterRestart(nowMs = 12_000L)
        assertEquals(1_000L, resumed.startMs)
        assertEquals(state.distanceKm, resumed.distanceKm, 1e-9)
    }

    @Test
    fun `a trip interrupted for hours comes back stopped with its totals`() {
        val state = TripState(
            startMs = 1_000L,
            lastMs = 10_000L,
            elapsedMs = 0L,
            distanceKm = 0.2,
            maxKmh = 80.0,
            maxRpm = 2400,
            idleMs = 2_000L,
            driveMs = 7_000L
        )
        // Killed and reopened an hour later: not running, totals untouched.
        val resumed = state.afterRestart(nowMs = 10_000L + 3_600_000L)
        org.junit.Assert.assertNull(resumed.startMs)
        assertEquals(9_000L, resumed.elapsedMs)
        assertEquals(0.2, resumed.distanceKm, 1e-9)
        assertEquals(80.0, resumed.maxKmh, 1e-9)
    }
}
