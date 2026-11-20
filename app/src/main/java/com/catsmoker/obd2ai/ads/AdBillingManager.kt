package com.catsmoker.obd2ai.ads

import android.app.Activity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.util.Log
import androidx.core.content.edit
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import com.catsmoker.obd2ai.prefs.PrefsKeys
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Play Billing owner for the `remove_ads` one-time product.
 *
 * Single source of truth for ad removal: [isAdsRemoved] starts from the
 * local cache and is overwritten by the Play verdict on every [restore],
 * so reinstalls, data clears, device moves and refunds all resolve from
 * the store. Every ad placement consults [isAdsRemoved] (via
 * [AdEntitlement.shouldShowAds]) instead of any preference. Purchase-flow
 * results arrive on [purchaseEvents] for user-facing feedback; the grant
 * itself comes only from a PURCHASED store row, never from opening the
 * dialog. Android-framework code (no unit tests) — the grant rules live
 * in the tested [AdEntitlement].
 */
class AdBillingManager @Inject constructor(@ApplicationContext appContext: Context) {
    private val context = appContext.applicationContext
    private val prefs =
        context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    private val _isAdsRemoved = MutableStateFlow(prefs.getBoolean(PrefsKeys.ADS_REMOVED, false))

    /** True once Play confirms `remove_ads` ownership. Collect to gate ads. */
    val isAdsRemoved: StateFlow<Boolean> = _isAdsRemoved.asStateFlow()

    private val _removeAdsPrice = MutableStateFlow<String?>(null)

    /** Localized Play price for the Settings button, null until resolved. */
    val removeAdsPrice: StateFlow<String?> = _removeAdsPrice.asStateFlow()

    private val _purchaseEvents =
        MutableSharedFlow<AdEntitlement.PurchaseOutcome>(extraBufferCapacity = 1)

    /** One-shot user-facing purchase results (toasts); state flows separately. */
    val purchaseEvents: SharedFlow<AdEntitlement.PurchaseOutcome> =
        _purchaseEvents.asSharedFlow()

    private var productDetails: ProductDetails? = null
    private var billingClient: BillingClient? = null
    private var started = false

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        onPurchasesUpdated(result.responseCode, purchases.orEmpty())
    }

    /**
     * Connects to Play, then resolves the localized price and restores any
     * existing entitlement. Idempotent; safe to call from `onCreate`.
     */
    fun start() {
        if (started) return
        started = true
        val client = BillingClient.newBuilder(context)
            .setListener(purchasesListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()
            .build()
        billingClient = client
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    return
                }
                queryPrice()
                restore()
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    /**
     * Re-queries owned one-time products and applies the store verdict
     * (grant or revoke) over the local cache. A failed query leaves the
     * cache untouched — offline is not a revocation.
     */
    fun restore() {
        val client = billingClient ?: return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Purchase restore failed: ${result.debugMessage}")
                return@queryPurchasesAsync
            }
            handleStorePurchases(purchases)
        }
    }

    /**
     * Starts the Play purchase flow for `remove_ads`. Never grants here:
     * the grant lands in [onPurchasesUpdated] once Play reports PURCHASED.
     * Already-owned users get the owned state (plus a restore), never a
     * second sale; unknown product details degrade to a graceful event.
     */
    fun launchPurchase(activity: Activity) {
        if (_isAdsRemoved.value) {
            _purchaseEvents.tryEmit(AdEntitlement.PurchaseOutcome.ALREADY_OWNED)
            return
        }
        val details = productDetails
        val client = billingClient
        if (details == null || client == null) {
            _purchaseEvents.tryEmit(AdEntitlement.PurchaseOutcome.DETAILS_UNAVAILABLE)
            queryPrice()
            restore()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            restore()
        }
    }

    private fun queryPrice() {
        val client = billingClient ?: return
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(AdEntitlement.PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()
        ) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Product query failed: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val match = details.productDetailsList
                .firstOrNull { it.productId == AdEntitlement.PRODUCT_ID }
            productDetails = match
            _removeAdsPrice.value = match?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    private fun onPurchasesUpdated(responseCode: Int, purchases: List<Purchase>) {
        val store = purchases.flatMap { purchase ->
            purchase.products.map { id ->
                AdEntitlement.StorePurchase(
                    productId = id,
                    state = purchase.purchaseState,
                    acknowledged = purchase.isAcknowledged
                )
            }
        }
        when (AdEntitlement.outcomeFor(responseCode, store)) {
            AdEntitlement.PurchaseOutcome.GRANTED -> {
                handleStorePurchases(purchases)
                _purchaseEvents.tryEmit(AdEntitlement.PurchaseOutcome.GRANTED)
            }
            AdEntitlement.PurchaseOutcome.ALREADY_OWNED -> restore()
            AdEntitlement.PurchaseOutcome.CANCELLED ->
                _purchaseEvents.tryEmit(AdEntitlement.PurchaseOutcome.CANCELLED)
            AdEntitlement.PurchaseOutcome.PENDING ->
                _purchaseEvents.tryEmit(AdEntitlement.PurchaseOutcome.PENDING)
            AdEntitlement.PurchaseOutcome.FAILED -> {
                Log.w(TAG, "Purchase failed with code $responseCode")
                _purchaseEvents.tryEmit(AdEntitlement.PurchaseOutcome.FAILED)
            }
            AdEntitlement.PurchaseOutcome.DETAILS_UNAVAILABLE ->
                _purchaseEvents.tryEmit(AdEntitlement.PurchaseOutcome.DETAILS_UNAVAILABLE)
        }
    }

    /**
     * Applies the store verdict and acknowledges granted rows. Granting
     * needs only the PURCHASED state; acknowledgement follows so Play
     * never refunds for a missing ack, and a failed ack is retried on the
     * next restore via [AdEntitlement.needsAcknowledgement].
     */
    private fun handleStorePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.products.contains(AdEntitlement.PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                !purchase.isAcknowledged
            ) {
                billingClient?.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                ) { result ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
                    }
                }
            }
        }
        val store = purchases.flatMap { purchase ->
            purchase.products.map { id ->
                AdEntitlement.StorePurchase(
                    productId = id,
                    state = purchase.purchaseState,
                    acknowledged = purchase.isAcknowledged
                )
            }
        }
        setEntitled(AdEntitlement.isEntitled(store))
    }

    private fun setEntitled(granted: Boolean) {
        if (_isAdsRemoved.value != granted) {
            _isAdsRemoved.value = granted
            prefs.edit { putBoolean(PrefsKeys.ADS_REMOVED, granted) }
        }
    }

    companion object {
        private const val TAG = "AdBilling"
    }
}
