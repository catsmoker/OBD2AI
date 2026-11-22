package com.catsmoker.obd2ai.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stored trip JSON must round-trip and reject garbage instead of inventing a trip. */
class TripStoreTest {

    private val state = TripState(
        startMs = 1_000L,
        lastMs = 42_000L,
        elapsedMs = 500L,
        distanceKm = 12.34,
        maxKmh = 98.5,
        maxRpm = 4120,
        idleMs = 5_000L,
        driveMs = 36_000L
    )

    @Test
    fun `round trip keeps the running trip`() {
        assertEquals(state, TripStore.fromJson(TripStore.toJson(state)))
    }

    @Test
    fun `a stopped trip round trips with null timestamps`() {
        val stopped = state.copy(startMs = null, lastMs = null)
        val restored = TripStore.fromJson(TripStore.toJson(stopped))
        assertEquals(stopped, restored)
        assertNull(restored?.startMs)
    }

    @Test
    fun `garbage and empty payloads are not a trip`() {
        assertNull(TripStore.fromJson(null))
        assertNull(TripStore.fromJson(""))
        assertNull(TripStore.fromJson("{}"))
        assertNull(TripStore.fromJson("not json"))
    }

    @Test
    fun `negative values are clamped, never displayed as such`() {
        val restored = TripStore.fromJson(
            """{"elapsedMs":-5,"distanceKm":-3.2,"maxKmh":-1,"maxRpm":-9,"idleMs":-1,"driveMs":-1}"""
        )
        assertTrue(restored != null)
        assertEquals(0L, restored!!.elapsedMs)
        assertEquals(0.0, restored.distanceKm, 0.0)
        assertEquals(0, restored.maxRpm)
    }
}
