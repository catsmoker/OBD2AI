package com.catsmoker.obd2ai.ui.common

import com.catsmoker.obd2ai.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the shared top-level navigation mapping. */
class AppNavTest {

    @Test
    fun `detail screens resolve to the diagnostics section`() {
        assertEquals(R.id.errorOverviewFragment, AppNav.sectionFor(R.id.errorOverviewFragment))
        assertEquals(R.id.errorOverviewFragment, AppNav.sectionFor(R.id.errorDetailFragment))
        assertEquals(R.id.errorOverviewFragment, AppNav.sectionFor(R.id.askAiFragment))
    }

    @Test
    fun `sections highlight only themselves`() {
        assertEquals(R.id.liveDataFragment, AppNav.sectionFor(R.id.liveDataFragment))
        assertEquals(R.id.tripFragment, AppNav.sectionFor(R.id.tripFragment))
        assertEquals(R.id.consoleFragment, AppNav.sectionFor(R.id.consoleFragment))
        assertEquals(R.id.settingsFragment, AppNav.sectionFor(R.id.settingsFragment))
        assertEquals(R.id.aboutFragment, AppNav.sectionFor(R.id.aboutFragment))
    }

    @Test
    fun `welcome root setup screens and unknown destinations highlight nothing`() {
        assertNull(AppNav.sectionFor(R.id.onboardingFragment))
        assertNull(AppNav.sectionFor(R.id.setupFragment))
        assertNull(AppNav.sectionFor(R.id.permissionsFragment))
        assertNull(AppNav.sectionFor(R.id.connectFragment))
        assertNull(AppNav.sectionFor(R.id.legalFragment))
        assertNull(AppNav.sectionFor(-1))
    }

    @Test
    fun `back arrow shows everywhere except the welcome root`() {
        assertFalse(AppNav.showBackArrow(R.id.onboardingFragment))
        assertFalse(AppNav.showBackArrow(R.id.setupFragment))
        assertFalse(AppNav.showBackArrow(R.id.permissionsFragment))
        assertFalse(AppNav.showBackArrow(R.id.connectFragment))
        assertTrue(AppNav.showBackArrow(R.id.settingsFragment))
        assertTrue(AppNav.showBackArrow(R.id.aboutFragment))
        assertTrue(AppNav.showBackArrow(R.id.liveDataFragment))
        assertTrue(AppNav.showBackArrow(R.id.errorDetailFragment))
        assertTrue(AppNav.showBackArrow(R.id.legalFragment))
    }

    @Test
    fun `chrome hides on menu setup and transient setup screens`() {
        assertTrue(AppNav.isChromeHidden(R.id.onboardingFragment))
        assertTrue(AppNav.isChromeHidden(R.id.setupFragment))
        assertTrue(AppNav.isChromeHidden(R.id.permissionsFragment))
        assertTrue(AppNav.isChromeHidden(R.id.connectFragment))
        assertFalse(AppNav.isChromeHidden(R.id.settingsFragment))
        assertFalse(AppNav.isChromeHidden(R.id.liveDataFragment))
    }

    @Test
    fun `phone section buttons hide on menu leaf screens only`() {
        assertFalse(AppNav.showSectionButtons(R.id.settingsFragment))
        assertFalse(AppNav.showSectionButtons(R.id.aboutFragment))
        assertFalse(AppNav.showSectionButtons(R.id.legalFragment))
        assertFalse(AppNav.showSectionButtons(R.id.onboardingFragment))
        assertFalse(AppNav.showSectionButtons(R.id.connectFragment))
        assertTrue(AppNav.showSectionButtons(R.id.liveDataFragment))
        assertTrue(AppNav.showSectionButtons(R.id.errorOverviewFragment))
        assertTrue(AppNav.showSectionButtons(R.id.errorDetailFragment))
        assertTrue(AppNav.showSectionButtons(R.id.askAiFragment))
        assertTrue(AppNav.showSectionButtons(R.id.tripFragment))
        assertTrue(AppNav.showSectionButtons(R.id.consoleFragment))
    }
}
