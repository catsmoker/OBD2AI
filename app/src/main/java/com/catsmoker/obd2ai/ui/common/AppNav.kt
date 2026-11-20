package com.catsmoker.obd2ai.ui.common

import com.catsmoker.obd2ai.prefs.NavStyle

/**
 * Single source of truth for top-level navigation state, now route-based:
 * the single-activity Compose app navigates by route string, and the
 * bottom dock / navigation rail read this for visibility and highlight.
 * Pure logic, tested.
 */
object AppNav {
    /** Dock/rail tabs in display order: Console replaced the Home tab. */
    val TAB_ROUTES = listOf("console", "live", "diagnostics", "trip")

    /** Transient setup screens: chrome is hidden while they are visible. */
    private val CHROME_FREE_ROUTES = setOf("setup", "permissions", "connect")

    /**
     * Screens without section chrome. When [linked] is false — no connection
     * and no demo — chrome is hidden everywhere: the tabs all lead to screens
     * that need a connection, so a disconnected app shows only the Home
     * connection card (progressive disclosure). In [cardsMode] the user chose
     * hub-and-spoke navigation at first start: chrome never shows.
     */
    fun isChromeHidden(
        route: String,
        linked: Boolean = true,
        cardsMode: Boolean = false
    ): Boolean = cardsMode || !linked || route in CHROME_FREE_ROUTES

    /**
     * Top-level section for a route, or null when nothing highlights
     * (the welcome root and the secondary screens — settings, about, legal —
     * belong to no tab). Detail screens resolve to their parent section.
     */
    fun sectionFor(route: String): String? = when {
        route == "console" -> "console"
        route == "live" -> "live"
        route == "diagnostics" || route.startsWith("errorDetail/") || route.startsWith("askAi/") ->
            "diagnostics"
        route == "trip" -> "trip"
        else -> null
    }

    /**
     * Visible up-arrow: every chromed screen except the welcome root
     * (nowhere meaningful to go back to).
     */
    fun showBackArrow(route: String): Boolean =
        !isChromeHidden(route) && route != "home"

    /**
     * True when the user chose hub-and-spoke (cards) navigation: Home tiles
     * drive everything and chrome never shows. Pure logic, tested.
     */
    fun isCardsMode(navStyleTag: String?): Boolean =
        NavStyle.fromPref(navStyleTag) == NavStyle.CARDS
}
