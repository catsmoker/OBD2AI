package com.catsmoker.obd2ai.ui.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.AiService
import com.catsmoker.obd2ai.ai.OnlineAiManager
import com.catsmoker.obd2ai.ai.OnlineAiSeverity
import com.catsmoker.obd2ai.ai.QueuedSpeech
import com.catsmoker.obd2ai.ai.SpeechQueue
import com.catsmoker.obd2ai.instruments.CoolantGaugeView
import com.catsmoker.obd2ai.instruments.FuelGaugeView
import com.catsmoker.obd2ai.instruments.RpmGaugeView
import com.catsmoker.obd2ai.instruments.VoltageGaugeView
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.Units
import com.catsmoker.obd2ai.speedometers.BaseSpeedometerView
import com.catsmoker.obd2ai.speedometers.SpeedometerHost
import com.catsmoker.obd2ai.speedometers.SpeedometerStyle
import com.catsmoker.obd2ai.ui.common.ErrorDialogFragment
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import com.github.anastr.speedviewlib.TubeSpeedometer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live Data screen: real-time OBD-II gauges, Offline AI cues, the Online AI
 * assistant card and demo mode.
 *
 * The screen owns the polling session: it starts
 * [ObdHelper.startLiveDataMonitoring], translates the shared
 * [ObdDataHolder] flows into gauges, runs the Offline AI event detectors
 * (thresholds, cooldowns, Easter eggs), feeds the Online AI, and stops
 * everything again when the view dies. Unknown reads stay unknown: the
 * telemetry tiles show "--" instead of an invented value.
 */
class LiveDataFragment : Fragment(), android.location.LocationListener {

    /** Logical Offline AI events — detectors and UI use these, never filenames. */
    enum class OfflineAiEvent {
        WELCOME,
        SHIFT_POINT,
        HIGH_RPM,
        HIGH_SPEED,
        COOLANT_HIGH,
        COOLANT_OK,
        VOLTAGE_LOW,
        VOLTAGE_CRITICAL,
        FUEL_LOW,
        FUEL_CRITICAL,
        NEW_FAULT_CODE,
        CONNECTION_LOST,
        CONNECTION_RESTORED,
        ENGINE_STARTED,
        ENGINE_STOPPED
    }

