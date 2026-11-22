package com.catsmoker.obd2ai.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for theme/language prefs and display-unit math. */
class AppPrefsTest {

    @Test
    fun `ThemeMode fromPref maps known values and defaults to system`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPref("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPref("dark"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref("system"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref("amoled"))
    }

    @Test
    fun `ThemeMode migrates the legacy dark-mode switch`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromLegacyDarkMode(true))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromLegacyDarkMode(false))
    }

    @Test
    fun `AppLanguage fromTag maps known tags and defaults to system`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("es"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar"))
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromTag("zh-CN"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("system"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("fr"))
    }

    @Test
    fun `AppLanguage tags are unique`() {
        val tags = AppLanguage.entries.map { it.tag }
        assertEquals(tags.size, tags.distinct().size)
    }

    @Test
    fun `units convert speed and temperature`() {
        assertEquals(62.1371, Units.kmhToMph(100.0), 0.001)
        assertEquals(32.0, Units.cToF(0.0), 0.001)
        assertEquals(212.0, Units.cToF(100.0), 0.001)
    }

    @Test
    fun `units gauge adapts to imperial`() {
        assertEquals(140f, Units.speedGaugeMax(true))
        assertEquals(220f, Units.speedGaugeMax(false))
        assertEquals("mph", Units.speedUnitLabel(true))
        assertEquals("Km/h", Units.speedUnitLabel(false))
    }

    @Test
    fun `units display helpers pass metric through`() {
        assertEquals(100.0, Units.displaySpeed(100.0, false), 0.0)
        assertEquals(90.0, Units.displayTemp(90.0, false), 0.0)
        assertEquals(Units.kmhToMph(100.0), Units.displaySpeed(100.0, true), 0.0)
        assertEquals(Units.cToF(90.0), Units.displayTemp(90.0, true), 0.0)
    }
}
