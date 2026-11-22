package com.catsmoker.obd2ai.ui.diagnostics

import com.catsmoker.obd2ai.diagnostics.FreezeFrame

/**
 * One freeze frame per scan. Reading it is a live Mode 02 request, and the
 * detail screen is opened repeatedly while triaging codes — re-asking on every
 * open made the panel flicker from empty to values. A cached "not available"
 * is remembered too: re-asking an adapter that has no freeze frame gives the
 * same answer and only costs bus time.
 *
 * [invalidate] runs whenever a scan or a clear starts, so a new read is never
 * mixed with a frame from the previous one.
 */
object FreezeFrameCache {

    @Volatile
    private var frame: FreezeFrame? = null

    @Volatile
    private var loaded = false

    /** The frame from this scan, or null when it is unread/empty. */
    fun get(): FreezeFrame? = if (loaded) frame else null

    /** True once this scan has an answer, including "no freeze frame". */
    fun isLoaded(): Boolean = loaded

    /** Stores the answer for this scan; null means the ECU had none. */
    fun put(value: FreezeFrame?) {
        frame = value
        loaded = true
    }

    /** A new scan starts: forget the previous frame. */
    fun invalidate() {
        frame = null
        loaded = false
    }
}
