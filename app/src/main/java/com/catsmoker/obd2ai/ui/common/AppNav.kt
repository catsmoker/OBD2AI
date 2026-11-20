package com.catsmoker.obd2ai.ui.common

import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.prefs.NavStyle

/**
 * Single source of truth for top-level navigation state. The welcome screen
 * (onboarding) is the root and main menu; MainActivity's phone bottom-dock
 * and tablet rail both read this, so highlighting and the visible back
 * arrow stay consistent. Pure logic, tested.
 */
object AppNav {
    /**
     * Screens without section chrome. The transient setup screens hide the
     * whole chrome (setup, permissions, connect). When [linked] is false —
     * no connection and no demo — chrome is hidden everywhere: the dock/rail
     * tabs all lead to screens that need a connection, so a disconnected app
     * shows only the Home connection card (progressive disclosure). In
     * [cardsMode] the user chose hub-and-spoke navigation at first start:
     * chrome never shows, screens are reached from the Home cards.
     */
    fun isChromeHidden(
        destinationId: Int,
        linked: Boolean = true,
        cardsMode: Boolean = false
    ): Boolean =
        cardsMode || !linked || destinationId in setOf(
            R.id.setupFragment,
            R.id.permissionsFragment,
            R.id.connectFragment
        )

    /**
     * Top-level section for a destination, or null when nothing highlights
     * (setup screens and the secondary screens — settings, about, legal —
     * which belong to no dock tab). The welcome root resolves to no tab:
     * the dock's first tab is the OBD Console, and Home sits above the tabs
     * (reachable via system back). Detail screens resolve to their parent
     * section.
     */
    fun sectionFor(destinationId: Int): Int? = when (destinationId) {
        // Data sections (Console replaced the Home tab in the dock)
        R.id.consoleFragment -> R.id.consoleFragment
        R.id.liveDataFragment -> R.id.liveDataFragment
        R.id.errorOverviewFragment,
        R.id.errorDetailFragment,
        R.id.askAiFragment -> R.id.errorOverviewFragment
        R.id.tripFragment -> R.id.tripFragment

        // Welcome root and secondary screens: no tab highlights
        else -> null
    }

    /**
     * Visible up-arrow: every chromed screen except the welcome root
     * (nowhere meaningful to go back to).
     */
    fun showBackArrow(destinationId: Int): Boolean =
        !isChromeHidden(destinationId) && destinationId != R.id.onboardingFragment

    /**
     * True when the user chose hub-and-spoke (cards) navigation: Home tiles
     * drive everything and chrome never shows. Pure logic, tested.
     */
    fun isCardsMode(navStyleTag: String?): Boolean =
        NavStyle.fromPref(navStyleTag) == NavStyle.CARDS
}
