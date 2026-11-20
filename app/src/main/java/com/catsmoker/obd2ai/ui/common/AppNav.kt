package com.catsmoker.obd2ai.ui.common

import com.catsmoker.obd2ai.R

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
     * (setup screens and the secondary screens — console, settings, about,
     * legal — which belong to no dock tab). The welcome root resolves to the
     * Home tab; detail screens resolve to their parent section.
     */
    fun sectionFor(destinationId: Int): Int? = when (destinationId) {
        // Welcome screen → Home tab
        R.id.onboardingFragment -> R.id.onboardingFragment

        // Data sections
        R.id.liveDataFragment -> R.id.liveDataFragment
        R.id.errorOverviewFragment,
        R.id.errorDetailFragment,
        R.id.askAiFragment -> R.id.errorOverviewFragment
        R.id.tripFragment -> R.id.tripFragment

        // Secondary screens: no tab highlights
        else -> null
    }

    /**
     * Visible up-arrow: every chromed screen except the welcome root
     * (nowhere meaningful to go back to).
     */
    fun showBackArrow(destinationId: Int): Boolean =
        !isChromeHidden(destinationId) && destinationId != R.id.onboardingFragment
}
