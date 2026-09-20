package com.catsmoker.obd2ai.vehicle

/** One trip's totals; all speeds in km/h (callers convert for display). */
data class TripSnapshot(
    val durationMs: Long,
    val distanceKm: Double,
    val avgKmh: Double,
    val maxKmh: Double,
    val maxRpm: Int,
    val idleMs: Long,
    val driveMs: Long
)

/**
 * OBD-only trip computer (android-obd-reader's TripRecord idea,
 * reimplemented): integrates speed into distance and splits idle vs drive
 * time. No GPS, nothing persisted — the screen owns the instance. Pure
 * logic, tested.
 */
class TripComputer {
    private var startMs: Long? = null
    private var lastMs: Long? = null
    private var elapsedMs = 0L
    private var distanceKm = 0.0
    private var maxKmh = 0.0
    private var maxRpm = 0
    private var idleMs = 0L
    private var driveMs = 0L

    val isRunning: Boolean get() = startMs != null

    fun start(nowMs: Long) {
        reset()
        startMs = nowMs
        lastMs = nowMs
    }

    fun stop(nowMs: Long) {
        val start = startMs ?: return
        elapsedMs += (nowMs - start).coerceAtLeast(0)
        startMs = null
    }

    fun reset() {
        startMs = null
        lastMs = null
        elapsedMs = 0L
        distanceKm = 0.0
        maxKmh = 0.0
        maxRpm = 0
        idleMs = 0L
        driveMs = 0L
    }

    /** One ~1 s sample; null = unknown and counts as standing still. */
    fun sample(speedKmh: Double?, rpm: Int?, nowMs: Long) {
        if (startMs == null) return
        val prev = lastMs ?: nowMs
        val dtMs = (nowMs - prev).coerceAtLeast(0)
        lastMs = nowMs
        val speed = (speedKmh ?: 0.0).coerceAtLeast(0.0)
        distanceKm += speed * dtMs / 3_600_000.0
        if (speed < 3.0) idleMs += dtMs else driveMs += dtMs
        if (speed > maxKmh) maxKmh = speed
        if (rpm != null && rpm > maxRpm) maxRpm = rpm
    }

    fun snapshot(nowMs: Long): TripSnapshot {
        val start = startMs
        val duration = elapsedMs + (if (start != null) (nowMs - start).coerceAtLeast(0) else 0)
        val hours = duration / 3_600_000.0
        return TripSnapshot(
            durationMs = duration,
            distanceKm = distanceKm,
            avgKmh = if (hours > 0) distanceKm / hours else 0.0,
            maxKmh = maxKmh,
            maxRpm = maxRpm,
            idleMs = idleMs,
            driveMs = driveMs
        )
    }

    companion object {
        fun formatDuration(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        }
    }
}