    companion object {
        private const val RPM_GREEN = 0xFF43A047.toInt()
        private const val RPM_AMBER = 0xFFC07F00.toInt()
        private const val RPM_RED = 0xFFE53935.toInt()
        const val UNKNOWN = "--"

        fun greenThreshold(shiftRpm: Int): Int =
            maxOf(shiftRpm - 1000, 1000)

        fun amberThreshold(shiftRpm: Int): Int =
            maxOf(shiftRpm, greenThreshold(shiftRpm) + 500)

        fun rpmZoneColor(rpmValue: Int, shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM): Int = when {
            rpmValue < greenThreshold(shiftRpm) -> RPM_GREEN
            rpmValue < amberThreshold(shiftRpm) -> RPM_AMBER
            else -> RPM_RED
        }

        fun rpmSections(
            rpmMax: Float,
            shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM
        ): List<Triple<Float, Float, Int>> {
            val greenEnd = (greenThreshold(shiftRpm) / 100f / rpmMax).coerceIn(0.05f, 0.9f)
            val amberEnd =
                (amberThreshold(shiftRpm) / 100f / rpmMax).coerceIn(greenEnd + 0.05f, 0.95f)
            return listOf(
                Triple(0f, greenEnd, RPM_GREEN),
                Triple(greenEnd, amberEnd, RPM_AMBER),
                Triple(amberEnd, 1f, RPM_RED)
            )
        }

        fun rpmTriggers(shiftRpm: Int): IntArray {
            val t1 = maxOf(shiftRpm - 2500, 800)
            val t2 = maxOf(shiftRpm - 1500, t1 + 500)
            val t3 = maxOf(shiftRpm - 500, t2 + 500)
            val t4 = maxOf(shiftRpm + 1000, t3 + 500)
            return intArrayOf(t1, t2, t3, t4)
        }

        fun nextRpmTier(
            rpmValue: Int,
            currentTier: Int,
            shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM
        ): Int {
            val (t1, t2, t3, t4) = rpmTriggers(shiftRpm).let {
                listOf(it[0], it[1], it[2], it[3])
            }
            return when {
                rpmValue > t4 && currentTier < 4 -> 4
                rpmValue > t3 && currentTier < 3 -> 3
                rpmValue > t2 && currentTier < 2 -> 2
                rpmValue > t1 && currentTier < 1 -> 1
                rpmValue < t1 - 100 && currentTier >= 1 -> 0
                rpmValue < t2 - 100 && currentTier >= 2 -> 1
                rpmValue < t3 - 100 && currentTier >= 3 -> 2
                rpmValue < t4 - 100 && currentTier >= 4 -> 3
                else -> currentTier
            }
        }

        fun nextCoolantAlarmed(coolantC: Int?, currentlyAlarmed: Boolean): Boolean = when {
            currentlyAlarmed -> (coolantC ?: 0) >= PrefsKeys.COOLANT_ALARM_RELEASE_C
            else -> (coolantC ?: 0) >= PrefsKeys.COOLANT_ALARM_C
        }

        fun voltageWarnTier(volts: Float?): Int = when {
            volts == null -> 0
            volts < 11.0f || volts > 15.5f -> 2
            volts < 12.0f || volts > 15.0f -> 1
            else -> 0
        }

        fun fuelWarnTier(pct: Int?): Int = when {
            pct == null -> 0
            pct <= 5 -> 2
            pct <= 15 -> 1
            else -> 0
        }

        fun gasStationGeoUri(): String = "geo:0,0?q=gas+station"

        const val OFFLINE_AI_SHIFT_COOLDOWN_MS = 15_000L
        const val OFFLINE_AI_EVENT_COOLDOWN_MS = 30_000L

        fun shiftCrossed(prevRpm: Int, newRpm: Int, threshold: Int): Boolean =
            prevRpm < threshold && newRpm >= threshold

        fun crossedDown(prevValue: Int, newValue: Int, threshold: Int): Boolean =
            prevValue >= threshold && newValue < threshold

        fun offlineAiReady(lastFiredMs: Long, nowMs: Long, cooldownMs: Long): Boolean =
            nowMs - lastFiredMs >= cooldownMs

        fun bannerDurationMs(text: String): Long {
            val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            return maxOf(2, words) * 1000L
        }

        const val POOL_SPEED_130 = "speed_130"
        const val POOL_SPEED_140 = "speed_140"
        const val POOL_SPEED_150 = "speed_150"
        const val POOL_RPM = "rpm"
        const val POOL_COMBO = "combo"
        const val POOL_COOLANT_AFTER = "coolant_after"
        const val POOL_FAULT_NEW = "fault_new"
        const val POOL_FAULT_RETURN = "fault_return"
        const val POOL_FAULT_SPEEDING = "fault_speeding"
        const val POOL_ULTRA = "ultra"
        const val EASTER_RECENT_MAX = 8

        fun speedEasterTier(speedKmh: Float): Int = when {
            speedKmh >= 150 -> 3
            speedKmh >= 140 -> 2
            speedKmh >= 130 -> 1
            else -> 0
        }

        fun poolForSpeedTier(tier: Int): String = when (tier) {
            3 -> POOL_SPEED_150
            2 -> POOL_SPEED_140
            else -> POOL_SPEED_130
        }

        fun comboActive(
            rpm: Int,
            rpmThreshold: Int,
            speedKmh: Float,
            speedThreshold: Int
        ): Boolean = rpm >= rpmThreshold && speedKmh >= speedThreshold.toFloat()

        fun rollEasterEgg(random01: Float, probability: Float): Boolean =
            random01 in 0f..<probability

        fun pickFreshLine(
            pool: List<String>,
            recent: ArrayDeque<String>,
            startIndex: Int
        ): String? {
            if (pool.isEmpty()) return null
            for (k in pool.indices) {
                val line = pool[(startIndex + k) % pool.size]
                if (line !in recent) return line
            }
            return null
        }

        data class EasterPoolConfig(
            val probability: Float,
            val cooldownMs: Long,
            val delayMs: Long
        )

        fun easterPoolConfig(pool: String): EasterPoolConfig = when (pool) {
            POOL_SPEED_130 -> EasterPoolConfig(0.35f, 5 * 60_000L, 0L)
            POOL_SPEED_140 -> EasterPoolConfig(0.35f, 10 * 60_000L, 0L)
            POOL_SPEED_150 -> EasterPoolConfig(0.15f, 20 * 60_000L, 0L)
            POOL_RPM -> EasterPoolConfig(0.30f, 10 * 60_000L, 4000L)
            POOL_COMBO -> EasterPoolConfig(0.25f, 15 * 60_000L, 4000L)
            POOL_COOLANT_AFTER -> EasterPoolConfig(0.30f, 20 * 60_000L, 5000L)
            POOL_FAULT_NEW -> EasterPoolConfig(0.30f, 15 * 60_000L, 4000L)
            POOL_FAULT_RETURN -> EasterPoolConfig(0.40f, 20 * 60_000L, 4000L)
            POOL_FAULT_SPEEDING -> EasterPoolConfig(0.30f, 15 * 60_000L, 4000L)
            POOL_ULTRA -> EasterPoolConfig(0.02f, 60 * 60_000L, 0L)
            else -> EasterPoolConfig(0f, Long.MAX_VALUE, 0L)
        }
    }

    /** Banner state; the fragment owns the countdown, the UI only renders it. */
    private data class LiveBanner(
        val id: Long,
        val text: String,
        val actionLabel: String? = null,
        val action: (() -> Unit)? = null
    )

    private data class OnlineCard(val text: String)

    private lateinit var obdHelper: ObdHelper
    private lateinit var aiService: AiService
    private lateinit var prefs: SharedPreferences

    // ---- Compose-observable dashboard state (null = unknown, never a fake 0) ----
    private val speedKmh = mutableStateOf<Float?>(null)
    private val rpmReading = mutableStateOf<Int?>(null)
    private val coolantC = mutableStateOf<Int?>(null)
    private val loadPct = mutableStateOf<Int?>(null)
    private val voltageV = mutableStateOf<Float?>(null)
    private val fuelPct = mutableStateOf<Int?>(null)
    private val deviceTempC = mutableStateOf<Float?>(null)
    private val muted = mutableStateOf(false)
    private val rpmColor = mutableStateOf(RPM_GREEN)
    private val coolantAlarm = mutableStateOf(false)
    private val banner = mutableStateOf<LiveBanner?>(null)
    private val bannerSeconds = mutableStateOf(0)
    private val onlineCard = mutableStateOf<OnlineCard?>(null)
    private val onlineSeconds = mutableStateOf(0)

    private var bannerJob: Job? = null
    private var onlineJob: Job? = null

    // ---- Tunables read once when the screen opens ----
    private var rpmTier = 0
    private var lastRpmValue = 0
    private var coolantAlarmed = false
    private var shiftRpm = PrefsKeys.DEFAULT_SHIFT_RPM
    private var rpmMax = PrefsKeys.DEFAULT_RPM_MAX
    private var highRpmThreshold = PrefsKeys.DEFAULT_HIGH_RPM
    private var highSpeedThreshold = PrefsKeys.DEFAULT_HIGH_SPEED
    private var coolantThreshold = PrefsKeys.DEFAULT_OFFLINE_COOLANT
    private var offlineVolume = PrefsKeys.DEFAULT_OFFLINE_VOLUME / 100f
    private var imperial = false
    private var lastSpeedKmh = 0f
    private var firstSpeedRead = true
    private var firstRpmRead = true
    private var prevCoolantC: Int? = null
    private var connLost = false
    private var lastVoltageTier = 0
    private var lastFuelTier = 0
    private var demoEngineOn = false
    private var speedometerStyle = SpeedometerStyle.CLASSIC
    private var gpsSpeedSource = false
    private var demoMode = false

