package com.catsmoker.obd2ai.prefs

/**
 * SharedPreferences keys (`app_prefs.xml`, excluded from backup/transfer).
 * The AI key is entered by the user in Settings and lives only here —
 * never baked into BuildConfig. Legacy `openai_*` key names are reused
 * as-is for migration; don't rename them.
 */
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
    /**
     * Tachometer full-scale range. Single source of truth for the RPM gauge,
     * the RPM readout, the redline visualization and the demo slider —
     * Live and Demo dashboards share it.
     */
    const val RPM_MAX = "rpm_max"
    const val DEFAULT_RPM_MAX = 8000
    const val RPM_MAX_MIN = 1000
    const val RPM_MAX_MAX = 10000
    const val RPM_MAX_STEP = 500
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
    // -- Speedometer style (OBD2 AI 2.0; see SpeedometerStyle) -------------------
    const val SPEEDOMETER_STYLE = "speedometer_style"
    const val SPEEDOMETER_DEFAULT = "classic"
}

/**
 * Display units. Every threshold, detector and AI prompt stays in SI
 * (km/h, °C); only pixels and spoken numbers convert, via [displaySpeed]
 * and [displayTemp]. Pure logic, tested.
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
