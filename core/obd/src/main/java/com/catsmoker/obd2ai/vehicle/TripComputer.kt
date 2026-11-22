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
 * Everything [TripComputer] needs to be restored after the process dies.
 * All speeds in km/h, all times in milliseconds.
 */
data class TripState(
    val startMs: Long?,
    val lastMs: Long?,
    val elapsedMs: Long,
    val distanceKm: Double,
    val maxKmh: Double,
    val maxRpm: Int,
    val idleMs: Long,
    val driveMs: Long
) {
    /**
     * A trip can only continue if it was observed moments ago. After a longer
     * gap (app killed, phone off) the totals stay but counting stops: the
     * vehicle was not watched, so the missing time must not be sold as a trip.
     */
    /** True when nothing was ever measured — a started-but-unsampled trip. */
    val isEmpty: Boolean
        get() = elapsedMs == 0L && distanceKm == 0.0 && maxKmh == 0.0 &&
            maxRpm == 0 && idleMs == 0L && driveMs == 0L

    fun afterRestart(nowMs: Long, maxGapMs: Long = TripComputer.MAX_SAMPLE_GAP_MS): TripState {
        val last = lastMs ?: return this
        if (startMs == null || nowMs - last <= maxGapMs) return this
        // Freeze at the last observed instant, keeping the totals.
        return copy(
            startMs = null,
            lastMs = null,
            elapsedMs = elapsedMs + (last - startMs).coerceAtLeast(0)
        )
    }
}

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

    /**
     * One ~1 s sample; null = unknown and counts as standing still.
     *
     * A gap longer than [MAX_SAMPLE_GAP_MS] (app backgrounded, screen off,
     * sampler paused) is clipped: the vehicle was not observed during it, so
     * integrating the last known speed across it would invent distance. Elapsed
     * time still comes from the wall clock, so the duration stays correct.
     */
    fun sample(speedKmh: Double?, rpm: Int?, nowMs: Long) {
        if (startMs == null) return
        val prev = lastMs ?: nowMs
        val dtMs = (nowMs - prev).coerceIn(0L, MAX_SAMPLE_GAP_MS)
        lastMs = nowMs
        val speed = (speedKmh ?: 0.0).coerceAtLeast(0.0)
        distanceKm += speed * dtMs / 3_600_000.0
        if (speed < 3.0) idleMs += dtMs else driveMs += dtMs
        if (speed > maxKmh) maxKmh = speed
        if (rpm != null && rpm > maxRpm) maxRpm = rpm
    }

    /** Full state for persistence. */
    fun exportState(): TripState = TripState(
        startMs = startMs,
        lastMs = lastMs,
        elapsedMs = elapsedMs,
        distanceKm = distanceKm,
        maxKmh = maxKmh,
        maxRpm = maxRpm,
        idleMs = idleMs,
        driveMs = driveMs
    )

    /** Replaces the current trip with a stored one (already sanity-checked). */
    fun restoreState(state: TripState) {
        startMs = state.startMs
        lastMs = state.lastMs
        elapsedMs = state.elapsedMs
        distanceKm = state.distanceKm
        maxKmh = state.maxKmh
        maxRpm = state.maxRpm
        idleMs = state.idleMs
        driveMs = state.driveMs
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
        /** Longest interval still trusted as continuous observation. */
        const val MAX_SAMPLE_GAP_MS = 5_000L

        fun formatDuration(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        }
    }
}
