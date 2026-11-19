package com.catsmoker.obd2ai.ads

import com.catsmoker.obd2ai.ads.AdEntitlement.PurchaseOutcome
import com.catsmoker.obd2ai.ads.AdEntitlement.StorePurchase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the paid `remove_ads` entitlement policy: ads stay on
 * until Play confirms a PURCHASED row, and the store verdict always beats
 * the local cache when reachable. Mirrors the purchase / restore / edge
 * cases in `docs/REMOVE_ADS.md`.
 */
class AdEntitlementTest {

    private fun purchased(acknowledged: Boolean = true) = StorePurchase(
        productId = AdEntitlement.PRODUCT_ID,
        state = AdEntitlement.STATE_PURCHASED,
        acknowledged = acknowledged
    )

    private fun pending() = StorePurchase(
        productId = AdEntitlement.PRODUCT_ID,
        state = AdEntitlement.STATE_PENDING,
        acknowledged = false
    )

    // 1. Never purchased -> ads displayed.
    @Test
    fun `no purchases means not entitled and ads show`() {
        assertFalse(AdEntitlement.isEntitled(emptyList()))
        assertTrue(AdEntitlement.shouldShowAds(false))
    }

    // 2. Purchased -> no ads.
    @Test
    fun `purchased row grants entitlement and hides ads`() {
        assertTrue(AdEntitlement.isEntitled(listOf(purchased())))
        assertFalse(AdEntitlement.shouldShowAds(true))
    }

    // 3. Restart after purchase -> ads stay removed.
    @Test
    fun `restart keeps entitlement when store confirms purchase`() {
        assertTrue(AdEntitlement.effectiveEntitlement(true, true, listOf(purchased())))
    }

    // 4. Data cleared -> restored from Play, ads stay removed.
    @Test
    fun `cleared cache restores entitlement from the store`() {
        assertFalse(AdEntitlement.effectiveEntitlement(false, false, emptyList()))
        assertTrue(AdEntitlement.effectiveEntitlement(true, false, listOf(purchased())))
    }

    // 5. Reinstall -> restored from Play, ads stay removed.
    @Test
    fun `reinstall restores entitlement from the store`() {
        assertTrue(AdEntitlement.effectiveEntitlement(true, false, listOf(purchased())))
    }

    // 6. Cancelled purchase -> ads stay on.
    @Test
    fun `cancelled purchase grants nothing`() {
        assertEquals(
            PurchaseOutcome.CANCELLED,
            AdEntitlement.outcomeFor(AdEntitlement.RESPONSE_USER_CANCELED, emptyList())
        )
        assertFalse(AdEntitlement.isEntitled(emptyList()))
    }

    // 7. Failed purchase -> ads stay on.
    @Test
    fun `failed purchase grants nothing`() {
        assertEquals(
            PurchaseOutcome.FAILED,
            AdEntitlement.outcomeFor(AdEntitlement.RESPONSE_ERROR, emptyList())
        )
        assertFalse(AdEntitlement.isEntitled(emptyList()))
    }

    // 8. Pending purchase -> no grant until completed.
    @Test
    fun `pending purchase grants nothing and needs no acknowledgement`() {
        val store = listOf(pending())
        assertEquals(
            PurchaseOutcome.PENDING,
            AdEntitlement.outcomeFor(AdEntitlement.RESPONSE_OK, store)
        )
        assertFalse(AdEntitlement.isEntitled(store))
        assertTrue(AdEntitlement.needsAcknowledgement(store).isEmpty())
    }

    // 9. Existing purchase found at startup -> restored.
    @Test
    fun `startup restore grants on a stored purchased row`() {
        assertTrue(AdEntitlement.isEntitled(listOf(purchased(acknowledged = false))))
        assertEquals(
            PurchaseOutcome.GRANTED,
            AdEntitlement.outcomeFor(AdEntitlement.RESPONSE_OK, listOf(purchased()))
        )
    }

    // 10. Product cannot be queried -> no false grant, graceful UI.
    @Test
    fun `unknown price never grants and keeps a plain buy button`() {
        val ui = AdEntitlement.purchaseUi(isEntitled = false, price = null)
        assertFalse(ui.owned)
        assertNull(ui.price)
        assertEquals(
            PurchaseOutcome.FAILED,
            AdEntitlement.outcomeFor(AdEntitlement.RESPONSE_ERROR, emptyList())
        )
    }

    // 11. Already owned -> restore instead of selling again.
    @Test
    fun `already owned restores and the owned UI hides the price`() {
        assertEquals(
            PurchaseOutcome.ALREADY_OWNED,
            AdEntitlement.outcomeFor(AdEntitlement.RESPONSE_ITEM_ALREADY_OWNED, emptyList())
        )
        val ui = AdEntitlement.purchaseUi(isEntitled = true, price = "$9.99")
        assertTrue(ui.owned)
        assertNull(ui.price)
    }

    @Test
    fun `unreachable store falls back to the local cache`() {
        assertTrue(AdEntitlement.effectiveEntitlement(false, true, emptyList()))
        assertFalse(AdEntitlement.effectiveEntitlement(false, false, emptyList()))
    }

    @Test
    fun `reachable store overrules a stale cache both ways`() {
        // Refund/revocation clears the entitlement despite the cache.
        assertFalse(AdEntitlement.effectiveEntitlement(true, true, emptyList()))
        // A fresh store grant sets it despite an empty cache.
        assertTrue(AdEntitlement.effectiveEntitlement(true, false, listOf(purchased())))
    }

    @Test
    fun `unacknowledged grants are reported for acknowledgement`() {
        val store = listOf(purchased(acknowledged = false), pending())
        assertEquals(listOf(purchased(acknowledged = false)), AdEntitlement.needsAcknowledgement(store))
    }

    @Test
    fun `other products never grant remove_ads`() {
        val store = listOf(StorePurchase("other_sku", AdEntitlement.STATE_PURCHASED, true))
        assertFalse(AdEntitlement.isEntitled(store))
        assertEquals(PurchaseOutcome.FAILED, AdEntitlement.outcomeFor(AdEntitlement.RESPONSE_OK, store))
    }

    @Test
    fun `purchased row wins even with an odd result code`() {
        assertEquals(
            PurchaseOutcome.GRANTED,
            AdEntitlement.outcomeFor(AdEntitlement.RESPONSE_ERROR, listOf(purchased()))
        )
    }

    @Test
    fun `buy button carries the localized Play price`() {
        val ui = AdEntitlement.purchaseUi(isEntitled = false, price = "$4.99")
        assertFalse(ui.owned)
        assertEquals("$4.99", ui.price)
    }
}
