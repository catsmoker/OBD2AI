package com.catsmoker.obd2ai.ui.common

import android.content.SharedPreferences
import androidx.core.content.edit
import com.catsmoker.obd2ai.prefs.PrefsKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * UI-only preferences the Compose root has to observe live (theme is applied
 * at the root, so writing a pref is not enough on its own). Values are seeded
 * from SharedPreferences once per process and mirrored to a flow the theme
 * reads; the prefs file stays the single source of truth on disk.
 */
object UiSettings {
    private val _dynamicColor = MutableStateFlow(false)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    private var seeded = false

    fun seed(prefs: SharedPreferences) {
        if (seeded) return
        seeded = true
        _dynamicColor.value = prefs.getBoolean(PrefsKeys.DYNAMIC_COLOR, false)
    }

    fun setDynamicColor(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit { putBoolean(PrefsKeys.DYNAMIC_COLOR, enabled) }
        _dynamicColor.value = enabled
    }
}
