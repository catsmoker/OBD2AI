package com.catsmoker.obd2ai

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.eltonvs.obd.command.at.AdapterVoltageCommand
import com.github.eltonvs.obd.command.at.DescribeProtocolNumberCommand
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdRawResponse
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.command.BusInitException
import com.github.eltonvs.obd.command.NoDataException
import com.github.eltonvs.obd.command.StoppedException
import com.github.eltonvs.obd.command.UnSupportedCommandException
import com.github.eltonvs.obd.command.UnableToConnectException
import com.github.eltonvs.obd.command.control.DTCNumberCommand
import com.github.eltonvs.obd.command.control.DistanceSinceCodesClearedCommand
import com.github.eltonvs.obd.command.control.MILOnCommand
import com.github.eltonvs.obd.command.control.ModuleVoltageCommand
import com.github.eltonvs.obd.command.control.TimeSinceCodesClearedCommand
import com.github.eltonvs.obd.command.control.VINCommand
import com.github.eltonvs.obd.command.control.PendingTroubleCodesCommand
import com.github.eltonvs.obd.command.control.PermanentTroubleCodesCommand
import com.github.eltonvs.obd.command.control.ResetTroubleCodesCommand
import com.github.eltonvs.obd.command.control.TroubleCodesCommand
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

// =================================================================================
// MODELS
// =================================================================================

data class BluetoothDeviceDTO(
    val name: String,
    val address: String
)

data class DtpCodeDTO(
    val errorCode: String,
    val severity: ErrorSeverity,
    val title: String,
    val detail: String,
    val implications: String,
    val suggestedActions: List<String>,
    /** True when produced by the offline fallback instead of the OpenAI assessment. */
    val offline: Boolean = false,
    /**
     * Which read modes reported this code (stored/pending/permanent).
     * Empty = unknown (e.g. older caches) — UI hides the pills then.
     */
    val sources: Set<DtcSource> = emptySet()
)

/** Which OBD read mode reported a fault code. */
enum class DtcSource {
    STORED,
    PENDING,
    PERMANENT;
}

/** One bundled generic fault-code explanation (offline, no network). */
data class DtcInfo(
    val code: String,
    val title: String,
    val detail: String,
    val implications: String,
    val actions: List<String>
)

/**
 * Bundled generic DTC knowledge (`res/raw/dtc_generic.json`, English only —
 * like the Easter-egg lines, translating a fault library is future work).
 * Covers common codes so the offline fallback says something specific
 * instead of "unknown system". Pure logic, tested in AppCoreTest.
 */
object DtcDictionary {
    /** Parses the bundled table; bad rows are skipped, garbage yields empty. */
    fun fromJson(json: String): Map<String, DtcInfo> {
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val code = obj.optString("code").trim().uppercase()
                val title = obj.optString("title").trim()
                if (code.isEmpty() || title.isEmpty()) return@mapNotNull null
                val actions = obj.optJSONArray("actions")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList()
                code to DtcInfo(
                    code = code,
                    title = title,
                    detail = obj.optString("detail"),
                    implications = obj.optString("implications"),
                    actions = actions
                )
            }.toMap()
        } catch (e: Exception) {
            Log.e("DtcDictionary", "Failed to parse DTC dictionary", e)
            emptyMap()
        }
    }

    /**
     * Exact match first; cylinder misfires P0301..P0312 share the P0300
     * family entry. Returns null when nothing is known — callers fall back
     * to the generic system/origin text.
     */
    fun lookup(code: String, table: Map<String, DtcInfo>): DtcInfo? {
        val key = code.trim().uppercase()
        table[key]?.let { return it }
        val misfireCylinder = key.length == 5 && key[4].isDigit() &&
            (key.startsWith("P030") || key == "P0310" || key == "P0311" || key == "P0312")
        if (misfireCylinder) {
            return table["P0300"]
        }
        return null
    }
}

data class MilStatus(
    val milOn: Boolean,
    val storedCount: Int
)

enum class ErrorSeverity {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromInt(value: Int) = when (value) {
            0 -> LOW
            1 -> MEDIUM
            2 -> HIGH
            else -> throw IllegalArgumentException("Invalid severity level: $value")
        }

        fun getColor(severity: ErrorSeverity) = when (severity) {
            MEDIUM -> Color.rgb(255, 165, 0)
            HIGH -> Color.RED
            else -> Color.GRAY
        }
    }
}

object ObdDataHolder {
    var dtpResults: List<DtpCodeDTO> = emptyList()
    var lastMil: MilStatus? = null
    val isMonitoring = AtomicBoolean(false)
    val speedFlow = MutableStateFlow("-- km/h")
    val rpmFlow = MutableStateFlow("-- RPM")
    val coolantTempFlow = MutableStateFlow("-- °C")
    val engineLoadFlow = MutableStateFlow("-- %")
    val voltageFlow = MutableStateFlow("-- V")
    val fuelFlow = MutableStateFlow("-- %")
    /**
     * PIDs the vehicle confirmed via the 0100/0120/0140 support bitmasks
     * (see PidRegistry). Null = not probed yet — callers must treat every
     * PID as potentially supported until discovery runs.
     */
    var supportedPids: Set<Int>? = null

    /** Null (unknown) counts as supported; only an explicit probe can rule a PID out. */
    fun isPidSupported(pid: Int): Boolean = supportedPids?.contains(pid) ?: true
}

object DtcStore {
    const val FILE_NAME = "dtc_results.json"

    fun toJson(results: List<DtpCodeDTO>): String {
        val array = org.json.JSONArray()
        for (dto in results) {
            array.put(
                JSONObject()
                    .put("errorCode", dto.errorCode)
                    .put("severity", dto.severity.ordinal)
                    .put("title", dto.title)
                    .put("detail", dto.detail)
                    .put("implications", dto.implications)
                    .put("suggestedActions", org.json.JSONArray(dto.suggestedActions))
                    .put("offline", dto.offline)
                    .put("sources", org.json.JSONArray(dto.sources.map { it.name }))
            )
        }
        return array.toString()
    }

    fun fromJson(json: String): List<DtpCodeDTO> {
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val code = obj.optString("errorCode").ifBlank { return@mapNotNull null }
                val severity = runCatching { ErrorSeverity.fromInt(obj.optInt("severity")) }
                    .getOrDefault(ErrorSeverity.MEDIUM)
                val actions = obj.optJSONArray("suggestedActions")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList()
                // Unknown names (e.g. written by a newer app version) are
                // skipped, never fatal: sources are hints, not data.
                val sources = obj.optJSONArray("sources")?.let { arr ->
                    (0 until arr.length()).mapNotNull { idx ->
                        runCatching { DtcSource.valueOf(arr.optString(idx)) }.getOrNull()
                    }.toSet()
                } ?: emptySet()
                DtpCodeDTO(
                    errorCode = code,
                    severity = severity,
                    title = obj.optString("title", code),
                    detail = obj.optString("detail"),
                    implications = obj.optString("implications"),
                    suggestedActions = actions,
                    offline = obj.optBoolean("offline", false),
                    sources = sources
                )
            }
        } catch (e: Exception) {
            Log.e("DtcStore", "Failed to parse cached DTC results", e)
            emptyList()
        }
    }

    fun save(context: Context, results: List<DtpCodeDTO>) {
        runCatching {
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(toJson(results).toByteArray())
            }
        }.onFailure { e -> Log.e("DtcStore", "Failed to cache DTC results", e) }
    }

    fun load(context: Context): List<DtpCodeDTO> {
        return runCatching {
            context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        }.mapCatching(::fromJson).getOrDefault(emptyList())
    }
}

/**
 * Edge-to-edge inset handling, applied once to the activity root so every
 * screen (including future ones) stays clear of the status bar, navigation
 * bar / gesture area, and display cutouts. Existing padding is preserved and
 * insets are passed through unconsumed, so children (EditText auto-scroll,
 * IME, ScrollViews) keep working.
 */
fun View.applySystemBarInsets() {
    val startLeft = paddingLeft
    val startTop = paddingTop
    val startRight = paddingRight
    val startBottom = paddingBottom
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(
            startLeft + bars.left,
            startTop + bars.top,
            startRight + bars.right,
            startBottom + bars.bottom
        )
        insets
    }
}

object PrefsKeys {
    const val PREFS_NAME = "app_prefs"
    const val OPENAI_API_KEY = "openai_api_key"
    const val OPENAI_MODEL_ID = "openai_model_id"
    const val AI_PROVIDER = "ai_provider"
    const val AI_BASE_URL = "ai_base_url"
    const val SPEED_SOURCE = "speed_source"
    const val SPEED_SOURCE_DEVICE = "this_device"
    const val SPEED_SOURCE_OBD = "obd2_device"
    const val DARK_MODE = "dark_mode"
    const val MUTE_SOUND = "mute_sound"
    const val THEME_MODE = "theme_mode"
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val WIFI_HOST = "wifi_host"
    const val WIFI_PORT = "wifi_port"
    const val VOICE_INSIGHT = "voice_insight" // legacy key: Offline AI voice-alerts switch
    const val APP_LANGUAGE = "app_language"
    const val SHIFT_RPM = "shift_rpm"
    const val DEFAULT_SHIFT_RPM = 3500
    const val SHIFT_RPM_MIN = 1500
    const val SHIFT_RPM_MAX = 4500
    const val SHIFT_RPM_STEP = 100
    /** Coolant overheat alarm threshold (°C). */
    const val COOLANT_ALARM_C = 120
    /** Alarm releases below this so the siren does not flap at the edge. */
    const val COOLANT_ALARM_RELEASE_C = 115
    // -- Offline AI (scripted events + prerecorded audio, no network) -----------
    const val OFFLINE_AI_ENABLED = "offline_ai_enabled"
    const val OFFLINE_TEXT_ALERTS = "offline_text_alerts"
    const val OFFLINE_VOLUME = "offline_volume"
    const val DEFAULT_OFFLINE_VOLUME = 80
    const val SHIFT_POINT_ENABLED = "shift_point_enabled"
    const val HIGH_RPM_ENABLED = "high_rpm_enabled"
    const val HIGH_RPM_THRESHOLD = "high_rpm_threshold"
    const val DEFAULT_HIGH_RPM = 5500
    const val HIGH_RPM_MIN = 3000
    const val HIGH_RPM_MAX = 7000
    const val HIGH_RPM_STEP = 100
    const val HIGH_SPEED_ENABLED = "high_speed_enabled"
    const val HIGH_SPEED_THRESHOLD = "high_speed_threshold"
    const val DEFAULT_HIGH_SPEED = 120
    const val HIGH_SPEED_MIN = 30
    const val HIGH_SPEED_MAX = 220
    const val HIGH_SPEED_STEP = 5
    const val OFFLINE_COOLANT_ENABLED = "offline_coolant_enabled"
    const val OFFLINE_COOLANT_THRESHOLD = "offline_coolant_threshold"
    const val DEFAULT_OFFLINE_COOLANT = 105
    const val OFFLINE_COOLANT_MIN = 70
    const val OFFLINE_COOLANT_MAX = 130
    const val OFFLINE_COOLANT_STEP = 1
    const val NEW_FAULT_ENABLED = "new_fault_enabled"
    const val CONN_LOST_ENABLED = "conn_lost_enabled"
    const val CONN_RESTORED_ENABLED = "conn_restored_enabled"
    const val ENGINE_START_ENABLED = "engine_start_enabled"
    const val ENGINE_STOP_ENABLED = "engine_stop_enabled"
    const val EASTER_EGGS_ENABLED = "easter_eggs_enabled"
    // -- Online AI output switches (at least one must stay on) ------------------
    const val ONLINE_VOICE_ALERTS = "online_voice_alerts"
    const val ONLINE_TEXT_ALERTS = "online_text_alerts"
    // -- Online AI (online diagnostic assistant) ----------------------------------
    const val ONLINE_AI_ENABLED = "real_ai_enabled"
    const val AI_FREQUENCY = "ai_frequency"
    const val AI_PERSONALITY = "ai_personality"
    const val AI_VOLUME = "ai_volume"
    const val DEFAULT_AI_VOLUME = 80
    const val DEFAULT_WIFI_HOST = "192.168.0.10"
    const val DEFAULT_WIFI_PORT = 35000
    // -- Display & experience --------------------------------------------------
    const val UNITS = "units"
    const val UNITS_METRIC = "metric"
    const val UNITS_IMPERIAL = "imperial"
    const val KEEP_SCREEN_ON = "keep_screen_on"
    const val ENGINE_SOUND_ENABLED = "engine_sound_enabled"
}

/**
 * Display units. Every threshold, detector and AI prompt stays in SI
 * (km/h, °C); only pixels and spoken numbers convert, via [displaySpeed]
 * and [displayTemp]. Pure logic, tested in AppCoreTest.
 */
object Units {
    fun kmhToMph(kmh: Double): Double = kmh * 0.621371

    fun cToF(celsius: Double): Double = celsius * 1.8 + 32.0

    fun displaySpeed(kmh: Double, imperial: Boolean): Double =
        if (imperial) kmhToMph(kmh) else kmh

    fun displayTemp(celsius: Double, imperial: Boolean): Double =
        if (imperial) cToF(celsius) else celsius

    /** Speedometer dial cap matching the layout default (220 km/h ≈ 140 mph). */
    fun speedGaugeMax(imperial: Boolean): Float = if (imperial) 140f else 220f

    fun speedUnitLabel(imperial: Boolean): String = if (imperial) "mph" else "Km/h"

    fun isImperial(prefs: android.content.SharedPreferences): Boolean =
        prefs.getString(PrefsKeys.UNITS, PrefsKeys.UNITS_METRIC) == PrefsKeys.UNITS_IMPERIAL
}

/** One trip's totals; all speeds in km/h (callers convert for display). */
data class TripSnapshot(
    val durationMs: Long,
    val distanceKm: Double,
    val avgKmh: Double,
    val maxKmh: Double,
    val maxRpm: Int,
    val idleMs: Long,
    val driveMs: Long
)

/**
 * OBD-only trip computer (android-obd-reader's TripRecord idea,
 * reimplemented): integrates speed into distance and splits idle vs drive
 * time. No GPS, nothing persisted — the screen owns the instance. Pure
 * logic, tested in AppCoreTest.
 */
