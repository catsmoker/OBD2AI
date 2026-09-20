package com.catsmoker.obd2ai.speedometers

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the speedometer registry and gauge math. */
class SpeedometerStyleTest {

    @Test
    fun `speedometer style ids are unique and round-trip`() {
        val ids = SpeedometerStyle.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        for (style in SpeedometerStyle.entries) {
            assertEquals(style, SpeedometerStyle.fromId(style.id))
        }
    }

    @Test
    fun `speedometer fromId falls back to classic on unknown`() {
        assertEquals(SpeedometerStyle.CLASSIC, SpeedometerStyle.fromId(null))
        assertEquals(SpeedometerStyle.CLASSIC, SpeedometerStyle.fromId("hyperdrive"))
        assertEquals(SpeedometerStyle.CLASSIC, SpeedometerStyle.fromId(""))
    }

    @Test
    fun `speedometer registry covers eight styles`() {
        assertEquals(8, SpeedometerStyle.entries.size)
    }

    @Test
    fun `speedometer clamp keeps the needle on the dial`() {
        assertEquals(0f, SpeedometerStyle.clampSpeed(-5f, 220f))
        assertEquals(220f, SpeedometerStyle.clampSpeed(999f, 220f))
        assertEquals(86f, SpeedometerStyle.clampSpeed(86f, 220f))
        assertEquals(0, SpeedometerStyle.clampRpm(-100, 8000))
        assertEquals(8000, SpeedometerStyle.clampRpm(9000, 8000))
    }

    @Test
    fun `speedometer fraction never invents a position`() {
        assertEquals(0f, SpeedometerStyle.fraction(50f, 0f))
        assertEquals(0f, SpeedometerStyle.fraction(-10f, 220f))
        assertEquals(1f, SpeedometerStyle.fraction(300f, 220f))
        assertEquals(0.5f, SpeedometerStyle.fraction(110f, 220f), 0.001f)
    }

    @Test
    fun `speedometer angle mapping round-trips`() {
        val max = 220f
        assertEquals(
            SpeedometerStyle.MIN_ANGLE,
            SpeedometerStyle.mapSpeedToAngle(0f, max),
            0.001f
        )
        assertEquals(
            SpeedometerStyle.MAX_ANGLE,
            SpeedometerStyle.mapSpeedToAngle(max, max),
            0.001f
        )
        val mid = SpeedometerStyle.mapSpeedToAngle(110f, max)
        assertEquals(110f, SpeedometerStyle.mapAngleToSpeed(mid, max), 0.5f)
    }

    @Test
    fun `speedometer rpm zones follow the shift point`() {
        assertEquals(0, SpeedometerStyle.rpmZone(2000, 3500))
        assertEquals(1, SpeedometerStyle.rpmZone(3000, 3500))
        assertEquals(2, SpeedometerStyle.rpmZone(3600, 3500))
    }

    @Test
    fun `speedometer major ticks stay round in both unit systems`() {
        // 220 km/h -> 11 intervals of exactly 20; 140 mph -> 7 of exactly 20.
        assertEquals(11, SpeedometerStyle.majorTickCount(220f))
        assertEquals(7, SpeedometerStyle.majorTickCount(140f))
        // Degenerate dials still get a usable scale, huge ones stay capped.
        assertEquals(6, SpeedometerStyle.majorTickCount(0f))
        assertEquals(6, SpeedometerStyle.majorTickCount(-50f))
        assertEquals(12, SpeedometerStyle.majorTickCount(400f))
    }

    @Test
    fun `speedometer carousel index wraps around both ends`() {
        val last = SpeedometerStyle.entries.size - 1
        assertEquals(0, SpeedometerStyle.wrappedIndex(0))
        assertEquals(3, SpeedometerStyle.wrappedIndex(3))
        assertEquals(0, SpeedometerStyle.wrappedIndex(last + 1))
        assertEquals(last, SpeedometerStyle.wrappedIndex(-1))
        assertEquals(1, SpeedometerStyle.wrappedIndex(last + 2))
        assertEquals(last - 1, SpeedometerStyle.wrappedIndex(-2))
    }
}
