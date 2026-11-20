package com.catsmoker.obd2ai.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the shared top-level navigation mapping (route-based). */
class AppNavTest {

    @Test
    fun `detail routes resolve to the diagnostics section`() {
        assertEquals("diagnostics", AppNav.sectionFor("diagnostics"))
        assertEquals("diagnostics", AppNav.sectionFor("errorDetail/P0300"))
        assertEquals("diagnostics", AppNav.sectionFor("askAi/P0420"))
    }

    @Test
    fun `sections highlight only themselves`() {
        assertEquals("live", AppNav.sectionFor("live"))
        assertEquals("trip", AppNav.sectionFor("trip"))
    }

    @Test
    fun `console is a top-level dock tab`() {
        assertEquals("console", AppNav.sectionFor("console"))
    }

    @Test
    fun `secondary screens highlight nothing`() {
        assertNull(AppNav.sectionFor("settings"))
        assertNull(AppNav.sectionFor("about"))
        assertNull(AppNav.sectionFor("legal/terms"))
    }

    @Test
    fun `disconnected app hides chrome everywhere including home`() {
        // Progressive disclosure: with no connection and no demo, every tab
        // leads to a screen that cannot work — so the bar never shows.
        for (route in listOf("home", "live", "diagnostics", "trip")) {
            assertTrue("route $route should hide chrome when disconnected", AppNav.isChromeHidden(route, linked = false))
        }
    }

    @Test
    fun `linked app shows chrome on data screens only`() {
        // Connected/demo: data sections show chrome, transient flows hide it.
        assertFalse(AppNav.isChromeHidden("home", linked = true))
        assertFalse(AppNav.isChromeHidden("live", linked = true))
        assertTrue(AppNav.isChromeHidden("setup", linked = true))
        assertTrue(AppNav.isChromeHidden("connect", linked = true))
        // Default param keeps the old call sites working (linked = true).
        assertTrue(AppNav.isChromeHidden("permissions"))
    }

    @Test
    fun `cards mode hides chrome everywhere regardless of state`() {
        // Hub-and-spoke navigation: the user chose cards at first start, so
        // the bar never shows — even connected, even on data screens.
        for (route in listOf("home", "live", "diagnostics", "trip")) {
            assertTrue(
                "route $route should hide chrome in cards mode",
                AppNav.isChromeHidden(route, linked = true, cardsMode = true)
            )
        }
        // Cards mode does not rescue a disconnected app either.
        assertTrue(AppNav.isChromeHidden("live", linked = false, cardsMode = true))
    }

    @Test
    fun `welcome root setup screens and unknown routes highlight nothing`() {
        assertNull(AppNav.sectionFor("home"))
        assertNull(AppNav.sectionFor("setup"))
        assertNull(AppNav.sectionFor("permissions"))
        assertNull(AppNav.sectionFor("connect"))
        assertNull(AppNav.sectionFor("does-not-exist"))
    }

    @Test
    fun `tab routes are the dock order`() {
        assertEquals(listOf("console", "live", "diagnostics", "trip"), AppNav.TAB_ROUTES)
    }

    @Test
    fun `back arrow shows everywhere except the welcome root`() {
        assertFalse(AppNav.showBackArrow("home"))
        assertFalse(AppNav.showBackArrow("setup"))
        assertFalse(AppNav.showBackArrow("permissions"))
        assertFalse(AppNav.showBackArrow("connect"))
        assertTrue(AppNav.showBackArrow("settings"))
        assertTrue(AppNav.showBackArrow("about"))
        assertTrue(AppNav.showBackArrow("live"))
        assertTrue(AppNav.showBackArrow("errorDetail/P0300"))
        assertTrue(AppNav.showBackArrow("legal/terms"))
    }

    @Test
    fun `cards mode is only the cards tag`() {
        assertTrue(AppNav.isCardsMode("cards"))
        assertFalse(AppNav.isCardsMode("bar"))
        assertFalse(AppNav.isCardsMode(null))
    }
}