class TripComputer {
    private var startMs: Long? = null
    private var lastMs: Long? = null
    private var elapsedMs = 0L
    private var distanceKm = 0.0
    private var maxKmh = 0.0
    private var maxRpm = 0
    private var idleMs = 0L
    private var driveMs = 0L

    val isRunning: Boolean get() = startMs != null

    fun start(nowMs: Long) {
        reset()
        startMs = nowMs
        lastMs = nowMs
    }

    fun stop(nowMs: Long) {
        val start = startMs ?: return
        elapsedMs += (nowMs - start).coerceAtLeast(0)
        startMs = null
    }

    fun reset() {
        startMs = null
        lastMs = null
        elapsedMs = 0L
        distanceKm = 0.0
        maxKmh = 0.0
        maxRpm = 0
        idleMs = 0L
        driveMs = 0L
    }

    /** One ~1 s sample; null = unknown and counts as standing still. */
    fun sample(speedKmh: Double?, rpm: Int?, nowMs: Long) {
        if (startMs == null) return
        val prev = lastMs ?: nowMs
        val dtMs = (nowMs - prev).coerceAtLeast(0)
        lastMs = nowMs
        val speed = (speedKmh ?: 0.0).coerceAtLeast(0.0)
        distanceKm += speed * dtMs / 3_600_000.0
        if (speed < 3.0) idleMs += dtMs else driveMs += dtMs
        if (speed > maxKmh) maxKmh = speed
        if (rpm != null && rpm > maxRpm) maxRpm = rpm
    }

    fun snapshot(nowMs: Long): TripSnapshot {
        val start = startMs
        val duration = elapsedMs + (if (start != null) (nowMs - start).coerceAtLeast(0) else 0)
        val hours = duration / 3_600_000.0
        return TripSnapshot(
            durationMs = duration,
            distanceKm = distanceKm,
            avgKmh = if (hours > 0) distanceKm / hours else 0.0,
            maxKmh = maxKmh,
            maxRpm = maxRpm,
            idleMs = idleMs,
            driveMs = driveMs
        )
    }

    companion object {
        fun formatDuration(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        }
    }
}

enum class ThemeMode(val prefValue: String) {
    SYSTEM(PrefsKeys.THEME_SYSTEM),
    LIGHT(PrefsKeys.THEME_LIGHT),
    DARK(PrefsKeys.THEME_DARK);

    companion object {
        fun fromPref(value: String?): ThemeMode =
            entries.firstOrNull { it.prefValue == value } ?: SYSTEM

        /** Migrates the legacy dark-mode switch to the new three-way setting. */
        fun fromLegacyDarkMode(darkMode: Boolean): ThemeMode =
            if (darkMode) DARK else SYSTEM
    }
}

/** In-app language override; SYSTEM follows the device locale. */
enum class AppLanguage(val tag: String, val displayName: String) {
    SYSTEM("system", "System default"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    ARABIC("ar", "العربية"),
    CHINESE("zh-CN", "中文");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: SYSTEM
    }
}

/** How chatty the Online AI may be. */
enum class AiFrequency(val prefValue: String) {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high");

    companion object {
        fun fromPref(value: String?): AiFrequency =
            entries.firstOrNull { it.prefValue == value } ?: NORMAL
    }
}

/** Voice character of the Online AI's spoken replies. */
enum class AiPersonality(val prefValue: String) {
    NORMAL("normal"),
    FUNNY("funny"),
    PROFESSIONAL("professional");

    companion object {
        fun fromPref(value: String?): AiPersonality =
            entries.firstOrNull { it.prefValue == value } ?: NORMAL
    }

    fun styleLine(): String = when (this) {
        FUNNY -> "You may include at most one light joke; never joke about safety-critical faults."
        PROFESSIONAL -> "Be concise and formal. No jokes, no filler words."
        NORMAL -> "Be warm and plain-spoken, like a knowledgeable friend in the passenger seat."
    }
}

/** Urgency of a Online AI occasion; also drives queue priority and card time. */
enum class OnlineAiSeverity { INFO, WARNING, FAULT }

/**
 * Simulated adapter (inspired by the SwiftOBD2 MOCKComm and AndrOBD demo ideas
 * in the reference projects): lets users explore the whole app with no car.
 */
object DemoObdSource {
    val storedCodes = listOf("P0301", "P0455")
    val pendingCodes = listOf("P0420")
    val permanentCodes = emptyList<String>()
    val milStatus = MilStatus(milOn = true, storedCount = 2)
    const val VOLTAGE = "13.8"
    const val ADAPTER_VOLTAGE = "13.5"
    const val PROTOCOL = "ISO 15765-4 CAN"
    const val VIN = "1M8GDM9AXKP042788"
    const val SINCE_KM = "145 Km"
    const val SINCE_MIN = "320 min"

    /** Drive-cycle-like waves so gauges move realistically. */
    fun liveValuesAt(second: Int): Triple<Int, Int, Int> {
        val t = second.toDouble()
        val speed = (62 + 38 * kotlin.math.sin(t / 9.0)).toInt().coerceIn(0, 220)
        val rpm = (2300 + 1700 * kotlin.math.sin(t / 5.0 + 1.0)).toInt().coerceIn(800, 5200)
        val coolant = (89 + 3 * kotlin.math.sin(t / 25.0)).toInt()
        return Triple(speed, rpm, coolant)
    }

    /** Calculated engine load wave (%) for the demo dashboard. */
    fun engineLoadAt(second: Int): Int {
        val t = second.toDouble()
        return (42 + 25 * kotlin.math.sin(t / 7.0)).toInt().coerceIn(5, 95)
    }

    /**
     * Correlated demo waves for the extended PID set. They deliberately share
     * phases with [liveValuesAt]/[engineLoadAt] so the fake vehicle behaves
     * coherently: throttle follows load, MAF follows RPM × load, intake air
     * sits near ambient, timing retards under load, trims oscillate mildly.
     */
    fun throttleAt(second: Int): Int {
        val t = second.toDouble()
        return (35 + 28 * kotlin.math.sin(t / 7.0 + 0.4)).toInt().coerceIn(0, 100)
    }

    fun intakeTempAt(second: Int): Int {
        val t = second.toDouble()
        return (31 + 4 * kotlin.math.sin(t / 31.0)).toInt().coerceIn(-40, 120)
    }

    fun mafAt(second: Int): Double {
        val rpm = liveValuesAt(second).second.toDouble()
        val load = engineLoadAt(second).toDouble()
        // Toy correlation: airflow scales with RPM × load, normalized to g/s.
        return ((rpm / 1000.0) * (load / 100.0) * 28.0).coerceIn(1.0, 220.0)
    }

    fun timingAt(second: Int): Double {
        val t = second.toDouble()
        val load = engineLoadAt(second).toDouble()
        return (28.0 - load * 0.28 + 4.0 * kotlin.math.sin(t / 11.0)).coerceIn(-20.0, 50.0)
    }

    fun shortTrimAt(second: Int): Double {
        val t = second.toDouble()
        return (3.5 * kotlin.math.sin(t / 13.0)).coerceIn(-15.0, 15.0)
    }

    fun longTrimAt(second: Int): Double {
        val t = second.toDouble()
        return (2.0 + 1.5 * kotlin.math.sin(t / 47.0)).coerceIn(-15.0, 15.0)
    }

    const val FUEL_PCT = 64
}


// =================================================================================
// CUSTOM OBD COMMANDS WITH CORRECTED PARSING LOGIC
// =================================================================================

class MySpeedCommand : ObdCommand() {
    override val tag = "SPEED"
    override val name = "Vehicle Speed"
    override val mode = "01"
    override val pid = "0D"
    override val defaultUnit = "Km/h"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "410D"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 2) {
            val speedHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            // Clones can return non-hex garbage (e.g. "410DZZ"); never throw from a handler.
            // Blank = unreadable so callers can keep the last good value instead of
            // inventing a 0 (a moving car is never at 0 km/h with the engine running).
            runCatching { Integer.parseInt(speedHex, 16).toString() }.getOrDefault("")
        } else {
            ""
        }
    }
}

class MyRPMCommand : ObdCommand() {
    override val tag = "ENGINE_RPM"
    override val name = "Engine RPM"
    override val mode = "01"
    override val pid = "0C"
    override val defaultUnit = "RPM"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "410C"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 4) {
            val aHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            val bHex = rawValue.substring(index + identifier.length + 2, index + identifier.length + 4)

            val a = runCatching { Integer.parseInt(aHex, 16) }.getOrNull()
            val b = runCatching { Integer.parseInt(bHex, 16) }.getOrNull()

            // Either byte unreadable (or the frame truncated) -> blank, never
            // an invented 0 RPM: 0 with the ignition on does not exist.
            if (a == null || b == null) "" else (((a * 256) + b) / 4).toString()
        } else {
            ""
        }.toString()
    }
}

class MyCoolantTempCommand : ObdCommand() {
    override val tag = "COOLANT_TEMP"
    override val name = "Engine Coolant Temperature"
    override val mode = "01"
    override val pid = "05"
    override val defaultUnit = "°C"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "4105"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 2) {
            val tempHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            runCatching { (Integer.parseInt(tempHex, 16) - 40).toString() }.getOrDefault("")
        } else {
            ""
        }
    }
}

class MyEngineLoadCommand : ObdCommand() {
    override val tag = "ENGINE_LOAD"
    override val name = "Calculated Engine Load"
    override val mode = "01"
    override val pid = "04"
    override val defaultUnit = "%"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "4104"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 2) {
            val loadHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            // Empty string = unsupported/missing so callers can skip silently.
            runCatching { (Integer.parseInt(loadHex, 16) * 100 / 255).toString() }.getOrDefault("")
        } else {
            ""
        }
    }
}

class MyFuelLevelCommand : ObdCommand() {
    override val tag = "FUEL_LEVEL"
    override val name = "Fuel Tank Level"
    override val mode = "01"
    override val pid = "2F"
    override val defaultUnit = "%"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "412F"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 2) {
            val fuelHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            runCatching { (Integer.parseInt(fuelHex, 16) * 100 / 255).toString() }.getOrDefault("")
        } else {
            ""
        }
    }
}

/** Shared single-byte-percentage parsing (throttlenioskich 0x11): A * 100 / 255. Blank on failure. */
private fun parseSingleBytePercent(rawValue: String, identifier: String): String {
    val index = rawValue.indexOf(identifier)
    if (index != -1 && rawValue.length >= index + identifier.length + 2) {
        val hex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
        return runCatching { (Integer.parseInt(hex, 16) * 100 / 255).toString() }.getOrDefault("")
    }
    return ""
}

class MyThrottleCommand : ObdCommand() {
    override val tag = "THROTTLE_POSITION"
    override val name = "Throttle Position"
    override val mode = "01"
    override val pid = "11"
    override val defaultUnit = "%"

    override val handler = { it: ObdRawResponse ->
        parseSingleBytePercent(it.processedValue, "4111")
    }
}

class MyIntakeTempCommand : ObdCommand() {
    override val tag = "INTAKE_TEMP"
    override val name = "Intake Air Temperature"
    override val mode = "01"
    override val pid = "0F"
    override val defaultUnit = "°C"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "410F"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 2) {
            val tempHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            runCatching { (Integer.parseInt(tempHex, 16) - 40).toString() }.getOrDefault("")
        } else {
            ""
        }
    }
}

class MyMafCommand : ObdCommand() {
    override val tag = "MAF"
    override val name = "Mass Air Flow"
    override val mode = "01"
    override val pid = "10"
    override val defaultUnit = "g/s"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "4110"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 4) {
            val aHex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            val bHex = rawValue.substring(index + identifier.length + 2, index + identifier.length + 4)
            val a = runCatching { Integer.parseInt(aHex, 16) }.getOrNull()
            val b = runCatching { Integer.parseInt(bHex, 16) }.getOrNull()
            // One decimal, US locale so clones in any locale still parse downstream.
            if (a == null || b == null) "" else "%.1f".format(java.util.Locale.US, (a * 256 + b) / 100.0)
        } else {
            ""
        }
    }
}

class MyTimingCommand : ObdCommand() {
    override val tag = "TIMING_ADVANCE"
    override val name = "Timing Advance"
    override val mode = "01"
    override val pid = "0E"
    override val defaultUnit = "°"

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        val identifier = "410E"
        val index = rawValue.indexOf(identifier)

        if (index != -1 && rawValue.length >= index + identifier.length + 2) {
            val hex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
            // (A / 2) - 64: negative values are valid (retarded timing).
            runCatching {
                "%.1f".format(java.util.Locale.US, Integer.parseInt(hex, 16) / 2.0 - 64.0)
            }.getOrDefault("")
        } else {
            ""
        }
    }
}

/** Shared fuel-trim parsing (0x06-0x09): (A - 128) * 100 / 128, one decimal. Blank on failure. */
private fun parseFuelTrim(rawValue: String, identifier: String): String {
    val index = rawValue.indexOf(identifier)
    if (index != -1 && rawValue.length >= index + identifier.length + 2) {
        val hex = rawValue.substring(index + identifier.length, index + identifier.length + 2)
        return runCatching {
            "%.1f".format(java.util.Locale.US, (Integer.parseInt(hex, 16) - 128) * 100.0 / 128.0)
        }.getOrDefault("")
    }
    return ""
}

class MyShortFuelTrimCommand : ObdCommand() {
    override val tag = "SHORT_FUEL_TRIM_1"
    override val name = "Short Term Fuel Trim (Bank 1)"
    override val mode = "01"
    override val pid = "06"
    override val defaultUnit = "%"

    override val handler = { it: ObdRawResponse ->
        parseFuelTrim(it.processedValue, "4106")
    }
}

class MyLongFuelTrimCommand : ObdCommand() {
    override val tag = "LONG_FUEL_TRIM_1"
    override val name = "Long Term Fuel Trim (Bank 1)"
    override val mode = "01"
    override val pid = "08"
    override val defaultUnit = "%"

    override val handler = { it: ObdRawResponse ->
        parseFuelTrim(it.processedValue, "4108")
    }
}

