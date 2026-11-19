package com.catsmoker.obd2ai.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.AiFrequency
import com.catsmoker.obd2ai.ai.AiPersonality
import com.catsmoker.obd2ai.ai.AiProvider
import com.catsmoker.obd2ai.prefs.AppLanguage
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.ThemeMode
import com.catsmoker.obd2ai.prefs.Units
import com.catsmoker.obd2ai.speedometers.SpeedometerStyle
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.components.SectionCard
import com.catsmoker.obd2ai.ui.components.SectionLabel
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme

@Composable
fun SettingsScreen(
    prefs: SharedPreferences,
    adBilling: com.catsmoker.obd2ai.ads.AdBillingManager,
    context: Context,
    onSave: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    onUnitsChange: (Boolean) -> Unit,
    onSpeedSourceChange: (Boolean) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onKeepScreenChange: (Boolean) -> Unit,
    onEngineSoundChange: (Boolean) -> Unit,
    onStyleChange: (SpeedometerStyle) -> Unit,
    onStylePrev: () -> Unit,
    onStyleNext: () -> Unit,
    onOfflineAiChange: (Boolean) -> Unit,
    onOnlineAiChange: (Boolean) -> Unit,
    onOfflineVoiceChange: (Boolean) -> Unit,
    onOfflineTextChange: (Boolean) -> Unit,
    onOfflineVolumeChange: (Int) -> Unit,
    onShiftPointChange: (Boolean) -> Unit,
    onShiftRpmChange: (Int) -> Unit,
    onHighRpmChange: (Boolean) -> Unit,
    onHighRpmThresholdChange: (Int) -> Unit,
    onHighSpeedChange: (Boolean) -> Unit,
    onHighSpeedThresholdChange: (Int) -> Unit,
    onCoolantChange: (Boolean) -> Unit,
    onCoolantThresholdChange: (Int) -> Unit,
    onNewFaultChange: (Boolean) -> Unit,
    onConnLostChange: (Boolean) -> Unit,
    onConnRestoredChange: (Boolean) -> Unit,
    onEngineStartChange: (Boolean) -> Unit,
    onEngineStopChange: (Boolean) -> Unit,
    onEasterChange: (Boolean) -> Unit,
    onOnlineVoiceChange: (Boolean) -> Unit,
    onOnlineTextChange: (Boolean) -> Unit,
    onAiVolumeChange: (Int) -> Unit,
    onAiFrequencyChange: (AiFrequency) -> Unit,
    onAiPersonalityChange: (AiPersonality) -> Unit,
    onAnalyticsChange: (Boolean) -> Unit,
    onPersonalizedAdsChange: (Boolean) -> Unit,
    onAdConsentOptions: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTerms: () -> Unit,
    onOnlineLegal: () -> Unit,
    onRemoveAdsClick: () -> Unit,
    onTestVoice: () -> Unit,
    onTtsSettings: () -> Unit,
    onTestOfflineVoice: () -> Unit,
    onOfflineTtsSettings: () -> Unit,
    navigateUp: () -> Unit
) {
    val ctx = LocalContext.current

    var themeMode by remember { mutableStateOf(ThemeMode.fromPref(prefs.getString(PrefsKeys.THEME_MODE, null))) }
    var appLang by remember { mutableStateOf(AppLanguage.fromTag(prefs.getString(PrefsKeys.APP_LANGUAGE, null))) }
    var imperial by remember { mutableStateOf(Units.isImperial(prefs)) }
    var speedSourceDevice by remember { mutableStateOf(prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD) == PrefsKeys.SPEED_SOURCE_DEVICE) }
    var mute by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)) }
    var keepScreen by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.KEEP_SCREEN_ON, false)) }
    var engineSound by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ENGINE_SOUND_ENABLED, false)) }
    var styleIndex by remember { mutableStateOf(SpeedometerStyle.entries.indexOf(SpeedometerStyle.fromId(prefs.getString(PrefsKeys.SPEEDOMETER_STYLE, PrefsKeys.SPEEDOMETER_DEFAULT))).coerceAtLeast(0)) }
    var offlineAi by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.OFFLINE_AI_ENABLED, true)) }
    var onlineAi by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)) }
    var offlineVoice by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.VOICE_INSIGHT, true)) }
    var offlineText by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, true)) }
    var offlineVolume by remember { mutableStateOf(prefs.getInt(PrefsKeys.OFFLINE_VOLUME, com.catsmoker.obd2ai.prefs.PrefsKeys.DEFAULT_OFFLINE_VOLUME)) }
    var shiftPoint by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.SHIFT_POINT_ENABLED, true)) }
    var shiftRpm by remember { mutableStateOf(prefs.getInt(PrefsKeys.SHIFT_RPM, com.catsmoker.obd2ai.prefs.PrefsKeys.DEFAULT_SHIFT_RPM)) }
    var highRpm by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.HIGH_RPM_ENABLED, true)) }
    var highRpmThreshold by remember { mutableStateOf(prefs.getInt(PrefsKeys.HIGH_RPM_THRESHOLD, com.catsmoker.obd2ai.prefs.PrefsKeys.DEFAULT_HIGH_RPM)) }
    var highSpeed by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.HIGH_SPEED_ENABLED, true)) }
    var highSpeedThreshold by remember { mutableStateOf(prefs.getInt(PrefsKeys.HIGH_SPEED_THRESHOLD, com.catsmoker.obd2ai.prefs.PrefsKeys.DEFAULT_HIGH_SPEED)) }
    var coolant by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.OFFLINE_COOLANT_ENABLED, true)) }
    var coolantThreshold by remember { mutableStateOf(prefs.getInt(PrefsKeys.OFFLINE_COOLANT_THRESHOLD, com.catsmoker.obd2ai.prefs.PrefsKeys.DEFAULT_OFFLINE_COOLANT)) }
    var newFault by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.NEW_FAULT_ENABLED, true)) }
    var connLost by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.CONN_LOST_ENABLED, true)) }
    var connRestored by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.CONN_RESTORED_ENABLED, true)) }
    var engineStart by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ENGINE_START_ENABLED, true)) }
    var engineStop by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ENGINE_STOP_ENABLED, false)) }
    var easter by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.EASTER_EGGS_ENABLED, true)) }
    var onlineVoice by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, true)) }
    var onlineText by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, true)) }
    var aiVolume by remember { mutableStateOf(prefs.getInt(PrefsKeys.AI_VOLUME, com.catsmoker.obd2ai.prefs.PrefsKeys.DEFAULT_AI_VOLUME)) }
    var aiFrequency by remember { mutableStateOf(AiFrequency.fromPref(prefs.getString(PrefsKeys.AI_FREQUENCY, null))) }
    var aiPersonality by remember { mutableStateOf(AiPersonality.fromPref(prefs.getString(PrefsKeys.AI_PERSONALITY, null))) }
    var analytics by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ANALYTICS_ENABLED, true)) }
    var personalizedAds by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.PERSONALIZED_ADS, true)) }

    var apiKeyText by remember { mutableStateOf(prefs.getString(PrefsKeys.OPENAI_API_KEY, "")) }
    var modelIdText by remember { mutableStateOf(prefs.getString(PrefsKeys.OPENAI_MODEL_ID, "")) }
    var baseUrlText by remember { mutableStateOf(prefs.getString(PrefsKeys.AI_BASE_URL, "")) }

    val styles = SpeedometerStyle.entries
    val selectedStyle = styles[styleIndex]
    val providers = AiProvider.entries
    val selectedProvider = providers.firstOrNull { it.id == prefs.getString(PrefsKeys.AI_PROVIDER, AiProvider.OPENAI.id) } ?: AiProvider.OPENAI
    val frequencies = AiFrequency.entries
    val personalities = AiPersonality.entries

    var themeDropdownOpen by remember { mutableStateOf(false) }
    var langDropdownOpen by remember { mutableStateOf(false) }
    var providerDropdownOpen by remember { mutableStateOf(false) }
    var freqDropdownOpen by remember { mutableStateOf(false) }
    var personalityDropdownOpen by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = ctx.getString(R.string.settings_title),
        onBack = navigateUp,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = ctx.getString(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ---- General ----
            SectionCard {
                SectionLabel(text = ctx.getString(R.string.settings_category_general), accent = true)
                Spacer(modifier = Modifier.height(8.dp))
                SettingsRow(
                    title = ctx.getString(R.string.settings_theme_title),
                    summary = when (themeMode) {
                        ThemeMode.LIGHT -> ctx.getString(R.string.settings_theme_light)
                        ThemeMode.DARK -> ctx.getString(R.string.settings_theme_dark)
                        ThemeMode.SYSTEM -> ctx.getString(R.string.settings_theme_system)
                    },
                    onClick = { themeDropdownOpen = true }
                )
                DropdownMenu(
                    expanded = themeDropdownOpen,
                    onDismissRequest = { themeDropdownOpen = false }
                ) {
                    DropdownMenuItem(text = { Text(text = ctx.getString(R.string.settings_theme_system)) }, onClick = {
                        themeDropdownOpen = false; themeMode = ThemeMode.SYSTEM; onThemeChange(ThemeMode.SYSTEM)
                    })
                    DropdownMenuItem(text = { Text(text = ctx.getString(R.string.settings_theme_light)) }, onClick = {
                        themeDropdownOpen = false; themeMode = ThemeMode.LIGHT; onThemeChange(ThemeMode.LIGHT)
                    })
                    DropdownMenuItem(text = { Text(text = ctx.getString(R.string.settings_theme_dark)) }, onClick = {
                        themeDropdownOpen = false; themeMode = ThemeMode.DARK; onThemeChange(ThemeMode.DARK)
                    })
                }
                Spacer(modifier = Modifier.height(16.dp))

                SettingsRow(
                    title = ctx.getString(R.string.settings_language_title),
                    summary = appLang.displayName,
                    onClick = { langDropdownOpen = true }
                )
                DropdownMenu(
                    expanded = langDropdownOpen,
                    onDismissRequest = { langDropdownOpen = false }
                ) {
                    AppLanguage.entries.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(text = lang.displayName) },
                            onClick = { langDropdownOpen = false; appLang = lang; onLanguageChange(lang.tag) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                SettingsRow(
                    title = ctx.getString(R.string.settings_speed_source_title),
                    summary = if (speedSourceDevice) ctx.getString(R.string.settings_speed_source_gps) else ctx.getString(R.string.settings_speed_source_obd),
                    onClick = { speedSourceDevice = !speedSourceDevice; onSpeedSourceChange(speedSourceDevice) }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ---- Driving ----
            SectionCard {
                SectionLabel(text = ctx.getString(R.string.settings_category_driving), accent = true)
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow(
                    title = ctx.getString(R.string.settings_mute_sound),
                    checked = mute,
                    onCheckedChange = { mute = it; onMuteChange(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SettingsRow(title = ctx.getString(R.string.settings_units_title), onClick = {})
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioTile(selected = !imperial, label = ctx.getString(R.string.settings_units_metric), onClick = { imperial = false; onUnitsChange(false) })
                    RadioTile(selected = imperial, label = ctx.getString(R.string.settings_units_imperial), onClick = { imperial = true; onUnitsChange(true) })
                }
                Spacer(modifier = Modifier.height(16.dp))
                SwitchRow(title = ctx.getString(R.string.settings_keep_screen), checked = keepScreen, onCheckedChange = { keepScreen = it; onKeepScreenChange(it) })
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow(title = ctx.getString(R.string.settings_engine_sound), checked = engineSound, onCheckedChange = { engineSound = it; onEngineSoundChange(it) })
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ---- Speedometer ----
            SectionCard {
                SectionLabel(text = ctx.getString(R.string.settings_speedometer_title), accent = true)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = ctx.getString(R.string.settings_speedometer_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ObdOutlinedButton(onClick = onStylePrev, modifier = Modifier.width(40.dp).height(40.dp)) {
                        Text(text = "‹", style = MaterialTheme.typography.bodyLarge)
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = "${styleIndex + 1} / ${styles.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    ObdOutlinedButton(onClick = onStyleNext, modifier = Modifier.width(40.dp).height(40.dp)) {
                        Text(text = "›", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = ctx.getString(selectedStyle.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ctx.getString(selectedStyle.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, summary: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.weight(1f))
        if (summary != null) {
            Text(text = summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = "▾", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadioTile(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(18.dp).height(18.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    androidx.compose.foundation.shape.CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
