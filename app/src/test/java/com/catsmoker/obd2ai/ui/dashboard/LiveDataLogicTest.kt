package com.catsmoker.obd2ai.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the dashboard's pure companion logic: RPM tiers/zones, cue
 * edges, AI switch state machines, cooldowns and Easter-egg helpers.
 */
class LiveDataLogicTest {

    @Test
    fun `nextRpmTier escalates through the bands`() {
        assertEquals(1, LiveDataFragment.nextRpmTier(1500, 0))
        assertEquals(2, LiveDataFragment.nextRpmTier(2500, 1))
        assertEquals(3, LiveDataFragment.nextRpmTier(3500, 2))
        assertEquals(4, LiveDataFragment.nextRpmTier(4600, 3))
    }

    @Test
    fun `nextRpmTier jumps straight to danger on a spike`() {
        assertEquals(4, LiveDataFragment.nextRpmTier(5000, 0))
    }

    @Test
    fun `nextRpmTier holds while hovering inside a band`() {
        assertEquals(4, LiveDataFragment.nextRpmTier(4450, 4))
        assertEquals(0, LiveDataFragment.nextRpmTier(950, 0))
        assertEquals(4, LiveDataFragment.nextRpmTier(4500, 4))
    }

    @Test
    fun `nextRpmTier steps down through hysteresis gaps`() {
        // Danger releases below 4400, not at the 4500 trigger edge.
        assertEquals(4, LiveDataFragment.nextRpmTier(4450, 4))
        assertEquals(3, LiveDataFragment.nextRpmTier(4300, 4))
        assertEquals(2, LiveDataFragment.nextRpmTier(2500, 3))
        assertEquals(1, LiveDataFragment.nextRpmTier(1500, 2))
        assertEquals(0, LiveDataFragment.nextRpmTier(500, 1))
    }

    @Test
    fun `nextRpmTier drops straight to zero from a stall`() {
        assertEquals(0, LiveDataFragment.nextRpmTier(500, 4))
    }

    @Test
    fun `rpmZoneColor paints green amber red bands`() {
        val green = LiveDataFragment.rpmZoneColor(0)
        assertEquals(green, LiveDataFragment.rpmZoneColor(2499))
        val amber = LiveDataFragment.rpmZoneColor(2500)
        assertEquals(amber, LiveDataFragment.rpmZoneColor(3499))
        val red = LiveDataFragment.rpmZoneColor(3500)
        assertEquals(red, LiveDataFragment.rpmZoneColor(6000))
        assertTrue(green != amber)
        assertTrue(amber != red)
    }

    @Test
    fun `rpmSections are contiguous and cover the whole dial`() {
        // The gauge library throws when a section conflicts with its neighbour,
        // so the zones must tile [0, 1] with no gaps or overlaps.
        val sections = LiveDataFragment.rpmSections(60f)
        assertEquals(3, sections.size)
        assertEquals(0f, sections.first().first, 0f)
        assertEquals(1f, sections.last().second, 0f)
        for (i in sections.indices) {
            val (start, end, _) = sections[i]
            assertTrue("section $i inverted: $start..$end", start < end)
            assertTrue("section $i out of range", start >= 0f && end <= 1f)
            if (i > 0) {
                assertEquals("gap/overlap at $i", sections[i - 1].second, start, 0f)
            }
        }
    }

    @Test
    fun `rpmSections agree with rpmZoneColor`() {
        val sections = LiveDataFragment.rpmSections(60f)
        fun colorAt(rpm: Int): Int {
            val offset = rpm / 6000f
            return sections.first { offset >= it.first && offset < it.second || offset == 1f && it.second == 1f }.third
        }
        assertEquals(LiveDataFragment.rpmZoneColor(1500), colorAt(1500))
        assertEquals(LiveDataFragment.rpmZoneColor(3000), colorAt(3000))
        assertEquals(LiveDataFragment.rpmZoneColor(5000), colorAt(5000))
    }