/**
 * Mode 01 PID-support probe (0100/0120/0140): returns the raw bitmask payload
 * after the "41XX" identifier, or blank when the ECU answers NO DATA. Parsed
 * by [PidRegistry.parseSupportBitmask].
 */
class MySupportedPidsCommand(private val rangePid: String) : ObdCommand() {
    override val tag = "SUPPORTED_PIDS_$rangePid"
    override val name = "Supported PIDs ($rangePid)"
    override val mode = "01"
    override val pid = rangePid
    override val defaultUnit = ""

    override val handler = { it: ObdRawResponse ->
        val rawValue = it.processedValue
        // processedValue strips whitespace, so "41 00 BE 1F" arrives as "4100BE1F".
        val identifier = "41$rangePid"
        val index = rawValue.indexOf(identifier)
        if (index != -1) {
            rawValue.substring(index + identifier.length).filter { c -> c.isLetterOrDigit() }
        } else {
            ""
        }
    }
}

/** One live-data sensor the app knows how to read, display and test. */
data class PidDefinition(
    /** Mode 01 PID number, e.g. 0x0D for vehicle speed. */
    val pid: Int,
    /** Plausible display range; readings outside it are treated as garbage, never shown. */
    val min: Double,
    val max: Double,
    val unit: String,
    val decimals: Int = 0,
)

/**
 * Central registry of the Mode 01 PIDs the app supports (idea borrowed from
 * OBDvis's PidRegistry and ObdMetrics' PID groups, reimplemented here in the
 * codebase's own defensive-parsing style). The fast gauge loop stays small;
 * everything else is polled on demand or in slow telemetry, gated by
 * [ObdDataHolder.isPidSupported] after [ObdHelper.discoverSupportedPids] runs.
 */
object PidRegistry {
    const val PID_ENGINE_LOAD = 0x04
    const val PID_COOLANT_TEMP = 0x05
    const val PID_SHORT_TRIM_1 = 0x06
    const val PID_LONG_TRIM_1 = 0x08
    const val PID_RPM = 0x0C
    const val PID_SPEED = 0x0D
    const val PID_TIMING = 0x0E
    const val PID_INTAKE_TEMP = 0x0F
    const val PID_MAF = 0x10
    const val PID_THROTTLE = 0x11
    const val PID_FUEL_LEVEL = 0x2F
    const val PID_VOLTAGE = 0x42

    val all: List<PidDefinition> = listOf(
        PidDefinition(PID_ENGINE_LOAD, 0.0, 100.0, "%"),
        PidDefinition(PID_COOLANT_TEMP, -40.0, 215.0, "°C"),
        PidDefinition(PID_SHORT_TRIM_1, -100.0, 99.2, "%", 1),
        PidDefinition(PID_LONG_TRIM_1, -100.0, 99.2, "%", 1),
        PidDefinition(PID_RPM, 0.0, 8000.0, "RPM"),
        PidDefinition(PID_SPEED, 0.0, 255.0, "Km/h"),
        PidDefinition(PID_TIMING, -64.0, 63.5, "°", 1),
        PidDefinition(PID_INTAKE_TEMP, -40.0, 215.0, "°C"),
        PidDefinition(PID_MAF, 0.0, 655.35, "g/s", 1),
        PidDefinition(PID_THROTTLE, 0.0, 100.0, "%"),
        PidDefinition(PID_FUEL_LEVEL, 0.0, 100.0, "%"),
        PidDefinition(PID_VOLTAGE, 0.0, 30.0, "V", 1),
    )

    fun forPid(pid: Int): PidDefinition? = all.find { it.pid == pid }

    /** True when a parsed reading is inside the plausible range (else: garbage, don't show). */
    fun isPlausible(pid: Int, value: Double): Boolean {
        val def = forPid(pid) ?: return true
        return value in def.min..def.max
    }

    /**
     * Decodes one 0100/0120/0140 support bitmask into the set of supported
     * PID numbers. [payloadHex] is the raw hex after the "41XX" identifier
     * (whitespace/case tolerated). [rangeBase]
     * is 0x00/0x20/0x40 for the 0100/0120/0140 ranges. Bit N (MSB-first) of
     * byte B means PID (rangeBase + B * 8 + N + 1) is supported. Garbage in,
     * empty set out — never throws.
     */
    fun parseSupportBitmask(payloadHex: String, rangeBase: Int): Set<Int> {
        val clean = payloadHex.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.uppercase()
        // Real bitmasks are whole bytes; an odd nibble count means the input
        // was prose/adapter chatter ("NO DATA" filters down to "DAA"), not data.
        if (clean.length < 2 || clean.length % 2 != 0) return emptySet()
        val supported = mutableSetOf<Int>()
        val bytes = clean.chunked(2)
        for ((byteIndex, pair) in bytes.withIndex()) {
            if (pair.length < 2) break
            val byte = runCatching { Integer.parseInt(pair, 16) }.getOrNull() ?: return emptySet()
            for (bit in 0 until 8) {
                if (byte and (0x80 shr bit) != 0) {
                    supported.add(rangeBase + byteIndex * 8 + bit + 1)
                }
            }
        }
        return supported
    }
}

/** What the caller should do about an [ElmStatus]: nothing, retry once, or re-init the adapter. */
enum class ElmRecovery { NONE, RETRY, REINIT }

/**
 * Adapter states decoded from raw ELM327 text. The mechanism is studied from
 * LTSupportAutomotive's input filter and ObdMetrics' AdapterErrorType, then
 * reimplemented here in this codebase's defensive style. The check order in
 * [ElmSanitizer.classifyStatus] is load-bearing: specific states first,
 * generic ERROR and "?" last.
 */
enum class ElmStatus(val recovery: ElmRecovery) {
    NONE(ElmRecovery.NONE),
    OK(ElmRecovery.NONE),
    NO_DATA(ElmRecovery.NONE),
    SEARCHING(ElmRecovery.RETRY),
    STOPPED(ElmRecovery.RETRY),
    UNABLE_TO_CONNECT(ElmRecovery.REINIT),
    BUS_BUSY(ElmRecovery.RETRY),
    BUS_ERROR(ElmRecovery.REINIT),
    CAN_ERROR(ElmRecovery.REINIT),
    LOW_VOLTAGE_RESET(ElmRecovery.REINIT),
    GENERIC_ERROR(ElmRecovery.RETRY);
}

/**
 * Clone-proofing for raw ELM327 text: cheap adapters inject control bytes,
 * high bytes and chatter around real frames. All functions are pure (no
 * Android imports) so they run in JVM tests.
 */
object ElmSanitizer {
    /**
     * Drops non-printable bytes (keeping tab/CR/LF as separators), splits
     * into lines and drops empties. "410D1F\\r\\r>\\nSEARCHING..." becomes
     * ["410D1F", ">", "SEARCHING..."].
     */
    fun sanitizeLines(raw: String): List<String> =
        raw.filter { c -> c == '\t' || c == '\n' || c == '\r' || c.code in 0x20..0x7E }
            .split('\r', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** True for frames that are only hex digits and spaces ("41 0D 1F"). */
    fun isValidPidLine(line: String): Boolean =
        line.isNotEmpty() && line.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' || it == ' ' }

    /**
     * Names the adapter state in raw text. Specific states win over generic
     * markers ("CAN ERROR" is CAN_ERROR, not GENERIC_ERROR; "?" never
     * swallows a real state), and a bare OK token wins over prose that
     * merely contains those letters ("BROKEN" is NONE).
     */
    fun classifyStatus(raw: String): ElmStatus {
        val text = raw.uppercase()
        fun has(vararg needles: String) = needles.any { it in text }
        val tokens = text.split(Regex("\\s+"))
        return when {
            has("NO DATA", "NODATA") -> ElmStatus.NO_DATA
            has("SEARCHING") -> ElmStatus.SEARCHING
            has("STOPPED") -> ElmStatus.STOPPED
            has("UNABLE TO CONNECT", "UNABLETOCONNECT") -> ElmStatus.UNABLE_TO_CONNECT
            has("BUS BUSY", "BUSBUSY") -> ElmStatus.BUS_BUSY
            has("BUS ERROR", "BUSERROR", "BUSINIT", "BUS INIT") -> ElmStatus.BUS_ERROR
            has("CAN ERROR", "CANERROR") -> ElmStatus.CAN_ERROR
            has("LVRESET", "LOW VOLTAGE", "LOWVOLTAGE") -> ElmStatus.LOW_VOLTAGE_RESET
            has("ERROR") -> ElmStatus.GENERIC_ERROR
            "?" in text -> ElmStatus.GENERIC_ERROR
            tokens.any { it == "OK" } -> ElmStatus.OK
            else -> ElmStatus.NONE
        }
    }
}

/**
 * Per-PID failure budget (AndrOBD's MAX_ERROR_COUNT idea, reimplemented):
 * a PID that fails repeatedly is dropped from the poll loop instead of
 * wasting bus time and risking invented values. A single success clears the
 * streak. Pure logic, tested in AppCoreTest.
 */
class PidHealthTracker(private val maxErrors: Int = 3) {
    private val badStreaks = mutableMapOf<Int, Int>()
    private val disabled = mutableSetOf<Int>()

    fun recordSuccess(pid: Int) {
        badStreaks.remove(pid)
    }

    /** Records one failed read; returns true when the PID just got disabled. */
    fun recordFailure(pid: Int): Boolean {
        if (pid in disabled) return true
        val streak = (badStreaks[pid] ?: 0) + 1
        return if (streak >= maxErrors) {
            disabled.add(pid)
            badStreaks.remove(pid)
            true
        } else {
            badStreaks[pid] = streak
            false
        }
    }

    fun isDisabled(pid: Int): Boolean = pid in disabled

    fun disabledPids(): Set<Int> = disabled.toSet()

    fun reset() {
        badStreaks.clear()
        disabled.clear()
    }
}

/**
 * Snapshot of sensor values stored by the ECU when a fault was set
 * (OBD mode 02). Every field is null when that reading is unavailable —
 * callers show "not available", never zeros. Pure parsing, tested.
 */
data class FreezeFrame(
    val dtc: String?,
    val rpm: Int?,
    val speedKmh: Int?,
    val coolantC: Int?,
    val loadPct: Int?
) {
    companion object {
        /**
         * Decodes one 2-byte DTC exactly like stored-code readers do:
         * bits 7-6 = P/C/B/U, bits 5-4 = 0/1/2/3, then three hex nibbles.
         * 0x0000 means "no code", so it yields null.
         */
        fun decodeDtcBytes(b0: Int, b1: Int): String? {
            if (b0 == 0 && b1 == 0) return null
            val prefix = when (b0 shr 6) {
                0 -> "P"
                1 -> "C"
                2 -> "B"
                else -> "U"
            }
            val digits = intArrayOf((b0 shr 4) and 0x03, b0 and 0x0F, (b1 shr 4) and 0x0F, b1 and 0x0F)
            return prefix + digits.joinToString("") { it.toString(16).uppercase() }
        }

        /** Hex payload after a mode-02 identifier ("420C" + N bytes), or null. */
        fun payloadAfter(raw: String?, identifier: String, bytes: Int): String? {
            if (raw.isNullOrBlank()) return null
            val clean = raw.uppercase().filter { it.isLetterOrDigit() }
            val index = clean.indexOf(identifier)
            if (index < 0) return null
            val hex = clean.substring(index + identifier.length).take(bytes * 2)
            if (hex.length < bytes * 2) return null
            if (hex.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
            return hex
        }

        /** Parses one mode-02 reply set; unknown/truncated replies yield nulls. */
        fun parse(
            dtcRaw: String?,
            rpmRaw: String?,
            speedRaw: String?,
            coolantRaw: String?,
            loadRaw: String?
        ): FreezeFrame {
            val dtc = payloadAfter(dtcRaw, "4202", 2)?.let { hex ->
                decodeDtcBytes(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16))
            }
            fun oneByte(raw: String?, id: String, f: (Int) -> Int): Int? {
                val hex = payloadAfter(raw, id, 1) ?: return null
                return f(hex.toInt(16))
            }
            fun twoBytes(raw: String?, id: String, f: (Int, Int) -> Int): Int? {
                val hex = payloadAfter(raw, id, 2) ?: return null
                return f(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16))
            }
            return FreezeFrame(
                dtc = dtc,
                rpm = twoBytes(rpmRaw, "420C") { a, b -> (a * 256 + b) / 4 },
                speedKmh = oneByte(speedRaw, "420D") { it },
                coolantC = oneByte(coolantRaw, "4205") { it - 40 },
                loadPct = oneByte(loadRaw, "4204") { it * 100 / 255 }
            )
        }
    }
}

/**
 * Bounded transcript for the raw OBD console: newest-first thinking with a
 * fixed cap (200 lines) so a chatty adapter can never grow memory without
 * bound. Pure logic, tested in AppCoreTest.
 */
class ConsoleLog(private val capacity: Int = 200) {
    private val lines = ArrayDeque<String>()

    fun append(line: String) {
        lines.addLast(line)
        while (lines.size > capacity) lines.removeFirst()
    }

    fun clear() {
        lines.clear()
    }

    fun snapshot(): List<String> = lines.toList()
}


// =================================================================================
// HELPERS
// =================================================================================

