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
    }

    @Test
    fun `secondary screens highlight nothing`() {
        assertNull(AppNav.sectionFor(R.id.consoleFragment))
        assertNull(AppNav.sectionFor(R.id.settingsFragment))
        assertNull(AppNav.sectionFor(R.id.aboutFragment))
        assertNull(AppNav.sectionFor(R.id.legalFragment))
    }

    @Test
    fun `disconnected app hides chrome everywhere including home`() {
        // Progressive disclosure: with no connection and no demo, every tab
        // leads to a screen that cannot work — so the bar never shows.
        for (id in listOf(
            R.id.onboardingFragment,
            R.id.liveDataFragment,
            R.id.errorOverviewFragment,
            R.id.tripFragment
        )) {
            assertTrue("dest $id should hide chrome when disconnected", AppNav.isChromeHidden(id, linked = false))
        }
    }

    @Test
    fun `linked app shows chrome on data screens only`() {
        // Connected/demo: data sections show chrome, transient flows hide it.
        assertFalse(AppNav.isChromeHidden(R.id.onboardingFragment, linked = true))
        assertFalse(AppNav.isChromeHidden(R.id.liveDataFragment, linked = true))
        assertTrue(AppNav.isChromeHidden(R.id.setupFragment, linked = true))
        assertTrue(AppNav.isChromeHidden(R.id.connectFragment, linked = true))
        // Default param keeps the old call sites working (linked = true).
        assertTrue(AppNav.isChromeHidden(R.id.permissionsFragment))
    }

    @Test
    fun `cards mode hides chrome everywhere regardless of state`() {
        // Hub-and-spoke navigation: the user chose cards at first start, so
        // the bar never shows — even connected, even on data screens.
        for (id in listOf(
            R.id.onboardingFragment,
            R.id.liveDataFragment,
            R.id.errorOverviewFragment,
            R.id.tripFragment
        )) {
            assertTrue(
                "dest $id should hide chrome in cards mode",
                AppNav.isChromeHidden(id, linked = true, cardsMode = true)
            )
        }
        // Cards mode does not rescue a disconnected app either.
        assertTrue(AppNav.isChromeHidden(R.id.liveDataFragment, linked = false, cardsMode = true))
    }

    @Test
    fun `welcome root setup screens and unknown destinations highlight nothing`() {
        assertEquals(R.id.onboardingFragment, AppNav.sectionFor(R.id.onboardingFragment))
        assertNull(AppNav.sectionFor(R.id.setupFragment))
        assertNull(AppNav.sectionFor(R.id.permissionsFragment))
        assertNull(AppNav.sectionFor(R.id.connectFragment))
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
    fun `chrome hides on transient setup screens only`() {
        assertFalse(AppNav.isChromeHidden(R.id.onboardingFragment))
        assertTrue(AppNav.isChromeHidden(R.id.setupFragment))
        assertTrue(AppNav.isChromeHidden(R.id.permissionsFragment))
        assertTrue(AppNav.isChromeHidden(R.id.connectFragment))
        assertFalse(AppNav.isChromeHidden(R.id.settingsFragment))
        assertFalse(AppNav.isChromeHidden(R.id.liveDataFragment))
    }
}
