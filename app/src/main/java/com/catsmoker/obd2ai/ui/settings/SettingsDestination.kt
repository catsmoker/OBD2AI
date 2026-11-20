package com.catsmoker.obd2ai.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.navigation.NavHostController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.AiProvider
import com.catsmoker.obd2ai.ai.AiSwitchRules
import com.catsmoker.obd2ai.prefs.NavStyle
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.ui.common.ConnectionStateStore

/**
 * Settings destination: the same wiring the old SettingsFragment performed,
 * as a plain composable. Every control writes through the callback it is
 * given; the screen never touches prefs except to seed initial state.
 */
@Composable
fun SettingsDestination(activity: MainActivity, navController: NavHostController) {
    val prefs: SharedPreferences = activity.getSharedPreferences(
        PrefsKeys.PREFS_NAME,
        android.content.Context.MODE_PRIVATE
    )
    val context = LocalContext.current

    SettingsScreen(
        prefs = prefs,
        adBilling = activity.adBilling,
        context = context,
        onThemeChange = { mode ->
            prefs.edit { putString(PrefsKeys.THEME_MODE, mode.prefValue) }
            activity.applyThemeMode(mode)
        },
        onLanguageChange = { tag ->
            prefs.edit { putString(PrefsKeys.APP_LANGUAGE, tag) }
            activity.applyAppLanguage(tag)
            activity.recreate()
        },
        onUnitsChange = { imperial ->
            prefs.edit {
                putString(
                    PrefsKeys.UNITS,
                    if (imperial) PrefsKeys.UNITS_IMPERIAL else PrefsKeys.UNITS_METRIC
                )
            }
        },
        onSpeedSourceChange = { device ->
            prefs.edit {
                putString(
                    PrefsKeys.SPEED_SOURCE,
                    if (device) PrefsKeys.SPEED_SOURCE_DEVICE else PrefsKeys.SPEED_SOURCE_OBD
                )
            }
        },
        onNavStyleChange = { style ->
            prefs.edit { putString(PrefsKeys.NAV_STYLE, style.prefValue) }
            // Applies immediately: the chrome observes ConnectionStateStore.
            ConnectionStateStore.setNavStyle(style)
        },
        onMuteChange = { prefs.edit { putBoolean(PrefsKeys.MUTE_SOUND, it) } },
        onKeepScreenChange = { prefs.edit { putBoolean(PrefsKeys.KEEP_SCREEN_ON, it) } },
        onEngineSoundChange = { prefs.edit { putBoolean(PrefsKeys.ENGINE_SOUND_ENABLED, it) } },
        onStyleChange = { prefs.edit { putString(PrefsKeys.SPEEDOMETER_STYLE, it.id) } },
        onAiMasterChange = { system, checked ->
            val (offline, online) = AiSwitchRules.resolveAiSwitches(
                system,
                checked,
                prefs.getBoolean(PrefsKeys.OFFLINE_AI_ENABLED, true),
                prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)
            )
            prefs.edit {
                putBoolean(PrefsKeys.OFFLINE_AI_ENABLED, offline)
                putBoolean(PrefsKeys.ONLINE_AI_ENABLED, online)
            }
            offline to online
        },
        onOfflineOutputChange = { voice, text ->
            val (voiceOn, textOn) = AiSwitchRules.enforceOfflineOutput(voice, voice, text)
            prefs.edit {
                putBoolean(PrefsKeys.VOICE_INSIGHT, voiceOn)
                putBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, textOn)
            }
            voiceOn to textOn
        },
        onOfflineVolumeChange = { prefs.edit { putInt(PrefsKeys.OFFLINE_VOLUME, it) } },
        onShiftPointChange = { prefs.edit { putBoolean(PrefsKeys.SHIFT_POINT_ENABLED, it) } },
        onShiftRpmChange = { prefs.edit { putInt(PrefsKeys.SHIFT_RPM, it) } },
        onHighRpmChange = { prefs.edit { putBoolean(PrefsKeys.HIGH_RPM_ENABLED, it) } },
        onHighRpmThresholdChange = { prefs.edit { putInt(PrefsKeys.HIGH_RPM_THRESHOLD, it) } },
        onHighSpeedChange = { prefs.edit { putBoolean(PrefsKeys.HIGH_SPEED_ENABLED, it) } },
        onHighSpeedThresholdChange = { prefs.edit { putInt(PrefsKeys.HIGH_SPEED_THRESHOLD, it) } },
        onCoolantChange = { prefs.edit { putBoolean(PrefsKeys.OFFLINE_COOLANT_ENABLED, it) } },
        onCoolantThresholdChange = { prefs.edit { putInt(PrefsKeys.OFFLINE_COOLANT_THRESHOLD, it) } },
        onNewFaultChange = { prefs.edit { putBoolean(PrefsKeys.NEW_FAULT_ENABLED, it) } },
        onConnLostChange = { prefs.edit { putBoolean(PrefsKeys.CONN_LOST_ENABLED, it) } },
        onConnRestoredChange = { prefs.edit { putBoolean(PrefsKeys.CONN_RESTORED_ENABLED, it) } },
        onEngineStartChange = { prefs.edit { putBoolean(PrefsKeys.ENGINE_START_ENABLED, it) } },
        onEngineStopChange = { prefs.edit { putBoolean(PrefsKeys.ENGINE_STOP_ENABLED, it) } },
        onEasterChange = { prefs.edit { putBoolean(PrefsKeys.EASTER_EGGS_ENABLED, it) } },
        onOnlineOutputChange = { voice, text ->
            val (voiceOn, textOn) = AiSwitchRules.enforceOnlineOutput(voice, voice, text)
            prefs.edit {
                putBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, voiceOn)
                putBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, textOn)
            }
            voiceOn to textOn
        },
        onAiVolumeChange = { prefs.edit { putInt(PrefsKeys.AI_VOLUME, it) } },
        onAiFrequencyChange = { prefs.edit { putString(PrefsKeys.AI_FREQUENCY, it.prefValue) } },
        onAiPersonalityChange = { prefs.edit { putString(PrefsKeys.AI_PERSONALITY, it.prefValue) } },
        onApiSave = { provider, key, model, baseUrl ->
            prefs.edit {
                putString(PrefsKeys.AI_PROVIDER, provider.id)
                putString(PrefsKeys.OPENAI_API_KEY, key.trim())
                putString(PrefsKeys.OPENAI_MODEL_ID, model.trim())
                putString(PrefsKeys.AI_BASE_URL, baseUrl.trim())
            }
            toastApiState(context, provider, key.trim())
        },
        onAnalyticsChange = { checked ->
            prefs.edit { putBoolean(PrefsKeys.ANALYTICS_ENABLED, checked) }
            activity.applyPrivacyChoices()
        },
        onPersonalizedAdsChange = { checked ->
            if (checked) {
                activity.syncAdsConsent { status ->
                    if (activity.isDestroyed) return@syncAdsConsent
                    if (status == com.google.android.ump.ConsentInformation.ConsentStatus.REQUIRED) {
                        prefs.edit { putBoolean(PrefsKeys.PERSONALIZED_ADS, false) }
                    }
                }
            } else {
                prefs.edit { putBoolean(PrefsKeys.PERSONALIZED_ADS, false) }
                activity.applyPrivacyChoices()
            }
        },
        onAdConsentOptions = {
            activity.showAdPrivacyOptions { status ->
                if (activity.isDestroyed) return@showAdPrivacyOptions
                if (status == com.google.android.ump.ConsentInformation.ConsentStatus.REQUIRED) {
                    prefs.edit { putBoolean(PrefsKeys.PERSONALIZED_ADS, false) }
                }
            }
        },
        onPrivacyPolicy = { navController.navigate("legal/${LegalPages.PAGE_PRIVACY}") { launchSingleTop = true } },
        onTerms = { navController.navigate("legal/${LegalPages.PAGE_TERMS}") { launchSingleTop = true } },
        onOnlineLegal = {
            val ok = LegalPages.openHostedLegal(context)
            if (!ok) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.no_errors_found, context.getString(R.string.legal_url)),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        },
        onRemoveAdsClick = { activity.adBilling.launchPurchase(activity) },
        onTestVoice = { testOnlineAiVoice(activity, prefs) },
        onTtsSettings = { openSystemTtsSettings(context) },
        onTestOfflineVoice = { testOfflineAiVoice(activity, prefs) },
        onOfflineTtsSettings = { openSystemTtsSettings(context) }
    )
}

