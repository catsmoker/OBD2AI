package com.catsmoker.obd2ai.ui.dashboard

import com.catsmoker.obd2ai.prefs.PrefsKeys

/**
 * Pure Live-dashboard logic (RPM zones, tiers, cooldowns, Easter-egg pools),
 * extracted from the old LiveDataFragment companion object so it stays
 * unit-testable without any Android framework dependency.
 */
object LiveLogic {
    val RPM_GREEN = 0xFF43A047.toInt()
    val RPM_AMBER = 0xFFC07F00.toInt()
    val RPM_RED = 0xFFE53935.toInt()
    const val UNKNOWN = "--"

    fun greenThreshold(shiftRpm: Int): Int =
        maxOf(shiftRpm - 1000, 1000)

    fun amberThreshold(shiftRpm: Int): Int =
        maxOf(shiftRpm, greenThreshold(shiftRpm) + 500)

    fun rpmZoneColor(rpmValue: Int, shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM): Int = when {
        rpmValue < greenThreshold(shiftRpm) -> RPM_GREEN
        rpmValue < amberThreshold(shiftRpm) -> RPM_AMBER
        else -> RPM_RED
    }

    fun rpmSections(
        rpmMax: Float,
        shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM
    ): List<Triple<Float, Float, Int>> {
        val greenEnd = (greenThreshold(shiftRpm) / 100f / rpmMax).coerceIn(0.05f, 0.9f)
        val amberEnd =
            (amberThreshold(shiftRpm) / 100f / rpmMax).coerceIn(greenEnd + 0.05f, 0.95f)
        return listOf(
            Triple(0f, greenEnd, RPM_GREEN),
            Triple(greenEnd, amberEnd, RPM_AMBER),
            Triple(amberEnd, 1f, RPM_RED)
        )
    }

    fun rpmTriggers(shiftRpm: Int): IntArray {
        val t1 = maxOf(shiftRpm - 2500, 800)
        val t2 = maxOf(shiftRpm - 1500, t1 + 500)
        val t3 = maxOf(shiftRpm - 500, t2 + 500)
        val t4 = maxOf(shiftRpm + 1000, t3 + 500)
        return intArrayOf(t1, t2, t3, t4)
    }

    fun nextRpmTier(
        rpmValue: Int,
        currentTier: Int,
        shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM
    ): Int {
        val (t1, t2, t3, t4) = rpmTriggers(shiftRpm).let {
            listOf(it[0], it[1], it[2], it[3])
        }
        return when {
            rpmValue > t4 && currentTier < 4 -> 4
            rpmValue > t3 && currentTier < 3 -> 3
            rpmValue > t2 && currentTier < 2 -> 2
            rpmValue > t1 && currentTier < 1 -> 1
            rpmValue < t1 - 100 && currentTier >= 1 -> 0
            rpmValue < t2 - 100 && currentTier >= 2 -> 1
            rpmValue < t3 - 100 && currentTier >= 3 -> 2
            rpmValue < t4 - 100 && currentTier >= 4 -> 3
            else -> currentTier
        }
    }

    fun nextCoolantAlarmed(coolantC: Int?, currentlyAlarmed: Boolean): Boolean = when {
        currentlyAlarmed -> (coolantC ?: 0) >= PrefsKeys.COOLANT_ALARM_RELEASE_C
        else -> (coolantC ?: 0) >= PrefsKeys.COOLANT_ALARM_C
    }

    fun voltageWarnTier(volts: Float?): Int = when {
        volts == null -> 0
        volts < 11.0f || volts > 15.5f -> 2
        volts < 12.0f || volts > 15.0f -> 1
        else -> 0
    }

    fun fuelWarnTier(pct: Int?): Int = when {
        pct == null -> 0
        pct <= 5 -> 2
        pct <= 15 -> 1
        else -> 0
    }

    fun gasStationGeoUri(): String = "geo:0,0?q=gas+station"

