package com.catsmoker.obd2ai.ui.common

import com.catsmoker.obd2ai.R

/**
 * Single source of truth for top-level navigation state. The welcome screen
 * (onboarding) is the root and main menu; MainActivity's phone top bar and
 * tablet rail both read this, so highlighting and the visible back arrow
 * stay consistent. Pure logic, tested.
 */
object AppNav {
    /**
     * Screens without section chrome. The welcome menu IS the navigation,
     * so the top bar / rail would only duplicate its cards — it stays
     * standalone, as does the first-launch setup screen. The transient
     * setup screens hide the whole chrome too. System Back still works
     * everywhere.
     */
    fun isChromeHidden(destinationId: Int): Boolean = destinationId in setOf(
        R.id.onboardingFragment,
        R.id.setupFragment,
        R.id.permissionsFragment,
        R.id.connectFragment
    )

    /**
     * Top-level section for a destination, or null when nothing highlights
     * (welcome root, setup screens, and detail screens without their own
     * entry resolve to their section or nothing).
     */
    fun sectionFor(destinationId: Int): Int? = when (destinationId) {
        R.id.liveDataFragment -> R.id.liveDataFragment
        R.id.errorOverviewFragment,
        R.id.errorDetailFragment,
        R.id.askAiFragment -> R.id.errorOverviewFragment
        R.id.tripFragment -> R.id.tripFragment
        R.id.consoleFragment -> R.id.consoleFragment
        R.id.settingsFragment -> R.id.settingsFragment
        R.id.aboutFragment -> R.id.aboutFragment
        else -> null
    }

    /**
     * Visible back arrow: every chromed screen except the welcome root
     * (nowhere meaningful to go back to).
     */
    fun showBackArrow(destinationId: Int): Boolean =
        !isChromeHidden(destinationId) && destinationId != R.id.onboardingFragment

    /**
     * Phone section buttons (dashboard … about icons). The leaf screens
     * opened from the menu (settings, about, legal) use the back-arrow-only
     * flow instead — back arrow + system Back return to the menu — so the
     * strip never pops in mid-flow. The data screens keep quick section
     * switching. (The bar itself stays visible for the arrow; only the
     * section buttons hide, since the arrow lives inside the bar.)
     */
    fun showSectionButtons(destinationId: Int): Boolean =
        !isChromeHidden(destinationId) && destinationId !in setOf(
            R.id.settingsFragment,
            R.id.aboutFragment,
            R.id.legalFragment
        )
}