    @Test
    fun `shiftCrossed fires only on the rising edge`() {
        assertTrue(LiveDataFragment.shiftCrossed(3400, 3500, 3500))
        assertTrue(!LiveDataFragment.shiftCrossed(3500, 3600, 3500))
        assertTrue(!LiveDataFragment.shiftCrossed(3400, 3400, 3500))
        assertTrue(!LiveDataFragment.shiftCrossed(3600, 3400, 3500))
        assertTrue(!LiveDataFragment.shiftCrossed(1000, 2000, 3500))
    }

    @Test
    fun `crossedDown fires only on the falling edge`() {
        assertTrue(LiveDataFragment.crossedDown(800, 0, 400))
        assertTrue(!LiveDataFragment.crossedDown(300, 0, 400))
        assertTrue(!LiveDataFragment.crossedDown(800, 500, 400))
        assertTrue(!LiveDataFragment.crossedDown(0, 800, 400))
        assertTrue(!LiveDataFragment.crossedDown(400, 400, 400))
    }

    @Test
    fun `offlineAiReady enforces the cooldown`() {
        assertTrue(LiveDataFragment.offlineAiReady(0L, 15_000L, 15_000L))
        assertTrue(!LiveDataFragment.offlineAiReady(1000L, 15_000L, 15_000L))
        assertTrue(LiveDataFragment.offlineAiReady(1000L, 16_000L, 15_000L))
        assertTrue(LiveDataFragment.offlineAiReady(0L, 0L, 0L))
    }

    @Test
    fun `bannerDurationMs gives one second per word`() {
        assertEquals(3_000L, LiveDataFragment.bannerDurationMs("Shift up now."))
        assertEquals(
            9_000L,
            LiveDataFragment.bannerDurationMs("Coolant overheat stop safely and check cooling now please")
        )
        assertEquals(2_000L, LiveDataFragment.bannerDurationMs(""))
        assertEquals(2_000L, LiveDataFragment.bannerDurationMs("   "))
        assertEquals(2_000L, LiveDataFragment.bannerDurationMs("Go"))
    }

    @Test
    fun `speedEasterTier splits 130 140 150`() {
        assertEquals(0, LiveDataFragment.speedEasterTier(0f))
        assertEquals(0, LiveDataFragment.speedEasterTier(129.9f))
        assertEquals(1, LiveDataFragment.speedEasterTier(130f))
        assertEquals(1, LiveDataFragment.speedEasterTier(139.9f))
        assertEquals(2, LiveDataFragment.speedEasterTier(140f))
        assertEquals(2, LiveDataFragment.speedEasterTier(149.9f))
        assertEquals(3, LiveDataFragment.speedEasterTier(150f))
        assertEquals(3, LiveDataFragment.speedEasterTier(220f))
        assertEquals(LiveDataFragment.POOL_SPEED_130, LiveDataFragment.poolForSpeedTier(1))
        assertEquals(LiveDataFragment.POOL_SPEED_140, LiveDataFragment.poolForSpeedTier(2))
        assertEquals(LiveDataFragment.POOL_SPEED_150, LiveDataFragment.poolForSpeedTier(3))
        assertEquals(LiveDataFragment.POOL_SPEED_130, LiveDataFragment.poolForSpeedTier(0))
    }

    @Test
    fun `rollEasterEgg respects probability edges`() {
        assertTrue(LiveDataFragment.rollEasterEgg(0f, 0.5f))
        assertTrue(!LiveDataFragment.rollEasterEgg(0.5f, 0.5f))
        assertTrue(!LiveDataFragment.rollEasterEgg(0.99f, 0.5f))
        assertTrue(!LiveDataFragment.rollEasterEgg(0f, 0f))
        assertTrue(LiveDataFragment.rollEasterEgg(0.999f, 1f))
        assertTrue(!LiveDataFragment.rollEasterEgg(-0.1f, 0.5f))
    }

