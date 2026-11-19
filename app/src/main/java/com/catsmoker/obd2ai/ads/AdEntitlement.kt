package com.catsmoker.obd2ai.ads

/**
 * Pure purchase-entitlement policy for the paid `remove_ads` one-time
 * in-app product (Google Play Billing, non-consumable).
 *
 * Ads are on for everyone until Play confirms ownership: there is no free
 * off-switch anywhere in the app. The local `ads_removed` preference is
 * only a startup cache — the store verdict wins whenever Play answers.
 * Pure logic with no Android imports so every rule below is unit-tested.
 */
object AdEntitlement {
    /** One-time Play product that removes the banner permanently. */
    const val PRODUCT_ID = "remove_ads"

    // BillingClient.BillingResponseCode values, mirrored as plain Ints so
    // this policy stays JVM-testable (stable public API values).
    const val RESPONSE_OK = 0
    const val RESPONSE_USER_CANCELED = 1
    const val RESPONSE_ERROR = 6
    const val RESPONSE_ITEM_ALREADY_OWNED = 7

    // Purchase.PurchaseState values, mirrored for the same reason.
    const val STATE_PURCHASED = 1
    const val STATE_PENDING = 2

    /** Minimal store view of one owned product row. */
    data class StorePurchase(
        val productId: String,
        val state: Int,
        val acknowledged: Boolean
    )

    /** What a finished purchase-flow attempt means for the entitlement. */
    enum class PurchaseOutcome {
        /** Play reports a PURCHASED row: grant, then acknowledge. */
        GRANTED,
        /** Still pending: show waiting state, grant nothing yet. */
        PENDING,
        /** User backed out: ads stay on, no error shown. */
        CANCELLED,
        /** Failed: ads stay on, report the failure. */
        FAILED,
        /** Play says already owned: re-query and restore instead of selling. */
        ALREADY_OWNED,
        /** Product details unavailable: cannot sell, report gracefully. */
        DETAILS_UNAVAILABLE
    }

    /** Button row state for Settings: owned banner vs buy button + price. */
    data class PurchaseUi(
        val owned: Boolean,
        /** Localized Play price, null while the product query is unresolved. */
        val price: String?
    )

    /** Non-consumable grant: any PURCHASED `remove_ads` row. Pending rows
     * never grant; other products never grant. */
    fun isEntitled(purchases: List<StorePurchase>): Boolean =
        purchases.any { it.productId == PRODUCT_ID && it.state == STATE_PURCHASED }

    /** Granted rows the app must still acknowledge with Play. */
    fun needsAcknowledgement(purchases: List<StorePurchase>): List<StorePurchase> =
        purchases.filter {
            it.productId == PRODUCT_ID && it.state == STATE_PURCHASED && !it.acknowledged
        }

    /** Single ad-visibility gate: entitled users load no ads at all. */
    fun shouldShowAds(isEntitled: Boolean): Boolean = !isEntitled

    /**
     * Startup resolution. The store verdict wins whenever Play answers
     * (reinstalls, data clears and refunds all resolve from the store);
     * the local cache only covers an unreachable store.
     */
    fun effectiveEntitlement(
        storeReachable: Boolean,
        cached: Boolean,
        store: List<StorePurchase>
    ): Boolean = if (storeReachable) isEntitled(store) else cached

    /**
     * Maps a finished purchase flow to an outcome. A PURCHASED row always
     * wins (grant even if the result code is odd); cancellation and
     * prior ownership map explicitly; anything else without a grant is a
     * failure — never a grant. The dialog merely opening grants nothing.
     */
    fun outcomeFor(responseCode: Int, purchases: List<StorePurchase>): PurchaseOutcome {
        val relevant = purchases.filter { it.productId == PRODUCT_ID }
        return when {
            relevant.any { it.state == STATE_PURCHASED } -> PurchaseOutcome.GRANTED
            responseCode == RESPONSE_USER_CANCELED -> PurchaseOutcome.CANCELLED
            responseCode == RESPONSE_ITEM_ALREADY_OWNED -> PurchaseOutcome.ALREADY_OWNED
            responseCode != RESPONSE_OK -> PurchaseOutcome.FAILED
            relevant.any { it.state == STATE_PENDING } -> PurchaseOutcome.PENDING
            else -> PurchaseOutcome.FAILED
        }
    }

    /** Settings row: owned state replaces the buy button entirely so the
     * product can never be sold twice. */
    fun purchaseUi(isEntitled: Boolean, price: String?): PurchaseUi =
        PurchaseUi(owned = isEntitled, price = if (isEntitled) null else price)
}
