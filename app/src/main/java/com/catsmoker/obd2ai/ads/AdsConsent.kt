package com.catsmoker.obd2ai.ads

/**
 * Ad/analytics effective-state policy for the always-on banner.
 *
 * The banner itself cannot be turned off (it funds the free app); only
 * personalization is user-controlled, and regulator-required consent
 * (EEA/UK via the UMP form) always wins. Nothing is effective before the
 * user has made the first-launch choice, so no collection or personalised
 * request can precede it. Pure logic with no Android imports — the Activity
 * maps prefs + UMP's `ConsentStatus` onto the three booleans.
 */
object AdsConsent {
    /**
     * Personalize only after the choice exists, when the user opted in,
     * AND while no regulator-required consent is still pending. When the
     * UMP update fails (offline) the Activity reports "not required" and
     * the stored toggle decides.
     */
    fun effectivePersonalizedAds(
        choiceMade: Boolean,
        userOptIn: Boolean,
        consentRequired: Boolean
    ): Boolean = choiceMade && userOptIn && !consentRequired

    /** Analytics collect only after the choice exists and opts in. */
    fun analyticsEffective(choiceMade: Boolean, userOptIn: Boolean): Boolean =
        choiceMade && userOptIn
}