class BluetoothHelper(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter?
    private var bluetoothSocket: BluetoothSocket? = null
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _isBluetoothPermissionGranted = MutableLiveData<Boolean>()
    val isBluetoothPermissionGranted: LiveData<Boolean> = _isBluetoothPermissionGranted

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 101
    }

    private val requiredPermissions: Array<String>
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            } else {
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_ADMIN,
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled ?: false

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (checkBluetoothPermissions()) {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
            bluetoothAdapter?.startDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (checkBluetoothPermissions() && bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
    }

    fun checkBluetoothPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(activity, requiredPermissions, REQUEST_CODE_PERMISSIONS)
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceDTO> {
        if (!checkBluetoothPermissions()) throw SecurityException("Permissions not granted")
        val adapter = bluetoothAdapter ?: throw IOException("Bluetooth adapter is not available.")
        return adapter.bondedDevices.map { convertToDeviceDTO(it) }
    }

    @Throws(IOException::class, SecurityException::class)
    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(deviceAddress: String): Pair<InputStream, OutputStream> = withContext(Dispatchers.IO) {
        if (!checkBluetoothPermissions()) throw SecurityException("Permissions not granted")
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            ?: throw IOException("Device not found")
        bluetoothSocket = try {
            device.createRfcommSocketToServiceRecord(sppUuid).apply {
                try {
                    bluetoothAdapter.cancelDiscovery()
                    connect()
                } catch (e: IOException) {
                    close()
                    throw IOException("Failed to connect to device.", e)
                }
            }
        } catch (e: IOException) {
            // Many ELM327 clones reject the secure socket; retry once insecurely.
            Log.w("BluetoothHelper", "Secure RFCOMM failed, trying insecure socket", e)
            try {
                device.createInsecureRfcommSocketToServiceRecord(sppUuid).apply { connect() }
            } catch (e2: IOException) {
                throw IOException("Failed to connect to device (secure and insecure).", e2)
            }
        }
        val socket = bluetoothSocket ?: throw IOException("Bluetooth socket connection failed.")
        return@withContext Pair(socket.inputStream, socket.outputStream)
    }

    fun disconnectFromDevice() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothHelper", "Error closing Bluetooth socket", e)
        } finally {
            bluetoothSocket = null
        }
    }

    fun resolvePermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            _isBluetoothPermissionGranted.postValue(granted)
        }
    }

    @SuppressLint("MissingPermission")
    fun convertToDeviceDTO(bluetoothDevice: BluetoothDevice): BluetoothDeviceDTO {
        return BluetoothDeviceDTO(
            name = bluetoothDevice.name ?: "Unknown Device",
            address = bluetoothDevice.address
        )
    }
}

class ObdHelper(private val bluetoothHelper: BluetoothHelper) {
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var obdConnection: ObdDeviceConnection? = null
    private var wifiSocket: java.net.Socket? = null

    /** When true, all reads come from [DemoObdSource] instead of hardware. */
    var demoMode: Boolean = false

    /**
     * Per-PID failure budget for the fast loop: a PID that keeps failing is
     * skipped so one dead sensor stops costing bus time. Reset on connect.
     */
    val pidHealth = PidHealthTracker()

    /**
     * Serializes every byte on the adapter streams. The library serializes
     * its own run() calls, but raw paths (console, init, batch) share the
     * same socket — without this, a slow poll colliding with a console send
     * garbles both frames into STOPPED soup.
     */
    private val ioMutex = Mutex()

    val isConnected: Boolean
        get() = demoMode || obdConnection != null

    suspend fun setupObd(deviceAddress: String) {
        demoMode = false
        pidHealth.reset()
        val (iStream, oStream) = bluetoothHelper.connectToDevice(deviceAddress)
        this.inputStream = iStream
        this.outputStream = oStream
        obdConnection = ObdDeviceConnection(iStream, oStream)
    }

    /** WiFi adapters (e.g. ELM327 clones at 192.168.0.10:35000) speak the same
     * serial protocol over TCP, so only the transport differs from Bluetooth. */
    suspend fun setupWifi(host: String, port: Int, timeoutMs: Int = 5000) = withContext(Dispatchers.IO) {
        demoMode = false
        pidHealth.reset()
        disconnectTransports()
        val socket = java.net.Socket()
        try {
            socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw IOException("Failed to connect to $host:$port.", e)
        }
        wifiSocket = socket
        inputStream = socket.getInputStream()
        outputStream = socket.getOutputStream()
        obdConnection = ObdDeviceConnection(inputStream!!, outputStream!!)
    }

    /** Enters demo mode: no transport, simulated data only. */
    fun setupDemo() {
        disconnectTransports()
        pidHealth.reset()
        demoMode = true
        obdConnection = null
    }

    suspend fun initializeObd() = withContext(Dispatchers.IO) {
        if (demoMode) return@withContext
        ioMutex.withLock {
            val out = outputStream ?: throw IOException("Output stream is not available.")
            val `in` = inputStream ?: throw IOException("Input stream is not available.")

            suspend fun sendRawCommand(command: String) {
                out.write((command + "\r").toByteArray())
                out.flush()
                delay(400)
            }

            sendRawCommand("ATZ")
            sendRawCommand("ATE0")
            sendRawCommand("ATL0")
            sendRawCommand("ATS0")
            sendRawCommand("ATH0")
            sendRawCommand("ATSP0")
            sendRawCommand("ATAT1")

            delay(1000)
            if (`in`.available() > 0) {
                val buffer = ByteArray(`in`.available())
                `in`.read(buffer)
                Log.d("ObdHelper", "Initialization buffer cleared. Read: ${String(buffer)}")
            }
        }
    }

    private suspend fun runCommand(command: ObdCommand): ObdResponse = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val connection = obdConnection ?: throw IOException("OBD connection not established.")
            val `in` = inputStream ?: throw IOException("Input stream is not available.")

            if (`in`.available() > 0) {
                val buffer = ByteArray(`in`.available())
                `in`.read(buffer)
            }

            val response = connection.run(command)
            Log.d("ObdHelper", "Command: ${command.name}, Raw: ${response.rawResponse.value}, Parsed: ${response.value} ${response.unit}")
            return@withLock response
        }
    }

    /**
     * Raw console: sends one free-text line (AT command or PID like "010C")
     * and returns everything up to the ">" prompt. Power-user debugging aid
     * for clone adapters; shares the connection, so avoid it while Live Data
     * is polling. Throws on empty input, oversize input, or reply timeout.
     */
    suspend fun sendRaw(command: String, timeoutMs: Long = 3000): String = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val cmd = command.trim()
            require(cmd.isNotEmpty()) { "Empty command." }
            require(cmd.length <= 64) { "Command too long." }
            if (demoMode) return@withLock "(demo) $cmd\r\nOK\r\n>"
            val out = outputStream ?: throw IOException("Output stream is not available.")
            val `in` = inputStream ?: throw IOException("Input stream is not available.")
            out.write((cmd + "\r").toByteArray())
            out.flush()
            val reply = StringBuilder()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (`in`.available() > 0) {
                    val chunk = ByteArray(`in`.available())
                    val read = `in`.read(chunk)
                    if (read > 0) {
                        reply.append(String(chunk, 0, read))
                        if (reply.contains('>')) break
                    }
                } else {
                    delay(50)
                }
            }
            val text = reply.toString()
            if (text.isBlank()) throw IOException("No reply from adapter (timeout).")
            return@withLock text
        }
    }

    suspend fun getDtpCodes(): List<String> = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(600)
            return@withContext DemoObdSource.storedCodes
        }
        val result = runCommand(TroubleCodesCommand()).value
        splitErrors(result)
    }

    suspend fun getPendingDtpCodes(): List<String> = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(300)
            return@withContext DemoObdSource.pendingCodes
        }
        val result = runCommand(PendingTroubleCodesCommand()).value
        splitErrors(result)
    }

    suspend fun getPermanentDtpCodes(): List<String> = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(300)
            return@withContext DemoObdSource.permanentCodes
        }
        val result = runCommand(PermanentTroubleCodesCommand()).value
        splitErrors(result)
    }

    /** Mode 01 PID 01: malfunction-indicator lamp state + ECU's stored-code count. */
    suspend fun getMilStatus(): MilStatus = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(200)
            return@withContext DemoObdSource.milStatus
        }
        val mil = runCommand(MILOnCommand()).value
        val count = runCommand(DTCNumberCommand()).value
        parseMilStatus(mil, count)
    }

    /** Mode 04: ask the ECU to erase stored codes and switch the MIL off. */
    suspend fun clearTroubleCodes(): String = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(600)
            return@withContext "OK"
        }
        runCommand(ResetTroubleCodesCommand()).value
    }

    /** ECU voltage (mode 01-42) + adapter supply voltage (ATRV) + protocol, for the health line. */
    suspend fun getAdapterHealth(): Triple<String, String, String> = withContext(Dispatchers.IO) {
        if (demoMode) return@withContext Triple(DemoObdSource.VOLTAGE, DemoObdSource.ADAPTER_VOLTAGE, DemoObdSource.PROTOCOL)
        val ecuVoltage = runCatching { runCommand(ModuleVoltageCommand()) }
            .map { "${it.value} ${it.unit}".trim() }.getOrDefault("?")
        // ATRV reads the adapter's own supply; a healthy adapter sits near
        // battery voltage. "?" = unreadable, never an invented number.
        val adapterVoltage = runCatching { runCommand(AdapterVoltageCommand()).value }
            .map { parseVoltage(it)?.let { volts -> "$volts V" } ?: "?" }.getOrDefault("?")
        val protocol = runCatching { runCommand(DescribeProtocolNumberCommand()) }
            .map { it.value.trim() }.getOrDefault("?")
        Triple(ecuVoltage, adapterVoltage, protocol)
    }

    /** VIN + distance/time since codes were last cleared (mode 01/09). */
    suspend fun getVehicleInfo(): Triple<String, String, String> = withContext(Dispatchers.IO) {
        if (demoMode) return@withContext Triple(DemoObdSource.VIN, DemoObdSource.SINCE_KM, DemoObdSource.SINCE_MIN)
        val vin = runCatching { runCommand(VINCommand()).value.trim() }.getOrDefault("?")
        val dist = runCatching { runCommand(DistanceSinceCodesClearedCommand()) }
            .map { "${it.value} ${it.unit}".trim() }.getOrDefault("?")
        val time = runCatching { runCommand(TimeSinceCodesClearedCommand()) }
            .map { "${it.value} ${it.unit}".trim() }.getOrDefault("?")
        Triple(vin, dist, time)
    }

    /**
     * Probes the 0100/0120/0140 support bitmasks and caches the result in
     * [ObdDataHolder.supportedPids] (same idea as OBDvis's bitmask discovery,
     * reimplemented on this codebase's defensive parsing). Runs once per
     * process; unknown stays unknown (treated as supported) when the ECU
     * answers NO DATA or the read fails. In demo every registry PID is
     * reported supported.
     */
    suspend fun discoverSupportedPids(): Set<Int> = withContext(Dispatchers.IO) {
        ObdDataHolder.supportedPids?.let { return@withContext it }
        if (demoMode) {
            val all = PidRegistry.all.map { it.pid }.toSet()
            ObdDataHolder.supportedPids = all
            return@withContext all
        }
        val found = mutableSetOf<Int>()
        val ranges = listOf("00" to 0x00, "20" to 0x20, "40" to 0x40)
        for ((rangePid, base) in ranges) {
            val payload = runCatching { runCommand(MySupportedPidsCommand(rangePid)).value }.getOrNull()
            if (payload.isNullOrBlank()) break
            val pids = PidRegistry.parseSupportBitmask(payload, base)
            if (pids.isEmpty()) break
            found.addAll(pids)
            // Last bit of the range = "another range follows" (0x20/0x40/0x60).
            if (!pids.contains(base + 0x20)) break
        }
        if (found.isNotEmpty()) ObdDataHolder.supportedPids = found
        return@withContext ObdDataHolder.supportedPids ?: emptySet()
    }

    private fun disconnectTransports() {
        bluetoothHelper.disconnectFromDevice()
        runCatching { wifiSocket?.close() }
        wifiSocket = null
        inputStream = null
        outputStream = null
    }

    fun disconnectFromObdDevice() {
        // NOTE: demoMode is intentionally NOT reset here. Leaving Live Data
        // (or rotating the phone) destroys the view and calls this; resetting
        // would silently drop demo mode and every later read would fail with
        // "OBD connection not established". setupObd/setupWifi/setupDemo own it.
        disconnectTransports()
        obdConnection = null
    }

    companion object {
        /**
         * Words an ELM327 adapter emits instead of codes ("NO DATA", "SEARCHING...",
         * "STOPPED", "UNABLE TO CONNECT", "?", …). They are filtered so they are
         * never shown as fault codes or sent to the AI backend. No real DTC
         * (P/C/B/U + hex digits) can equal one of these.
         */
        private val ELM_STATUS_TOKENS = setOf(
            "NO", "DATA", "NODATA", "SEARCHING", "STOPPED", "BUS",
            "ERROR", "UNKNOWN", "UNABLE", "TO", "CONNECT", "OK",
            // Clone chatter beyond the classic set: bus/CAN states, voltage
            // resets and timeouts. No real DTC (P/C/B/U + hex) equals these.
            "CAN", "CANERROR", "BUSY", "INIT", "BUSINIT",
            "LOW", "VOLTAGE", "LVRESET", "TIMEOUT", "FCRX"
        )

        fun splitErrors(errors: String): List<String> {
            if (errors.isBlank()) return emptyList()
            return errors.split(Regex("[\\s,>]+"))
                .map { it.trim().trim('.', ':') }
                .filter { it.isNotEmpty() && !isElmStatusToken(it) }
        }

        private fun isElmStatusToken(token: String): Boolean {
            val compact = token.filter { it.isLetterOrDigit() }.uppercase()
            return compact.isEmpty() || compact in ELM_STATUS_TOKENS
        }

        fun parseMilStatus(milValue: String, countValue: String): MilStatus {
            val milOn = milValue.toBooleanStrictOrNull() ?: false
            val count = countValue.toIntOrNull()?.coerceAtLeast(0) ?: 0
            return MilStatus(milOn, count)
        }

        /**
         * Tags every code with the read modes that reported it, so the report
         * can show Stored/Pending/Permanent pills instead of one merged list.
         * Codes are normalized to uppercase; blanks are skipped. Pure logic.
         */
        fun mergeCodeSources(
            stored: List<String>,
            pending: List<String>,
            permanent: List<String>
        ): Map<String, Set<DtcSource>> {
            val merged = linkedMapOf<String, MutableSet<DtcSource>>()
            fun add(codes: List<String>, source: DtcSource) {
                for (code in codes) {
                    val key = code.trim().uppercase()
                    if (key.isEmpty()) continue
                    merged.getOrPut(key) { mutableSetOf() }.add(source)
                }
            }
            add(stored, DtcSource.STORED)
            add(pending, DtcSource.PENDING)
            add(permanent, DtcSource.PERMANENT)
            return merged
        }

        /** First decimal number in adapter text ("13.8 V", "12.6V", …) or null. */
        fun parseVoltage(raw: String): Float? =
            Regex("""[-+]?\d+(\.\d+)?""").find(raw)?.value?.toFloatOrNull()

        /**
         * Maps a read failure to the ElmRecovery action. Bus-level failures
         * (lost connection, failed init) deserve a re-init; STOPPED and
         * unknown I/O just retry; NO DATA and unsupported PIDs are healthy
         * unknowns, not errors. Pure logic, tested in AppCoreTest.
         */
        fun recoveryFor(error: Throwable): ElmRecovery = when (error) {
            is UnableToConnectException, is BusInitException -> ElmRecovery.REINIT
            is StoppedException -> ElmRecovery.RETRY
            is NoDataException, is UnSupportedCommandException -> ElmRecovery.NONE
            else -> ElmRecovery.RETRY
        }

        /**
         * Splits one concatenated fast-loop reply ("010C0D0504" asked once)
         * back into per-PID values by reusing the tested single-PID handlers,
         * which find their own identifier anywhere in the frame. Missing
         * frames yield blanks, never exceptions.
         */
        fun parseFastBatch(raw: String): Map<Int, String> {
            val response = ObdRawResponse(raw, 0L)
            return mapOf(
                PidRegistry.PID_RPM to MyRPMCommand().handler(response),
                PidRegistry.PID_SPEED to MySpeedCommand().handler(response),
                PidRegistry.PID_COOLANT_TEMP to MyCoolantTempCommand().handler(response),
                PidRegistry.PID_ENGINE_LOAD to MyEngineLoadCommand().handler(response)
            )
        }
    }

    /**
     * Slow background telemetry for the Online AI (polled ~every 30 s, never in
     * the fast gauge loop): stored fault codes, battery voltage, fuel level.
     * Null = unsupported/unreadable; callers must skip silently.
     */
    suspend fun readSlowTelemetry(): SlowTelemetry = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(300)
            return@withContext SlowTelemetry(DemoObdSource.storedCodes, 13.8f, DemoObdSource.FUEL_PCT)
        }
        val codes = runCatching { splitErrors(runCommand(TroubleCodesCommand()).value) }
            .getOrDefault(emptyList())
        val voltage = runCatching { runCommand(ModuleVoltageCommand()).value }
            .getOrNull()?.let { parseVoltage(it) }
        val fuel = runCatching { runCommand(MyFuelLevelCommand()).value.toIntOrNull() }
            .getOrNull()
        SlowTelemetry(codes, voltage, fuel)
    }

    /**
     * Mode 02 freeze frame: the sensor snapshot the ECU stored when a fault
     * was set. Each PID is read independently; unreadable ones stay null so
     * the UI can say "not available" instead of inventing values.
     */
    suspend fun getFreezeFrame(): FreezeFrame = withContext(Dispatchers.IO) {
        if (demoMode) {
            delay(300)
            return@withContext FreezeFrame(dtc = "P0301", rpm = 2100, speedKmh = 64, coolantC = 91, loadPct = 43)
        }
        suspend fun read(cmd: String): String? = runCatching { sendRaw(cmd, 2500) }.getOrNull()
        FreezeFrame.parse(
            dtcRaw = read("020200"),
            rpmRaw = read("020C00"),
            speedRaw = read("020D00"),
            coolantRaw = read("020500"),
            loadRaw = read("020400")
        )
    }

    suspend fun startLiveDataMonitoring() = withContext(Dispatchers.IO) {
        ObdDataHolder.isMonitoring.set(true)
        if (demoMode) {
            // Demo values come from the user's sliders (LiveDataFragment), not
            // from a loop — seed the starting point and return.
            val (speed, rpm, coolant) = DemoObdSource.liveValuesAt(0)
            ObdDataHolder.speedFlow.value = "$speed Km/h"
            ObdDataHolder.rpmFlow.value = "$rpm RPM"
            ObdDataHolder.coolantTempFlow.value = "$coolant °C"
            ObdDataHolder.engineLoadFlow.value = "${DemoObdSource.engineLoadAt(0)} %"
            return@withContext
        }
        var errorCount = 0
        // Best effort: learn which PIDs exist so later screens can mark the
        // rest unsupported instead of showing invented values. Never fatal.
        runCatching { discoverSupportedPids() }

        while (ObdDataHolder.isMonitoring.get()) {
            try {
                var attempted = 0
                var failed = 0
                var lastError: Exception? = null

                // One PID never kills the cycle: failures are counted per PID
                // (3 strikes disables it via pidHealth) and only a cycle where
                // EVERYTHING failed still trips the global error path below.
                suspend fun poll(pid: Int, command: ObdCommand, publish: (String, String) -> Unit) {
                    if (pidHealth.isDisabled(pid)) return
                    attempted++
                    try {
                        val response = runCommand(command)
                        // Blank = unreadable: keep the last good value (never
                        // invent 0) and count it against the PID's budget.
                        if (response.value.isNotBlank()) {
                            pidHealth.recordSuccess(pid)
                            publish(response.value, response.unit)
                        } else {
                            pidHealth.recordFailure(pid)
                        }
                    } catch (e: Exception) {
                        failed++
                        lastError = e
                        pidHealth.recordFailure(pid)
                        Log.e("ObdHelper", "Live read failed for PID ${pid.toString(16)}:", e)
                    }
                }

                // Fast path: all four gauges in one round-trip ("010C0D0504").
                // Adapters that reject concatenated PIDs just fail here and
                // the single reads below run instead. Disabled PIDs are
                // skipped in both paths.
                val batchValues = runCatching { sendRaw("010C0D0504", 1500) }
                    .mapCatching { parseFastBatch(it) }.getOrNull()
                var batchGood = 0
                if (batchValues != null) {
                    fun takeBatch(pid: Int, unit: String, publish: (String) -> Unit) {
                        if (pidHealth.isDisabled(pid)) return
                        val value = batchValues[pid].orEmpty()
                        if (value.isNotBlank()) {
                            pidHealth.recordSuccess(pid)
                            publish("$value $unit")
                            batchGood++
                        } else {
                            pidHealth.recordFailure(pid)
                        }
                    }
                    takeBatch(PidRegistry.PID_SPEED, MySpeedCommand().defaultUnit) {
                        ObdDataHolder.speedFlow.value = it
                    }
                    takeBatch(PidRegistry.PID_RPM, MyRPMCommand().defaultUnit) {
                        ObdDataHolder.rpmFlow.value = it
                    }
                    takeBatch(PidRegistry.PID_COOLANT_TEMP, MyCoolantTempCommand().defaultUnit) {
                        ObdDataHolder.coolantTempFlow.value = it
                    }
                    takeBatch(PidRegistry.PID_ENGINE_LOAD, MyEngineLoadCommand().defaultUnit) {
                        ObdDataHolder.engineLoadFlow.value = it
                    }
                }
                if (batchGood == 0) {
                    poll(PidRegistry.PID_SPEED, MySpeedCommand()) { value, unit ->
                        ObdDataHolder.speedFlow.value = "$value $unit"
                    }
                    poll(PidRegistry.PID_RPM, MyRPMCommand()) { value, unit ->
                        ObdDataHolder.rpmFlow.value = "$value $unit"
                    }
                    poll(PidRegistry.PID_COOLANT_TEMP, MyCoolantTempCommand()) { value, unit ->
                        ObdDataHolder.coolantTempFlow.value = "$value $unit"
                    }
                    poll(PidRegistry.PID_ENGINE_LOAD, MyEngineLoadCommand()) { value, unit ->
                        ObdDataHolder.engineLoadFlow.value = "$value $unit"
                    }
                }

                if (attempted > 0 && failed == attempted) {
                    throw lastError ?: IOException("OBD read failed.")
                }

                errorCount = 0
                delay(800)

            } catch (e: Exception) {
                errorCount++
                Log.e("ObdHelper", "Error during live data monitoring (Attempt $errorCount):", e)

                // Bus-level failures get one best-effort re-init so the next
                // cycle does not fail the same way; the 3-strike rule below
                // still gives up on a truly dead link.
                if (!demoMode && isConnected && recoveryFor(e) == ElmRecovery.REINIT) {
                    runCatching { initializeObd() }
                        .onFailure { Log.w("ObdHelper", "Recovery re-init failed", it) }
                }

                if (errorCount >= 3) {
                    ObdDataHolder.isMonitoring.set(false)
                    ObdDataHolder.speedFlow.value = "ERROR"
                    ObdDataHolder.rpmFlow.value = "ERROR"
                    ObdDataHolder.coolantTempFlow.value = "ERROR"
                    ObdDataHolder.engineLoadFlow.value = "ERROR"
                    break
                }
                delay(1000)
            }
        }
    }

    fun stopLiveDataMonitoring() {
        ObdDataHolder.isMonitoring.set(false)
    }
}

