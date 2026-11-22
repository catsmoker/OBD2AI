package com.catsmoker.obd2ai.ui.trip

import android.content.Context
import com.catsmoker.obd2ai.vehicle.TripComputer
import com.catsmoker.obd2ai.vehicle.TripSnapshot
import com.catsmoker.obd2ai.vehicle.TripStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one trip in progress, app-wide.
 *
 * The counter used to live in `TripScreen`'s composition, so glancing at the
 * dashboard and coming back silently wiped the trip. It is now a session
 * object sampled by the app root while the app is resumed, which means the
 * trip keeps accumulating on every screen and only restarts when the driver
 * asks it to.
 */
object TripSession {

    private val computer = TripComputer()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var appContext: Context? = null
    private var ticksSinceSave = 0

    private val _snapshot = MutableStateFlow(emptySnapshot())
    val snapshot: StateFlow<TripSnapshot> = _snapshot.asStateFlow()

    /**
     * The running flag is published, not read through a getter: a plain getter
     * does not invalidate a composable that captured it, which left the header
     * badge saying "Idle" while the timer counted.
     */
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * Called once from the Activity: restores a trip that outlived the process.
     * A trip whose last sample is old is restored as stopped — the totals are
     * real, the missing hours are not.
     */
    fun init(context: Context, nowMs: Long = System.currentTimeMillis()) {
        appContext = context.applicationContext
        val stored = TripStore.load(context.applicationContext) ?: return
        val resumed = stored.afterRestart(nowMs)
        if (resumed.isEmpty) {
            // Started but never actually measured: nothing to restore.
            TripStore.clear(context.applicationContext)
            return
        }
        computer.restoreState(resumed)
        _isRunning.value = computer.isRunning
        publish(nowMs)
        if (!computer.isRunning) persist()
    }

    fun start(nowMs: Long) {
        computer.start(nowMs)
        _isRunning.value = true
        publish(nowMs)
        persist()
    }

    fun stop(nowMs: Long) {
        computer.stop(nowMs)
        _isRunning.value = false
        publish(nowMs)
        persist()
    }

    fun reset(nowMs: Long) {
        computer.reset()
        _isRunning.value = false
        publish(nowMs)
        persist()
    }

    /**
     * One tick from the live OBD text flows ("62 Km/h", "-- RPM" = unknown).
     * Does nothing while no trip is running.
     */
    fun sample(nowMs: Long, speedText: String, rpmText: String) {
        if (!computer.isRunning) return
        computer.sample(parseSpeed(speedText), parseRpm(rpmText), nowMs)
        publish(nowMs)
        // Checkpoint every ~15 s: losing at most a quarter minute of trip is
        // cheaper than a disk write every second.
        if (++ticksSinceSave >= SAVE_EVERY_TICKS) persist()
    }

    /**
     * Writes the trip now. Called when the app leaves the foreground, so the
     * totals at that instant are never lost to a later kill.
     */
    fun checkpoint() {
        if (computer.exportState().isEmpty) {
            appContext?.let { TripStore.clear(it) }
            return
        }
        persist()
    }

    /** Writes the current trip (or deletes the file when there is none). */
    private fun persist() {
        ticksSinceSave = 0
        val context = appContext ?: return
        val state = computer.exportState()
        ioScope.launch {
            if (state.startMs == null && state.elapsedMs == 0L) {
                TripStore.clear(context)
            } else {
                TripStore.save(context, state)
            }
        }
    }

    /** Re-reads the totals without sampling, e.g. right after stopping. */
    fun refresh(nowMs: Long) {
        publish(nowMs)
    }

    private fun publish(nowMs: Long) {
        _snapshot.value = computer.snapshot(nowMs)
    }

    /** "62 Km/h" -> 62.0; "-- km/h"/"ERROR" -> null (unknown, never zero). */
    fun parseSpeed(text: String): Double? =
        text.split(" ").firstOrNull()?.toFloatOrNull()?.toDouble()

    /** "1800 RPM" -> 1800; anything unparsable -> null. */
    fun parseRpm(text: String): Int? =
        text.split(" ").firstOrNull()?.toIntOrNull()

    private const val SAVE_EVERY_TICKS = 10

    private fun emptySnapshot() = TripSnapshot(
        durationMs = 0L,
        distanceKm = 0.0,
        avgKmh = 0.0,
        maxKmh = 0.0,
        maxRpm = 0,
        idleMs = 0L,
        driveMs = 0L
    )
}