/** Honest feedback: a keyless cloud provider is not "saved and working". */
private fun toastApiState(context: android.content.Context, provider: AiProvider, key: String) {
    val message = if (provider.needsKey && key.isEmpty()) {
        context.getString(R.string.settings_api_key_missing, provider.displayName)
    } else {
        context.getString(R.string.settings_saved)
    }
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}

private fun testOnlineAiVoice(activity: MainActivity, prefs: SharedPreferences) {
    if (prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)) {
        android.widget.Toast.makeText(activity, R.string.ai_test_off, android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val line = activity.getString(R.string.ai_test_line)
    val volume = prefs.getInt(PrefsKeys.AI_VOLUME, PrefsKeys.DEFAULT_AI_VOLUME).coerceIn(0, 100) / 100f
    val params = android.os.Bundle().apply {
        putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
    }
    var engine: android.speech.tts.TextToSpeech? = null
    engine = android.speech.tts.TextToSpeech(activity) { status ->
        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
            runCatching { engine?.language = java.util.Locale.getDefault() }
            engine?.speak(line, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "test")
        } else if (!activity.isDestroyed) {
            android.widget.Toast.makeText(
                activity,
                activity.getString(R.string.ai_test_failed, "TTS engine"),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    android.widget.Toast.makeText(activity, R.string.ai_test_ok_device, android.widget.Toast.LENGTH_SHORT).show()
}

private fun testOfflineAiVoice(activity: MainActivity, prefs: SharedPreferences) {
    if (prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)) {
        android.widget.Toast.makeText(activity, R.string.ai_test_off, android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val line = activity.getString(R.string.offline_ai_test_line)
    val volume = prefs.getInt(PrefsKeys.OFFLINE_VOLUME, PrefsKeys.DEFAULT_OFFLINE_VOLUME)
        .coerceIn(0, 100) / 100f
    val params = android.os.Bundle().apply {
        putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
    }
    var engine: android.speech.tts.TextToSpeech? = null
    engine = android.speech.tts.TextToSpeech(activity) { status ->
        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
            runCatching { engine?.language = java.util.Locale.getDefault() }
            engine?.speak(line, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "test-offline")
        } else if (!activity.isDestroyed) {
            android.widget.Toast.makeText(
                activity,
                activity.getString(R.string.ai_test_failed, "TTS engine"),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    android.widget.Toast.makeText(activity, R.string.ai_test_ok_device, android.widget.Toast.LENGTH_SHORT).show()
}

private fun openSystemTtsSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
    }.onFailure { e ->
        Log.e("SettingsDestination", "Cannot open system TTS settings", e)
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.ai_test_failed, e.message.orEmpty()),
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}