    @Test
    fun `pickFreshLine skips recent lines and rotates`() {
        val pool = listOf("a", "b", "c")
        val recent = ArrayDeque(listOf("a", "b"))
        assertEquals("c", LiveDataFragment.pickFreshLine(pool, recent, 0))
        assertEquals("c", LiveDataFragment.pickFreshLine(pool, recent, 1))
        assertEquals("a", LiveDataFragment.pickFreshLine(pool, ArrayDeque(), 0))
        assertEquals(null, LiveDataFragment.pickFreshLine(pool, ArrayDeque(listOf("a", "b", "c")), 0))
        assertEquals(null, LiveDataFragment.pickFreshLine(emptyList(), ArrayDeque(), 0))
    }

    @Test
    fun `comboActive needs both thresholds at once`() {
        assertTrue(LiveDataFragment.comboActive(5600, 5500, 125f, 120))
        assertTrue(!LiveDataFragment.comboActive(5400, 5500, 125f, 120))
        assertTrue(!LiveDataFragment.comboActive(5600, 5500, 110f, 120))
        assertTrue(!LiveDataFragment.comboActive(0, 5500, 0f, 120))
    }

    @Test
    fun `easterPoolConfig keeps ultra rare and unknown pools silent`() {
        val ultra = LiveDataFragment.easterPoolConfig(LiveDataFragment.POOL_ULTRA)
        assertTrue(ultra.probability in 0f..0.05f)
        assertTrue(ultra.cooldownMs >= 60 * 60_000L)
        for (pool in listOf(
            LiveDataFragment.POOL_SPEED_130,
            LiveDataFragment.POOL_SPEED_140,
            LiveDataFragment.POOL_SPEED_150,
            LiveDataFragment.POOL_RPM,
            LiveDataFragment.POOL_COMBO,
            LiveDataFragment.POOL_COOLANT_AFTER,
            LiveDataFragment.POOL_FAULT_NEW,
            LiveDataFragment.POOL_FAULT_RETURN,
            LiveDataFragment.POOL_FAULT_SPEEDING
        )) {
            val config = LiveDataFragment.easterPoolConfig(pool)
            assertTrue("$pool probability", config.probability in 0f..1f)
            assertTrue("$pool cooldown", config.cooldownMs > 0L)
            assertTrue("$pool delay", config.delayMs >= 0L)
        }
        assertEquals(0f, LiveDataFragment.easterPoolConfig("nope").probability)
    }

    @Test
    fun `nextCoolantAlarmed latches with hysteresis`() {
        assertTrue(!LiveDataFragment.nextCoolantAlarmed(119, false))
        assertTrue(LiveDataFragment.nextCoolantAlarmed(120, false))
        assertTrue(LiveDataFragment.nextCoolantAlarmed(115, true))
        assertTrue(!LiveDataFragment.nextCoolantAlarmed(114, true))
    }

    @Test
    fun `voltageWarnTier separates low from critical`() {
        assertEquals(0, LiveDataFragment.voltageWarnTier(null))
        assertEquals(0, LiveDataFragment.voltageWarnTier(13.8f))
        assertEquals(0, LiveDataFragment.voltageWarnTier(12.0f))
        assertEquals(0, LiveDataFragment.voltageWarnTier(15.0f))
        assertEquals(1, LiveDataFragment.voltageWarnTier(11.9f))
        assertEquals(1, LiveDataFragment.voltageWarnTier(15.1f))
        assertEquals(2, LiveDataFragment.voltageWarnTier(10.9f))
        assertEquals(2, LiveDataFragment.voltageWarnTier(15.6f))
    }

    @Test
    fun `fuelWarnTier separates low from critical`() {
        assertEquals(0, LiveDataFragment.fuelWarnTier(null))
        assertEquals(0, LiveDataFragment.fuelWarnTier(62))
        assertEquals(0, LiveDataFragment.fuelWarnTier(16))
        assertEquals(1, LiveDataFragment.fuelWarnTier(15))
        assertEquals(1, LiveDataFragment.fuelWarnTier(6))
        assertEquals(2, LiveDataFragment.fuelWarnTier(5))
        assertEquals(2, LiveDataFragment.fuelWarnTier(0))
    }

    @Test
    fun `gas station uri targets nearby pumps without a map SDK`() {
        assertEquals("geo:0,0?q=gas+station", LiveDataFragment.gasStationGeoUri())
    }
}
