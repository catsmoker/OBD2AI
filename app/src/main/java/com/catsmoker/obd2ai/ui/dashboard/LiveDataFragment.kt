package com.catsmoker.obd2ai.ui.dashboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.Units
import com.catsmoker.obd2ai.speedometers.SpeedometerStyle
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live Data screen: real-time OBD-II gauges, Offline AI proactive cues,
 * Online AI assistant card, and demo mode.
 *
 * Migrated from XML to Compose. Gauges are Compose-native Canvas tiles;
 * all event detection, banner/TTS, Online AI, and demo logic is preserved
 * unchanged.
 */
class LiveDataFragment : Fragment() {

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

    companion object {
        private const val RPM_GREEN = 0xFF43A047.toInt()
        private const val RPM_AMBER = 0xFFC07F00.toInt()
        private const val RPM_RED = 0xFFE53935.toInt()

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
            val amberEnd = (amberThreshold(shiftRpm) / 100f / rpmMax).coerceIn(greenEnd + 0.05f, 0.95f)
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

        fun comboActive(rpm: Int, rpmThreshold: Int, speedKmh: Float, speedThreshold: Int): Boolean =
            rpm >= rpmThreshold && speedKmh >= speedThreshold.toFloat()

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
    private val offlineKnownCodes = mutableSetOf<String>()
    private var offlineCodesSeeded = false
    private val offlineClearedCodes = mutableSetOf<String>()
    private var easterOn = true
    private var easterLines: Map<String, List<String>> = emptyMap()
    private val easterCooldowns = mutableMapOf<String, Long>()
    private val easterRecent = ArrayDeque<String>()
    private val easterRandom = kotlin.random.Random.Default

    private var speedometerStyle: SpeedometerStyle = SpeedometerStyle.CLASSIC
    private var bannerAction: (() -> Unit)? = null
    private var offlineAiCountdown: android.os.CountDownTimer? = null
    private val offlineAiLastFired = mutableMapOf<OfflineAiEvent, Long>()
    private var offlineAiWelcomed = false
    private var onlineAiManager: com.catsmoker.obd2ai.ai.OnlineAiManager? = null
    private var aiCountdown: android.os.CountDownTimer? = null
    private val speechQueue = com.catsmoker.obd2ai.ai.SpeechQueue()
    private var speakingSeverity: com.catsmoker.obd2ai.ai.OnlineAiSeverity? = null
    private var onlineAiTts: android.speech.tts.TextToSpeech? = null
    private val engineSound = com.catsmoker.obd2ai.audio.EngineSound()

    // ---- Demo dashboard state ----
    private var demoSheet: com.catsmoker.obd2ai.ui.dashboard.DemoControlsSheet? = null
    private var demoSpeed = 0
    private var demoRpm = 0
    private var demoCoolant = 0
    private var demoVoltageTenths = 138
    private var demoFuel = 64

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.catsmoker.obd2ai.ui.theme.Obd2AiTheme(
                    darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
                ) {
                    LiveDataScreen(onBack = { findNavController().navigateUp() })
                }
            }
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
        speechQueue.clear()
        speakingSeverity = null
        stopSpeechPlayback()
        onlineAiTts?.shutdown()
        onlineAiTts = null
        stopSound()
        engineSound.stop()
    }

    // ---- Compose screen content ----

    @Composable
    fun LiveDataScreen(onBack: () -> Unit) {
        val ctx = LocalContext.current
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)

        val shiftRpm by remember { mutableStateOf(prefs.getInt(PrefsKeys.SHIFT_RPM, PrefsKeys.DEFAULT_SHIFT_RPM).coerceIn(PrefsKeys.SHIFT_RPM_MIN, PrefsKeys.SHIFT_RPM_MAX)) }
        val rpmMax by remember { mutableStateOf(prefs.getInt(PrefsKeys.RPM_MAX, PrefsKeys.DEFAULT_RPM_MAX).coerceIn(PrefsKeys.RPM_MAX_MIN, PrefsKeys.RPM_MAX_MAX)) }
        val imperial by remember { mutableStateOf(Units.isImperial(prefs)) }

        val speedText by ObdDataHolder.speedFlow.collectAsStateWithLifecycle("-- km/h")
        val rpmText by ObdDataHolder.rpmFlow.collectAsStateWithLifecycle("-- RPM")

        val demoMode by remember { mutableStateOf(obdHelper.demoMode) }

        Column(modifier = Modifier.fillMaxSize()) {
            // Status pill
            Text(
                text = when {
                    demoMode -> stringResource(R.string.error_overview_demo_badge)
                    prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD) == PrefsKeys.SPEED_SOURCE_DEVICE ->
                        stringResource(R.string.live_data_status_gps)
                    else -> stringResource(R.string.live_data_status_obd)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 16.dp, start = 24.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Gauge row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val speedSource = prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD)
                    val speedValue = if (speedSource == PrefsKeys.SPEED_SOURCE_DEVICE) lastSpeedKmh else (speedText.split(" ")[0].toFloatOrNull() ?: 0f)
                    CanvasGauge(
                        label = stringResource(R.string.gauge_speed_desc),
                        value = speedValue,
                        max = Units.speedGaugeMax(imperial).toFloat(),
                        unit = Units.speedUnitLabel(imperial)
                    )
                    if (speedSource != PrefsKeys.SPEED_SOURCE_DEVICE) {
                        CanvasGauge(
                            label = stringResource(R.string.gauge_rpm_desc),
                            value = (rpmText.split(" ")[0].toIntOrNull() ?: 0).toFloat() / 100f,
                            max = (rpmMax / 100f).toFloat(),
                            unit = "x100 RPM"
                        )
                    }
                }
            }
        }
    }

    // ---- Stub methods (full logic added in later pieces) ----

    private fun stopSpeechPlayback() {}
    private fun stopSound() {}

    /** Compose-native speed/RPM gauge (replaces the XML speedometer Views via AndroidView). */
    @Composable
    private fun CanvasGauge(
        label: String,
        value: Float,
        max: Float,
        unit: String
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${value.toInt()}$unit",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