/** A selectable AI backend for DTC assessments. */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    /** Cloud providers refuse keyless calls; local/custom ones may allow them. */
    val needsKey: Boolean,
    val showBaseUrl: Boolean
) {
    OPENAI(
        "openai", "OpenAI",
        // No free API tier; gpt-4o-mini is the cheapest general-purpose model.
        "gpt-4o-mini", "https://api.openai.com/v1",
        needsKey = true, showBaseUrl = false
    ),
    GEMINI(
        "gemini", "Google Gemini",
        // Free tier via AI Studio (no card). 2.5-flash retires Oct 2026,
        // so default to the free-tier 3.x Flash-Lite line.
        "gemini-3.1-flash-lite", "https://generativelanguage.googleapis.com/v1beta",
        needsKey = true, showBaseUrl = false
    ),
    ANTHROPIC(
        "anthropic", "Anthropic",
        // Paid-only API; Haiku is the cheapest Claude tier.
        "claude-haiku-4-5", "https://api.anthropic.com/v1",
        needsKey = true, showBaseUrl = false
    ),
    CUSTOM(
        "custom", "Custom (OpenAI-compatible)",
        // Local servers (Ollama, LM Studio, …) are free; llama3.2 runs on modest hardware.
        "llama3.2", "",
        needsKey = false, showBaseUrl = true
    );

    companion object {
        fun fromId(id: String?): AiProvider =
            entries.firstOrNull { it.id == id } ?: OPENAI
    }
}

data class AiConfig(
    val provider: AiProvider,
    val apiKey: String,
    val model: String,
    val baseUrl: String
)

/**
 * Fault-code explanations from any chat-capable AI. The API key is used
 * ONLY here: driving-voice cues (Offline AI) are fully offline (bundled clips
 * + on-device TTS) and never touch the network. Cloud providers and local
 * OpenAI-compatible servers (Ollama, LM Studio, llama.cpp, OpenRouter, …)
 * are covered; each provider speaks its native HTTP API directly so no
 * vendor SDK is needed.
 */
class AiService(private val context: Context) {

    fun getConfig(): AiConfig {
        val prefs = context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val provider = AiProvider.fromId(prefs.getString(PrefsKeys.AI_PROVIDER, null))
        val apiKey = prefs.getString(PrefsKeys.OPENAI_API_KEY, "").orEmpty().trim()
        val model = prefs.getString(PrefsKeys.OPENAI_MODEL_ID, "").orEmpty().trim()
            .ifEmpty { provider.defaultModel }
        val baseUrl = prefs.getString(PrefsKeys.AI_BASE_URL, "").orEmpty().trim()
            .ifEmpty { provider.defaultBaseUrl }
        return AiConfig(provider, apiKey, model, baseUrl)
    }

    fun hasApiKey(): Boolean {
        val config = getConfig()
        return !config.provider.needsKey || config.apiKey.isNotEmpty()
    }

    suspend fun getDtpCodeAssessment(dtpCode: String): DtpCodeDTO = withContext(Dispatchers.IO) {
        val config = getConfig()
        if (config.provider.needsKey && config.apiKey.isEmpty()) {
            throw IllegalStateException("No API key configured. Set one in Settings.")
        }
        if (config.provider.showBaseUrl && config.baseUrl.isEmpty()) {
            throw IllegalStateException("No server URL configured. Set one in Settings.")
        }
        val assessmentJson = when (config.provider) {
            AiProvider.OPENAI, AiProvider.CUSTOM -> postChatCompletions(config, dtpCode)
            AiProvider.GEMINI -> postGemini(config, dtpCode)
            AiProvider.ANTHROPIC -> postAnthropic(config, dtpCode)
        }
        parseErrorInfo(assessmentJson)
    }

    /**
     * Free-form chat for the Online AI driving assistant. Returns plain
     * speakable text (never JSON), trimmed to a safe length.
     */
    suspend fun chatText(systemPrompt: String, userText: String, maxTokens: Int = 160): String =
        withContext(Dispatchers.IO) {
            val config = getConfig()
            if (config.provider.needsKey && config.apiKey.isEmpty()) {
                throw IllegalStateException("No API key configured. Set one in Settings.")
            }
            if (config.provider.showBaseUrl && config.baseUrl.isEmpty()) {
                throw IllegalStateException("No server URL configured. Set one in Settings.")
            }
            val raw = when (config.provider) {
                AiProvider.OPENAI, AiProvider.CUSTOM ->
                    parseOpenAiCompatibleResponse(postChatText(config, systemPrompt, userText, maxTokens))
                AiProvider.GEMINI ->
                    parseGeminiResponse(postGeminiText(config, systemPrompt, userText))
                AiProvider.ANTHROPIC ->
                    parseAnthropicResponse(postAnthropicText(config, systemPrompt, userText, maxTokens))
            }
            sanitizeSpoken(raw)
        }

    private fun postChatText(config: AiConfig, system: String, user: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", maxTokens)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
            .toString()
        val headers = mutableMapOf("Content-Type" to "application/json")
        if (config.apiKey.isNotEmpty()) {
            headers["Authorization"] = "Bearer ${config.apiKey}"
        }
        return post(config.baseUrl.trimEnd('/') + "/chat/completions", headers, body)
    }

