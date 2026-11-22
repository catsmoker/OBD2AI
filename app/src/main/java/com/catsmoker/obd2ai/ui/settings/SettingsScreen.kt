package com.catsmoker.obd2ai.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.AiFrequency
import com.catsmoker.obd2ai.ai.AiPersonality
import com.catsmoker.obd2ai.ai.AiProvider
import com.catsmoker.obd2ai.ai.AiSwitchRules
import com.catsmoker.obd2ai.prefs.AppLanguage
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.ThemeMode
import com.catsmoker.obd2ai.prefs.Units
import com.catsmoker.obd2ai.speedometers.SpeedometerHost
import com.catsmoker.obd2ai.speedometers.SpeedometerStyle
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.CollapsibleSectionCard
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.components.SectionCard
import com.catsmoker.obd2ai.ui.components.SectionLabel

/**
 * Settings, one card per concern: General / Driving alarms / Speedometer /
 * Offline AI / Online AI / Privacy. Every control writes through the callback
 * it is given; the screen never touches prefs except to seed initial state.
 */
@Composable
fun SettingsScreen(
    prefs: SharedPreferences,
    adBilling: com.catsmoker.obd2ai.ads.AdBillingManager,
    context: Context,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onUnitsChange: (Boolean) -> Unit,
    onSpeedSourceChange: (Boolean) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onKeepScreenChange: (Boolean) -> Unit,
    onEngineSoundChange: (Boolean) -> Unit,
    onStyleChange: (SpeedometerStyle) -> Unit,
    onAiMasterChange: (AiSwitchRules.AiSystem, Boolean) -> Pair<Boolean, Boolean>,
    onOfflineOutputChange: (Boolean, Boolean) -> Pair<Boolean, Boolean>,
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
    onOnlineOutputChange: (Boolean, Boolean) -> Pair<Boolean, Boolean>,
    onAiVolumeChange: (Int) -> Unit,
    onAiFrequencyChange: (AiFrequency) -> Unit,
    onAiPersonalityChange: (AiPersonality) -> Unit,
    onApiSave: (AiProvider, String, String, String) -> Unit,
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
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current

    var themeMode by remember { mutableStateOf(ThemeMode.fromPref(prefs.getString(PrefsKeys.THEME_MODE, null))) }
    var dynamicColor by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.DYNAMIC_COLOR, false)) }
    var appLang by remember { mutableStateOf(AppLanguage.fromTag(prefs.getString(PrefsKeys.APP_LANGUAGE, null))) }
    var imperial by remember { mutableStateOf(Units.isImperial(prefs)) }
    var speedSourceDevice by remember {
        mutableStateOf(
            prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD) == PrefsKeys.SPEED_SOURCE_DEVICE
        )
    }
    var mute by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)) }
    var keepScreen by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.KEEP_SCREEN_ON, false)) }
    var engineSound by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ENGINE_SOUND_ENABLED, false)) }
    var styleIndex by remember {
        mutableStateOf(
            SpeedometerStyle.entries.indexOf(
                SpeedometerStyle.fromId(prefs.getString(PrefsKeys.SPEEDOMETER_STYLE, PrefsKeys.SPEEDOMETER_DEFAULT))
            ).coerceAtLeast(0)
        )
    }

    var offlineAi by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.OFFLINE_AI_ENABLED, true)) }
    var onlineAi by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)) }
    var offlineVoice by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.VOICE_INSIGHT, true)) }
    var offlineText by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, true)) }
    var offlineVolume by remember {
        mutableStateOf(prefs.getInt(PrefsKeys.OFFLINE_VOLUME, PrefsKeys.DEFAULT_OFFLINE_VOLUME).coerceIn(0, 100))
    }

    var shiftPoint by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.SHIFT_POINT_ENABLED, true)) }
    var shiftRpm by remember {
        mutableStateOf(prefs.getInt(PrefsKeys.SHIFT_RPM, PrefsKeys.DEFAULT_SHIFT_RPM).coerceIn(PrefsKeys.SHIFT_RPM_MIN, PrefsKeys.SHIFT_RPM_MAX))
    }
    var highRpm by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.HIGH_RPM_ENABLED, true)) }
    var highRpmThreshold by remember {
        mutableStateOf(prefs.getInt(PrefsKeys.HIGH_RPM_THRESHOLD, PrefsKeys.DEFAULT_HIGH_RPM).coerceIn(PrefsKeys.HIGH_RPM_MIN, PrefsKeys.HIGH_RPM_MAX))
    }
    var highSpeed by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.HIGH_SPEED_ENABLED, true)) }
    var highSpeedThreshold by remember {
        mutableStateOf(prefs.getInt(PrefsKeys.HIGH_SPEED_THRESHOLD, PrefsKeys.DEFAULT_HIGH_SPEED).coerceIn(PrefsKeys.HIGH_SPEED_MIN, PrefsKeys.HIGH_SPEED_MAX))
    }
    var coolant by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.OFFLINE_COOLANT_ENABLED, true)) }
    var coolantThreshold by remember {
        mutableStateOf(prefs.getInt(PrefsKeys.OFFLINE_COOLANT_THRESHOLD, PrefsKeys.DEFAULT_OFFLINE_COOLANT).coerceIn(PrefsKeys.OFFLINE_COOLANT_MIN, PrefsKeys.OFFLINE_COOLANT_MAX))
    }
    var newFault by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.NEW_FAULT_ENABLED, true)) }
    var connLost by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.CONN_LOST_ENABLED, true)) }
    var connRestored by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.CONN_RESTORED_ENABLED, true)) }
    var engineStart by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ENGINE_START_ENABLED, true)) }
    var engineStop by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ENGINE_STOP_ENABLED, false)) }
    var easter by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.EASTER_EGGS_ENABLED, true)) }

    var onlineVoice by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, true)) }
    var onlineText by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, true)) }
    var aiVolume by remember {
        mutableStateOf(prefs.getInt(PrefsKeys.AI_VOLUME, PrefsKeys.DEFAULT_AI_VOLUME).coerceIn(0, 100))
    }
    var aiFrequency by remember {
        mutableStateOf(AiFrequency.fromPref(prefs.getString(PrefsKeys.AI_FREQUENCY, null)))
    }
    var aiPersonality by remember {
        mutableStateOf(AiPersonality.fromPref(prefs.getString(PrefsKeys.AI_PERSONALITY, null)))
    }

    // Open sections survive rotation; everything but General starts folded so
    // the list stays short on a phone.
    var openSections by rememberSaveable { mutableStateOf(listOf("general")) }
    val toggleSection: (String) -> Unit = { id ->
        openSections = if (openSections.contains(id)) openSections - id else openSections + id
    }

    var analytics by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ANALYTICS_ENABLED, true)) }
    var personalizedAds by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.PERSONALIZED_ADS, true)) }

    // API configuration is only committed by Save: draft edits stay local so a
    // half-typed key is never used by a background request.
    var provider by remember { mutableStateOf(AiProvider.fromId(prefs.getString(PrefsKeys.AI_PROVIDER, null))) }
    var apiKey by remember { mutableStateOf(prefs.getString(PrefsKeys.OPENAI_API_KEY, "").orEmpty()) }
    var modelId by remember { mutableStateOf(prefs.getString(PrefsKeys.OPENAI_MODEL_ID, "").orEmpty()) }
    var baseUrl by remember { mutableStateOf(prefs.getString(PrefsKeys.AI_BASE_URL, "").orEmpty()) }
    var savedConfigVersion by remember { mutableStateOf(0) }

    val adsRemoved by adBilling.isAdsRemoved.collectAsState()
    val removeAdsPrice by adBilling.removeAdsPrice.collectAsState()

    // The two assistants are mutually exclusive; a legacy state with both ON
    // settles to Offline ON / Online OFF the first time Settings opens.
    LaunchedEffect(Unit) {
        if (offlineAi && onlineAi) {
            val (o, n) = onAiMasterChange(AiSwitchRules.AiSystem.OFFLINE, true)
            offlineAi = o
            onlineAi = n
        }
    }

    val styles = SpeedometerStyle.entries
    val selectedStyle = styles[styleIndex]
    val configuredProvider = AiProvider.fromId(prefs.getString(PrefsKeys.AI_PROVIDER, null))
    val configuredKey = prefs.getString(PrefsKeys.OPENAI_API_KEY, "").orEmpty().trim()
    val aiConfigured = remember(savedConfigVersion) {
        !configuredProvider.needsKey || configuredKey.isNotEmpty()
    }
    val aiStatus = remember(savedConfigVersion, aiConfigured) {
        if (aiConfigured) {
            val model = prefs.getString(PrefsKeys.OPENAI_MODEL_ID, "").orEmpty().trim()
                .ifEmpty { configuredProvider.defaultModel }
            ctx.getString(R.string.settings_ai_status_ok, configuredProvider.displayName, model)
        } else {
            ctx.getString(R.string.settings_ai_status_missing)
        }
    }

    ScreenScaffold(
        title = ctx.getString(R.string.settings_title),
        onBack = onBack,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ---- General ----
            CollapsibleSectionCard(
                title = ctx.getString(R.string.settings_category_general),
                expanded = openSections.contains("general"),
                onToggle = { toggleSection("general") },
                expandLabel = ctx.getString(R.string.settings_section_expand),
                collapseLabel = ctx.getString(R.string.settings_section_collapse)
            ) {
                DropdownRow(
                    title = ctx.getString(R.string.settings_theme_title),
                    value = when (themeMode) {
                        ThemeMode.LIGHT -> ctx.getString(R.string.settings_theme_light)
                        ThemeMode.DARK -> ctx.getString(R.string.settings_theme_dark)
                        ThemeMode.SYSTEM -> ctx.getString(R.string.settings_theme_system)
                    },
                    options = listOf(
                        ctx.getString(R.string.settings_theme_system),
                        ctx.getString(R.string.settings_theme_light),
                        ctx.getString(R.string.settings_theme_dark)
                    ),
                    onSelect = { index ->
                        themeMode = when (index) {
                            1 -> ThemeMode.LIGHT
                            2 -> ThemeMode.DARK
                            else -> ThemeMode.SYSTEM
                        }
                        onThemeChange(themeMode)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                DropdownRow(
                    title = ctx.getString(R.string.settings_language_title),
                    value = appLang.displayName,
                    options = AppLanguage.entries.map { it.displayName },
                    onSelect = { index ->
                        appLang = AppLanguage.entries[index]
                        onLanguageChange(appLang.tag)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                DropdownRow(
                    title = ctx.getString(R.string.settings_speed_source_title),
                    value = if (speedSourceDevice) {
                        ctx.getString(R.string.settings_speed_source_gps)
                    } else {
                        ctx.getString(R.string.settings_speed_source_obd)
                    },
                    options = listOf(
                        ctx.getString(R.string.settings_speed_source_obd),
                        ctx.getString(R.string.settings_speed_source_gps)
                    ),
                    onSelect = { index ->
                        speedSourceDevice = index == 1
                        onSpeedSourceChange(speedSourceDevice)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    SwitchRow(
                        title = ctx.getString(R.string.settings_dynamic_color),
                        subtitle = ctx.getString(R.string.settings_dynamic_color_desc),
                        checked = dynamicColor,
                        onCheckedChange = {
                            dynamicColor = it
                            onDynamicColorChange(it)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Driving alerts ----
            CollapsibleSectionCard(
                title = ctx.getString(R.string.settings_category_driving),
                expanded = openSections.contains("driving"),
                onToggle = { toggleSection("driving") },
                expandLabel = ctx.getString(R.string.settings_section_expand),
                collapseLabel = ctx.getString(R.string.settings_section_collapse)
            ) {
                SwitchRow(
                    title = ctx.getString(R.string.settings_mute_sound),
                    checked = mute,
                    onCheckedChange = { mute = it; onMuteChange(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = ctx.getString(R.string.settings_units_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioTile(
                        selected = !imperial,
                        label = ctx.getString(R.string.settings_units_metric),
                        modifier = Modifier.weight(1f),
                        onClick = { imperial = false; onUnitsChange(false) }
                    )
                    RadioTile(
                        selected = imperial,
                        label = ctx.getString(R.string.settings_units_imperial),
                        modifier = Modifier.weight(1f),
                        onClick = { imperial = true; onUnitsChange(true) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                SwitchRow(
                    title = ctx.getString(R.string.settings_keep_screen),
                    checked = keepScreen,
                    onCheckedChange = { keepScreen = it; onKeepScreenChange(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SwitchRow(
                    title = ctx.getString(R.string.settings_engine_sound),
                    checked = engineSound,
                    onCheckedChange = { engineSound = it; onEngineSoundChange(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Speedometer ----
            CollapsibleSectionCard(
                title = ctx.getString(R.string.settings_speedometer_title),
                expanded = openSections.contains("speedometer"),
                onToggle = { toggleSection("speedometer") },
                expandLabel = ctx.getString(R.string.settings_section_expand),
                collapseLabel = ctx.getString(R.string.settings_section_collapse)
            ) {
                Text(
                    text = ctx.getString(R.string.settings_speedometer_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ObdOutlinedButton(
                        onClick = {
                            styleIndex = SpeedometerStyle.wrappedIndex(styleIndex - 1)
                            onStyleChange(styles[styleIndex])
                        },
                        modifier = Modifier.width(48.dp).height(48.dp)
                    ) {
                        Text(text = "‹", style = MaterialTheme.typography.titleLarge)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        key(selectedStyle, imperial) {
                            AndroidView(
                                factory = { viewCtx -> SpeedometerHost.createView(viewCtx, selectedStyle) },
                                update = { view -> SpeedometerHost.bindPreview(view, imperial) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    ObdOutlinedButton(
                        onClick = {
                            styleIndex = SpeedometerStyle.wrappedIndex(styleIndex + 1)
                            onStyleChange(styles[styleIndex])
                        },
                        modifier = Modifier.width(48.dp).height(48.dp)
                    ) {
                        Text(text = "›", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = ctx.getString(selectedStyle.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    text = ctx.getString(selectedStyle.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Offline AI ----
            CollapsibleSectionCard(
                title = ctx.getString(R.string.settings_offline_ai_title),
                expanded = openSections.contains("offline_ai"),
                onToggle = { toggleSection("offline_ai") },
                expandLabel = ctx.getString(R.string.settings_section_expand),
                collapseLabel = ctx.getString(R.string.settings_section_collapse)
            ) {
                Text(
                    text = ctx.getString(R.string.settings_offline_ai_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow(
                    title = ctx.getString(R.string.settings_offline_ai_title),
                    checked = offlineAi,
                    onCheckedChange = { checked ->
                        val (o, n) = onAiMasterChange(AiSwitchRules.AiSystem.OFFLINE, checked)
                        offlineAi = o
                        onlineAi = n
                    }
                )
                if (offlineAi) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = ctx.getString(R.string.settings_alert_output),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SwitchRow(
                        title = ctx.getString(R.string.settings_voice_alerts),
                        checked = offlineVoice,
                        onCheckedChange = { checked ->
                            val (v, t) = onOfflineOutputChange(checked, offlineText)
                            offlineVoice = v
                            offlineText = t
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SwitchRow(
                        title = ctx.getString(R.string.settings_text_alerts),
                        checked = offlineText,
                        onCheckedChange = { checked ->
                            // Turning the last output off would silence the
                            // assistant: the shared rule forces the other on.
                            val (v, t) = onOfflineOutputChange(offlineVoice, checked)
                            offlineVoice = v
                            offlineText = t
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SliderRow(
                        title = ctx.getString(R.string.settings_voice_volume),
                        valueText = ctx.getString(R.string.settings_ai_volume_value, offlineVolume),
                        value = offlineVolume,
                        range = 0..100,
                        step = 5,
                        onChange = { offlineVolume = it; onOfflineVolumeChange(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = ctx.getString(R.string.settings_category_driving),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SwitchRow(
                        title = ctx.getString(R.string.settings_shift_rpm_title),
                        checked = shiftPoint,
                        onCheckedChange = { shiftPoint = it; onShiftPointChange(it) }
                    )
                    if (shiftPoint) {
                        SliderRow(
                            title = ctx.getString(R.string.settings_shift_rpm_value, shiftRpm),
                            valueText = "",
                            value = shiftRpm,
                            range = PrefsKeys.SHIFT_RPM_MIN..PrefsKeys.SHIFT_RPM_MAX,
                            step = PrefsKeys.SHIFT_RPM_STEP,
                            onChange = { shiftRpm = it; onShiftRpmChange(it) }
                        )
                    }
                    SwitchRow(
                        title = ctx.getString(R.string.settings_alert_high_rpm),
                        checked = highRpm,
                        onCheckedChange = { highRpm = it; onHighRpmChange(it) }
                    )
                    if (highRpm) {
                        SliderRow(
                            title = ctx.getString(R.string.settings_shift_rpm_value, highRpmThreshold),
                            valueText = "",
                            value = highRpmThreshold,
                            range = PrefsKeys.HIGH_RPM_MIN..PrefsKeys.HIGH_RPM_MAX,
                            step = PrefsKeys.HIGH_RPM_STEP,
                            onChange = { highRpmThreshold = it; onHighRpmThresholdChange(it) }
                        )
                    }
                    SwitchRow(
                        title = ctx.getString(R.string.settings_alert_high_speed),
                        checked = highSpeed,
                        onCheckedChange = { highSpeed = it; onHighSpeedChange(it) }
                    )
                    if (highSpeed) {
                        SliderRow(
                            title = ctx.getString(R.string.settings_speed_value, highSpeedThreshold),
                            valueText = "",
                            value = highSpeedThreshold,
                            range = PrefsKeys.HIGH_SPEED_MIN..PrefsKeys.HIGH_SPEED_MAX,
                            step = PrefsKeys.HIGH_SPEED_STEP,
                            onChange = { highSpeedThreshold = it; onHighSpeedThresholdChange(it) }
                        )
                    }
                    SwitchRow(
                        title = ctx.getString(R.string.settings_alert_coolant),
                        checked = coolant,
                        onCheckedChange = { coolant = it; onCoolantChange(it) }
                    )
                    if (coolant) {
                        SliderRow(
                            title = ctx.getString(R.string.settings_temp_value, coolantThreshold),
                            valueText = "",
                            value = coolantThreshold,
                            range = PrefsKeys.OFFLINE_COOLANT_MIN..PrefsKeys.OFFLINE_COOLANT_MAX,
                            step = PrefsKeys.OFFLINE_COOLANT_STEP,
                            onChange = { coolantThreshold = it; onCoolantThresholdChange(it) }
                        )
                    }
                    SwitchRow(
                        title = ctx.getString(R.string.settings_alert_new_fault),
                        checked = newFault,
                        onCheckedChange = { newFault = it; onNewFaultChange(it) }
                    )
                    SwitchRow(
                        title = ctx.getString(R.string.settings_alert_conn_lost),
                        checked = connLost,
                        onCheckedChange = { connLost = it; onConnLostChange(it) }
                    )
                    SwitchRow(
                        title = ctx.getString(R.string.settings_alert_conn_restored),
                        checked = connRestored,
                        onCheckedChange = { connRestored = it; onConnRestoredChange(it) }
                    )
                    SwitchRow(
                        title = ctx.getString(R.string.settings_alert_engine_start),
                        checked = engineStart,
                        onCheckedChange = { engineStart = it; onEngineStartChange(it) }
                    )
                    SwitchRow(
                        title = ctx.getString(R.string.settings_alert_engine_stop),
                        checked = engineStop,
                        onCheckedChange = { engineStop = it; onEngineStopChange(it) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SwitchRow(
                        title = ctx.getString(R.string.settings_easter_eggs),
                        checked = easter,
                        onCheckedChange = { easter = it; onEasterChange(it) }
                    )
                    Text(
                        text = ctx.getString(R.string.settings_easter_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ObdOutlinedButton(onClick = onTestOfflineVoice, modifier = Modifier.weight(1f)) {
                            Text(text = ctx.getString(R.string.settings_test_voice))
                        }
                        ObdOutlinedButton(onClick = onOfflineTtsSettings, modifier = Modifier.weight(1f)) {
                            Text(text = ctx.getString(R.string.settings_tts_system))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Online AI ----
            CollapsibleSectionCard(
                title = ctx.getString(R.string.settings_online_ai_title),
                expanded = openSections.contains("online_ai"),
                onToggle = { toggleSection("online_ai") },
                expandLabel = ctx.getString(R.string.settings_section_expand),
                collapseLabel = ctx.getString(R.string.settings_section_collapse)
            ) {
                Text(
                    text = ctx.getString(R.string.settings_online_ai_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow(
                    title = ctx.getString(R.string.settings_online_ai_title),
                    checked = onlineAi,
                    onCheckedChange = { checked ->
                        val (o, n) = onAiMasterChange(AiSwitchRules.AiSystem.ONLINE, checked)
                        offlineAi = o
                        onlineAi = n
                    }
                )
                if (onlineAi) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SwitchRow(
                        title = ctx.getString(R.string.settings_voice_alerts),
                        checked = onlineVoice,
                        onCheckedChange = { checked ->
                            val (v, t) = onOnlineOutputChange(checked, onlineText)
                            onlineVoice = v
                            onlineText = t
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SwitchRow(
                        title = ctx.getString(R.string.settings_text_alerts),
                        checked = onlineText,
                        onCheckedChange = { checked ->
                            val (v, t) = onOnlineOutputChange(onlineVoice, checked)
                            onlineVoice = v
                            onlineText = t
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = ctx.getString(R.string.settings_ai_config),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = aiStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (aiConfigured) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DropdownRow(
                        title = ctx.getString(R.string.settings_ai_provider),
                        value = provider.displayName,
                        options = AiProvider.entries.map { it.displayName },
                        onSelect = { index -> provider = AiProvider.entries[index] }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = {
                            Text(
                                text = if (provider.needsKey) {
                                    ctx.getString(R.string.settings_api_key_hint)
                                } else {
                                    ctx.getString(R.string.settings_api_key_hint_custom)
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text(text = ctx.getString(R.string.settings_model_id_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (provider.showBaseUrl) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text(text = ctx.getString(R.string.settings_base_url_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = ctx.getString(R.string.settings_api_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ObdButton(
                        onClick = {
                            onApiSave(provider, apiKey, modelId, baseUrl)
                            savedConfigVersion++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = ctx.getString(R.string.settings_button_save))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = ctx.getString(R.string.settings_ai_behavior),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    DropdownRow(
                        title = ctx.getString(R.string.settings_ai_frequency),
                        value = displayName(aiFrequency.name),
                        options = AiFrequency.entries.map { displayName(it.name) },
                        onSelect = { index ->
                            aiFrequency = AiFrequency.entries[index]
                            onAiFrequencyChange(aiFrequency)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DropdownRow(
                        title = ctx.getString(R.string.settings_ai_personality),
                        value = displayName(aiPersonality.name),
                        options = AiPersonality.entries.map { displayName(it.name) },
                        onSelect = { index ->
                            aiPersonality = AiPersonality.entries[index]
                            onAiPersonalityChange(aiPersonality)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SliderRow(
                        title = ctx.getString(R.string.settings_ai_volume),
                        valueText = ctx.getString(R.string.settings_ai_volume_value, aiVolume),
                        value = aiVolume,
                        range = 0..100,
                        step = 5,
                        onChange = { aiVolume = it; onAiVolumeChange(it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ctx.getString(R.string.settings_device_voice_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ObdOutlinedButton(onClick = onTestVoice, modifier = Modifier.weight(1f)) {
                            Text(text = ctx.getString(R.string.settings_test_voice))
                        }
                        ObdOutlinedButton(onClick = onTtsSettings, modifier = Modifier.weight(1f)) {
                            Text(text = ctx.getString(R.string.settings_tts_system))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Privacy ----
            CollapsibleSectionCard(
                title = ctx.getString(R.string.settings_privacy_title),
                expanded = openSections.contains("privacy"),
                onToggle = { toggleSection("privacy") },
                expandLabel = ctx.getString(R.string.settings_section_expand),
                collapseLabel = ctx.getString(R.string.settings_section_collapse)
            ) {
                Text(
                    text = ctx.getString(R.string.settings_privacy_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow(
                    title = ctx.getString(R.string.settings_analytics_title),
                    checked = analytics,
                    onCheckedChange = { analytics = it; onAnalyticsChange(it) }
                )
                Text(
                    text = ctx.getString(R.string.settings_analytics_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                SwitchRow(
                    title = ctx.getString(R.string.settings_ads_title),
                    checked = personalizedAds,
                    onCheckedChange = { personalizedAds = it; onPersonalizedAdsChange(it) }
                )
                Text(
                    text = ctx.getString(R.string.settings_ads_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = ctx.getString(R.string.settings_ads_consent_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                ObdOutlinedButton(onClick = onAdConsentOptions, modifier = Modifier.fillMaxWidth()) {
                    Text(text = ctx.getString(R.string.settings_ads_consent_button))
                }

                Spacer(modifier = Modifier.height(12.dp))
                if (adsRemoved) {
                    Text(
                        text = ctx.getString(R.string.settings_ads_removed_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = ctx.getString(R.string.settings_ads_removed_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = ctx.getString(R.string.settings_remove_ads_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = ctx.getString(R.string.settings_remove_ads_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ObdButton(onClick = onRemoveAdsClick, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = removeAdsPrice?.let {
                                ctx.getString(R.string.settings_remove_ads_button_price, it)
                            } ?: ctx.getString(R.string.settings_remove_ads_button)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ObdOutlinedButton(onClick = onPrivacyPolicy, modifier = Modifier.weight(1f)) {
                        Text(text = ctx.getString(R.string.legal_privacy_title))
                    }
                    ObdOutlinedButton(onClick = onTerms, modifier = Modifier.weight(1f)) {
                        Text(text = ctx.getString(R.string.legal_terms_title))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ObdOutlinedButton(onClick = onOnlineLegal, modifier = Modifier.fillMaxWidth()) {
                    Text(text = ctx.getString(R.string.about_legal_online_button))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** "low" -> "Low": enum names are the only labels the AI settings have. */
private fun displayName(raw: String): String =
    raw.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (valueText.isNotEmpty()) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
        )
    }
}

/**
 * Label row with the current value in a tonal pill. The pill is what makes
 * the row read as a control rather than as text, and it keeps long
 * translations from colliding with the label.
 */
@Composable
private fun DropdownRow(
    title: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { expanded = true }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(text = label) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    }
                )
            }
        }
    }
}

/**
 * Selectable tile. Takes a [Modifier] so callers can give it weight — without
 * one, `fillMaxWidth` in a Row squeezes the sibling to a one-letter column.
 */
@Composable
private fun RadioTile(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = 2.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