    private val offlineKnownCodes = mutableSetOf<String>()
    private var offlineCodesSeeded = false
    private val offlineClearedCodes = mutableSetOf<String>()
    private var easterOn = true
    private var easterLines: Map<String, List<String>> = emptyMap()
    private val easterCooldowns = mutableMapOf<String, Long>()
    private val easterRecent = ArrayDeque<String>()
    private val easterRandom = kotlin.random.Random.Default

    private val offlineAiLastFired = mutableMapOf<OfflineAiEvent, Long>()
    private var offlineAiWelcomed = false

    private var demoSheet: DemoControlsSheet? = null
    private var locationManager: LocationManager? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tts: android.speech.tts.TextToSpeech? = null
    private var onlineAiTts: android.speech.tts.TextToSpeech? = null
    private var onlineAiManager: OnlineAiManager? = null
    private val speechQueue = SpeechQueue()
    private var speakingSeverity: OnlineAiSeverity? = null
    private val engineSound = com.catsmoker.obd2ai.audio.EngineSound()

    private val onlineAiListener = object : OnlineAiManager.Listener {
        override fun onOnlineAiResponse(text: String, severity: OnlineAiSeverity) {
            if (!isAdded) return
            if (prefs.getBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, true)) {
                showOnlineAiCard(text)
            }
            if (prefs.getBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, true)) {
                speakOnlineAi(text, severity)
            }
        }
    }

    private val batteryTempReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val temperature = intent?.getIntExtra(
                android.os.BatteryManager.EXTRA_TEMPERATURE,
                0
            )?.div(10f)
            if (temperature != null && temperature > 0f) deviceTempC.value = temperature
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Obd2AiTheme(darkTheme = isSystemInDarkTheme()) {
                    LiveScreen()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        obdHelper = (activity as MainActivity).obdHelper
        aiService = (activity as MainActivity).aiService
        prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)

        // Fresh view, fresh detectors: thresholds are snapshotted when the
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
        view.keepScreenOn = prefs.getBoolean(PrefsKeys.KEEP_SCREEN_ON, false)

        rpmTier = 0
        coolantAlarmed = false
        lastVoltageTier = 0
        lastFuelTier = 0
        demoEngineOn = false
        demoSheet?.dismiss()
        demoSheet = null
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
            POOL_SPEED_130 to resources.getStringArray(R.array.easter_speed_130).toList(),
            POOL_SPEED_140 to resources.getStringArray(R.array.easter_speed_140).toList(),
            POOL_SPEED_150 to resources.getStringArray(R.array.easter_speed_150).toList(),
            POOL_RPM to resources.getStringArray(R.array.easter_rpm_high).toList(),
            POOL_COMBO to resources.getStringArray(R.array.easter_rpm_speed_combo).toList(),
            POOL_COOLANT_AFTER to resources.getStringArray(R.array.easter_coolant_after).toList(),
            POOL_FAULT_NEW to resources.getStringArray(R.array.easter_fault_new).toList(),
            POOL_FAULT_RETURN to resources.getStringArray(R.array.easter_fault_return).toList(),
            POOL_FAULT_SPEEDING to resources.getStringArray(R.array.easter_fault_speeding).toList(),
            POOL_ULTRA to resources.getStringArray(R.array.easter_ultra_rare).toList()
        )
        easterCooldowns.clear()
        easterRecent.clear()

        if (gpsSpeedSource) {
            startGpsSpeed()
            return
        }

        if (demoMode) seedDemoFlows()
        maybeStartEngineSound()
        fireOfflineAiOnce(OfflineAiEvent.WELCOME, getString(R.string.offline_ai_online))

        onlineAiManager = OnlineAiManager(requireContext(), aiService, onlineAiListener)
        if (prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true) &&
            onlineAiManager?.isBrainConfigured() == false
        ) {
            showOnlineAiCard(getString(R.string.ai_no_key_hint))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            ObdDataHolder.isMonitoring.set(false)
            obdHelper.startLiveDataMonitoring()
        }
        collectDashboardFlows()
    }

    /** GPS speed source: no adapter reads, so no RPM/telemetry tiles at all. */
    private fun startGpsSpeed() {
        fireOfflineAiOnce(OfflineAiEvent.WELCOME, getString(R.string.offline_ai_online))
        locationManager =
            requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500,
                1f,
                this
            )
        } catch (e: SecurityException) {
            Log.e("LiveDataFragment", "Location permission missing for GPS speed", e)
            ErrorDialogFragment.newInstance(getString(R.string.live_data_gps_permission))
                .show(parentFragmentManager, "errorDialog")
            return
        }
        ContextCompat.registerReceiver(
            requireContext(),
            batteryTempReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun collectDashboardFlows() {
        val scope = viewLifecycleOwner.lifecycleScope
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
                            OFFLINE_AI_EVENT_COOLDOWN_MS
                        )
                        if (lastSpeedKmh >= highSpeedThreshold) {
                            maybeFireEasterEgg(POOL_FAULT_SPEEDING)
                        } else {
                            maybeFireEasterEgg(POOL_FAULT_NEW)
                        }
                    }
                    if (returned.isNotEmpty()) {
                        maybeFireEasterEgg(POOL_FAULT_RETURN)
                    }
                }
            }
            pollSlow()
            while (ObdDataHolder.isMonitoring.get()) {
                delay(OnlineAiManager.SLOW_POLL_MS)
                if (!isAdded) break
                runCatching { pollSlow() }
            }
        }
    }

    // ---------------------------------------------------------------- handlers

    private suspend fun handleSpeed(speedString: String) {
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
                OFFLINE_AI_EVENT_COOLDOWN_MS
            )
        }
        if (!firstSpeedRead) {
            val tier = speedEasterTier(value)
            if (tier > 0 && tier > speedEasterTier(prevSpeed)) {
                maybeFireEasterEgg(poolForSpeedTier(tier))
            }
            maybeComboEaster(lastRpmValue)
        }
        firstSpeedRead = false
    }

    private suspend fun handleRpm(rpmString: String) {
        if (rpmString == "ERROR") {
            if (!connLost) {
                connLost = true
                if (alertEnabled(PrefsKeys.CONN_LOST_ENABLED)) {
                    fireOfflineAi(
                        OfflineAiEvent.CONNECTION_LOST,
                        offlineAiText(OfflineAiEvent.CONNECTION_LOST),
                        OFFLINE_AI_EVENT_COOLDOWN_MS
                    )
                }
            }
        } else if (connLost) {
            connLost = false
            if (alertEnabled(PrefsKeys.CONN_RESTORED_ENABLED)) {
                fireOfflineAi(
                    OfflineAiEvent.CONNECTION_RESTORED,
                    offlineAiText(OfflineAiEvent.CONNECTION_RESTORED),
                    OFFLINE_AI_EVENT_COOLDOWN_MS
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
            rpmColor.value = rpmZoneColor(rpmValue, shiftRpm)
            onlineAiManager?.onRpm(rpmValue, shiftRpm)
        }
        if (!firstRpmRead) {
            if (alertEnabled(PrefsKeys.ENGINE_START_ENABLED) &&
                shiftCrossed(prevRpm, rpmValue, 500)
            ) {
                fireOfflineAi(
                    OfflineAiEvent.ENGINE_STARTED,
                    offlineAiText(OfflineAiEvent.ENGINE_STARTED),
                    OFFLINE_AI_EVENT_COOLDOWN_MS
                )
            }
            if (alertEnabled(PrefsKeys.ENGINE_STOP_ENABLED) &&
                prevRpm > 500 && crossedDown(prevRpm, rpmValue, 400)
            ) {
                fireOfflineAi(
                    OfflineAiEvent.ENGINE_STOPPED,
                    offlineAiText(OfflineAiEvent.ENGINE_STOPPED),
                    OFFLINE_AI_EVENT_COOLDOWN_MS
                )
            }
            if (alertEnabled(PrefsKeys.SHIFT_POINT_ENABLED) &&
                shiftCrossed(prevRpm, rpmValue, amberThreshold(shiftRpm))
            ) {
                fireOfflineAi(
                    OfflineAiEvent.SHIFT_POINT,
                    offlineAiText(OfflineAiEvent.SHIFT_POINT),
                    OFFLINE_AI_SHIFT_COOLDOWN_MS
                )
            }
            if (alertEnabled(PrefsKeys.HIGH_RPM_ENABLED) &&
                shiftCrossed(prevRpm, rpmValue, highRpmThreshold)
            ) {
                fireOfflineAi(
                    OfflineAiEvent.HIGH_RPM,
                    offlineAiText(OfflineAiEvent.HIGH_RPM),
                    OFFLINE_AI_EVENT_COOLDOWN_MS
                )
                maybeFireEasterEgg(POOL_RPM)
                maybeComboEaster(rpmValue)
            }
        }
        firstRpmRead = false

        val newTier = nextRpmTier(rpmValue, rpmTier, shiftRpm)
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

    private suspend fun handleCoolant(coolantTempString: String) {
        val value = coolantTempString.split(" ")[0].toIntOrNull()
        coolantC.value = value
        if (value != null) {
            onlineAiManager?.onCoolant(value)
        }
        if (prevCoolantC != null &&
            alertEnabled(PrefsKeys.OFFLINE_COOLANT_ENABLED) &&
            shiftCrossed(prevCoolantC ?: 0, value ?: 0, coolantThreshold)
        ) {
            // The alarm below already carries the urgent wording; do not talk twice.
            if (!nextCoolantAlarmed(value, coolantAlarmed)) {
                fireOfflineAi(
                    OfflineAiEvent.COOLANT_HIGH,
                    offlineAiText(OfflineAiEvent.COOLANT_HIGH),
                    OFFLINE_AI_EVENT_COOLDOWN_MS
                )
            }
        }
        prevCoolantC = value
        val shouldAlarm = nextCoolantAlarmed(value, coolantAlarmed)
        if (shouldAlarm && !coolantAlarmed) {
            coolantAlarm.value = true
            if (offlineMasterOn()) {
                playSound(R.raw.danger)
                val warning = if (imperial) {
                    getString(
                        R.string.live_data_coolant_warning_f,
                        Units.displayTemp((value ?: 0).toDouble(), true).toInt()
                    )
                } else {
                    getString(R.string.live_data_coolant_warning, value ?: 0)
                }
                fireOfflineAi(OfflineAiEvent.COOLANT_HIGH, warning, cooldownMs = 0L)
            }
        } else if (!shouldAlarm && coolantAlarmed) {
            coolantAlarm.value = false
            stopSound()
            fireOfflineAi(OfflineAiEvent.COOLANT_OK, getString(R.string.offline_ai_coolant_ok), cooldownMs = 0L)
            maybeFireEasterEgg(POOL_COOLANT_AFTER)
        }
        coolantAlarmed = shouldAlarm
    }

    /** Speaks only when the voltage tier worsens; re-arms on recovery. */
    private fun checkVoltageWarning(v: Float?) {
        val tier = voltageWarnTier(v)
        if (tier > lastVoltageTier && tier > 0) {
            val event =
                if (tier == 2) OfflineAiEvent.VOLTAGE_CRITICAL else OfflineAiEvent.VOLTAGE_LOW
            fireOfflineAi(event, offlineAiText(event), OFFLINE_AI_EVENT_COOLDOWN_MS)
        }
        lastVoltageTier = tier
    }

    /** Speaks only when the fuel tier worsens; critical adds the Get Gas action. */
    private fun checkFuelWarning(pct: Int?) {
        val tier = fuelWarnTier(pct)
        if (tier > lastFuelTier && tier > 0) {
            if (tier == 2) {
                val text = getString(R.string.warn_fuel_critical)
                fireOfflineAi(OfflineAiEvent.FUEL_CRITICAL, text, OFFLINE_AI_EVENT_COOLDOWN_MS)
                if (offlineMasterOn() && offlineTextSwitchOn()) {
                    showOfflineAiBanner(text, getString(R.string.get_gas)) { openGasStations() }
                }
            } else {
                fireOfflineAi(
                    OfflineAiEvent.FUEL_LOW,
                    offlineAiText(OfflineAiEvent.FUEL_LOW),
                    OFFLINE_AI_EVENT_COOLDOWN_MS
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
                    OFFLINE_AI_EVENT_COOLDOWN_MS
                )
            }
        }
        if (!firstSpeedRead) {
            val tier = speedEasterTier(gpsKmh)
            if (tier > 0 && tier > speedEasterTier(prevSpeed)) {
                runCatching { maybeFireEasterEgg(poolForSpeedTier(tier)) }
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
    private fun seedDemoFlows() {
        demoEngineOn = false
        pushDemoSpeed(0)
        pushDemoRpm(0)
        pushDemoCoolant(0)
        pushDemoVoltage(0)
        pushDemoFuel(0)
        lastVoltageTier = voltageWarnTier(0f)
        lastFuelTier = fuelWarnTier(0)
    }

    private fun pushDemoSpeed(v: Int) {
        ObdDataHolder.speedFlow.value = if (imperial) {
            "${Units.kmhToMph(v.toDouble()).toInt()} mph"
        } else {
            "$v Km/h"
        }
    }

    private fun pushDemoRpm(v: Int) {
        ObdDataHolder.rpmFlow.value = "$v RPM"
        // Demo has no load slider: derive a plausible load from the RPM.
        val load = (10 + v * 85 / rpmMax).coerceIn(5, 99)
        ObdDataHolder.engineLoadFlow.value = "$load %"
    }

    private fun pushDemoCoolant(v: Int) {
        ObdDataHolder.coolantTempFlow.value = "$v \u00b0C"
    }

    private fun pushDemoVoltage(tenths: Int) {
        // US locale: the flow parser expects a dot decimal separator.
        ObdDataHolder.voltageFlow.value = "%.1f V".format(java.util.Locale.US, tenths / 10f)
    }

    private fun pushDemoFuel(pct: Int) {
        ObdDataHolder.fuelFlow.value = "$pct %"
    }

    /** Opens the demo control sheet; the dashboard keeps full size behind it. */
    private fun showDemoSheet() {
        if (!obdHelper.demoMode || demoSheet?.isShowing() == true) return
        val speed = speedKmh.value?.toInt() ?: 0
        val rpm = rpmReading.value ?: 0
        val coolant = coolantC.value ?: 89
        val voltageTenths = (voltageV.value ?: 13.8f).let { (it * 10).toInt() }
        val fuel = fuelPct.value ?: 64
        demoSheet = DemoControlsSheet(
            requireContext(),
            imperial,
            rpmMax,
            DemoSheetState(speed, rpm, coolant, voltageTenths, fuel, demoEngineOn),
            object : DemoControlsSheet.Listener {
                override fun onDemoSpeed(kmh: Int) = pushDemoSpeed(kmh)
                override fun onDemoRpm(rpm: Int) = pushDemoRpm(rpm)
                override fun onDemoCoolant(celsius: Int) = pushDemoCoolant(celsius)
                override fun onDemoVoltage(tenths: Int) = pushDemoVoltage(tenths)
                override fun onDemoFuel(pct: Int) = pushDemoFuel(pct)
                override fun onDemoEngineChanged(on: Boolean) {
                    demoEngineOn = on
                }
            }
        ).also { it.show() }
    }

    // ---------------------------------------------------------- offline AI

    private fun offlineMasterOn(): Boolean =
        prefs.getBoolean(PrefsKeys.OFFLINE_AI_ENABLED, true)

    private fun offlineVoiceSwitchOn(): Boolean =
        prefs.getBoolean(PrefsKeys.VOICE_INSIGHT, true)

    private fun offlineTextSwitchOn(): Boolean =
        prefs.getBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, true)

    private fun alertEnabled(key: String): Boolean = prefs.getBoolean(key, true)

    /** Per-event text with the optional detail suffix (codes, temp). */
    private fun offlineAiText(event: OfflineAiEvent, detail: String? = null): String {
        val text = when (event) {
            OfflineAiEvent.WELCOME -> getString(R.string.offline_ai_online)
            OfflineAiEvent.SHIFT_POINT -> getString(R.string.offline_ai_shift_up)
            OfflineAiEvent.HIGH_RPM -> getString(R.string.offline_ai_high_rpm)
            OfflineAiEvent.HIGH_SPEED -> getString(R.string.offline_ai_high_speed)
            OfflineAiEvent.COOLANT_HIGH -> {
                val value = coolantC.value ?: 0
                if (imperial) {
                    getString(
                        R.string.live_data_coolant_warning_f,
                        Units.displayTemp(value.toDouble(), true).toInt()
                    )
                } else {
                    getString(R.string.live_data_coolant_warning, value)
                }
            }
            OfflineAiEvent.COOLANT_OK -> getString(R.string.offline_ai_coolant_ok)
            OfflineAiEvent.VOLTAGE_LOW -> getString(R.string.warn_voltage_low)
            OfflineAiEvent.VOLTAGE_CRITICAL -> getString(R.string.warn_voltage_critical)
            OfflineAiEvent.FUEL_LOW -> getString(R.string.warn_fuel_low)
            OfflineAiEvent.FUEL_CRITICAL -> getString(R.string.warn_fuel_critical)
            OfflineAiEvent.NEW_FAULT_CODE -> getString(R.string.offline_ai_new_fault)
            OfflineAiEvent.CONNECTION_LOST -> getString(R.string.offline_ai_conn_lost)
            OfflineAiEvent.CONNECTION_RESTORED -> getString(R.string.offline_ai_conn_restored)
            OfflineAiEvent.ENGINE_STARTED -> getString(R.string.offline_ai_engine_start)
            OfflineAiEvent.ENGINE_STOPPED -> getString(R.string.offline_ai_engine_stop)
        }
        return if (detail.isNullOrBlank()) text else "$text $detail"
    }

    private fun fireOfflineAi(event: OfflineAiEvent, text: String, cooldownMs: Long = 0L) {
        if (!offlineMasterOn()) return
        if (cooldownMs > 0L) {
            val now = System.currentTimeMillis()
            val last = offlineAiLastFired[event]
            if (last != null && !offlineAiReady(last, now, cooldownMs)) return
            offlineAiLastFired[event] = now
        }
        emitOfflineAiCue(text)
    }

    private fun fireOfflineAiOnce(event: OfflineAiEvent, text: String) {
        if (offlineAiWelcomed && event == OfflineAiEvent.WELCOME) return
        if (event == OfflineAiEvent.WELCOME) offlineAiWelcomed = true
        fireOfflineAi(event, text)
    }

    /** One cue reaches the user through both enabled outputs (voice + banner). */
    private fun emitOfflineAiCue(text: String) {
        if (offlineVoiceSwitchOn()) speakOfflineAi(text)
        if (offlineTextSwitchOn()) showOfflineAiBanner(text)
    }

    /** Speaks [text] with TTS at Offline AI volume, initializing on first use. */
    private fun speakOfflineAi(text: String, utterance: String = "offlineAi") {
        val params = Bundle().apply {
            putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, offlineVolume)
        }
        val engine = tts
        if (engine == null) {
            // First use: speak from the init callback once the engine is ready.
            tts = android.speech.tts.TextToSpeech(requireContext()) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    runCatching { tts?.language = java.util.Locale.getDefault() }
                    tts?.speak(
                        text,
                        android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                        params,
                        utterance
                    )
                } else {
                    Log.e("LiveDataFragment", "TTS init failed: $status")
                }
            }
        } else {
            engine.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, utterance)
        }
    }

    private fun showOfflineAiBanner(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        bannerJob?.cancel()
        val ms = bannerDurationMs(text)
        banner.value = LiveBanner(
            id = System.currentTimeMillis(),
            text = text,
            actionLabel = actionLabel,
            action = action
        )
        bannerSeconds.value = (ms / 1000).toInt()
        bannerJob = viewLifecycleOwner.lifecycleScope.launch {
            var remaining = bannerSeconds.value
            while (remaining > 0) {
                delay(1000)
                remaining--
                bannerSeconds.value = remaining
            }
            banner.value = null
        }
    }

    private fun skipOfflineAi() {
        bannerJob?.cancel()
        banner.value = null
        tts?.takeIf { it.isSpeaking }?.stop()
    }

    // -------------------------------------------------------- easter eggs

    private fun maybeFireEasterEgg(pool: String) {
        if (!easterOn || !offlineMasterOn()) return
        val lines = easterLines[pool].orEmpty()
        if (lines.isEmpty()) return
        val config = easterPoolConfig(pool)
        val now = System.currentTimeMillis()
        val last = easterCooldowns[pool] ?: 0L
        if (!offlineAiReady(last, now, config.cooldownMs)) return
        val ultraRare = rollEasterEgg(easterRandom.nextFloat(), easterPoolConfig(POOL_ULTRA).probability)
        val probability = if (ultraRare) 1f else config.probability
        if (!rollEasterEgg(easterRandom.nextFloat(), probability)) return
        val source = if (ultraRare) easterLines[POOL_ULTRA].orEmpty().ifEmpty { lines } else lines
        val start = (easterRandom.nextFloat() * source.size).toInt().coerceIn(0, source.size - 1)
        val line = pickFreshLine(source, easterRecent, start) ?: return
        easterCooldowns[pool] = now
        easterRecent.addLast(line)
        while (easterRecent.size > EASTER_RECENT_MAX) easterRecent.removeFirst()
        val emit = { emitOfflineAiCue(line) }
        if (config.delayMs > 0L) {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(config.delayMs)
                if (isAdded) emit()
            }
        } else {
            emit()
        }
    }

    private fun maybeComboEaster(rpmValue: Int) {
        if (!comboActive(rpmValue, highRpmThreshold, lastSpeedKmh, highSpeedThreshold)) return
        maybeFireEasterEgg(POOL_COMBO)
    }

    // ----------------------------------------------------------- online AI

    private fun aiVolume(): Float =
        (prefs.getInt(PrefsKeys.AI_VOLUME, PrefsKeys.DEFAULT_AI_VOLUME).coerceIn(0, 100)) / 100f

    private fun showOnlineAiCard(text: String) {
        onlineJob?.cancel()
        onlineCard.value = OnlineCard(text)
        val seconds = (bannerDurationMs(text) / 1000).toInt().coerceAtLeast(4)
        onlineSeconds.value = seconds
        onlineJob = viewLifecycleOwner.lifecycleScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                onlineSeconds.value = remaining
            }
            onlineCard.value = null
        }
    }

    /** Dismisses the Online AI card early and drops its queued voice. */
    private fun skipOnlineAi() {
        onlineJob?.cancel()
        onlineCard.value = null
        speechQueue.clear()
        speakingSeverity = null
        stopSpeechPlayback()
    }

    /** Speaks an Online AI reply with the device voice; the dashboard always survives. */
    private fun speakOnlineAi(text: String, severity: OnlineAiSeverity) {
        if (muted.value) return // card already shown; voice stays silent
        val utterance = QueuedSpeech(text, severity)
        val current = speakingSeverity
        if (current != null) {
            if (severity.ordinal > current.ordinal) {
                // Higher severity preempts: drop the current remainder.
                stopSpeechPlayback()
                speakingSeverity = null
                viewLifecycleOwner.lifecycleScope.launch { playSpeech(utterance) }
            } else {
                speechQueue.offer(utterance)
            }
        } else {
            viewLifecycleOwner.lifecycleScope.launch { playSpeech(utterance) }
        }
    }

    private fun playSpeech(utterance: QueuedSpeech) {
        speakingSeverity = utterance.severity
        if (!isAdded) {
            speakingSeverity = null
            return
        }
        playDeviceVoice(utterance.text)
    }

    /** Device voice on its own engine so Offline AI cues can never flush it. */
    private fun playDeviceVoice(text: String) {
        val params = Bundle().apply {
            putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, aiVolume())
        }
        val engine = onlineAiTts
        if (engine == null) {
            onlineAiTts = android.speech.tts.TextToSpeech(requireContext()) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    onlineAiTts?.setOnUtteranceProgressListener(speechProgressListener)
                    runCatching { onlineAiTts?.language = java.util.Locale.getDefault() }
                    onlineAiTts?.speak(
                        text,
                        android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                        params,
                        "onlineai"
                    )
                } else {
                    Log.e("LiveDataFragment", "Online AI TTS init failed: $status")
                    Handler(Looper.getMainLooper()).post { onSpeechDone() }
                }
            }
        } else {
            engine.speak(
                text,
                android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                params,
                "onlineai"
            )
        }
    }

    private val speechProgressListener = object : android.speech.tts.UtteranceProgressListener() {
        override fun onDone(utteranceId: String?) {
            Handler(Looper.getMainLooper()).post { onSpeechDone() }
        }

        override fun onError(utteranceId: String?) {
            Handler(Looper.getMainLooper()).post { onSpeechDone() }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Handler(Looper.getMainLooper()).post { onSpeechDone() }
        }

        override fun onStart(utteranceId: String?) {}
    }

    private fun onSpeechDone() {
        speakingSeverity = null
        val next = speechQueue.poll()
        if (next != null && isAdded) {
            viewLifecycleOwner.lifecycleScope.launch { playSpeech(next) }
        }
    }

    /** Stops whatever the Online AI is saying (preemption, mute, teardown). */
    private fun stopSpeechPlayback() {
        runCatching { onlineAiTts?.stop() }
    }

    // ------------------------------------------------------------- sounds

    private fun playSound(resId: Int) {
        if (muted.value) return
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), resId)?.apply {
            setVolume(offlineVolume, offlineVolume)
            setOnCompletionListener { it.release() }
            start()
        }
    }

    private fun stopSound() {
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            player.release()
        }
        mediaPlayer = null
    }

    private fun toggleMute() {
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

    private fun maybeStartEngineSound() {
        if (muted.value) return
        if (!prefs.getBoolean(PrefsKeys.ENGINE_SOUND_ENABLED, true)) return
        engineSound.start()
        demoEngineOn = true
    }

    private fun openGasStations() {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(gasStationGeoUri()))
        try {
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            ErrorDialogFragment.newInstance(getString(R.string.demo_no_maps_app))
                .show(parentFragmentManager, "errorDialog")
        }
    }
    // ---------------------------------------------------------------- UI

    @Composable
    private fun LiveScreen() {
        val speed = speedKmh.value
        val rpm = rpmReading.value
        val coolant = coolantC.value
        val volts = voltageV.value
        val fuel = fuelPct.value
        val load = loadPct.value
        val bannerState = banner.value
        val onlineState = onlineCard.value
        val isMuted = muted.value
        val zone = rpmColor.value

        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val viewport = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill()
                    Spacer(modifier = Modifier.weight(1f))
                    if (demoMode) {
                        IconButton(onClick = { showDemoSheet() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_tune),
                                contentDescription = stringResource(R.string.demo_controls_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = { toggleMute() }) {
                        Icon(
                            painter = painterResource(
                                if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
                            ),
                            contentDescription = stringResource(
                                if (isMuted) R.string.live_data_unmute else R.string.live_data_mute
                            ),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (gpsSpeedSource || speedometerStyle == SpeedometerStyle.AUTOMOTIVE) {
                    SpeedGauge(
                        speedKmh = speed,
                        rpm = rpm,
                        imperial = imperial,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeedGauge(
                            speedKmh = speed,
                            rpm = rpm,
                            imperial = imperial,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                        TachoGauge(
                            rpm = rpm,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ValueTile(
                        label = stringResource(R.string.live_data_speed_label),
                        value = speedText(speed),
                        modifier = Modifier.weight(1f)
                    )
                    if (!gpsSpeedSource) {
                        ValueTile(
                            label = stringResource(R.string.live_data_load_label),
                            value = if (load != null) "$load %" else UNKNOWN,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (!gpsSpeedSource) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AndroidView(
                            factory = { ctx -> CoolantGaugeView(ctx) },
                            update = { gauge ->
                                gauge.setTemp(
                                    coolant,
                                    imperial,
                                    coolantThreshold,
                                    PrefsKeys.COOLANT_ALARM_C
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(150.dp)
                        )
                        AndroidView(
                            factory = { ctx -> VoltageGaugeView(ctx) },
                            update = { gauge -> gauge.setVoltage(volts) },
                            modifier = Modifier
                                .weight(1f)
                                .height(150.dp)
                        )
                        AndroidView(
                            factory = { ctx -> FuelGaugeView(ctx) },
                            update = { gauge -> gauge.setFuel(fuel) },
                            modifier = Modifier
                                .weight(1f)
                                .height(150.dp)
                        )
                    }
                } else {
                    val temp = deviceTempC.value
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (temp != null) getString(R.string.device_temp, temp) else UNKNOWN,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
            }

            // Cue cards are pinned above the bottom edge so a warning can never
            // scroll out of sight while driving.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onlineState != null) {
                    OnlineAiCard(text = onlineState.text, seconds = onlineSeconds.value)
                }
                if (bannerState != null) {
                    OfflineAiBannerCard(
                        text = bannerState.text,
                        seconds = bannerSeconds.value,
                        actionLabel = bannerState.actionLabel,
                        onAction = bannerState.action
                    )
                }
            }

            // Edge flash: bright on the edge change, then a faint reminder tint.
            // A permanent frame while cruising would be noise.
            val alarm = coolantAlarm.value
            val edgeColor = if (alarm) RPM_RED else zone
            val peak = when {
                alarm -> 0.95f
                zone == RPM_GREEN -> 0f
                zone == RPM_AMBER -> 0.6f
                else -> 0.9f
            }
            val flash = remember { Animatable(0f) }
            LaunchedEffect(peak) {
                if (peak <= 0f) {
                    flash.snapTo(0f)
                } else {
                    flash.snapTo(peak)
                    flash.animateTo(peak * 0.25f, tween(durationMillis = 450))
                }
            }
            if (flash.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color(edgeColor).copy(alpha = flash.value))
                )
            }
        }
    }

    @Composable
    private fun StatusPill() {
        val label = when {
            demoMode -> getString(R.string.error_overview_demo_badge)
            gpsSpeedSource -> getString(R.string.live_data_status_gps)
            else -> getString(R.string.live_data_status_obd)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }

    @Composable
    private fun ValueTile(label: String, value: String, modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    @Composable
    private fun OfflineAiBannerCard(
        text: String,
        seconds: Int,
        actionLabel: String?,
        onAction: (() -> Unit)?
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Text(
                text = getString(R.string.offline_ai_name),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = {
                            onAction()
                            skipOfflineAi()
                        }
                    ) { Text(actionLabel) }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = seconds.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                TextButton(onClick = { skipOfflineAi() }) {
                    Text(getString(R.string.popup_skip))
                }
            }
        }
    }

    @Composable
    private fun OnlineAiCard(text: String, seconds: Int) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.online_ai_card_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = seconds.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                TextButton(onClick = { skipOnlineAi() }) {
                    Text(getString(R.string.popup_skip))
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }

    @Composable
    private fun SpeedGauge(
        speedKmh: Float?,
        rpm: Int?,
        imperial: Boolean,
        modifier: Modifier
    ) {
        AndroidView(
            modifier = modifier,
            factory = { ctx -> SpeedometerHost.createView(ctx, speedometerStyle) },
            update = { view ->
                val displaySpeed = if (speedKmh != null) {
                    Units.displaySpeed(speedKmh.toDouble(), imperial).toFloat()
                } else {
                    0f
                }
                when (view) {
                    is BaseSpeedometerView -> {
                        view.maxSpeed = Units.speedGaugeMax(imperial)
                        view.unitLabel = Units.speedUnitLabel(imperial)
                        view.rpmMax = rpmMax
                        view.setSpeed(displaySpeed, animate = speedKmh != null)
                        view.setRpm(rpm ?: 0, shiftRpm)
                    }
                    is android.widget.LinearLayout -> {
                        for (i in 0 until view.childCount) {
                            val tube = view.getChildAt(i) as? TubeSpeedometer ?: continue
                            if (i == 0) {
                                tube.maxSpeed = Units.speedGaugeMax(imperial)
                                tube.unit = Units.speedUnitLabel(imperial)
                                tube.speedTo(displaySpeed, 400)
                            } else {
                                tube.maxSpeed = rpmMax / 100f
                                tube.unit = "x100 RPM"
                                tube.speedTo((rpm ?: 0) / 100f, 400)
                            }
                        }
                    }
                }
            }
        )
    }

    @Composable
    private fun TachoGauge(rpm: Int?, modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx -> SpeedometerHost.createRpmView(ctx, speedometerStyle) },
            update = { gauge ->
                gauge.rpmMax = rpmMax
                gauge.shiftRpm = shiftRpm
                gauge.setRpm(rpm, shiftRpm)
            }
        )
    }

    private fun speedText(speed: Float?): String {
        if (speed == null) return UNKNOWN
        val value = Units.displaySpeed(speed.toDouble(), imperial)
        return String.format(
            java.util.Locale.US,
            "%.0f %s",
            value,
            Units.speedUnitLabel(imperial)
        )
    }

    private fun coolantText(coolant: Int?): String {
        if (coolant == null) return UNKNOWN
        return if (imperial) {
            String.format(
                java.util.Locale.US,
                "%.0f \u00b0F",
                Units.displayTemp(coolant.toDouble(), true)
            )
        } else {
            "$coolant \u00b0C"
        }
    }

    override fun onResume() {
        super.onResume()
        if (!gpsSpeedSource && this::obdHelper.isInitialized) {
            maybeStartEngineSound()
            engineSound.rpm = lastRpmValue
        }
    }

    override fun onPause() {
        super.onPause()
        engineSound.stop()
    }

    override fun onDestroyView() {
        bannerJob?.cancel()
        onlineJob?.cancel()
        demoSheet?.dismiss()
        demoSheet = null
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
                Log.w("LiveDataFragment", "removeUpdates without permission", e)
            }
            locationManager = null
            runCatching { requireContext().unregisterReceiver(batteryTempReceiver) }
        }
        val activity = activity
        if (activity != null && !activity.isChangingConfigurations) {
            viewLifecycleOwner.lifecycleScope.launch {
                ObdDataHolder.isMonitoring.set(false)
                obdHelper.stopLiveDataMonitoring()
                obdHelper.disconnectFromObdDevice()
            }
        }
        super.onDestroyView()
    }

}
