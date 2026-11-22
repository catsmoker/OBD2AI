package com.catsmoker.obd2ai.ui.common

import android.content.Context
import androidx.core.content.edit
import com.catsmoker.obd2ai.prefs.PrefsKeys

/**
 * Remembers that the user was in demo mode, so a killed app comes back in the
 * demo instead of on "Not connected". Only demo is ever resumed — a real
 * adapter needs permissions, pairing and a live socket, and reconnecting to a
 * vehicle without the driver asking is not acceptable.
 *
 * The flag is set when demo starts and cleared when the user leaves it, so it
 * cannot resurrect a session the driver already ended.
 */
object DemoSession {

    fun remember(context: Context) {
        prefs(context).edit { putBoolean(PrefsKeys.DEMO_SESSION, true) }
    }

    fun forget(context: Context) {
        prefs(context).edit { putBoolean(PrefsKeys.DEMO_SESSION, false) }
    }

    fun isRemembered(context: Context): Boolean =
        prefs(context).getBoolean(PrefsKeys.DEMO_SESSION, false)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
}
