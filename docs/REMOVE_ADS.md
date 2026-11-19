# Remove Ads — one-time purchase (`remove_ads`)

Paid ad removal for OBD2AI: a single non-consumable Google Play Billing
product. There is no free ad switch anywhere in the app — the banner
(`activity_main.xml` + `layout-sw600dp`, loaded only from
`MainActivity.applyAdChoice()`) stays on until Play confirms ownership.

## What lives where

| Piece | Location |
|---|---|
| Grant rules (pure, unit-tested) | `ads/AdEntitlement.kt` (`PRODUCT_ID`, `isEntitled`, `effectiveEntitlement`, `outcomeFor`, `purchaseUi`) |
| Play owner (connect, price, flow, ack, restore) | `ads/AdBillingManager.kt` (`isAdsRemoved`, `removeAdsPrice`, `purchaseEvents`, `start`, `restore`, `launchPurchase`) |
| Local startup cache only (`ads_removed`) | `prefs/PrefsKeys.ADS_REMOVED` (excluded from backup like all `app_prefs.xml`) |
| Single ad gate | `MainActivity.applyAdChoice()` + `adsRemovedApplied` lifecycle guards |
| Purchase UI | Settings → Privacy card (`buttonRemoveAds`, owned state replaces it) |
| Tests | `src/test/.../ads/AdEntitlementTest.kt` (purchase, restore, cancel, fail, pending, already-owned, unknown-price) |

Flow: `MainActivity.onCreate` creates `adBilling`, calls `start()`
(connect → localized price via `queryProductDetailsAsync` →
`restore()` via `queryPurchasesAsync`), and collects `isAdsRemoved`
into `applyAdChoice()`. A grant hides the `AdView` **and destroys it**
(no further loads); Settings collects the same flows for the
button/owned state and `purchaseEvents` toasts. Cache seeds the first
frame; the Play verdict overwrites it on every successful query, so
reinstalls, data clears, device moves and refunds all resolve from the
store. Offline failures leave the cache untouched (offline ≠ revoke).

## Google Play Console setup (owner does this, not code)

1. Play Console → the OBD2AI app → **Monetize → Products → In-app products
   → Create product**.
2. Product ID: exactly `remove_ads`. Name e.g. “Remove Ads”.
   Type: **one-time product** (non-consumable). Status: **Active**.
3. Set the price per country (or use a pricing template). The app never
   hard-codes it — Settings shows
   `ProductDetails.oneTimePurchaseOfferDetails.formattedPrice`.
4. The app binary containing the Billing Library must be uploaded at
   least once (internal track is enough) before the Console lets the
   product go Active.
5. License testing: Console → **Setup → License testing** — add tester
   Gmail accounts. Testers buy without being charged on test tracks.
6. Data Safety: review the form’s **Financial info → Purchase history**
   section — Play processes the payment; the app only reads ownership.
7. Merchant/refund policy: one-time products are refundable through
   Play; a refund surfaces as “not owned” on the next restore and ads
   return — by design, no app-side revocation list.

## Testing the purchase (tracks)

1. Push a build with this change to the **internal testing track** and
   add your Gmail as a tester (also under License testing).
2. On the test device, join the internal track (opt-in link), install,
   open Settings → Privacy → **Remove Ads — <price>**.
3. Buy with the license-tester account: no charge, test card shown.
   Banner must vanish immediately; reopening Settings must show
   **Ads Removed** (button gone, cannot buy twice).
4. Restart the app → still no ads. Clear app data → ads stay removed
   after the restore query. Cancel the sheet mid-flow → ads stay on.
5. Static test SKUs are unnecessary here; license testers exercise the
   real `remove_ads` product end to end.

## Console vs Studio split

- Android Studio: everything above (query price, launch flow,
  acknowledge, restore, gating, UI, cache, tests).
- Play Console: product creation/activation/pricing, license testers,
  Data Safety financial-info review, merchant account + tax/refund
  handling, and the production rollout that makes the product live.
