# Play Data Safety — OBD2AI (copy-paste sheet)

Fill this in Play Console → App content → Data safety. It is grounded in
Google's own disclosures (Mobile Ads SDK collects IP address, user product
interactions, diagnostics, device/account identifiers for
advertising/analytics/fraud prevention — see
`https://developers.google.com/admob/android/privacy/play-data-disclosure`)
plus what this app's code actually does (verified, not guessed).

## Short answers first

| Question | Answer |
| -------- | ------ |
| Does the app collect or share user data? | **Yes** (see table) |
| Is all user data encrypted in transit? | **Yes** (HTTPS/TLS: AI providers, Firebase, AdMob) |
| Can users request data deletion? | **Yes** — in-app: Settings → Privacy toggles off analytics/personalization; full wipe via Android Settings → Apps → OBD2AI → Storage → Clear data, or uninstall. Support: `https://catsmoker.vercel.app/legal` |
| Target audience includes children? | **No** (general vehicle-utility tool) |

## Declare: collected AND shared

| Data type | Collected | Shared | Purposes | Notes |
| --------- | --------- | ------ | -------- | ----- |
| Approximate location | ✅¹ | ✅¹ | Advertising, analytics, fraud prevention | ¹Via AdMob SDK (IP-derived). The app's own location use (BT discovery, GPS speed) stays on-device and is NOT transmitted by app code — but AdMob's automatic collection must still be declared |
| App activity (app interactions: launches, taps) | ✅ | ✅ | Advertising, analytics | AdMob automatic + Firebase Analytics events (`analyze_clicked`, `clear_codes_clicked`, `ask_ai_sent`) — Firebase on by default with first-launch choice + Settings opt-out |
| App info & performance (launch time, hang rate, energy) | ✅ | ✅ | Analytics, fraud prevention | AdMob automatic diagnostics |
| Device or other IDs (ad ID, app-set ID, Firebase instance ID) | ✅ | ✅ | Advertising, analytics, fraud prevention | AdMob automatic; personalization on by default with first-launch choice + Settings opt-out (`npa=1` until then) |

## Declare: collected, NOT shared (leaves the device only to the user's own chosen endpoint)

| Data type | Collected | Shared | Purposes | Notes |
| --------- | --------- | ------ | -------- | ----- |
| User-generated content / messages (fault codes, VIN, AI follow-up questions) | ✅ | ❌² | App functionality | ²Sent ONLY to the AI provider the user selects in Settings (OpenAI/Gemini/Anthropic/custom server), under that provider's policy — never to the developer, never to advertisers |

## Do NOT declare (stays on-device — verified in code)

- Precise location (GPS speed is in-memory only, never transmitted)
- AI API key (`app_prefs.xml`, excluded from backup/transfer)
- Contacts, photos, files, health, financial info — the app never touches these
- No cookies/WebView/pixels; no Meta/TikTok/heatmap SDKs

## Owner actions this sheet does NOT replace

1. **Production AdMob IDs**: `app/build.gradle.kts` (`manifestPlaceholders
   admobAppId`) + `res/values/strings.xml` (`admob_banner_id`) still hold
   Google's demo IDs — swap both before release.
2. **Funding Choices messages**: AdMob console → Privacy & messaging →
   create GDPR + US-states messages for the production app ID, otherwise the
   UMP form never appears and EEA consent is unmanaged.
3. Re-check this sheet whenever an SDK is added/removed or a new
   `logEvent` is added — the table must match the code, and reviewers
   compare them.
