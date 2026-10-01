package com.catsmoker.obd2ai.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the in-app legal page routing + privacy defaults contract. */
class LegalConsentTest {

    @Test
    fun `terms page selected only for terms key`() {
        assertTrue(LegalFragment.isTermsPage(LegalFragment.PAGE_TERMS))
        assertFalse(LegalFragment.isTermsPage(LegalFragment.PAGE_PRIVACY))
    }

    @Test
    fun `unknown legal page falls back to privacy`() {
        assertFalse(LegalFragment.isTermsPage("cookies"))
        assertFalse(LegalFragment.isTermsPage(""))
    }

    @Test
    fun `legal page keys are distinct`() {
        assertTrue(LegalFragment.PAGE_PRIVACY != LegalFragment.PAGE_TERMS)
    }
}
