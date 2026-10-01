# Privacy Policy — OBD2AI (developer reference)

> The user-facing text lives in the app itself (About → Privacy Policy,
> available offline in every supported language) and is sourced from
> `app/src/main/res/values*/strings.xml` (`legal_privacy_body`). This file is
> the maintainer reference: the factual basis, the decisions behind it, and
> what still needs the owner before a store release.

## Factual basis (verified in the codebase)

- **What the app does:** reads vehicle diagnostics (DTCs, MIL, VIN, live
  telemetry: speed, RPM, coolant, voltage, fuel) from an ELM327 adapter over
  Bluetooth SPP or WiFi TCP, or simulates them in demo mode; explains fault
  codes with a user-configured AI provider.
- **On-device only:** settings + AI config in private `SharedPreferences`
  (`app_prefs.xml`); AI key in the same file, **excluded** from Android
  backup and device transfer (`backup_rules.xml`,
  `data_extraction_rules.xml`); cached assessments in internal
  `dtc_results.json` (overwritten each scan); live telemetry and GPS speed
  in memory only, never persisted.
- **Off-device (user-triggered only):** fault codes + VIN + follow-up
  questions → the AI provider selected in Settings (OpenAI / Gemini /
  Anthropic / custom server) over plain `HttpURLConnection`, no vendor SDK.
  Anonymous usage events → Google Firebase Analytics, **on by default,
  opt-out** under Settings → Privacy (first-launch choice; collection also
  disabled in the manifest until the choice exists). They report screens
  used plus device and app identifiers (app-instance ID, advertising ID,
  IP-derived geo) — pseudonymous technical identifiers, not a name or
  contact details. The banner ad (AdMob) is **always
  shown** — it funds the free app and cannot be turned off; ad
  personalization is **on by default, opt-out** under Settings → Privacy
  (`npa=1` until then). The Google UMP flow (`syncAdsConsent`, UMP SDK
  4.0.0) shows the Funding Choices consent form where required (EEA/UK)
  before any ad request, gates loads on `canRequestAds`, and never writes
  preferences itself — the setup screen and Settings toggles are the sole
  writers. No cookies, no WebView, no tracking pixels, no
  social/map/video/chat/payment SDKs. “Get Gas” fires a `geo:` intent into
  the user’s own maps app; that search is handled by the maps app, not
  by us.
- **Permissions:** Bluetooth (+ admin on old APIs) for the adapter;
  location for Bluetooth discovery on older Android versions plus the
  optional GPS speed source (in-memory only); internet for AI/ads/
  analytics. See `AndroidManifest.xml` and `docs/OBD_CONNECTION.md`.

## Decisions recorded

- **No Cookie Policy page.** A native app with no WebView, no cookies, and
  no browser storage has nothing truthful to put in one. Cookie-equivalent
  identifiers (advertising ID via AdMob, app-instance ID via Firebase) are
  disclosed in the Privacy Policy and gated behind the same opt-in.
- **No Refund/Cancellation Policy.** The app sells nothing, has no
  in-app purchases or subscriptions, and takes no payments. The PayPal
  donate link exists only in the GitHub README (outside the app); external
  voluntary donations are covered by one sentence in the Terms of Service.
- **No invented identity.** Operator name, address, registration numbers,
  phone, email, and DPO details are marked `[TODO]` in the in-app texts
  and must be supplied by the owner — fabricating them would be worse
  than a visible gap.
- **Marketing honesty.** No reviews, testimonials, ratings, badges, or
  scarcity claims exist in the app; the only corrected claim is the
  onboarding title (“OBD2 AI Pro” → “OBD2AI”), which wrongly implied a
  paid tier.

## Still required from the owner (pre-store-release)

1. Operator contact email (optional but recommended) — operator identity
   is CATSMOKER and the hosted legal notices live at
   `https://catsmoker.vercel.app/legal` (linked from About, Settings →
   Privacy, and the in-app legal reader; use this URL for the Play Data
   Safety / store-listing privacy-policy field).
2. Trader details if the Play listing qualifies as a trader under the EU
   Digital Services Act (address, registration, VAT where applicable).
3. Replace the demo AdMob IDs (`ca-app-pub-3940256099942544/...`, now
   centralized: `manifestPlaceholders admobAppId` in `app/build.gradle.kts`
   + `admob_banner_id` in `strings.xml`) with real production IDs; create
   the Funding Choices messages (AdMob console → Privacy & messaging, GDPR
   + US-states) or the UMP form never appears; then fill the Play Data
   Safety form from `docs/PLAY_DATA_SAFETY.md`.
4. Qualified-lawyer review of the in-app Privacy Policy + Terms before any
   EU/UK-targeted release (GDPR, ePrivacy/consent, consumer and
   accessibility rules differ by member state; China PIPL applies if the
   zh-CN build is distributed in mainland China).

## Related

- In-app texts: `legal_privacy_body`, `legal_terms_body` in all four
  `strings.xml` files (key parity is unit-tested).
- Security model: `SECURITY.md`. AI endpoints: `docs/AI_PROVIDERS.md`.
- Consent implementation: `MainActivity.syncAdsConsent()` (UMP) +
  `applyPrivacyChoices()`, `logAnalyticsEvent()`, Settings → Privacy
  switches, `ads/AdsConsent` policy (unit-tested).