    private fun postGeminiText(config: AiConfig, system: String, user: String): String {
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", org.json.JSONArray()
                .put(JSONObject().put("text", system))))
            .put("contents", org.json.JSONArray()
                .put(JSONObject().put("parts", org.json.JSONArray()
                    .put(JSONObject().put("text", user)))))
            .toString()
        val encodedKey = URLEncoder.encode(config.apiKey, "UTF-8")
        val url = config.baseUrl.trimEnd('/') + "/models/${config.model}:generateContent?key=$encodedKey"
        return post(url, mapOf("Content-Type" to "application/json"), body)
    }

    private fun postAnthropicText(config: AiConfig, system: String, user: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "user").put("content", user)))
            .toString()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "x-api-key" to config.apiKey,
            "anthropic-version" to "2023-06-01"
        )
        return post(config.baseUrl.trimEnd('/') + "/messages", headers, body)
    }

    private fun postChatCompletions(config: AiConfig, dtpCode: String): String {
        val body = JSONObject()
            .put("model", config.model)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                .put(JSONObject().put("role", "user").put("content", dtpCode)))
            .toString()
        val headers = mutableMapOf("Content-Type" to "application/json")
        if (config.apiKey.isNotEmpty()) {
            headers["Authorization"] = "Bearer ${config.apiKey}"
        }
        val url = config.baseUrl.trimEnd('/') + "/chat/completions"
        return parseOpenAiCompatibleResponse(post(url, headers, body))
    }

    private fun postGemini(config: AiConfig, dtpCode: String): String {
        val body = JSONObject()
            .put("contents", org.json.JSONArray()
                .put(JSONObject().put("parts", org.json.JSONArray()
                    .put(JSONObject().put("text", "$SYSTEM_PROMPT\n\n$dtpCode")))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            .toString()
        val encodedKey = URLEncoder.encode(config.apiKey, "UTF-8")
        val url = config.baseUrl.trimEnd('/') + "/models/${config.model}:generateContent?key=$encodedKey"
        return parseGeminiResponse(post(url, mapOf("Content-Type" to "application/json"), body))
    }

    private fun postAnthropic(config: AiConfig, dtpCode: String): String {
        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", 1024)
            .put("system", SYSTEM_PROMPT)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "user").put("content", dtpCode)))
            .toString()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "x-api-key" to config.apiKey,
            "anthropic-version" to "2023-06-01"
        )
        val url = config.baseUrl.trimEnd('/') + "/messages"
        return parseAnthropicResponse(post(url, headers, body))
    }

    private fun post(url: String, headers: Map<String, String>, body: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.requestMethod = "POST"
            connection.doOutput = true
            for ((name, value) in headers) {
                connection.setRequestProperty(name, value)
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty().take(500)
                throw IOException("AI request failed: HTTP $code. $errorBody".trim())
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        internal const val SYSTEM_PROMPT =
            "You are an expert mechanic. Given a plain OBD2 error code, provide a resolution " +
                "in a structured JSON format with fields: 'errorCode', 'severity' (0-Low, 1-Medium, 2-High), " +
                "'title' (max 60 chars), 'detail' (~300 chars), 'implications' (~300 chars), and " +
                "'suggestedActions' (array of strings). Reply with JSON only, no markdown fences."

        internal fun parseErrorInfo(jsonString: String): DtpCodeDTO {
            return try {
                val jsonObject = JSONObject(extractJsonObject(jsonString))
                val errorCode = jsonObject.getString("errorCode")
                val severity = ErrorSeverity.fromInt(jsonObject.getInt("severity"))
                val title = jsonObject.getString("title")
                val detail = jsonObject.getString("detail")
                val implications = jsonObject.getString("implications")
                val actionsArray = jsonObject.getJSONArray("suggestedActions")
                val suggestedActions = (0 until actionsArray.length()).map { actionsArray.getString(it) }
                DtpCodeDTO(errorCode, severity, title, detail, implications, suggestedActions)
            } catch (e: Exception) {
                Log.e("AiService", "Failed to parse JSON response: $jsonString", e)
                DtpCodeDTO("Error", ErrorSeverity.LOW, "Parsing Error", "Could not parse server response.", "Invalid data.", listOf("Try again."))
            }
        }

        /**
         * Local/chatty models often wrap JSON in ``` fences or prose. Extract the
         * outermost {...} so strict models and chatty ones both parse.
         */
        internal fun extractJsonObject(text: String): String {
            val unfenced = text.replace("```json", "").replace("```", "").trim()
            val start = unfenced.indexOf('{')
            val end = unfenced.lastIndexOf('}')
            return if (start >= 0 && end > start) unfenced.substring(start, end + 1) else text
        }

        /**
         * Collapses model chatter into one speakable line: strips markdown
         * artifacts, squeezes whitespace, caps length (cost + TTS sanity).
         */
        internal fun sanitizeSpoken(text: String, maxChars: Int = 400): String {
            var clean = text.replace("```", "")
            clean = clean.replace(Regex("[*_#>`]"), "")
            clean = clean.replace(Regex("\\s+"), " ").trim()
            return if (clean.length > maxChars) clean.take(maxChars).trimEnd() + "…" else clean
        }

        internal fun parseOpenAiCompatibleResponse(jsonString: String): String {
            return try {
                JSONObject(jsonString)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
            } catch (e: Exception) {
                Log.e("AiService", "Failed to parse chat-completions response", e)
                "{}"
            }
        }

        internal fun parseGeminiResponse(jsonString: String): String {
            return try {
                JSONObject(jsonString)
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text")
            } catch (e: Exception) {
                Log.e("AiService", "Failed to parse Gemini response", e)
                "{}"
            }
        }

        internal fun parseAnthropicResponse(jsonString: String): String {
            return try {
                JSONObject(jsonString)
                    .getJSONArray("content").getJSONObject(0).getString("text")
            } catch (e: Exception) {
                Log.e("AiService", "Failed to parse Anthropic response", e)
                "{}"
            }
        }

        /**
         * Offline fallback when no API key is set or a network request fails.
         * With a [DtcInfo] entry (bundled dictionary) the answer is specific;
         * without one it decodes only what the code letters guarantee (system
         * + generic/manufacturer origin per SAE J2012) so the report stays
         * useful without inventing details.
         */
        fun buildOfflineAssessment(rawCode: String, dict: DtcInfo? = null): DtpCodeDTO {
            val code = rawCode.trim().uppercase()
            if (dict != null) {
                return DtpCodeDTO(
                    errorCode = code,
                    severity = ErrorSeverity.MEDIUM,
                    title = dict.title,
                    detail = "Offline info: ${dict.detail}",
                    implications = dict.implications,
                    suggestedActions = dict.actions,
                    offline = true
                )
            }
            val system = when (code.firstOrNull()) {
                'P' -> "Powertrain (engine, transmission, emissions)"
                'C' -> "Chassis (ABS, steering, suspension)"
                'B' -> "Body (airbags, lighting, comfort electronics)"
                'U' -> "Network (ECU communication)"
                else -> "Unknown system"
            }
            val origin = when (code.getOrNull(1)) {
                '0', '2' -> "Generic (SAE standard)"
                '1' -> "Manufacturer-specific"
                '3' -> "Reserved range"
                else -> "Unknown origin"
            }
            return DtpCodeDTO(
                errorCode = code,
                severity = ErrorSeverity.MEDIUM,
                title = "$code · ${system.substringBefore(" (")} fault",
                detail = "Offline info: $code is an $origin code in the $system area. " +
                    "Add an AI API key in Settings for a full assessment.",
                implications = "If the engine light is on or you notice symptoms, have the " +
                    "vehicle checked and quote code $code.",
                suggestedActions = listOf(
                    "Note code $code and re-scan after a drive cycle to see if it returns",
                    "Add an AI API key in Settings for detailed guidance",
                    "Ask a mechanic, quoting code $code"
                ),
                offline = true
            )
        }

        /**
         * System prompt for the "Ask AI" follow-up chat on the fault-detail
         * screen. It replays the assessment the user already saw plus the
         * vehicle state, so follow-up answers build on that context instead
         * of starting from zero. Pure logic, tested in AppCoreTest.
         */
        internal fun buildFollowUpSystemPrompt(
            dto: DtpCodeDTO,
            mil: MilStatus?,
            vin: String?
        ): String {
            val lamp = when {
                mil == null -> "The engine light state is unknown."
                mil.milOn -> "The engine light is ON (${mil.storedCount} stored code(s) reported by ECU)."
                else -> "The engine light is OFF."
            }
            val vinLine = if (!vin.isNullOrBlank()) " VIN $vin." else ""
            val actions = dto.suggestedActions.joinToString("; ").ifEmpty { "none listed" }
            return "You are an expert mechanic talking to an ordinary driver about their car. " +
                "The driver already received this diagnosis for fault code ${dto.errorCode} " +
                "(severity ${dto.severity}, titled ${dto.title}): " +
                "Detail: ${dto.detail} What it can mean: ${dto.implications} " +
                "Suggested actions: $actions. " +
                "Vehicle context: $lamp$vinLine " +
                "Answer the follow-up question in plain language, briefly, in a few short sentences. " +
                "Stay consistent with the diagnosis above and never invent new fault codes. " +
                "If the question is about safety or whether to keep driving, err on caution " +
                "and recommend a qualified mechanic when the fault may be serious."
        }

        /**
         * User message for one follow-up turn: a compact recap of the
         * assessment, the most recent exchanges (capped so long chats stay
         * cheap), then the new question. Pure logic, tested in AppCoreTest.
         */
        internal fun buildFollowUpUserText(
            dto: DtpCodeDTO,
            history: List<Pair<String, String>>,
            question: String,
            maxExchanges: Int = 3
        ): String {
            val recap = "Fault ${dto.errorCode}: ${dto.title}. ${dto.detail} " +
                "Implications: ${dto.implications}"
            val recent = history.takeLast(maxExchanges).joinToString("\n") { (q, a) ->
                "Q: $q\nA: $a"
            }
            return if (recent.isEmpty()) {
                "$recap\nNew question: $question\nAnswer it."
            } else {
                "$recap\nPrevious questions and answers (most recent last):\n$recent\n" +
                    "New question: $question\nAnswer it."
            }
        }
    }

    /**
     * One "Ask AI" follow-up turn about an already-assessed fault code.
     * Returns plain text (never JSON). Throws when the brain is unconfigured
     * or the network fails, so the UI can explain instead of guessing.
     */
    suspend fun askFollowUp(
        dto: DtpCodeDTO,
        mil: MilStatus?,
        vin: String?,
        history: List<Pair<String, String>>,
        question: String
    ): String = chatText(
        buildFollowUpSystemPrompt(dto, mil, vin),
        buildFollowUpUserText(dto, history, question),
        maxTokens = 300
    )
}

// =================================================================================
// ONLINE AI — online diagnostic assistant (separate from the Offline AI cues)
// =================================================================================

/**
 * Slow background telemetry for the Online AI (polled ~every 30 s, never in
 * the fast gauge loop): stored fault codes, battery voltage, fuel level.
 * Null = unsupported/unreadable; callers must skip silently.
 */
data class SlowTelemetry(
    val codes: List<String>,
    val voltageV: Float?,
    val fuelPct: Int?
)

/** Readings the Online AI reasons about at one moment; null = unknown. */
data class OnlineAiSnapshot(
    val speedKmh: Int?,
    val rpm: Int?,
    val coolantC: Int?,
    val engineLoadPct: Int?,
    val voltageV: Float?,
    val fuelPct: Int?,
    val codes: List<String>
)


/** One queued spoken reply. */
data class QueuedSpeech(
    val text: String,
    val severity: OnlineAiSeverity
)

/**
 * Tiny bounded voice queue: FAULT occasions evict queued INFO lines, and an
 * over-full queue drops the lowest severity first — never the newest FAULT.
 */
class SpeechQueue(private val capacity: Int = 3) {
    private val items = ArrayDeque<QueuedSpeech>()

    val size: Int get() = items.size

    fun offer(utterance: QueuedSpeech) {
        if (utterance.severity == OnlineAiSeverity.FAULT) {
            items.removeAll { it.severity == OnlineAiSeverity.INFO }
        }
        items.addLast(utterance)
        while (items.size > capacity) {
            val dropAt = items.indices.minByOrNull { items[it].severity.ordinal } ?: 0
            items.removeAt(dropAt)
        }
    }

    fun poll(): QueuedSpeech? = items.removeFirstOrNull()

    fun clear() {
        items.clear()
    }
}

/**
 * The Online AI's event/decision layer + brain caller. Offline AI (scripted MP3 cues)
 * is a separate system and stays untouched: this manager only turns
 * *meaningful changes* into AI-generated explanations.
 *
 * Constructed per Live Data screen; call the onX entry points from the
 * telemetry collectors (main thread) and [stop] when leaving. All network
 * failures back off silently — the dashboard and Offline AI keep working.
 */
// =================================================================================
// ONLINE AI PERSONALITY — brain-generated companion reactions (Online AI only).
// Unlike Offline AI's static joke pools, these occasions only describe WHEN
// something may be said and in what style; the WORDS are generated live by
// the AI brain from the actual readings. Everything routes through the
// normal INFO-severity card + voice queue, so safety ordering, output
// switches, cooldowns and costs stay governed by the existing machinery.
// To add/moderate community lines, edit styleExamples below (or remove an
// occasion from OCCASIONS); the manager needs no changes.
// =================================================================================

object OnlineAiPersonality {
    enum class Rarity { COMMON, UNCOMMON, RARE, LEGENDARY }

    data class PersonalityOccasion(
        val key: String,
        val rarity: Rarity,
        val probability: Float,
        val cooldownMs: Long,
        val styleExamples: List<String>
    )

    const val SPEED_130 = "speed_130"
    const val SPEED_140 = "speed_140"
    const val SPEED_150 = "speed_150"
    const val RPM_HIGH = "rpm_high"
    const val RPM_SPIKE = "rpm_spike"
    const val COOLANT_WARM = "coolant_warm"
    const val FAULT_NEW = "fault_new"
    const val FAULT_RETURN = "fault_return"
    const val COMBO_SPEED_RPM = "combo_speed_rpm"
    const val COMBO_SPEED_FAULT = "combo_speed_fault"
    const val COMBO_RPM_COOLANT = "combo_rpm_coolant"
    const val COMBO_CALM_HOT = "combo_calm_hot"
    const val ULTRA = "ultra"

    val OCCASIONS: Map<String, PersonalityOccasion> = mapOf(
        SPEED_130 to PersonalityOccasion(
            SPEED_130, Rarity.COMMON, 0.30f, 10 * 60_000L,
            listOf(
                "Okay, I see you.",
                "Someone's in a hurry today.",
                "Yo, speed racer.",
                "Alright, we're moving.",
                "Okay… I noticed that.",
                "Bro really likes speed."
            )
        ),
        SPEED_140 to PersonalityOccasion(
            SPEED_140, Rarity.UNCOMMON, 0.30f, 15 * 60_000L,
            listOf(
                "Okay, we're getting serious now.",
                "I see what you're doing.",
                "Someone woke up today.",
                "Alright, racer.",
                "Okay, I respect the enthusiasm."
            )
        ),
        SPEED_150 to PersonalityOccasion(
            SPEED_150, Rarity.RARE, 0.20f, 25 * 60_000L,
            listOf(
                "Okay… we're really doing this.",
                "Ladies and gentlemen, we have a racer.",
                "And there he goes.",
                "I have officially noticed the speed.",
                "Okay, that escalated quickly.",
                "You really said full send."
            )
        ),
        RPM_HIGH to PersonalityOccasion(
            RPM_HIGH, Rarity.UNCOMMON, 0.30f, 12 * 60_000L,
            listOf(
                "Okay, she's singing.",
                "That engine is definitely awake.",
                "Someone's having fun with the throttle.",
                "Yeah, those revs are getting serious.",
                "The engine has entered the conversation.",
                "Okay… I heard that.",
                "She's really singing today."
            )
        ),
        RPM_SPIKE to PersonalityOccasion(
            RPM_SPIKE, Rarity.UNCOMMON, 0.35f, 12 * 60_000L,
            listOf(
                "Whoa, that was a jump.",
                "Okay, where did those revs come from?",
                "That escalated quickly.",
                "Someone got excited."
            )
        ),
        COOLANT_WARM to PersonalityOccasion(
            COOLANT_WARM, Rarity.UNCOMMON, 0.30f, 15 * 60_000L,
            listOf(
                "Yeah… she's getting a little warm.",
                "The engine is having a hot day.",
                "Things are getting toasty.",
                "Someone turned up the heat.",
                "I think the engine would like some air conditioning.",
                "Yeah… that's definitely warmer than I'd like.",
                "The engine is not exactly chilling right now."
            )
        ),
        FAULT_NEW to PersonalityOccasion(
            FAULT_NEW, Rarity.RARE, 0.50f, 20 * 60_000L,
            listOf(
                "Well… that's new.",
                "Uh, we have a new development.",
                "Your car just gave me something interesting.",
                "Okay, I wasn't expecting that.",
                "Looks like we have a new guest.",
                "The car has entered the chat.",
                "Well, that's one way to get my attention.",
                "Apparently, the car has something to say."
            )
        ),
        FAULT_RETURN to PersonalityOccasion(
            FAULT_RETURN, Rarity.RARE, 0.40f, 25 * 60_000L,
            listOf(
                "Oh… you again.",
                "I remember this one.",
                "We meet again.",
                "Apparently, we're doing this again.",
                "Yeah, this looks familiar.",
                "It came back.",
                "I was hoping we were done with this one."
            )
        ),
        COMBO_SPEED_RPM to PersonalityOccasion(
            COMBO_SPEED_RPM, Rarity.RARE, 0.30f, 20 * 60_000L,
            listOf(
                "Okay, somebody's having fun.",
                "The engine is definitely awake now.",
                "I see we're giving the engine some exercise."
            )
        ),
        COMBO_SPEED_FAULT to PersonalityOccasion(
            COMBO_SPEED_FAULT, Rarity.RARE, 0.40f, 20 * 60_000L,
            listOf(
                "Seriously? Right now?",
                "Okay, that's interesting timing.",
                "The car picked an interesting moment to complain."
            )
        ),
        COMBO_RPM_COOLANT to PersonalityOccasion(
            COMBO_RPM_COOLANT, Rarity.UNCOMMON, 0.30f, 15 * 60_000L,
            listOf(
                "We're revving it pretty hard, and the temperature noticed.",
                "The engine is working overtime today."
            )
        ),
        COMBO_CALM_HOT to PersonalityOccasion(
            COMBO_CALM_HOT, Rarity.COMMON, 0.25f, 12 * 60_000L,
            listOf(
                "At least she's keeping her cool.",
                "High revs, calm temperature. Interesting."
            )
        ),
        ULTRA to PersonalityOccasion(
            ULTRA, Rarity.LEGENDARY, 0.02f, 60 * 60_000L,
            listOf(
                "Okay… I definitely noticed that.",
                "I'm not saying anything. I'm just observing.",
                "Interesting choice.",
                "You know what? Fair enough.",
                "I was not expecting that.",
                "Okay, I'll remember that one.",
                "Well… that happened.",
                "I have questions.",
                "The car and I are going to need a meeting.",
                "I'm starting to understand your driving style.",
                "You really like keeping things interesting."
            )
        )
    )

    /** Minimum silence between any two personality reactions. */
    fun globalCooldownMs(frequency: AiFrequency): Long = when (frequency) {
        AiFrequency.LOW -> 20 * 60_000L
        AiFrequency.NORMAL -> 8 * 60_000L
        AiFrequency.HIGH -> 3 * 60_000L
    }

    /** Which rarities may speak at all (quiet modes keep only special moments). */
    fun allowsPersonality(frequency: AiFrequency, rarity: Rarity): Boolean = when (frequency) {
        AiFrequency.LOW -> rarity == Rarity.RARE || rarity == Rarity.LEGENDARY
        AiFrequency.NORMAL -> rarity != Rarity.COMMON
        AiFrequency.HIGH -> true
    }

    /** Speed Easter tier for personality: 0 = none, 1 = 130+, 2 = 140+, 3 = 150+. */
    fun speedTier(kmh: Int): Int = when {
        kmh >= 150 -> 3
        kmh >= 140 -> 2
        kmh >= 130 -> 1
        else -> 0
    }

    fun poolForSpeedTier(tier: Int): String = when (tier) {
        3 -> SPEED_150
        2 -> SPEED_140
        else -> SPEED_130
    }

    /** Sudden throttle stab: jump of at least 1500 RPM between reads. */
    fun isRpmSpike(prevRpm: Int?, rpm: Int): Boolean =
        prevRpm != null && rpm - prevRpm >= 1500

    /** Warm-but-not-alarming coolant band (the 110 °C+ watch stays diagnostic). */
    fun enteredWarmZone(prevC: Int?, nowC: Int?): Boolean {
        if (prevC == null || nowC == null) return false
        return prevC < 100 && nowC >= 100 && nowC < 110
    }

    fun comboSpeedRpm(speedKmh: Int?, rpm: Int?, shiftRpm: Int): Boolean =
        (speedKmh ?: 0) >= 130 && (rpm ?: 0) >= shiftRpm + 1500

    fun comboSpeedFault(speedKmh: Int?): Boolean = (speedKmh ?: 0) >= 120

    fun comboRpmCoolant(rpm: Int?, shiftRpm: Int, coolantC: Int?): Boolean =
        (rpm ?: 0) >= shiftRpm + 1500 && (coolantC ?: 0) >= 100

    fun calmHotRpm(rpm: Int?, shiftRpm: Int, coolantC: Int?): Boolean =
        (rpm ?: 0) >= shiftRpm + 1500 && coolantC != null && coolantC < 100

    /** Probability gate: [random01] must fall below [probability]. */
    fun rollEasterEgg(random01: Float, probability: Float): Boolean =
        random01 in 0f..<probability

    /** Normalized duplicate check so the same generated line never repeats. */
    fun isDuplicateOfRecent(text: String, recent: ArrayDeque<String>): Boolean {
        val norm = text.lowercase().filter { it.isLetterOrDigit() }
        if (norm.isEmpty()) return false
        return recent.any { it.lowercase().filter { c -> c.isLetterOrDigit() } == norm }
    }

    internal fun buildPersonalitySystem(personality: AiPersonality): String {
        val tone = when (personality) {
            AiPersonality.FUNNY -> "Lean into the humor; one light joke per reply at most."
            AiPersonality.PROFESSIONAL -> "Stay witty but restrained and professional. No jokes about faults."
            AiPersonality.NORMAL -> "Be warm with a light playful edge."
        }
        return "You are a funny car companion riding along, reacting to live OBD-II data. " +
            tone +
            " Keep every reply to 25 words or fewer, plain text, one or two short sentences," +
            " no markdown, no JSON, no lists, no emojis." +
            " Sound like a casual American car-enthusiast friend in the passenger seat:" +
            " confident, playful, occasionally sarcastic, never childish." +
            " No profanity, no insults at the driver, never pretend to be human." +
            " For TTS: spell measurements out in words (say 'one thirty' or 'a hundred" +
            " and twenty-one degrees', never digits with symbols like '130' or '121 °C')."
    }

    internal fun buildPersonalityUser(
        occasion: PersonalityOccasion,
        personality: AiPersonality,
        speedKmh: Int?,
        rpm: Int?,
        coolantC: Int?,
        engineLoadPct: Int?,
        codes: List<String>,
        detail: String
    ): String {
        fun fmt(value: Any?, unit: String) = value?.let { "$it $unit" } ?: "unknown"
        val lines = listOf(
            "Situation: $detail",
            "Live readings: speed ${fmt(speedKmh, "km/h")}, rpm ${fmt(rpm, "RPM")}," +
                " coolant ${fmt(coolantC, "degrees Celsius")}," +
                " engine load ${fmt(engineLoadPct, "percent")}," +
                " stored codes: ${codes.ifEmpty { listOf("none") }.joinToString(", ")}",
            "Your persona setting: ${personality.name.lowercase()}."
        )
        val examples = occasion.styleExamples.joinToString("\n") { "- $it" }
        return (lines + listOf(
            "Sound like these (same vibe, fresh words — never copy one exactly):",
            examples,
            "Reply with ONLY the reaction."
        )).joinToString("\n")
    }
}

class OnlineAiManager(
    private val context: Context,
    private val aiService: AiService,
    private val listener: Listener
) {
    interface Listener {
        fun onOnlineAiResponse(text: String, severity: OnlineAiSeverity)
    }

    companion object {
        const val SLOW_POLL_MS = 30_000L
        private const val FAILURE_BACKOFF_MS = 5 * 60_000L
        private const val RPM_HARD_SECONDS = 8L

        const val COOLANT_WATCH_C = 110
        const val COOLANT_WATCH_RELEASE_C = 105
        private const val VOLTAGE_LOW_V = 12.0f
        private const val VOLTAGE_HIGH_V = 15.0f
        private const val FUEL_LOW_PCT = 15
        private const val ENGINE_LOAD_HIGH_PCT = 95
        private const val RPM_HARD_OFFSET = 1500

        /** New vs cleared fault codes between two slow polls. */
        fun dtcDiff(known: Set<String>, current: List<String>): Pair<List<String>, List<String>> {
            val now = current.toSet()
            return Pair((now - known).sorted(), (known - now).sorted())
        }

        /** Early-warning latch below Offline AI's 120 °C critical alarm (110 trip / 105 release). */
        fun nextCoolantWatch(tempC: Int?, watching: Boolean): Boolean = when {
            watching -> (tempC ?: 0) >= COOLANT_WATCH_RELEASE_C
            else -> (tempC ?: 0) >= COOLANT_WATCH_C
        }

        fun voltageBad(voltageV: Float?): Boolean =
            voltageV != null && (voltageV < VOLTAGE_LOW_V || voltageV > VOLTAGE_HIGH_V)

        fun fuelLow(fuelPct: Int?): Boolean =
            fuelPct != null && fuelPct <= FUEL_LOW_PCT

        fun loadHigh(loadPct: Int?): Boolean =
            (loadPct ?: 0) >= ENGINE_LOAD_HIGH_PCT

        /** Frequency gates which severities may speak at all. */
        fun allows(severity: OnlineAiSeverity, frequency: AiFrequency): Boolean = when (frequency) {
            AiFrequency.LOW -> severity == OnlineAiSeverity.FAULT
            AiFrequency.NORMAL -> severity != OnlineAiSeverity.INFO
            AiFrequency.HIGH -> true
        }

        /** Repeat cooldown per severity, stretched (LOW) or halved (HIGH). */
        fun repeatCooldownMs(severity: OnlineAiSeverity, frequency: AiFrequency): Long {
            val base = when (severity) {
                OnlineAiSeverity.FAULT -> 10 * 60_000L
                OnlineAiSeverity.WARNING -> 10 * 60_000L
                OnlineAiSeverity.INFO -> 30 * 60_000L
            }
            return when (frequency) {
                AiFrequency.LOW -> base * 2
                AiFrequency.NORMAL -> base
                AiFrequency.HIGH -> base / 2
            }
        }

        internal fun buildSystemPrompt(personality: AiPersonality): String =
            "You are a smart car companion riding along, watching live OBD-II data. " +
                personality.styleLine() +
                " Keep every reply to 40 words or fewer, plain text, no markdown, no JSON, no lists." +
                " Match the mood to the severity: calm for info, concerned for warnings," +
                " serious for faults. Never be dramatic about normal readings."

        internal fun buildUserText(headline: String, severity: OnlineAiSeverity, snapshot: OnlineAiSnapshot, extra: String): String {
            fun fmt(value: Any?, unit: String) = value?.let { "$it $unit" } ?: "unknown"
            val lines = listOf(
                "Event: $headline",
                "Severity: ${severity.name.lowercase()}",
                "Speed: ${fmt(snapshot.speedKmh, "km/h")}",
                "RPM: ${fmt(snapshot.rpm, "RPM")}",
                "Coolant: ${fmt(snapshot.coolantC, "°C")}",
                "Engine load: ${fmt(snapshot.engineLoadPct, "%")}",
                "Battery: ${fmt(snapshot.voltageV, "V")}",
                "Fuel: ${fmt(snapshot.fuelPct, "%")}",
                "Stored codes: ${snapshot.codes.ifEmpty { listOf("none") }.joinToString(", ")}"
            )
            return (lines + if (extra.isNotBlank()) listOf("Note: $extra") else emptyList())
                .joinToString("\n")
        }
    }

    private fun prefs() =
        context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    private var lastSpeedKmh: Int? = null
    private var lastRpm: Int? = null
    private var lastCoolantC: Int? = null
    private var lastLoadPct: Int? = null
    private var lastVoltageV: Float? = null
    private var lastFuelPct: Int? = null

    private val announcedCodes = mutableSetOf<String>()
    private var codesSeeded = false
    private var coolantWatching = false
    private var rpmHardSinceMs: Long? = null
    private val lastSpokenMs = mutableMapOf<String, Long>()
    private var failureBackoffUntilMs: Long = 0L
    private var stopped = false
    // -- Personality state (independent cooldowns; failures never touch diagnostics)
    private var prevSpeedKmh: Int? = null
    private var prevRpm: Int? = null
    private var lastShiftRpm: Int = 3500
    private var lastPersonalityMs = 0L
    private val personalityCooldowns = mutableMapOf<String, Long>()
    private val recentPersonality = ArrayDeque<String>()
    private var personalityBackoffUntilMs: Long = 0L
    private val clearedCodes = mutableSetOf<String>()

    fun stop() {
        stopped = true
    }

    /** False when the brain cannot generate: cloud provider without a key,
     * or custom provider without a server URL. */
    fun isBrainConfigured(): Boolean {
        if (!aiService.hasApiKey()) return false
        val config = aiService.getConfig()
        return !(config.provider.showBaseUrl && config.baseUrl.isEmpty())
    }

    /** Latest speed only feeds the snapshot; overspeed voice stays Offline AI's job. */
    suspend fun onSpeed(kmh: Float) {
        val prev = prevSpeedKmh
        val now = kmh.toInt()
        lastSpeedKmh = now
        prevSpeedKmh = now
        if (stopped || prev == null) return
        val oldTier = OnlineAiPersonality.speedTier(prev)
        val newTier = OnlineAiPersonality.speedTier(now)
        if (newTier > oldTier && newTier > 0) {
            maybePersonality(
                OnlineAiPersonality.poolForSpeedTier(newTier),
                "Speed reached $now km/h."
            )
            if (OnlineAiPersonality.comboSpeedRpm(now, lastRpm, lastShiftRpm)) {
                maybePersonality(
                    OnlineAiPersonality.COMBO_SPEED_RPM,
                    "Speed $now km/h with RPM ${lastRpm ?: "unknown"}."
                )
            }
        }
    }

    /** Sustained hard running (well above the shift point) earns one WARNING. */
    suspend fun onRpm(rpm: Int, shiftRpm: Int) {
        val prev = prevRpm
        lastRpm = rpm
        prevRpm = rpm
        lastShiftRpm = shiftRpm
        if (stopped) return
        if (prev != null) {
            if (OnlineAiPersonality.isRpmSpike(prev, rpm)) {
                maybePersonality(
                    OnlineAiPersonality.RPM_SPIKE,
                    "RPM jumped from $prev to $rpm."
                )
            }
            if (prev < shiftRpm + 2000 && rpm >= shiftRpm + 2000) {
                maybePersonality(
                    OnlineAiPersonality.RPM_HIGH,
                    "RPM is at $rpm (shift point is $shiftRpm)."
                )
            }
            if (OnlineAiPersonality.comboSpeedRpm(lastSpeedKmh, rpm, shiftRpm) &&
                !OnlineAiPersonality.comboSpeedRpm(lastSpeedKmh, prev, shiftRpm)
            ) {
                // Entered the combo via RPM (speed-side entries are caught in onSpeed).
                maybePersonality(
                    OnlineAiPersonality.COMBO_SPEED_RPM,
                    "Speed ${lastSpeedKmh ?: "unknown"} km/h with RPM $rpm."
                )
            }
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (rpm > shiftRpm + RPM_HARD_OFFSET) {
            val since = rpmHardSinceMs ?: now.also { rpmHardSinceMs = it }
            if (now - since >= RPM_HARD_SECONDS * 1000L) {
                rpmHardSinceMs = now // re-arm the window instead of firing every read
                generate(
                    kind = "rpm_hard",
                    severity = OnlineAiSeverity.WARNING,
                    headline = "Engine has been running hard at $rpm RPM for a while",
                    extra = "Shift point is $shiftRpm RPM."
                )
            }
        } else {
            rpmHardSinceMs = null
        }
    }

    /** Early coolant watch; the 120 °C critical alarm stays Offline AI's scripted job. */
    suspend fun onCoolant(tempC: Int?) {
        val prev = lastCoolantC
        lastCoolantC = tempC
        if (stopped) return
        val watching = nextCoolantWatch(tempC, coolantWatching)
        if (watching && !coolantWatching) {
            generate(
                kind = "coolant_watch",
                severity = OnlineAiSeverity.WARNING,
                headline = "Coolant is climbing at ${tempC ?: "?"} °C",
                extra = "Below the critical alarm; advise watching it, not panic."
            )
        }
        coolantWatching = watching
        if (OnlineAiPersonality.enteredWarmZone(prev, tempC)) {
            maybePersonality(
                OnlineAiPersonality.COOLANT_WARM,
                "Coolant is at ${tempC ?: "?"} degrees and climbing."
            )
        }
        if (OnlineAiPersonality.comboRpmCoolant(lastRpm, lastShiftRpm, tempC) &&
            !OnlineAiPersonality.comboRpmCoolant(lastRpm, lastShiftRpm, prev)
        ) {
            // Entered the combo via temperature (RPM-side entries are caught in onRpm).
            maybePersonality(
                OnlineAiPersonality.COMBO_RPM_COOLANT,
                "RPM ${lastRpm ?: "unknown"} with coolant at ${tempC ?: "?"} degrees."
            )
        }
        if (OnlineAiPersonality.calmHotRpm(lastRpm, lastShiftRpm, tempC) &&
            !OnlineAiPersonality.calmHotRpm(lastRpm, lastShiftRpm, prev)
        ) {
            maybePersonality(
                OnlineAiPersonality.COMBO_CALM_HOT,
                "RPM ${lastRpm ?: "unknown"} but coolant is only ${tempC ?: "?"} degrees."
            )
        }
    }

    suspend fun onEngineLoad(loadPct: Int?) {
        lastLoadPct = loadPct
        if (stopped || !loadHigh(loadPct)) return
        generate(
            kind = "load_high",
            severity = OnlineAiSeverity.WARNING,
            headline = "Engine load very high at $loadPct%",
            extra = ""
        )
    }

    /** Slow poll: new/cleared codes, voltage and fuel. First poll also reports
     * stored codes once (a drive-start heads-up), then only changes speak. */
    suspend fun onSlowPoll(telemetry: SlowTelemetry) {
        lastVoltageV = telemetry.voltageV
        lastFuelPct = telemetry.fuelPct
        if (stopped) return
        val frequency = AiFrequency.fromPref(prefs().getString(PrefsKeys.AI_FREQUENCY, null))

        if (!codesSeeded) {
            codesSeeded = true
            announcedCodes.addAll(telemetry.codes)
            if (telemetry.codes.isNotEmpty()) {
                generate(
                    kind = "dtc_baseline",
                    severity = OnlineAiSeverity.FAULT,
                    headline = "Stored fault codes at drive start: ${telemetry.codes.joinToString(", ")}",
                    extra = "Brief heads-up on each code, then what to watch for."
                )
            }
        } else {
            val (newCodes, cleared) = dtcDiff(announcedCodes, telemetry.codes)
            clearedCodes.addAll(cleared)
            if (newCodes.isNotEmpty()) {
                announcedCodes.addAll(newCodes)
                generate(
                    kind = "new_dtc",
                    severity = OnlineAiSeverity.FAULT,
                    headline = "New fault code${if (newCodes.size > 1) "s" else ""} detected: ${newCodes.joinToString(", ")}",
                    extra = ""
                )
                val returned = newCodes.filter { it in clearedCodes }
                clearedCodes.removeAll(returned.toSet())
                val brandNew = newCodes - returned.toSet()
                if (returned.isNotEmpty()) {
                    maybePersonality(
                        OnlineAiPersonality.FAULT_RETURN,
                        "Code ${returned.joinToString(", ")} came back after clearing."
                    )
                }
                if (brandNew.isNotEmpty()) {
                    if (OnlineAiPersonality.comboSpeedFault(lastSpeedKmh)) {
                        maybePersonality(
                            OnlineAiPersonality.COMBO_SPEED_FAULT,
                            "New code ${brandNew.joinToString(", ")} while doing " +
                                "${lastSpeedKmh ?: "unknown"} km/h."
                        )
                    } else {
                        maybePersonality(
                            OnlineAiPersonality.FAULT_NEW,
                            "New fault code detected: ${brandNew.joinToString(", ")}."
                        )
                    }
                }
            }
            if (cleared.isNotEmpty()) {
                announcedCodes.removeAll(cleared.toSet())
                generate(
                    kind = "dtc_cleared",
                    severity = OnlineAiSeverity.INFO,
                    headline = "These codes cleared: ${cleared.joinToString(", ")}",
                    extra = "Confirm the fix looks real, in one short sentence."
                )
            }
        }

        if (voltageBad(telemetry.voltageV)) {
            generate(
                kind = "voltage",
                severity = OnlineAiSeverity.WARNING,
                headline = "Battery voltage abnormal at ${telemetry.voltageV} V",
                extra = "Below 12 V suggests charging trouble; above 15 V suggests overcharging."
            )
        }
        if (fuelLow(telemetry.fuelPct)) {
            generate(
                kind = "fuel_low",
                severity = if (frequency == AiFrequency.HIGH) OnlineAiSeverity.INFO else OnlineAiSeverity.WARNING,
                headline = "Fuel level low at ${telemetry.fuelPct}%",
                extra = ""
            )
        }
    }

    private suspend fun generate(kind: String, severity: OnlineAiSeverity, headline: String, extra: String) {
        if (stopped) return
        val prefs = prefs()
        if (!prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)) return
        val frequency = AiFrequency.fromPref(prefs.getString(PrefsKeys.AI_FREQUENCY, null))
        if (!allows(severity, frequency)) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now < failureBackoffUntilMs) return // fail silent, retry later — never nag
        val last = lastSpokenMs[kind] ?: 0L
        if (now - last < repeatCooldownMs(severity, frequency)) return
        val personality = AiPersonality.fromPref(prefs.getString(PrefsKeys.AI_PERSONALITY, null))
        val snapshot = OnlineAiSnapshot(
            lastSpeedKmh, lastRpm, lastCoolantC, lastLoadPct,
            lastVoltageV, lastFuelPct, announcedCodes.sorted()
        )
        val text = try {
            aiService.chatText(
                buildSystemPrompt(personality),
                buildUserText(headline, severity, snapshot, extra)
            )
        } catch (e: Exception) {
            Log.e("OnlineAiManager", "AI generation failed; backing off", e)
            failureBackoffUntilMs = now + FAILURE_BACKOFF_MS
            return
        }
        if (text.isBlank()) return
        lastSpokenMs[kind] = now
        listener.onOnlineAiResponse(text, severity)
    }

    /**
     * Personality reaction gate (Online AI only). Runs AFTER any diagnostic
     * handling at the same trigger so warnings always go first. Cooldowns are
     * recorded when an API call is committed to, so near-simultaneous
     * triggers from two collectors can't double-spend.
     */
    private suspend fun maybePersonality(occasionKey: String, detail: String) {
        if (stopped) return
        val prefs = prefs()
        if (!prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)) return
        if (!isBrainConfigured()) return
        val frequency = AiFrequency.fromPref(prefs.getString(PrefsKeys.AI_FREQUENCY, null))
        val personality = AiPersonality.fromPref(prefs.getString(PrefsKeys.AI_PERSONALITY, null))
        var occasion = OnlineAiPersonality.OCCASIONS[occasionKey] ?: return
        if (!OnlineAiPersonality.allowsPersonality(frequency, occasion.rarity)) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now < personalityBackoffUntilMs) return
        if (now - lastPersonalityMs < OnlineAiPersonality.globalCooldownMs(frequency)) return
        if (now - (personalityCooldowns[occasion.key] ?: 0L) < occasion.cooldownMs) return
        if (!rollGate(occasion.probability)) return
        // Ultra-rare substitution: same slot, its own gates, still just one call.
        val ultra = OnlineAiPersonality.OCCASIONS[OnlineAiPersonality.ULTRA]!!
        if (OnlineAiPersonality.allowsPersonality(frequency, ultra.rarity) &&
            now - (personalityCooldowns[ultra.key] ?: 0L) >= ultra.cooldownMs &&
            rollGate(ultra.probability)
        ) {
            occasion = ultra
        }
        lastPersonalityMs = now
        personalityCooldowns[occasion.key] = now
        val snapshot = OnlineAiSnapshot(
            lastSpeedKmh, lastRpm, lastCoolantC, lastLoadPct,
            lastVoltageV, lastFuelPct, announcedCodes.sorted()
        )
        val (system, user) = Pair(
            OnlineAiPersonality.buildPersonalitySystem(personality),
            OnlineAiPersonality.buildPersonalityUser(
                occasion, personality,
                snapshot.speedKmh, snapshot.rpm, snapshot.coolantC, snapshot.engineLoadPct,
                snapshot.codes, detail
            )
        )
        val text = try {
            aiService.chatText(system, user, 80)
        } catch (e: Exception) {
            Log.e("OnlineAiManager", "Personality generation failed; backing off", e)
            personalityBackoffUntilMs = now + FAILURE_BACKOFF_MS
            return
        }
        if (text.isBlank()) return
        if (OnlineAiPersonality.isDuplicateOfRecent(text, recentPersonality)) return
        recentPersonality.addLast(text.lowercase().filter { it.isLetterOrDigit() })
        while (recentPersonality.size > 8) recentPersonality.removeFirst()
        // INFO severity: queued behind everything, preempted by anything.
        listener.onOnlineAiResponse(text, OnlineAiSeverity.INFO)
    }

    private fun rollGate(probability: Float): Boolean =
        OnlineAiPersonality.rollEasterEgg(kotlin.random.Random.Default.nextFloat(), probability)
}
