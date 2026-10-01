package com.catsmoker.obd2ai.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the always-on banner policy: the banner itself has no
 * off-switch; personalization needs a recorded choice AND the user toggle
 * AND no pending regulator-required (UMP) consent. Analytics needs a
 * recorded choice plus the toggle.
 */
class AdsConsentTest {

    @Test
    fun `nothing is effective before the first-launch choice`() {
        assertFalse(AdsConsent.effectivePersonalizedAds(false, true, false))
        assertFalse(AdsConsent.effectivePersonalizedAds(false, false, false))
        assertFalse(AdsConsent.analyticsEffective(false, true))
    }

    @Test
    fun `personalizes when chosen opted in and no consent pending`() {
        assertTrue(AdsConsent.effectivePersonalizedAds(true, true, false))
    }

    @Test
    fun `generic ads when user did not opt in`() {
        assertFalse(AdsConsent.effectivePersonalizedAds(true, false, false))
    }

    @Test
    fun `regulator-required consent wins over the user toggle`() {
        assertFalse(AdsConsent.effectivePersonalizedAds(true, true, true))
        assertFalse(AdsConsent.effectivePersonalizedAds(true, false, true))
    }

    @Test
    fun `analytics follow choice plus toggle`() {
        assertTrue(AdsConsent.analyticsEffective(true, true))
        assertFalse(AdsConsent.analyticsEffective(true, false))
    }
}
