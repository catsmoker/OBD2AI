package com.catsmoker.obd2ai.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

class SettingsFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private lateinit var adBilling: com.catsmoker.obd2ai.ads.AdBillingManager

    private var testTts: android.speech.tts.TextToSpeech? = null
    private var testOfflineTts: android.speech.tts.TextToSpeech? = null
    private var stepStyle: (Int) -> Unit = {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        adBilling = (activity as MainActivity).adBilling

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Obd2AiTheme(darkTheme = isSystemInDarkTheme()) {
                    SettingsScreen(
                        prefs = prefs,
                        adBilling = adBilling,
                        context = requireContext(),
                        onSave = { saveSettings() },
                        onThemeChange = { mode ->
                            prefs.edit { putString(PrefsKeys.THEME_MODE, mode.prefValue) }
                            (activity as MainActivity).applyThemeMode(mode)
                        },
                        onLanguageChange = { tag ->
                            prefs.edit { putString(PrefsKeys.APP_LANGUAGE, tag) }
                            (activity as MainActivity).applyAppLanguage(tag)
                            requireActivity().recreate()
                        },
                        onUnitsChange = { imperial ->
                            prefs.edit { putString(PrefsKeys.UNITS, if (imperial) PrefsKeys.UNITS_IMPERIAL else PrefsKeys.UNITS_METRIC) }
                        },
                        onSpeedSourceChange = { device ->
                            prefs.edit { putString(PrefsKeys.SPEED_SOURCE, if (device) PrefsKeys.SPEED_SOURCE_DEVICE else PrefsKeys.SPEED_SOURCE_OBD) }
                        },
                        onMuteChange = { prefs.edit { putBoolean(PrefsKeys.MUTE_SOUND, it) } },
                        onKeepScreenChange = { prefs.edit { putBoolean(PrefsKeys.KEEP_SCREEN_ON, it) } },
                        onEngineSoundChange = { prefs.edit { putBoolean(PrefsKeys.ENGINE_SOUND_ENABLED, it) } },
                        onStyleChange = { prefs.edit { putString(PrefsKeys.SPEEDOMETER_STYLE, it.id) } },
                        onStylePrev = { stepStyle(-1) },
                        onStyleNext = { stepStyle(1) },
                        onOfflineAiChange = { checked ->
                            val (newOffline, newOnline) = com.catsmoker.obd2ai.ai.AiSwitchRules.resolveAiSwitches(
                                com.catsmoker.obd2ai.ai.AiSwitchRules.AiSystem.OFFLINE, checked,
                                prefs.getBoolean(PrefsKeys.OFFLINE_AI_ENABLED, true),
                                prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)
                            )
                            prefs.edit {
                                putBoolean(PrefsKeys.OFFLINE_AI_ENABLED, newOffline)
                                putBoolean(PrefsKeys.ONLINE_AI_ENABLED, newOnline)
                            }
                        },
                        onOnlineAiChange = { checked ->
                            val (newOffline, newOnline) = com.catsmoker.obd2ai.ai.AiSwitchRules.resolveAiSwitches(
                                com.catsmoker.obd2ai.ai.AiSwitchRules.AiSystem.ONLINE, checked,
                                prefs.getBoolean(PrefsKeys.OFFLINE_AI_ENABLED, true),
                                prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)
                            )
                            prefs.edit {
                                putBoolean(PrefsKeys.OFFLINE_AI_ENABLED, newOffline)
                                putBoolean(PrefsKeys.ONLINE_AI_ENABLED, newOnline)
                            }
                        },
                        onOfflineVoiceChange = { checked ->
                            val (voice, text) = com.catsmoker.obd2ai.ai.AiSwitchRules.enforceOfflineOutput(
                                checked,
                                prefs.getBoolean(PrefsKeys.VOICE_INSIGHT, true),
                                prefs.getBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, true)
                            )
                            prefs.edit { putBoolean(PrefsKeys.VOICE_INSIGHT, voice); putBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, text) }
                        },
                        onOfflineTextChange = { checked ->
                            val (voice, text) = com.catsmoker.obd2ai.ai.AiSwitchRules.enforceOfflineOutput(
                                false, prefs.getBoolean(PrefsKeys.VOICE_INSIGHT, true), checked
                            )
                            prefs.edit { putBoolean(PrefsKeys.VOICE_INSIGHT, voice); putBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, text) }
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
                        onOnlineVoiceChange = { checked ->
                            val (voice, text) = com.catsmoker.obd2ai.ai.AiSwitchRules.enforceOnlineOutput(
                                checked,
                                prefs.getBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, true),
                                prefs.getBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, true)
                            )
                            prefs.edit { putBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, voice); putBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, text) }
                        },
                        onOnlineTextChange = { checked ->
                            val (voice, text) = com.catsmoker.obd2ai.ai.AiSwitchRules.enforceOnlineOutput(
                                false, prefs.getBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, true), checked
                            )
                            prefs.edit { putBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, voice); putBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, text) }
                        },
                        onAiVolumeChange = { prefs.edit { putInt(PrefsKeys.AI_VOLUME, it) } },
                        onAiFrequencyChange = { prefs.edit { putString(PrefsKeys.AI_FREQUENCY, it.prefValue) } },
                        onAiPersonalityChange = { prefs.edit { putString(PrefsKeys.AI_PERSONALITY, it.prefValue) } },
                        onAnalyticsChange = { checked ->
                            prefs.edit { putBoolean(PrefsKeys.ANALYTICS_ENABLED, checked) }
                            (activity as MainActivity).applyPrivacyChoices()
                        },
                        onPersonalizedAdsChange = { checked ->
                            if (checked) {
                                (activity as MainActivity).syncAdsConsent { status ->
                                    if (!isAdded) return@syncAdsConsent
                                    if (status == com.google.android.ump.ConsentInformation.ConsentStatus.REQUIRED) {
                                        prefs.edit { putBoolean(PrefsKeys.PERSONALIZED_ADS, false) }
                                    }
                                }
                            } else {
                                prefs.edit { putBoolean(PrefsKeys.PERSONALIZED_ADS, false) }
                                (activity as MainActivity).applyPrivacyChoices()
                            }
                        },
                        onAdConsentOptions = {
                            (activity as MainActivity).showAdPrivacyOptions { status ->
                                if (!isAdded) return@showAdPrivacyOptions
                                if (status == com.google.android.ump.ConsentInformation.ConsentStatus.REQUIRED) {
                                    prefs.edit { putBoolean(PrefsKeys.PERSONALIZED_ADS, false) }
                                }
                            }
                        },
                        onPrivacyPolicy = { findNavController().navigate(
                            com.catsmoker.obd2ai.ui.settings.SettingsFragmentDirections
                                .actionSettingsFragmentToLegalFragment(com.catsmoker.obd2ai.ui.settings.LegalFragment.PAGE_PRIVACY)
                        ) },
                        onTerms = { findNavController().navigate(
                            com.catsmoker.obd2ai.ui.settings.SettingsFragmentDirections
                                .actionSettingsFragmentToLegalFragment(com.catsmoker.obd2ai.ui.settings.LegalFragment.PAGE_TERMS)
                        ) },
                        onOnlineLegal = {
                            val ok = com.catsmoker.obd2ai.ui.settings.LegalFragment.openHostedLegal(requireContext())
                            if (!ok) {
                                android.widget.Toast.makeText(
                                    requireContext(),
                                    getString(R.string.no_errors_found, getString(R.string.legal_url)),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onRemoveAdsClick = { adBilling.launchPurchase(requireActivity()) },
                        onTestVoice = ::testOnlineAiVoice,
                        onTtsSettings = ::openSystemTtsSettings,
                        onTestOfflineVoice = ::testOfflineAiVoice,
                        onOfflineTtsSettings = ::openSystemTtsSettings,
                        navigateUp = { findNavController().navigateUp() }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTestVoice()
        runCatching { testTts?.shutdown() }
        testTts = null
        runCatching { testOfflineTts?.shutdown() }
        testOfflineTts = null
    }

    private fun saveSettings() {
        val provider = com.catsmoker.obd2ai.ai.AiProvider.fromId(
            prefs.getString(PrefsKeys.AI_PROVIDER, com.catsmoker.obd2ai.ai.AiProvider.OPENAI.id)
        )
        val missingKey = provider.needsKey && (prefs.getString(PrefsKeys.OPENAI_API_KEY, "") ?: "").trim().isEmpty()
        val toast = if (missingKey) {
            getString(R.string.settings_api_key_missing, provider.displayName)
        } else {
            getString(R.string.settings_saved)
        }
        android.widget.Toast.makeText(requireActivity(), toast, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun testOnlineAiVoice() {
        if (prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)) {
            android.widget.Toast.makeText(requireContext(), R.string.ai_test_off, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val line = getString(R.string.ai_test_line)
        stopTestVoice()
        val volume = prefs.getInt(PrefsKeys.AI_VOLUME, com.catsmoker.obd2ai.prefs.PrefsKeys.DEFAULT_AI_VOLUME).coerceIn(0, 100) / 100f
        val params = android.os.Bundle().apply {
            putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        testTts = android.speech.tts.TextToSpeech(requireContext()) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                runCatching { testTts?.language = java.util.Locale.getDefault() }
                testTts?.speak(line, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "test")
            } else if (isAdded) {
                android.widget.Toast.makeText(
                    requireContext(), getString(R.string.ai_test_failed, "TTS engine"), android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        android.widget.Toast.makeText(requireContext(), R.string.ai_test_ok_device, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun testOfflineAiVoice() {
        if (prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)) {
            android.widget.Toast.makeText(requireContext(), R.string.ai_test_off, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val line = getString(R.string.offline_ai_test_line)
        stopTestVoice()
        val volume = prefs.getInt(PrefsKeys.OFFLINE_VOLUME, com.catsmoker.obd2ai.prefs.PrefsKeys.DEFAULT_OFFLINE_VOLUME)
            .coerceIn(0, 100) / 100f
        val params = android.os.Bundle().apply {
            putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        testOfflineTts = android.speech.tts.TextToSpeech(requireContext()) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                runCatching { testOfflineTts?.language = java.util.Locale.getDefault() }
                testOfflineTts?.speak(line, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "test-offline")
            } else if (isAdded) {
                android.widget.Toast.makeText(
                    requireContext(), getString(R.string.ai_test_failed, "TTS engine"), android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        android.widget.Toast.makeText(requireContext(), R.string.ai_test_ok_device, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun stopTestVoice() {
        runCatching { testTts?.stop() }
        runCatching { testOfflineTts?.stop() }
    }

    private fun openSystemTtsSettings() {
        runCatching {
            requireContext().startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        }.onFailure { e ->
            Log.e("SettingsFragment", "Cannot open system TTS settings", e)
            android.widget.Toast.makeText(
                requireContext(), getString(R.string.ai_test_failed, e.message.orEmpty()), android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}
