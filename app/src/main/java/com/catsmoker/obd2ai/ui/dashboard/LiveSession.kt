package com.catsmoker.obd2ai.ui.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.AiService
import com.catsmoker.obd2ai.ai.OnlineAiManager
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.Units
import com.catsmoker.obd2ai.speedometers.SpeedometerStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Banner state; the session owns the countdown, the UI only renders it. */
data class LiveBanner(
    val id: Long,
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

/** Current simulated values, so reopening the demo sheet resumes where it left off. */
data class DemoSheetState(
    val speedKmh: Int,
    val rpm: Int,
    val coolantC: Int,
    val voltageTenths: Int,
    val fuelPct: Int,
    val engineOn: Boolean
)

/**
 * Live Data session: owns the polling session, the Offline AI event detectors,
 * the Online AI feed and all dashboard state. Extracted from the old
 * LiveDataFragment so the screen can be pure Compose; the logic is unchanged.
 * [start] runs when the screen composes, [stop] when it leaves composition.
 */
class LiveSession(
    internal val activity: MainActivity,
    private val showError: (String) -> Unit
) : LocationListener {

    /** Runs flows, timers and detector work; cancelled when the screen closes. */
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Outlives [scope] so the monitoring teardown survives composition teardown. */
    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Logical Offline AI events — detectors and UI use these, never filenames. */
    enum class OfflineAiEvent {
        WELCOME, SHIFT_POINT, HIGH_RPM, HIGH_SPEED, COOLANT_HIGH, COOLANT_OK,
        VOLTAGE_LOW, VOLTAGE_CRITICAL, FUEL_LOW, FUEL_CRITICAL, NEW_FAULT_CODE,
        CONNECTION_LOST, CONNECTION_RESTORED, ENGINE_STARTED, ENGINE_STOPPED
    }

    internal val prefs: SharedPreferences =
        activity.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
    private val obdHelper: ObdHelper = activity.obdHelper
    private val aiService: AiService = activity.aiService

    /** False once [stop] ran; guards callbacks that outlive the screen. */
    @Volatile
    internal var active = false
        private set

    // ---- Compose-observable dashboard state (null = unknown, never a fake 0) ----
    val speedKmh = mutableStateOf<Float?>(null)
    val rpmReading = mutableStateOf<Int?>(null)
    val coolantC = mutableStateOf<Int?>(null)
    val loadPct = mutableStateOf<Int?>(null)
    val voltageV = mutableStateOf<Float?>(null)
    val fuelPct = mutableStateOf<Int?>(null)
    val deviceTempC = mutableStateOf<Float?>(null)
    val muted = mutableStateOf(false)
    val rpmColor = mutableStateOf(LiveLogic.RPM_GREEN)
    val coolantAlarm = mutableStateOf(false)
    val banner = mutableStateOf<LiveBanner?>(null)
    val bannerSeconds = mutableStateOf(0)
    val onlineCardText = mutableStateOf<String?>(null)
    val onlineSeconds = mutableStateOf(0)
    val demoSheet = mutableStateOf<DemoSheetState?>(null)

    internal var bannerJob: kotlinx.coroutines.Job? = null
    internal var onlineJob: kotlinx.coroutines.Job? = null

    // ---- Tunables read once when the screen opens ----
    internal var rpmTier = 0
    internal var lastRpmValue = 0
    internal var coolantAlarmed = false
    internal var shiftRpm = PrefsKeys.DEFAULT_SHIFT_RPM
    internal var rpmMax = PrefsKeys.DEFAULT_RPM_MAX
    internal var highRpmThreshold = PrefsKeys.DEFAULT_HIGH_RPM
    internal var highSpeedThreshold = PrefsKeys.DEFAULT_HIGH_SPEED
    internal var coolantThreshold = PrefsKeys.DEFAULT_OFFLINE_COOLANT
    internal var offlineVolume = PrefsKeys.DEFAULT_OFFLINE_VOLUME / 100f
    internal var imperial = false
    internal var lastSpeedKmh = 0f
    internal var firstSpeedRead = true
    internal var firstRpmRead = true
    internal var prevCoolantC: Int? = null
    internal var connLost = false
    internal var lastVoltageTier = 0
    internal var lastFuelTier = 0
    internal var demoEngineOn = false
    internal var speedometerStyle = SpeedometerStyle.CLASSIC
    internal var gpsSpeedSource = false
    internal var demoMode = false
    internal val keepScreenOn by lazy {
        prefs.getBoolean(PrefsKeys.KEEP_SCREEN_ON, false)
    }

    internal val offlineKnownCodes = mutableSetOf<String>()
    internal var offlineCodesSeeded = false
    internal val offlineClearedCodes = mutableSetOf<String>()
    internal var easterOn = true
    internal var easterLines: Map<String, List<String>> = emptyMap()
    internal val easterCooldowns = mutableMapOf<String, Long>()
    internal val easterRecent = ArrayDeque<String>()
    internal val easterRandom = kotlin.random.Random.Default

    internal val offlineAiLastFired = mutableMapOf<OfflineAiEvent, Long>()
    internal var offlineAiWelcomed = false

    internal var locationManager: LocationManager? = null
    internal var mediaPlayer: MediaPlayer? = null
    internal var tts: android.speech.tts.TextToSpeech? = null
    internal var onlineAiTts: android.speech.tts.TextToSpeech? = null
    internal var onlineAiManager: OnlineAiManager? = null
    internal val speechQueue = com.catsmoker.obd2ai.ai.SpeechQueue()
    internal var speakingSeverity: com.catsmoker.obd2ai.ai.OnlineAiSeverity? = null

    internal val speechProgressListener = object : android.speech.tts.UtteranceProgressListener() {
        override fun onDone(utteranceId: String?) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { onSpeechDone() }
        }

        override fun onError(utteranceId: String?) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { onSpeechDone() }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { onSpeechDone() }
        }

        override fun onStart(utteranceId: String?) {}
    }
    internal val engineSound = com.catsmoker.obd2ai.audio.EngineSound()

    internal val onlineAiListener = object : OnlineAiManager.Listener {
        override fun onOnlineAiResponse(text: String, severity: com.catsmoker.obd2ai.ai.OnlineAiSeverity) {
            if (!active) return
            if (prefs.getBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, true)) {
                showOnlineAiCard(text)
            }
            if (prefs.getBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, true)) {
                speakOnlineAi(text, severity)
            }
        }
    }

    internal val batteryTempReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val temperature = intent?.getIntExtra(
                android.os.BatteryManager.EXTRA_TEMPERATURE,
                0
            )?.div(10f)
            if (temperature != null && temperature > 0f) deviceTempC.value = temperature
        }
    }

    /** Starts monitoring and all detectors. Call once, when composition begins. */
    fun start() {
        active = true
        // Fresh screen, fresh detectors: thresholds are snapshotted when the
        // screen opens (edits in Settings rebuild the dashboard).
        shiftRpm = prefs.getInt(PrefsKeys.SHIFT_RPM, PrefsKeys.DEFAULT_SHIFT_RPM)
            .coerceIn(PrefsKeys.SHIFT_RPM_MIN, PrefsKeys.SHIFT_RPM_MAX)
        rpmMax = prefs.getInt(PrefsKeys.RPM_MAX, PrefsKeys.DEFAULT_RPM_MAX)
            .coerceIn(PrefsKeys.RPM_MAX_MIN, PrefsKeys.RPM_MAX_MAX)
        highRpmThreshold = prefs.getInt(PrefsKeys.HIGH_RPM_THRESHOLD, PrefsKeys.DEFAULT_HIGH_RPM)
            .coerceIn(PrefsKeys.HIGH_RPM_MIN, PrefsKeys.HIGH_RPM_MAX)
        highSpeedThreshold =
            prefs.getInt(PrefsKeys.HIGH_SPEED_THRESHOLD, PrefsKeys.DEFAULT_HIGH_SPEED)
                .coerceIn(PrefsKeys.HIGH_SPEED_MIN, PrefsKeys.HIGH_SPEED_MAX)
        coolantThreshold =
            prefs.getInt(PrefsKeys.OFFLINE_COOLANT_THRESHOLD, PrefsKeys.DEFAULT_OFFLINE_COOLANT)
                .coerceIn(PrefsKeys.OFFLINE_COOLANT_MIN, PrefsKeys.OFFLINE_COOLANT_MAX)
        offlineVolume =
            prefs.getInt(PrefsKeys.OFFLINE_VOLUME, PrefsKeys.DEFAULT_OFFLINE_VOLUME)
                .coerceIn(0, 100) / 100f
        imperial = Units.isImperial(prefs)
        muted.value = prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)
        demoMode = obdHelper.demoMode
        gpsSpeedSource =
            prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD) ==
                PrefsKeys.SPEED_SOURCE_DEVICE
        speedometerStyle = SpeedometerStyle.fromId(
            prefs.getString(PrefsKeys.SPEEDOMETER_STYLE, PrefsKeys.SPEEDOMETER_DEFAULT)
        )

        rpmTier = 0
        coolantAlarmed = false
        lastVoltageTier = 0
        lastFuelTier = 0
        demoEngineOn = false
        firstSpeedRead = true
        firstRpmRead = true
        prevCoolantC = null
        connLost = false
        offlineKnownCodes.clear()
        offlineCodesSeeded = false
        offlineClearedCodes.clear()
        offlineAiLastFired.clear()
        offlineAiWelcomed = false
        easterOn = prefs.getBoolean(PrefsKeys.EASTER_EGGS_ENABLED, true)
        easterLines = mapOf(
            LiveLogic.POOL_SPEED_130 to activity.resources.getStringArray(R.array.easter_speed_130).toList(),
            LiveLogic.POOL_SPEED_140 to activity.resources.getStringArray(R.array.easter_speed_140).toList(),
            LiveLogic.POOL_SPEED_150 to activity.resources.getStringArray(R.array.easter_speed_150).toList(),
            LiveLogic.POOL_RPM to activity.resources.getStringArray(R.array.easter_rpm_high).toList(),
            LiveLogic.POOL_COMBO to activity.resources.getStringArray(R.array.easter_rpm_speed_combo).toList(),
            LiveLogic.POOL_COOLANT_AFTER to activity.resources.getStringArray(R.array.easter_coolant_after).toList(),
            LiveLogic.POOL_FAULT_NEW to activity.resources.getStringArray(R.array.easter_fault_new).toList(),
            LiveLogic.POOL_FAULT_RETURN to activity.resources.getStringArray(R.array.easter_fault_return).toList(),
            LiveLogic.POOL_FAULT_SPEEDING to activity.resources.getStringArray(R.array.easter_fault_speeding).toList(),
            LiveLogic.POOL_ULTRA to activity.resources.getStringArray(R.array.easter_ultra_rare).toList()
        )
        easterCooldowns.clear()
        easterRecent.clear()

        if (gpsSpeedSource) {
            startGpsSpeed()
            return
        }

        if (demoMode) seedDemoFlows()
        maybeStartEngineSound()
        fireOfflineAiOnce(OfflineAiEvent.WELCOME, activity.getString(R.string.offline_ai_online))

        onlineAiManager = OnlineAiManager(activity, aiService, onlineAiListener)
        if (prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true) &&
            onlineAiManager?.isBrainConfigured() == false
        ) {
            showOnlineAiCard(activity.getString(R.string.ai_no_key_hint))
        }

        scope.launch {
            ObdDataHolder.isMonitoring.set(false)
            obdHelper.startLiveDataMonitoring()
        }
        collectDashboardFlows()
    }

    /** GPS speed source: no adapter reads, so no RPM/telemetry tiles at all. */
    private fun startGpsSpeed() {
        fireOfflineAiOnce(OfflineAiEvent.WELCOME, activity.getString(R.string.offline_ai_online))
        locationManager =
            activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500,
                1f,
                this
            )
        } catch (e: SecurityException) {
            Log.e("LiveSession", "Location permission missing for GPS speed", e)
            showError(activity.getString(R.string.live_data_gps_permission))
            return
        }
        ContextCompat.registerReceiver(
            activity,
            batteryTempReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    internal fun collectDashboardFlows() {
        scope.launch {
            ObdDataHolder.speedFlow.collect { handleSpeed(it) }
        }
        scope.launch {
            ObdDataHolder.rpmFlow.collect { handleRpm(it) }
        }
        scope.launch {
            ObdDataHolder.coolantTempFlow.collect { handleCoolant(it) }
        }
        scope.launch {
            ObdDataHolder.engineLoadFlow.collect { text ->
                val value = text.split(" ")[0].toIntOrNull()
                loadPct.value = value
                if (value != null) onlineAiManager?.onEngineLoad(value)
            }
        }
        scope.launch {
            ObdDataHolder.voltageFlow.collect { text ->
                val volts = text.split(" ")[0].toFloatOrNull()
                voltageV.value = volts
                checkVoltageWarning(volts)
            }
        }
        scope.launch {
            ObdDataHolder.fuelFlow.collect { text ->
                val pct = text.split(" ")[0].toIntOrNull()
                fuelPct.value = pct
                checkFuelWarning(pct)
            }
        }
        // Slow telemetry: Online AI poll + Offline AI fault tracking. Polls
        // once immediately (drive-start heads-up) then ~every 30 s.
        scope.launch {
            suspend fun pollSlow() {
                val slow = runCatching { obdHelper.readSlowTelemetry() }.getOrNull() ?: return
                runCatching { onlineAiManager?.onSlowPoll(slow) }
                // Publish only successful reads; "--" placeholders mean unknown.
                if (slow.voltageV != null) ObdDataHolder.voltageFlow.value = "${slow.voltageV} V"
                if (slow.fuelPct != null) ObdDataHolder.fuelFlow.value = "${slow.fuelPct} %"
                if (!offlineCodesSeeded) {
                    offlineCodesSeeded = true
                    offlineKnownCodes.addAll(slow.codes)
                } else {
                    val fresh = slow.codes.filter { it !in offlineKnownCodes }
                    val gone = offlineKnownCodes.filter { it !in slow.codes }
                    offlineClearedCodes.addAll(gone)
                    offlineKnownCodes.retainAll(slow.codes.toSet())
                    offlineKnownCodes.addAll(slow.codes)
                    val returned = fresh.filter { it in offlineClearedCodes }
                    offlineClearedCodes.removeAll(returned.toSet())
                    val brandNew = fresh - returned.toSet()
                    if (brandNew.isNotEmpty() && alertEnabled(PrefsKeys.NEW_FAULT_ENABLED)) {
                        fireOfflineAi(
                            OfflineAiEvent.NEW_FAULT_CODE,
                            offlineAiText(
                                OfflineAiEvent.NEW_FAULT_CODE,
                                brandNew.joinToString(", ")
                            ),
                            LiveLogic.OFFLINE_AI_EVENT_COOLDOWN_MS
                        )
                        if (lastSpeedKmh >= highSpeedThreshold) {
                            maybeFireEasterEgg(LiveLogic.POOL_FAULT_SPEEDING)
                        } else {
                            maybeFireEasterEgg(LiveLogic.POOL_FAULT_NEW)
                        }
                    }
                    if (returned.isNotEmpty()) {
                        maybeFireEasterEgg(LiveLogic.POOL_FAULT_RETURN)
                    }
                }
            }
            pollSlow()
            while (ObdDataHolder.isMonitoring.get()) {
                delay(OnlineAiManager.SLOW_POLL_MS)
                if (!active) break
                runCatching { pollSlow() }
            }
        }
    }

    // ---------------------------------------------------------------- handlers

    internal suspend fun handleSpeed(speedString: String) {
        val value = speedString.split(" ")[0].toFloatOrNull()
        speedKmh.value = value
        if (value == null) return
        onlineAiManager?.onSpeed(value)
        val prevSpeed = lastSpeedKmh
        lastSpeedKmh = value
        if (!firstSpeedRead &&
            alertEnabled(PrefsKeys.HIGH_SPEED_ENABLED) &&
            prevSpeed < highSpeedThreshold &&
            value >= highSpeedThreshold
        ) {
            fireOfflineAi(
                OfflineAiEvent.HIGH_SPEED,
                offlineAiText(OfflineAiEvent.HIGH_SPEED),
                LiveLogic.OFFLINE_AI_EVENT_COOLDOWN_MS
            )
        }
        if (!firstSpeedRead) {
            val tier = LiveLogic.speedEasterTier(value)
            if (tier > 0 && tier > LiveLogic.speedEasterTier(prevSpeed)) {
                maybeFireEasterEgg(LiveLogic.poolForSpeedTier(tier))
            }
            maybeComboEaster(lastRpmValue)
        }
        firstSpeedRead = false
    }

    internal suspend fun handleRpm(rpmString: String) {
        if (rpmString == "ERROR") {
            if (!connLost) {
                connLost = true
                if (alertEnabled(PrefsKeys.CONN_LOST_ENABLED)) {
                    fireOfflineAi(
                        OfflineAiEvent.CONNECTION_LOST,
                        offlineAiText(OfflineAiEvent.CONNECTION_LOST),
                        LiveLogic.OFFLINE_AI_EVENT_COOLDOWN_MS
                    )
                }
            }
        } else if (connLost) {
            connLost = false
            if (alertEnabled(PrefsKeys.CONN_RESTORED_ENABLED)) {
                fireOfflineAi(
                    OfflineAiEvent.CONNECTION_RESTORED,
                    offlineAiText(OfflineAiEvent.CONNECTION_RESTORED),
                    LiveLogic.OFFLINE_AI_EVENT_COOLDOWN_MS
                )
            }
        }
        val rpmOrNull = rpmString.split(" ")[0].toIntOrNull()
        rpmReading.value = rpmOrNull
        val rpmValue = rpmOrNull ?: 0
        val prevRpm = lastRpmValue
        lastRpmValue = rpmValue
        engineSound.rpm = rpmValue
        if (rpmOrNull != null) {
            rpmColor.value = LiveLogic.rpmZoneColor(rpmValue, shiftRpm)
            onlineAiManager?.onRpm(rpmValue, shiftRpm)
        }
        if (!firstRpmRead) {
            if (alertEnabled(PrefsKeys.ENGINE_START_ENABLED) &&
                LiveLogic.shiftCrossed(prevRpm, rpmValue, 500)
            ) {
                fireOfflineAi(
                    OfflineAiEvent.ENGINE_STARTED,
                    offlineAiText(OfflineAiEvent.ENGINE_STARTED),
                    LiveLogic.OFFLINE_AI_EVENT_COOLDOWN_MS
                )
            }
            if (alertEnabled(PrefsKeys.ENGINE_STOP_ENABLED) &&
                prevRpm > 500 && LiveLogic.crossedDown(prevRpm, rpmValue, 400)
            ) {
                fireOfflineAi(
                    OfflineAiEvent.ENGINE_STOPPED,
                    offlineAiText(OfflineAiEvent.ENGINE_STOPPED),
                    LiveLogic.OFFLINE_AI_EVENT_COOLDOWN_MS
                )
            }
            if (alertEnabled(PrefsKeys.SHIFT_POINT_ENABLED) &&
                LiveLogic.shiftCrossed(prevRpm, rpmValue, LiveLogic.amberThreshold(shiftRpm))
            ) {
                fireOfflineAi(
                    OfflineAiEvent.SHIFT_POINT,
                    offlineAiText(OfflineAiEvent.SHIFT_POINT),
                    LiveLogic.OFFLINE_AI_SHIFT_COOLDOWN_MS
                )
            }
            if (alertEnabled(PrefsKeys.HIGH_RPM_ENABLED) &&
                LiveLogic.shiftCrossed(prevRpm, rpmValue, highRpmThreshold)
            ) {
                fireOfflineAi(
                    OfflineAiEvent.HIGH_RPM,
                    offlineAiText(OfflineAiEvent.HIGH_RPM),
                    LiveLogic.OFFLINE_AI_EVENT_COOLDOWN_MS
                )
                maybeFireEasterEgg(LiveLogic.POOL_RPM)
                maybeComboEaster(rpmValue)
            }
        }
        firstRpmRead = false

        val newTier = LiveLogic.nextRpmTier(rpmValue, rpmTier, shiftRpm)
        if (newTier > rpmTier) {
            when (newTier) {
                4 -> playSound(R.raw.danger)
                3 -> playSound(R.raw.thirty)
                2 -> playSound(R.raw.twenty)
                1 -> playSound(R.raw.ten)
            }
        } else if (newTier < rpmTier) {
            // RPM fell back below the warning band: cut the beep off.
            stopSound()
        }
        rpmTier = newTier
    }

    internal suspend fun handleCoolant(coolantTempString: String) {
        val value = coolantTempString.split(" ")[0].toIntOrNull()
        coolantC.value = value
        if (value != null) {
            onlineAiManager?.onCoolant(value)
        }
        if (prevCoolantC != null &&
            alertEnabled(PrefsKeys.OFFLINE_COOLANT_ENABLED) &&
            LiveLogic.shiftCrossed(prevCoolantC ?: 0, value ?: 0, coolantThreshold)
        ) {
            // The alarm below already carries the urgent wording; do not talk twice.
            if (!LiveLogic.nextCoolantAlarmed(value, coolantAlarmed)) {
                fireOfflineAi(
                    OfflineAiEvent.COOLANT_HIGH,
                    offlineAiText(OfflineAiEvent.COOLANT_HIGH),
                    LiveLogic.OFFLINE_AI_EVENT_COOLDOWN_MS
                )
            }
        }
        prevCoolantC = value
        val shouldAlarm = LiveLogic.nextCoolantAlarmed(value, coolantAlarmed)
        if (shouldAlarm && !coolantAlarmed) {
            coolantAlarm.value = true
            if (offlineMasterOn()) {
                playSound(R.raw.danger)
                val warning = if (imperial) {
                    activity.getString(
                        R.string.live_data_coolant_warning_f,
                        Units.displayTemp((value ?: 0).toDouble(), true).toInt()
                    )
                } else {
                    activity.getString(R.string.live_data_coolant_warning, value ?: 0)
                }
                fireOfflineAi(OfflineAiEvent.COOLANT_HIGH, warning, cooldownMs = 0L)
            }
        } else if (!shouldAlarm && coolantAlarmed) {
            coolantAlarm.value = false
            stopSound()
            fireOfflineAi(
                OfflineAiEvent.COOLANT_OK,
                activity.getString(R.string.offline_ai_coolant_ok),
                cooldownMs = 0L
            )
            maybeFireEasterEgg(LiveLogic.POOL_COOLANT_AFTER)
        }
        coolantAlarmed = shouldAlarm
    }

    /** Speaks only when the voltage tier worsens; re-arms on recovery. */
    internal fun checkVoltageWarning(v: Float?) {
        val tier = LiveLogic.voltageWarnTier(v)
        if (tier > lastVoltageTier && tier > 0) {
            val event =
                if (tier == 2) OfflineAiEvent.VOLTAGE_CRITICAL else OfflineAiEvent.VOLTAGE_LOW
            fireOfflineAi(event, offlineAiText(event), LiveLogic.OFFLINE_AI_EVENT_COOLDOWN_MS)
        }
        lastVoltageTier = tier
    }

    /** Speaks only when the fuel tier worsens; critical adds the Get Gas action. */
    internal fun checkFuelWarning(pct: Int?) {
        val tier = LiveLogic.fuelWarnTier(pct)
        if (tier > lastFuelTier && tier > 0) {
            if (tier == 2) {
                val text = activity.getString(R.string.warn_fuel_critical)
                fireOfflineAi(OfflineAiEvent.FUEL_CRITICAL, text, LiveLogic.OFFLINE_AI_EVENT_COOLDOWN_MS)
                if (offlineMasterOn() && offlineTextSwitchOn()) {
                    showOfflineAiBanner(text, activity.getString(R.string.get_gas)) { openGasStations() }
                }
            } else {
                fireOfflineAi(
                    OfflineAiEvent.FUEL_LOW,
                    offlineAiText(OfflineAiEvent.FUEL_LOW),
                    LiveLogic.OFFLINE_AI_EVENT_COOLDOWN_MS
                )
            }
        }
        lastFuelTier = tier
    }

    override fun onLocationChanged(location: Location) {
        val gpsKmh = location.speed * 3.6f
        speedKmh.value = gpsKmh
        val prevSpeed = lastSpeedKmh
        lastSpeedKmh = gpsKmh
        if (!firstSpeedRead &&
            alertEnabled(PrefsKeys.HIGH_SPEED_ENABLED) &&
            prevSpeed < highSpeedThreshold &&
            gpsKmh >= highSpeedThreshold
        ) {
            runCatching {
                fireOfflineAi(
                    OfflineAiEvent.HIGH_SPEED,
                    offlineAiText(OfflineAiEvent.HIGH_SPEED),
                    LiveLogic.OFFLINE_AI_EVENT_COOLDOWN_MS
                )
            }
        }
        if (!firstSpeedRead) {
            val tier = LiveLogic.speedEasterTier(gpsKmh)
            if (tier > 0 && tier > LiveLogic.speedEasterTier(prevSpeed)) {
                runCatching { maybeFireEasterEgg(LiveLogic.poolForSpeedTier(tier)) }
            }
            runCatching { maybeComboEaster(lastRpmValue) }
        }
        firstSpeedRead = false
    }

    override fun onProviderDisabled(provider: String) {}

    override fun onProviderEnabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    // ------------------------------------------------------------- demo mode

    /**
     * Demo seeds every gauge at 0 so the sheet's initial state clearly shows the
     * simulation has not started. Warning tiers are pre-armed to the seeded
     * levels, so opening the screen stays silent; dragging a slider into a
     * worse tier still warns once.
     */
    internal fun seedDemoFlows() {
        demoEngineOn = false
        pushDemoSpeed(0)
        pushDemoRpm(0)
        pushDemoCoolant(0)
        pushDemoVoltage(0)
        pushDemoFuel(0)
        lastVoltageTier = LiveLogic.voltageWarnTier(0f)
        lastFuelTier = LiveLogic.fuelWarnTier(0)
    }

    internal fun pushDemoSpeed(v: Int) {
        ObdDataHolder.speedFlow.value = if (imperial) {
            "${Units.kmhToMph(v.toDouble()).toInt()} mph"
        } else {
            "$v Km/h"
        }
    }

    internal fun pushDemoRpm(v: Int) {
        ObdDataHolder.rpmFlow.value = "$v RPM"
        // Demo has no load slider: derive a plausible load from the RPM.
        val load = (10 + v * 85 / rpmMax).coerceIn(5, 99)
        ObdDataHolder.engineLoadFlow.value = "$load %"
    }

    internal fun pushDemoCoolant(v: Int) {
        ObdDataHolder.coolantTempFlow.value = "$v \u00b0C"
    }

    internal fun pushDemoVoltage(tenths: Int) {
        // US locale: the flow parser expects a dot decimal separator.
        ObdDataHolder.voltageFlow.value = "%.1f V".format(java.util.Locale.US, tenths / 10f)
    }

    internal fun pushDemoFuel(pct: Int) {
        ObdDataHolder.fuelFlow.value = "$pct %"
    }

    /** Opens the demo control sheet; the dashboard keeps full size behind it. */
    internal fun showDemoSheet() {
        if (!obdHelper.demoMode || demoSheet.value != null) return
        val speed = speedKmh.value?.toInt() ?: 0
        val rpm = rpmReading.value ?: 0
        val coolant = coolantC.value ?: 89
        val voltageTenths = (voltageV.value ?: 13.8f).let { (it * 10).toInt() }
        val fuel = fuelPct.value ?: 64
        demoSheet.value = DemoSheetState(speed, rpm, coolant, voltageTenths, fuel, demoEngineOn)
    }

    // ---------------------------------------------------------- offline AI

    internal fun offlineMasterOn(): Boolean =
        prefs.getBoolean(PrefsKeys.OFFLINE_AI_ENABLED, true)

    internal fun offlineVoiceSwitchOn(): Boolean =
        prefs.getBoolean(PrefsKeys.VOICE_INSIGHT, true)

    internal fun offlineTextSwitchOn(): Boolean =
        prefs.getBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, true)

    internal fun alertEnabled(key: String): Boolean = prefs.getBoolean(key, true)

    internal fun toggleMute() {
        val next = !muted.value
        muted.value = next
        prefs.edit { putBoolean(PrefsKeys.MUTE_SOUND, next) }
        if (next) {
            stopSound()
            engineSound.stop()
            tts?.takeIf { it.isSpeaking }?.stop()
            stopSpeechPlayback()
        } else {
            maybeStartEngineSound()
        }
    }

    internal fun maybeStartEngineSound() {
        if (muted.value) return
        if (!prefs.getBoolean(PrefsKeys.ENGINE_SOUND_ENABLED, true)) return
        engineSound.start()
        demoEngineOn = true
    }

    internal fun openGasStations() {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(LiveLogic.gasStationGeoUri()))
        try {
            activity.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            showError(activity.getString(R.string.demo_no_maps_app))
        }
    }

    // ---------------------------------------------------------- lifecycle

    /** Engine sound resumes when the screen comes back to the foreground. */
    fun onEnterForeground() {
        if (!gpsSpeedSource) {
            maybeStartEngineSound()
            engineSound.rpm = lastRpmValue
        }
    }

    fun onEnterBackground() {
        engineSound.stop()
    }

    /**
     * Tears everything down. [changingConfigurations] mirrors
     * Activity.isChangingConfigurations: rotation keeps the socket and the
     * polling loop alive.
     */
    fun stop(changingConfigurations: Boolean) {
        active = false
        bannerJob?.cancel()
        onlineJob?.cancel()
        stopSound()
        engineSound.stop()
        tts?.stop()
        tts?.shutdown()
        tts = null
        onlineAiTts?.stop()
        onlineAiTts?.shutdown()
        onlineAiTts = null
        speechQueue.clear()
        onlineAiManager?.stop()
        onlineAiManager = null
        if (gpsSpeedSource) {
            try {
                locationManager?.removeUpdates(this)
            } catch (e: SecurityException) {
                Log.w("LiveSession", "removeUpdates without permission", e)
            }
            locationManager = null
            runCatching { activity.unregisterReceiver(batteryTempReceiver) }
        }
        if (!changingConfigurations) {
            teardownScope.launch {
                try {
                    ObdDataHolder.isMonitoring.set(false)
                    obdHelper.stopLiveDataMonitoring()
                    obdHelper.disconnectFromObdDevice()
                } finally {
                    teardownScope.cancel()
                }
            }
        }
        scope.cancel()
    }
}
