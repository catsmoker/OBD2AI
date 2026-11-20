package com.catsmoker.obd2ai.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the in-app legal page routing + privacy defaults contract. */
class LegalConsentTest {

    @Test
    fun `terms page selected only for terms key`() {
        assertTrue(LegalPages.isTermsPage(LegalPages.PAGE_TERMS))
        assertFalse(LegalPages.isTermsPage(LegalPages.PAGE_PRIVACY))
    }

    @Test
    fun `unknown legal page falls back to privacy`() {
        assertFalse(LegalPages.isTermsPage("cookies"))
        assertFalse(LegalPages.isTermsPage(""))
    }

    @Test
    fun `legal page keys are distinct`() {
        assertTrue(LegalPages.PAGE_PRIVACY != LegalPages.PAGE_TERMS)
    }
}
