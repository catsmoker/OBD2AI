package com.catsmoker.obd2ai.ui.dashboard

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.OnlineAiManager
import com.catsmoker.obd2ai.ai.OnlineAiSeverity
import com.catsmoker.obd2ai.ai.QueuedSpeech
import com.catsmoker.obd2ai.ai.SpeechQueue
import com.catsmoker.obd2ai.audio.EngineSound
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
import com.github.anastr.speedviewlib.TubeSpeedometer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LiveDataFragment : Fragment(), LocationListener {

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

    private lateinit var obdHelper: ObdHelper
    private var mediaPlayer: MediaPlayer? = null
    private var tts: android.speech.tts.TextToSpeech? = null

    companion object {
        private const val RPM_GREEN = 0xFF43A047.toInt()
        private const val RPM_AMBER = 0xFFF9A825.toInt()
        private const val RPM_RED = 0xFFE53935.toInt()

        /** Green/amber boundary derived from the shift point (idle stays green). */
        fun greenThreshold(shiftRpm: Int): Int =
            maxOf(shiftRpm - 1000, 1000)

        /** Amber/red boundary: the shift point itself. */
        fun amberThreshold(shiftRpm: Int): Int =
            maxOf(shiftRpm, greenThreshold(shiftRpm) + 500)

        /** Gauge zones on the x100-RPM dial, arranged around the shift point. */
        fun rpmZoneColor(rpmValue: Int, shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM): Int = when {
            rpmValue < greenThreshold(shiftRpm) -> RPM_GREEN
            rpmValue < amberThreshold(shiftRpm) -> RPM_AMBER
            else -> RPM_RED
        }

        /**
         * RPM dial zones as (startOffset, endOffset, color) fractions of [rpmMax].
         * Kept in one pure function so unit tests can pin the tiling: the gauge
         * library throws if a section conflicts with its neighbour.
         */
        fun rpmSections(
            rpmMax: Float,
            shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM
        ): List<Triple<Float, Float, Int>> {
            val greenEnd = (greenThreshold(shiftRpm) / 100f / rpmMax).coerceIn(0.05f, 0.9f)
            val amberEnd = (amberThreshold(shiftRpm) / 100f / rpmMax).coerceIn(greenEnd + 0.05f, 0.95f)
            return listOf(
                Triple(0f, greenEnd, RPM_GREEN),
                Triple(greenEnd, amberEnd, RPM_AMBER),
                Triple(amberEnd, 1f, RPM_RED)
            )
        }

        /** RPM warning triggers derived from the shift point (tier 4 = over-rev). */
        fun rpmTriggers(shiftRpm: Int): IntArray {
            val t1 = maxOf(shiftRpm - 2500, 800)
            val t2 = maxOf(shiftRpm - 1500, t1 + 500)
            val t3 = maxOf(shiftRpm - 500, t2 + 500)
            val t4 = maxOf(shiftRpm + 1000, t3 + 500)
            return intArrayOf(t1, t2, t3, t4)
        }

        /**
         * Pure RPM warning-tier state machine (hysteresis bands: trigger above,
         * release 100 RPM below). Extracted so the logic is unit-testable.
         */
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

        /**
         * Pure coolant-alarm latch with hysteresis: trips at
         * [PrefsKeys.COOLANT_ALARM_C], releases below
         * [PrefsKeys.COOLANT_ALARM_RELEASE_C] so the siren does not flap.
         */
        fun nextCoolantAlarmed(coolantC: Int?, currentlyAlarmed: Boolean): Boolean = when {
            currentlyAlarmed -> (coolantC ?: 0) >= PrefsKeys.COOLANT_ALARM_RELEASE_C
            else -> (coolantC ?: 0) >= PrefsKeys.COOLANT_ALARM_C
        }

        /**
         * Vehicle-warning tiers (0 = normal/unknown, 1 = warning, 2 = critical).
         * Callers speak only when the tier rises and re-arm when it recovers,
         * so a stuck value warns once instead of spamming. Critical bands are
         * display-warning thresholds around the Online AI's own bad/low lines.
         */
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

        /** Maps search for nearby gas stations (external app, no map SDK). */
        fun gasStationGeoUri(): String = "geo:0,0?q=gas+station"

        // -- Offline AI proactive cues ------------------------------------------------
        const val OFFLINE_AI_SHIFT_COOLDOWN_MS = 15_000L
        const val OFFLINE_AI_EVENT_COOLDOWN_MS = 30_000L

        /** True only on the rising edge across [threshold] (no repeat while held). */
        fun shiftCrossed(prevRpm: Int, newRpm: Int, threshold: Int): Boolean =
            prevRpm < threshold && newRpm >= threshold

        /** True only on the falling edge across [threshold] (no repeat while held). */
        fun crossedDown(prevValue: Int, newValue: Int, threshold: Int): Boolean =
            prevValue >= threshold && newValue < threshold

        /** Cooldown gate so Offline AI nags at most once per [cooldownMs]. */
        fun offlineAiReady(lastFiredMs: Long, nowMs: Long, cooldownMs: Long): Boolean =
            nowMs - lastFiredMs >= cooldownMs

        /**
         * Popup linger: one second per word so the line stays readable while
         * driving (2 s floor so tiny cues don't just flash).
         */
        fun bannerDurationMs(text: String): Long {
            val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            return maxOf(2, words) * 1000L
        }

        // -- Easter eggs (Offline AI only; never Online AI) --------------------
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

        /** Speed Easter-egg tier: 0 = none, 1 = 130+, 2 = 140+, 3 = 150+. */
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

        /** True when RPM and speed are both unusually high at once. */
        fun comboActive(rpm: Int, rpmThreshold: Int, speedKmh: Float, speedThreshold: Int): Boolean =
            rpm >= rpmThreshold && speedKmh >= speedThreshold.toFloat()

        /** Probability gate: [random01] must fall below [probability]. */
        fun rollEasterEgg(random01: Float, probability: Float): Boolean =
            random01 in 0f..<probability

        /**
         * Picks the next joke, skipping recently played lines (rotating from
         * [startIndex] so repeats spread out). Null when the pool is empty
         * or every line played recently — then stay silent, don't force one.
         */
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

        /** Odds per pool: probability, cooldown, and post-warning delay. */
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

    private var rpmTier = 0
    private var lastRpmValue = 0
    private var coolantAlarmed = false
    private var shiftRpm = PrefsKeys.DEFAULT_SHIFT_RPM
    private var rpmMax = PrefsKeys.DEFAULT_RPM_MAX
    // Offline AI snapshot (taken when the screen opens; Settings edits rebuild it).
    private var highRpmThreshold = PrefsKeys.DEFAULT_HIGH_RPM
    private var highSpeedThreshold = PrefsKeys.DEFAULT_HIGH_SPEED
    private var coolantThreshold = PrefsKeys.DEFAULT_OFFLINE_COOLANT
    private var offlineVolume = PrefsKeys.DEFAULT_OFFLINE_VOLUME / 100f
    // Display units snapshot (SI stays underneath; only pixels convert).
    private var imperial = false
    private var lastSpeedKmh = 0f
    private var firstSpeedRead = true
    private var firstRpmRead = true
    private var prevCoolantC: Int? = null
    private var connLost = false
    // Vehicle-warning tiers: speak on rise, re-arm on recovery (no spam).
    private var lastVoltageTier = 0
    private var lastFuelTier = 0
    // Demo dashboard state (simulated source for the shared dashboard UI).
    private var demoEngineOn = false
    private var demoSheet: DemoControlsSheet? = null
    private val offlineKnownCodes = mutableSetOf<String>()
    private var offlineCodesSeeded = false
    private val offlineClearedCodes = mutableSetOf<String>()
    private var easterOn = true
    private var easterLines: Map<String, List<String>> = emptyMap()
    private val easterCooldowns = mutableMapOf<String, Long>()
    private val easterRecent = ArrayDeque<String>()
    private val easterRandom = kotlin.random.Random.Default

    private lateinit var locationManager: LocationManager
    private lateinit var speedView: TubeSpeedometer
    // OBD2 AI 2.0: style picked in Settings + live custom gauge (null = Automotive legacy pair).
    private var speedometerStyle: SpeedometerStyle = SpeedometerStyle.CLASSIC
    private var customGauge: BaseSpeedometerView? = null
    private var rpmGauge: RpmGaugeView? = null
    private lateinit var batteryTempView: TextView
    private lateinit var coolantGauge: CoolantGaugeView
    private lateinit var voltageGauge: VoltageGaugeView
    private lateinit var fuelGauge: FuelGaugeView
    private lateinit var instrumentsRow: View
    private lateinit var edgeFlashView: View
    private lateinit var muteButton: ImageButton
    private lateinit var offlineAiBanner: View
    private lateinit var offlineAiMessage: TextView
    private lateinit var offlineAiTimer: TextView
    private lateinit var offlineAiAction: Button
    private var bannerAction: (() -> Unit)? = null
    private var offlineAiCountdown: CountDownTimer? = null
    private val offlineAiLastFired = mutableMapOf<OfflineAiEvent, Long>()
    private var offlineAiWelcomed = false
    // -- Online AI (independent online assistant) ---------------------------------
    private var onlineAiManager: OnlineAiManager? = null
    private lateinit var onlineAiCard: View
    private lateinit var aiMessage: TextView
    private lateinit var aiTimer: TextView
    private var aiCountdown: CountDownTimer? = null
    private val speechQueue = SpeechQueue()
    private var speakingSeverity: OnlineAiSeverity? = null
    private var onlineAiTts: android.speech.tts.TextToSpeech? = null
    // Optional RPM-driven entertainment hum; reads rpm only, dies with mute.
    private val engineSound = EngineSound()
    private val onlineAiListener = object : OnlineAiManager.Listener {
        override fun onOnlineAiResponse(text: String, severity: OnlineAiSeverity) {
            if (!isAdded) return
            val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
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
            val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f)
            batteryTempView.text = getString(R.string.device_temp, temperature)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_live_data, container, false)
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        obdHelper = (activity as MainActivity).obdHelper

        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        shiftRpm = prefs.getInt(PrefsKeys.SHIFT_RPM, PrefsKeys.DEFAULT_SHIFT_RPM)
            .coerceIn(PrefsKeys.SHIFT_RPM_MIN, PrefsKeys.SHIFT_RPM_MAX)
        rpmMax = prefs.getInt(PrefsKeys.RPM_MAX, PrefsKeys.DEFAULT_RPM_MAX)
            .coerceIn(PrefsKeys.RPM_MAX_MIN, PrefsKeys.RPM_MAX_MAX)
        highRpmThreshold = prefs.getInt(PrefsKeys.HIGH_RPM_THRESHOLD, PrefsKeys.DEFAULT_HIGH_RPM)
            .coerceIn(PrefsKeys.HIGH_RPM_MIN, PrefsKeys.HIGH_RPM_MAX)
        highSpeedThreshold = prefs.getInt(PrefsKeys.HIGH_SPEED_THRESHOLD, PrefsKeys.DEFAULT_HIGH_SPEED)
            .coerceIn(PrefsKeys.HIGH_SPEED_MIN, PrefsKeys.HIGH_SPEED_MAX)
        coolantThreshold = prefs.getInt(PrefsKeys.OFFLINE_COOLANT_THRESHOLD, PrefsKeys.DEFAULT_OFFLINE_COOLANT)
            .coerceIn(PrefsKeys.OFFLINE_COOLANT_MIN, PrefsKeys.OFFLINE_COOLANT_MAX)
        offlineVolume = prefs.getInt(PrefsKeys.OFFLINE_VOLUME, PrefsKeys.DEFAULT_OFFLINE_VOLUME)
            .coerceIn(0, 100) / 100f
        imperial = Units.isImperial(prefs)
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
        offlineClearedCodes.clear()
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

        speedView = view.findViewById<TubeSpeedometer>(R.id.speedView2)
        // Imperial drivers get an mph dial; logic underneath stays km/h.
        speedView.maxSpeed = Units.speedGaugeMax(imperial)
        speedView.unit = Units.speedUnitLabel(imperial)
        val rpmView = view.findViewById<TubeSpeedometer>(R.id.rpmView)
        batteryTempView = view.findViewById<TextView>(R.id.batteryTempView)
        coolantGauge = view.findViewById<CoolantGaugeView>(R.id.coolantGauge)
        voltageGauge = view.findViewById<VoltageGaugeView>(R.id.voltageGauge)
        fuelGauge = view.findViewById<FuelGaugeView>(R.id.fuelGauge)
        instrumentsRow = view.findViewById(R.id.instrumentsRow)
        edgeFlashView = view.findViewById(R.id.edgeFlashView)
        muteButton = view.findViewById(R.id.button_mute)
        offlineAiBanner = view.findViewById(R.id.offlineAiBanner)
        offlineAiMessage = view.findViewById(R.id.offlineAiMessage)
        offlineAiTimer = view.findViewById(R.id.offlineAiTimer)
        offlineAiCountdown?.cancel()
        offlineAiCountdown = null
        offlineAiLastFired.clear()
        offlineAiWelcomed = false
        setupMuteButton()
        view.findViewById<Button>(R.id.offlineAiSkip).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            skipOfflineAi()
        }
        offlineAiAction = view.findViewById(R.id.offlineAiAction)
        offlineAiAction.visibility = View.GONE
        offlineAiAction.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            bannerAction?.invoke()
        }

        // RPM dial zones on the shared 0..rpmMax range, arranged around the
        // shift point. The readout text follows the needle's zone automatically.
        // NOTE: Gauge ships with 3 default sections and addSections() APPENDS,
        // so clearSections() must come first — otherwise our first section
        // (index 3) conflicts with the last default one and the gauge throws.
        rpmView.maxSpeed = rpmMax / 100f
        val rpmMaxDial = rpmView.maxSpeed
        rpmView.clearSections()
        rpmSections(rpmMaxDial, shiftRpm).forEach { (start, end, color) ->
            rpmView.addSections(
                com.github.anastr.speedviewlib.components.Section(start, end, color)
            )
        }
        rpmView.speedTextColor = rpmZoneColor(0, shiftRpm)
        rpmView.onSectionChangeListener = { _, newSection ->
            rpmView.speedTextColor = newSection?.color ?: rpmView.speedTextColor
        }

        // OBD2 AI 2.0: selectable speedometer. Automotive keeps the legacy tube
        // pair above; every other style renders in speedometerHost while the
        // tubes hide (same data feed, no dashboard duplication).
        speedometerStyle = SpeedometerStyle.fromId(
            prefs.getString(PrefsKeys.SPEEDOMETER_STYLE, PrefsKeys.SPEEDOMETER_DEFAULT)
        )
        val speedometerHost = view.findViewById<android.widget.FrameLayout>(R.id.speedometerHost)
        val rpmHost = view.findViewById<android.widget.FrameLayout>(R.id.rpmHost)
        if (speedometerStyle == SpeedometerStyle.AUTOMOTIVE) {
            speedometerHost.visibility = View.GONE
            rpmHost.visibility = View.GONE
            customGauge = null
            rpmGauge = null
        } else {
            val gaugeView = SpeedometerHost.createView(requireContext(), speedometerStyle)
            speedometerHost.removeAllViews()
            speedometerHost.addView(
                gaugeView,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
            speedometerHost.visibility = View.VISIBLE
            speedView.visibility = View.GONE
            rpmView.visibility = View.GONE
            customGauge = gaugeView as? BaseSpeedometerView
            customGauge?.maxSpeed = Units.speedGaugeMax(imperial)
            customGauge?.unitLabel = Units.speedUnitLabel(imperial)
            customGauge?.rpmMax = rpmMax
            customGauge?.setRpm(0, shiftRpm)
            customGauge?.setSpeed(0f, animate = false)
            // Twin tachometer, face matched to the speedometer style.
            val tachoView = SpeedometerHost.createRpmView(requireContext(), speedometerStyle)
            rpmHost.removeAllViews()
            rpmHost.addView(
                tachoView,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
            rpmHost.visibility = View.VISIBLE
            rpmGauge = tachoView
            rpmGauge?.rpmMax = rpmMax
            rpmGauge?.setRpm(null, shiftRpm)
        }

        val speedSource = prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD)

        // Session pill: demo data, GPS speed, or OBD-II live — set once per
        // screen open. Reads below it stay honest ("--" when unknown).
        view.findViewById<TextView>(R.id.liveStatusPill).text = when {
            obdHelper.demoMode -> getString(R.string.error_overview_demo_badge)
            speedSource == PrefsKeys.SPEED_SOURCE_DEVICE -> getString(R.string.live_data_status_gps)
            else -> getString(R.string.live_data_status_obd)
        }

        if (speedSource == PrefsKeys.SPEED_SOURCE_DEVICE) {
            rpmView.visibility = View.GONE
            view.findViewById<View>(R.id.rpmHost).visibility = View.GONE
            batteryTempView.visibility = View.VISIBLE
            instrumentsRow.visibility = View.GONE

            val constraintLayout = view as ConstraintLayout
            val constraintSet = ConstraintSet()
            constraintSet.clone(constraintLayout)
            constraintSet.connect(R.id.speedView2, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            // Landscape/tablet layouts park the speedo in the left pane next to
            // the readings; portrait centers it full-width instead.
            if (view.findViewById<View>(R.id.guideline_v) != null) {
                constraintSet.connect(R.id.speedView2, ConstraintSet.END, R.id.guideline_v, ConstraintSet.START)
            } else {
                constraintSet.connect(R.id.speedView2, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            }
            constraintSet.applyTo(constraintLayout)

            locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1f, this)
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
            fireOfflineAiOnce(OfflineAiEvent.WELCOME, getString(R.string.offline_ai_online))
        } else {
            batteryTempView.visibility = View.GONE
            instrumentsRow.visibility = View.VISIBLE
            // Demo shares the dashboard UI; only the telemetry source differs.
            // The small Demo button opens the control sheet — nothing inline.
            view.findViewById<ImageButton>(R.id.button_demo_controls).apply {
                visibility = if (obdHelper.demoMode) View.VISIBLE else View.GONE
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    showDemoSheet()
                }
            }
            if (obdHelper.demoMode) {
                seedDemoFlows()
            }
            fireOfflineAiOnce(OfflineAiEvent.WELCOME, getString(R.string.offline_ai_online))
            onlineAiCard = view.findViewById(R.id.onlineAiCard)
            aiMessage = view.findViewById(R.id.aiMessage)
            aiTimer = view.findViewById(R.id.aiTimer)
            view.findViewById<Button>(R.id.onlineAiSkip).setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                skipOnlineAi()
            }
            onlineAiManager = OnlineAiManager(
                requireContext(),
                (activity as MainActivity).aiService,
                onlineAiListener
            )
            // One hint per screen when the brain cannot generate — otherwise
            // the silence is a mystery. Not repeated, auto-dismisses.
            if (onlineAiEnabled() && onlineAiManager?.isBrainConfigured() == false) {
                showOnlineAiCard(getString(R.string.ai_no_key_hint))
            }
            lifecycleScope.launch {
                obdHelper.startLiveDataMonitoring()
            }
            maybeStartEngineSound()
            lifecycleScope.launch {
                // Slow telemetry: Online AI poll + Offline AI fault tracking.
                // Polls once immediately (drive-start heads-up) then ~every 30 s.
                suspend fun pollSlow() {
                    val slow = runCatching { obdHelper.readSlowTelemetry() }.getOrNull() ?: return
                    runCatching { onlineAiManager?.onSlowPoll(slow) }
                    // Publish only successful reads; "--" placeholders mean unknown.
                    if (slow.voltageV != null) {
                        ObdDataHolder.voltageFlow.value = "${slow.voltageV} V"
                    }
                    if (slow.fuelPct != null) {
                        ObdDataHolder.fuelFlow.value = "${slow.fuelPct} %"
                    }
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

            lifecycleScope.launch {
                ObdDataHolder.speedFlow.collect { speedString ->
                    val speedValue = speedString.split(" ")[0].toFloatOrNull() ?: 0f
                    // Needle converts; every threshold below stays km/h.
                    val displaySpeed = Units.displaySpeed(speedValue.toDouble(), imperial).toFloat()
                    speedView.speedTo(displaySpeed, 400)
                    customGauge?.setSpeed(displaySpeed)
                    onlineAiManager?.onSpeed(speedValue)
                    val prevSpeed = lastSpeedKmh
                    lastSpeedKmh = speedValue
                    if (!firstSpeedRead &&
                        alertEnabled(PrefsKeys.HIGH_SPEED_ENABLED) &&
                        prevSpeed < highSpeedThreshold &&
                        speedValue >= highSpeedThreshold
                    ) {
                        fireOfflineAi(
                            OfflineAiEvent.HIGH_SPEED,
                            offlineAiText(OfflineAiEvent.HIGH_SPEED),
                            OFFLINE_AI_EVENT_COOLDOWN_MS
                        )
                    }
                    if (!firstSpeedRead) {
                        val tier = speedEasterTier(speedValue)
                        if (tier > 0 && tier > speedEasterTier(prevSpeed)) {
                            maybeFireEasterEgg(poolForSpeedTier(tier))
                        }
                        maybeComboEaster(lastRpmValue)
                    }
                    firstSpeedRead = false
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.rpmFlow.collect { rpmString ->
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
                    val rpmValue = rpmOrNull ?: 0
                    val prevRpm = lastRpmValue
                    lastRpmValue = rpmValue
                    engineSound.rpm = rpmValue
                    rpmView.speedTo(rpmValue.toFloat() / 100, 400)
                    customGauge?.setRpm(rpmValue, shiftRpm)
                    rpmGauge?.setRpm(if (rpmString == "ERROR") null else rpmOrNull, shiftRpm)
                    flashEdges(rpmZoneColor(rpmValue, shiftRpm))
                    onlineAiManager?.onRpm(rpmValue, shiftRpm)
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
                            4 -> {
                                playSound(R.raw.danger)
                                rpmView.speedTextColor = Color.RED
                                Handler(Looper.getMainLooper()).postDelayed({
                                    // Back to the zone color, not a stale default.
                                    rpmView.speedTextColor = rpmZoneColor(lastRpmValue, shiftRpm)
                                }, 500)
                            }
                            3 -> playSound(R.raw.thirty)
                            2 -> playSound(R.raw.twenty)
                            1 -> playSound(R.raw.ten)
                        }
                    } else if (newTier < rpmTier) {
                        // RPM fell back below the warning band: cut the sound off
                        // instead of letting it play to the end.
                        stopSound()
                    }
                    rpmTier = newTier
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.coolantTempFlow.collect { coolantTempString ->
                    val coolantC = coolantTempString.split(" ")[0].toIntOrNull()
                    coolantGauge.setTemp(coolantC, imperial, coolantThreshold, PrefsKeys.COOLANT_ALARM_C)
                    onlineAiManager?.onCoolant(coolantC)
                    if (prevCoolantC != null &&
                        alertEnabled(PrefsKeys.OFFLINE_COOLANT_ENABLED) &&
                        shiftCrossed(prevCoolantC ?: 0, coolantC ?: 0, coolantThreshold)
                    ) {
                        fireOfflineAi(
                            OfflineAiEvent.COOLANT_HIGH,
                            offlineAiText(OfflineAiEvent.COOLANT_HIGH),
                            OFFLINE_AI_EVENT_COOLDOWN_MS
                        )
                    }
                    prevCoolantC = coolantC
                    val shouldAlarm = nextCoolantAlarmed(coolantC, coolantAlarmed)
                    if (shouldAlarm && !coolantAlarmed) {
                        flashEdges(RPM_RED, strong = true)
                        if (offlineMasterOn()) {
                            playSound(R.raw.danger)
                            val warning = if (imperial) {
                                getString(
                                    R.string.live_data_coolant_warning_f,
                                    Units.displayTemp((coolantC ?: 0).toDouble(), true).toInt()
                                )
                            } else {
                                getString(R.string.live_data_coolant_warning, coolantC ?: 0)
                            }
                            fireOfflineAi(OfflineAiEvent.COOLANT_HIGH, warning)
                        }
                    } else if (!shouldAlarm && coolantAlarmed) {
                        // Temperature is back to normal: cut the danger siren
                        // so the recovery message is heard, not drowned out.
                        stopSound()
                        fireOfflineAi(
                            OfflineAiEvent.COOLANT_OK,
                            getString(R.string.offline_ai_coolant_ok)
                        )
                        maybeFireEasterEgg(POOL_COOLANT_AFTER)
                    }
                    coolantAlarmed = shouldAlarm
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.engineLoadFlow.collect { loadString ->
                    onlineAiManager?.onEngineLoad(loadString.split(" ")[0].toIntOrNull())
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.voltageFlow.collect { voltageString ->
                    val volts = voltageString.split(" ")[0].toFloatOrNull()
                    voltageGauge.setVoltage(volts)
                    checkVoltageWarning(volts)
                }
            }

            lifecycleScope.launch {
                ObdDataHolder.fuelFlow.collect { fuelString ->
                    val pct = fuelString.split(" ")[0].toIntOrNull()
                    fuelGauge.setFuel(pct)
                    checkFuelWarning(pct)
                }
            }
        }
    }

    override fun onLocationChanged(location: android.location.Location) {
        val speedKmh = location.speed * 3.6f
        speedView.speedTo(speedKmh)
        customGauge?.setSpeed(speedKmh)
        val prevSpeed = lastSpeedKmh
        lastSpeedKmh = speedKmh
        if (!firstSpeedRead &&
            alertEnabled(PrefsKeys.HIGH_SPEED_ENABLED) &&
            prevSpeed < highSpeedThreshold &&
            speedKmh >= highSpeedThreshold
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
            val tier = speedEasterTier(speedKmh)
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

    /**
     * Demo dashboard seed: everything starts at zero with the engine OFF, so
     * the initial state clearly shows the simulation hasn't started. Warning
     * tiers are pre-armed to the seeded levels so entry itself stays silent;
     * dragging a slider into a worse tier still warns exactly once.
     * Everything flows through the same ObdDataHolder flows Live consumes.
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
        ObdDataHolder.speedFlow.value = "$v Km/h"
    }

    private fun pushDemoRpm(v: Int) {
        ObdDataHolder.rpmFlow.value = "$v RPM"
        // Demo has no load slider: derive a plausible load from the RPM.
        val load = (10 + v * 85 / rpmMax).coerceIn(5, 99)
        ObdDataHolder.engineLoadFlow.value = "$load %"
    }

    private fun pushDemoCoolant(v: Int) {
        ObdDataHolder.coolantTempFlow.value = "$v °C"
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
        val speed = ObdDataHolder.speedFlow.value.split(" ")[0].toIntOrNull() ?: 0
        val rpm = ObdDataHolder.rpmFlow.value.split(" ")[0].toIntOrNull() ?: 0
        val coolant = ObdDataHolder.coolantTempFlow.value.split(" ")[0].toIntOrNull() ?: 89
        val voltageTenths = ObdDataHolder.voltageFlow.value.split(" ")[0].toFloatOrNull()
            ?.let { (it * 10).toInt() } ?: 138
        val fuel = ObdDataHolder.fuelFlow.value.split(" ")[0].toIntOrNull() ?: 64
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

    private fun isMuted(): Boolean =
        requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PrefsKeys.MUTE_SOUND, false)

    private fun onlineAiEnabled(): Boolean =
        requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)

    private fun setupMuteButton() {
        refreshMuteLabel()
        muteButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val prefs = requireActivity()
                .getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            val muted = !prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)
            prefs.edit { putBoolean(PrefsKeys.MUTE_SOUND, muted) }
            refreshMuteLabel()
            if (muted) {
                stopSound()
                engineSound.stop()
                tts?.stop()
                stopSpeechPlayback()
            } else {
                maybeStartEngineSound()
            }
        }
    }

    /** Starts the engine hum when enabled, unmuted and on screen; else stops it. */
    private fun maybeStartEngineSound() {
        if (!isAdded) return
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PrefsKeys.ENGINE_SOUND_ENABLED, false) && !isMuted()) {
            engineSound.start()
        } else {
            engineSound.stop()
        }
    }

    private fun refreshMuteLabel() {
        if (!::muteButton.isInitialized) return
        val muted = isMuted()
        muteButton.setImageResource(
            if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
        )
        val label = getString(
            if (muted) R.string.live_data_unmute else R.string.live_data_mute
        )
        muteButton.contentDescription = label
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            muteButton.tooltipText = label
        }
    }

    /** Pulses a colored border around the screen; green rests faint, amber/red flash. */
    private fun flashEdges(color: Int, strong: Boolean = false) {
        if (!::edgeFlashView.isInitialized) return
        val stroke = (14 * resources.displayMetrics.density).toInt()
        edgeFlashView.background =
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(stroke, color)
            }
        edgeFlashView.visibility = View.VISIBLE
        edgeFlashView.animate().cancel()
        if (color == RPM_GREEN && !strong) {
            edgeFlashView.alpha = 0.25f
        } else {
            edgeFlashView.alpha = 1f
            edgeFlashView.animate().alpha(0.15f).setDuration(450).start()
        }
    }

    /** Speaks [text] with TTS at Offline AI volume, initializing on first use. */
    private fun speakText(text: String, utterance: String = "offlineAi") {
        val params = Bundle().apply {
            putFloat(
                android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME,
                offlineVolume
            )
        }
        val engine = tts
        if (engine == null) {
            // First use: speak from the init callback once the engine is ready.
            tts = android.speech.tts.TextToSpeech(requireContext()) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    runCatching { tts?.language = java.util.Locale.getDefault() }
                    tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, utterance)
                } else {
                    Log.e("LiveDataFragment", "TTS init failed: $status")
                }
            }
        } else {
            engine.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, utterance)
        }
    }

    private fun offlinePrefs() =
        requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    /** Master switch: the whole Offline AI system is dead when this is off. */
    private fun offlineMasterOn(): Boolean =
        offlinePrefs().getBoolean(PrefsKeys.OFFLINE_AI_ENABLED, true)

    private fun offlineVoiceSwitchOn(): Boolean =
        offlinePrefs().getBoolean(PrefsKeys.VOICE_INSIGHT, true)

    private fun offlineTextSwitchOn(): Boolean =
        offlinePrefs().getBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, true)

    private fun alertEnabled(key: String, def: Boolean = true): Boolean =
        offlinePrefs().getBoolean(key, def)

    /** Event → display text, shown on the banner and spoken with device TTS. */
    private fun offlineAiText(event: OfflineAiEvent, detail: String = ""): String = when (event) {
        OfflineAiEvent.WELCOME -> getString(R.string.offline_ai_online)
        OfflineAiEvent.SHIFT_POINT -> getString(R.string.offline_ai_shift_up)
        OfflineAiEvent.HIGH_RPM -> getString(R.string.offline_ai_high_rpm)
        OfflineAiEvent.HIGH_SPEED -> getString(R.string.offline_ai_high_speed)
        OfflineAiEvent.COOLANT_HIGH -> getString(R.string.warn_coolant_high)
        OfflineAiEvent.COOLANT_OK -> getString(R.string.offline_ai_coolant_ok)
        OfflineAiEvent.VOLTAGE_LOW -> getString(R.string.warn_voltage_low)
        OfflineAiEvent.VOLTAGE_CRITICAL -> getString(R.string.warn_voltage_critical)
        OfflineAiEvent.FUEL_LOW -> getString(R.string.warn_fuel_low)
        OfflineAiEvent.FUEL_CRITICAL -> getString(R.string.warn_fuel_critical)
        OfflineAiEvent.NEW_FAULT_CODE ->
            if (detail.isBlank()) getString(R.string.offline_ai_new_fault)
            else "${getString(R.string.offline_ai_new_fault)} $detail"
        OfflineAiEvent.CONNECTION_LOST -> getString(R.string.offline_ai_conn_lost)
        OfflineAiEvent.CONNECTION_RESTORED -> getString(R.string.offline_ai_conn_restored)
        OfflineAiEvent.ENGINE_STARTED -> getString(R.string.offline_ai_engine_start)
        OfflineAiEvent.ENGINE_STOPPED -> getString(R.string.offline_ai_engine_stop)
    }

    /**
     * Fires an Offline AI cue. Callers check the per-event enable switch and
     * threshold edge; here the master switch and the voice/text outputs are
     * applied. Events without a [cooldownMs] gate (0) fire on every edge.
     */
    private fun fireOfflineAi(event: OfflineAiEvent, text: String, cooldownMs: Long = 0L) {
        if (!offlineMasterOn()) return
        if (cooldownMs > 0) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (!offlineAiReady(offlineAiLastFired[event] ?: 0L, now, cooldownMs)) return
            offlineAiLastFired[event] = now
        }
        emitOfflineAiCue(text)
    }

    /** One-shot cue per screen (e.g. the "ready" greeting). */
    private fun fireOfflineAiOnce(event: OfflineAiEvent, text: String) {
        if (offlineAiWelcomed) return
        offlineAiWelcomed = true
        if (!offlineMasterOn()) return
        offlineAiLastFired[event] = android.os.SystemClock.elapsedRealtime()
        emitOfflineAiCue(text)
    }

    /** Voice + banner fan-out shared by normal cues and Easter eggs. */
    private fun emitOfflineAiCue(text: String) {
        if (offlineVoiceSwitchOn() && !isMuted()) {
            speakText(text)
        }
        if (offlineTextSwitchOn()) showOfflineAiBanner(text)
    }

    /**
     * Easter-egg gate (Offline AI only, never Online AI). The normal warning
     * for this edge has already fired (or the edge has none); this only adds
     * an occasional joke: master + egg switches, per-pool probability and
     * cooldown, no-repeat tracking, and a short delay so the serious message
     * speaks first. A tiny global ultra-rare roll may substitute the pick.
     */
    private fun maybeFireEasterEgg(pool: String) {
        if (!easterOn) return
        val lines = easterLines[pool].orEmpty()
        val config = easterPoolConfig(pool)
        if (lines.isEmpty() || config.probability <= 0f) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!offlineAiReady(easterCooldowns[pool] ?: 0L, now, config.cooldownMs)) return
        if (!rollEasterEgg(easterRandom.nextFloat(), config.probability)) return
        val ultraLines = easterLines[POOL_ULTRA].orEmpty()
        val ultraConfig = easterPoolConfig(POOL_ULTRA)
        val useUltra = ultraLines.isNotEmpty() &&
            offlineAiReady(easterCooldowns[POOL_ULTRA] ?: 0L, now, ultraConfig.cooldownMs) &&
            rollEasterEgg(easterRandom.nextFloat(), ultraConfig.probability)
        val finalLines = if (useUltra) ultraLines else lines
        val line = pickFreshLine(finalLines, easterRecent, easterRandom.nextInt(finalLines.size))
            ?: return
        easterCooldowns[pool] = now
        if (useUltra) easterCooldowns[POOL_ULTRA] = now
        easterRecent.addLast(line)
        while (easterRecent.size > EASTER_RECENT_MAX) easterRecent.removeFirst()
        val delayMs = if (useUltra) ultraConfig.delayMs else config.delayMs
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isAdded) return@postDelayed
            if (!easterEnabledPref() || !offlineMasterOn()) return@postDelayed
            emitOfflineAiCue(line)
        }, delayMs)
    }

    private fun easterEnabledPref(): Boolean =
        requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PrefsKeys.EASTER_EGGS_ENABLED, true)

    /** Combo check shared by the RPM and speed edges (needs both thresholds). */
    private fun maybeComboEaster(rpmValue: Int) {
        if (comboActive(rpmValue, highRpmThreshold, lastSpeedKmh, highSpeedThreshold)) {
            maybeFireEasterEgg(POOL_COMBO)
        }
    }

    /** Small bottom popup with a word-count countdown; replaces itself when re-fired. */
    private fun showOfflineAiBanner(
        text: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        if (!::offlineAiBanner.isInitialized) return
        offlineAiMessage.text = text
        offlineAiBanner.visibility = View.VISIBLE
        bannerAction = onAction
        if (actionLabel != null && onAction != null) {
            offlineAiAction.text = actionLabel
            offlineAiAction.visibility = View.VISIBLE
        } else {
            offlineAiAction.visibility = View.GONE
        }
        offlineAiCountdown?.cancel()
        val durationMs = bannerDurationMs(text)
        offlineAiTimer.text = getString(R.string.countdown_value, (durationMs / 1000).toInt())
        offlineAiCountdown = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                offlineAiTimer.text = getString(R.string.countdown_value, ((millisUntilFinished + 999) / 1000).toInt())
            }

            override fun onFinish() {
                if (::offlineAiBanner.isInitialized) offlineAiBanner.visibility = View.GONE
                if (::offlineAiAction.isInitialized) offlineAiAction.visibility = View.GONE
                bannerAction = null
            }
        }.also { it.start() }
    }

    /** Dismisses the Offline AI banner early and cuts its voice. */
    private fun skipOfflineAi() {
        offlineAiCountdown?.cancel()
        offlineAiCountdown = null
        if (::offlineAiBanner.isInitialized) offlineAiBanner.visibility = View.GONE
        if (::offlineAiAction.isInitialized) offlineAiAction.visibility = View.GONE
        bannerAction = null
        stopSound()
        tts?.stop()
    }

    /** Speaks only when the voltage tier worsens; re-arms on recovery. */
    private fun checkVoltageWarning(v: Float?) {
        val tier = voltageWarnTier(v)
        if (tier > lastVoltageTier && tier > 0) {
            val event = if (tier == 2) OfflineAiEvent.VOLTAGE_CRITICAL else OfflineAiEvent.VOLTAGE_LOW
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

    /** Opens the user's navigation app searching nearby gas stations. */
    private fun openGasStations() {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(gasStationGeoUri())
        )
        runCatching { startActivity(intent) }.onFailure {
            Log.w("LiveDataFragment", "No maps app for gas search", it)
            ErrorDialogFragment.newInstance(getString(R.string.demo_no_maps_app))
                .show(parentFragmentManager, "errorDialog")
        }
    }

    /** Online AI response card (bottom): linger matches the message length. */
    private fun showOnlineAiCard(text: String) {
        if (!::onlineAiCard.isInitialized) return
        aiMessage.text = text
        onlineAiCard.visibility = View.VISIBLE
        aiCountdown?.cancel()
        val durationMs = bannerDurationMs(text)
        aiTimer.text = getString(R.string.countdown_value, (durationMs / 1000).toInt())
        aiCountdown = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                aiTimer.text = getString(R.string.countdown_value, ((millisUntilFinished + 999) / 1000).toInt())
            }

            override fun onFinish() {
                if (::onlineAiCard.isInitialized) onlineAiCard.visibility = View.GONE
            }
        }.also { it.start() }
    }

    /** Dismisses the Online AI card early and drops its queued voice. */
    private fun skipOnlineAi() {
        aiCountdown?.cancel()
        aiCountdown = null
        if (::onlineAiCard.isInitialized) onlineAiCard.visibility = View.GONE
        speechQueue.clear()
        speakingSeverity = null
        stopSpeechPlayback()
    }

    private fun aiVolume(): Float {
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PrefsKeys.AI_VOLUME, PrefsKeys.DEFAULT_AI_VOLUME).coerceIn(0, 100) / 100f
    }

    /**
     * Speaks an Online AI reply with the device voice. Never throws; the dashboard always survives.
     */
    private fun speakOnlineAi(text: String, severity: OnlineAiSeverity) {
        if (isMuted()) return // card already shown; voice stays silent
        val utterance = QueuedSpeech(text, severity)
        val current = speakingSeverity
        if (current != null) {
            if (severity.ordinal > current.ordinal) {
                // Higher severity preempts: drop the current remainder.
                stopSpeechPlayback()
                speakingSeverity = null
                lifecycleScope.launch { playSpeech(utterance) }
            } else {
                speechQueue.offer(utterance)
            }
        } else {
            lifecycleScope.launch { playSpeech(utterance) }
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
            putFloat(
                android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME,
                aiVolume()
            )
        }
        val engine = onlineAiTts
        if (engine == null) {
            onlineAiTts = android.speech.tts.TextToSpeech(requireContext()) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    onlineAiTts?.setOnUtteranceProgressListener(speechProgressListener)
                    runCatching { onlineAiTts?.language = java.util.Locale.getDefault() }
                    onlineAiTts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "onlineai")
                } else {
                    Log.e("LiveDataFragment", "Online AI TTS init failed: $status")
                    Handler(Looper.getMainLooper()).post { onSpeechDone() }
                }
            }
        } else {
            engine.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "onlineai")
        }
    }

    private val speechProgressListener = object : android.speech.tts.UtteranceProgressListener() {
        override fun onDone(utteranceId: String?) {
            Handler(Looper.getMainLooper()).post { onSpeechDone() }
        }

        override fun onError(utteranceId: String?) {
            Handler(Looper.getMainLooper()).post { onSpeechDone() }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?, errorCode: Int) {
            Handler(Looper.getMainLooper()).post { onSpeechDone() }
        }

        override fun onStart(utteranceId: String?) {}
    }

    private fun onSpeechDone() {
        speakingSeverity = null
        val next = speechQueue.poll()
        if (next != null && isAdded) {
            lifecycleScope.launch { playSpeech(next) }
        }
    }

    /** Stops whatever the Online AI is saying (preemption, mute, teardown). */
    private fun stopSpeechPlayback() {
        runCatching { onlineAiTts?.stop() }
    }

    private fun playSound(soundResId: Int) {
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)) {
            return
        }

        // release() only — stop() on an already-released player throws IllegalStateException
        mediaPlayer?.release()
        mediaPlayer = null
        mediaPlayer = MediaPlayer.create(context, soundResId)?.apply {
            setVolume(offlineVolume, offlineVolume)
            start()
            setOnCompletionListener {
                mediaPlayer = null
                it.release()
            }
        }
    }

    /** Cuts off a warning that is still playing (e.g. RPM fell back down). */
    private fun stopSound() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        runCatching {
            if (player.isPlaying) player.stop()
            player.release()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        demoSheet?.dismiss()
        demoSheet = null
        bannerAction = null
        offlineAiCountdown?.cancel()
        offlineAiCountdown = null
        aiCountdown?.cancel()
        aiCountdown = null
        onlineAiManager?.stop()
        onlineAiManager = null
        customGauge = null
        rpmGauge = null
        speechQueue.clear()
        speakingSeverity = null
        stopSpeechPlayback()
        onlineAiTts?.shutdown()
        onlineAiTts = null
        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(this)
        }
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val speedSource = prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD)
        if (speedSource == PrefsKeys.SPEED_SOURCE_DEVICE) {
            requireContext().unregisterReceiver(batteryTempReceiver)
        }
        // Rotation recreates the view but keeps the process (and the socket):
        // only tear the connection down when truly leaving the screen.
        if (activity?.isChangingConfigurations != true) {
            obdHelper.disconnectFromObdDevice()
            obdHelper.stopLiveDataMonitoring()
        }
        stopSound()
        engineSound.stop()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
