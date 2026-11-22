package com.catsmoker.obd2ai.ui.connect

import android.content.Context
import androidx.core.content.edit
import com.catsmoker.obd2ai.prefs.PrefsKeys

/** How the app reached the adapter last time it actually worked. */
enum class ObdTransport(val prefValue: String) {
    BLUETOOTH("bluetooth"),
    WIFI("wifi");

    companion object {
        /** Unknown/legacy values resolve to null, never to a made-up default. */
        fun fromValue(value: String?): ObdTransport? =
            entries.firstOrNull { it.prefValue == value }
    }
}

/**
 * Remembers the transport that last connected, so Connect can say which one
 * works on this phone instead of presenting Bluetooth and Wi-Fi as
 * interchangeable every time. Advisory only: nothing is auto-connected.
 */
object TransportMemory {

    fun remember(context: Context, transport: ObdTransport) {
        prefs(context).edit { putString(PrefsKeys.LAST_TRANSPORT, transport.prefValue) }
    }

    fun last(context: Context): ObdTransport? =
        ObdTransport.fromValue(prefs(context).getString(PrefsKeys.LAST_TRANSPORT, null))

    private fun prefs(context: Context) =
        context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
}