    const val OFFLINE_AI_SHIFT_COOLDOWN_MS = 15_000L
    const val OFFLINE_AI_EVENT_COOLDOWN_MS = 30_000L

    fun shiftCrossed(prevRpm: Int, newRpm: Int, threshold: Int): Boolean =
        prevRpm < threshold && newRpm >= threshold

    fun crossedDown(prevValue: Int, newValue: Int, threshold: Int): Boolean =
        prevValue >= threshold && newValue < threshold

    fun offlineAiReady(lastFiredMs: Long, nowMs: Long, cooldownMs: Long): Boolean =
        nowMs - lastFiredMs >= cooldownMs

    fun bannerDurationMs(text: String): Long {
        val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        return maxOf(2, words) * 1000L
    }

    const val POOL_SPEED_130 = "speed_130"
    const val POOL_SPEED_140 = "speed_140"
    const val POOL_SPEED_150 = "speed_150"
    const val POOL_RPM = "rpm"
    const val POOL_COMBO = "combo"
    const val POOL_COOLANT_AFTER = "coolant_after"
    const val POOL_FAULT_NEW = "fault_new"
    const val POOL_FAULT_RETURN = "fault_return"
    const val POOL_FAULT_SPEEDING = "fault_speeding"
    const val POOL_ULTRA = "ultra"
    const val EASTER_RECENT_MAX = 8

    fun speedEasterTier(speedKmh: Float): Int = when {
        speedKmh >= 150 -> 3
        speedKmh >= 140 -> 2
        speedKmh >= 130 -> 1
        else -> 0
    }

    fun poolForSpeedTier(tier: Int): String = when (tier) {
        3 -> POOL_SPEED_150
        2 -> POOL_SPEED_140
        else -> POOL_SPEED_130
    }

    fun comboActive(
        rpm: Int,
        rpmThreshold: Int,
        speedKmh: Float,
        speedThreshold: Int
    ): Boolean = rpm >= rpmThreshold && speedKmh >= speedThreshold.toFloat()

    fun rollEasterEgg(random01: Float, probability: Float): Boolean =
        random01 in 0f..<probability

    fun pickFreshLine(
        pool: List<String>,
        recent: ArrayDeque<String>,
        startIndex: Int
    ): String? {
        if (pool.isEmpty()) return null
        for (k in pool.indices) {
            val line = pool[(startIndex + k) % pool.size]
            if (line !in recent) return line
        }
        return null
    }

    data class EasterPoolConfig(
        val probability: Float,
        val cooldownMs: Long,
        val delayMs: Long
    )

    fun easterPoolConfig(pool: String): EasterPoolConfig = when (pool) {
        POOL_SPEED_130 -> EasterPoolConfig(0.35f, 5 * 60_000L, 0L)
        POOL_SPEED_140 -> EasterPoolConfig(0.35f, 10 * 60_000L, 0L)
        POOL_SPEED_150 -> EasterPoolConfig(0.15f, 20 * 60_000L, 0L)
        POOL_RPM -> EasterPoolConfig(0.30f, 10 * 60_000L, 4000L)
        POOL_COMBO -> EasterPoolConfig(0.25f, 15 * 60_000L, 4000L)
        POOL_COOLANT_AFTER -> EasterPoolConfig(0.30f, 20 * 60_000L, 5000L)
        POOL_FAULT_NEW -> EasterPoolConfig(0.30f, 15 * 60_000L, 4000L)
        POOL_FAULT_RETURN -> EasterPoolConfig(0.40f, 20 * 60_000L, 4000L)
        POOL_FAULT_SPEEDING -> EasterPoolConfig(0.30f, 15 * 60_000L, 4000L)
        POOL_ULTRA -> EasterPoolConfig(0.02f, 60 * 60_000L, 0L)
        else -> EasterPoolConfig(0f, Long.MAX_VALUE, 0L)
    }
}
