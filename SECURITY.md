# Security Model

How OBD2AI handles the data it touches, the security-relevant surface area,
and an honest assessment of the risk. Read this before contributing code that
touches connections, keys, or exported components.

## Overview

OBD2AI is a diagnostics app that, by design, talks to a vehicle adapter over
**Bluetooth SPP or WiFi TCP** and to a user-chosen **AI provider over HTTPS**.
This document describes the boundaries around those channels and the
invariants the codebase is written around.

The app's stated privacy position: **no AI key in the build, no unnecessary
data collection**. The key is entered at runtime in Settings and stored only
on the device (see below).

## Supported versions

| Version | Supported |
| ------- | --------- |
| Latest `main` | ✅ |
| Older releases | ❌ (please update and re-test) |

## Reporting a vulnerability

- **Do not open a public issue** for anything that could put users at risk
  (key exfiltration, code execution, bypassing Android sandbox protections).
- Instead, open a **private security advisory** on the
  [repository's Security tab](https://github.com/catsmoker/OBD2AI/security/advisories)
  or contact a maintainer privately, with:
  1. What is affected (file/class, adapter type, Android version).
  2. Steps to reproduce or a proof of concept.
  3. What you think the impact is.
- You will get an acknowledgement, and we will coordinate a fix and a release
  before any public disclosure. General bugs go to the
  [Issue Tracker](https://github.com/catsmoker/OBD2AI/issues) as usual.

## Data model: what goes where

| Data | Where it lives | Backup / transfer |
| ---- | -------------- | ----------------- |
| AI API key (`openai_api_key`), provider, model, base URL | Private `SharedPreferences` (`app_prefs.xml`) | **Excluded** from Android Auto Backup and device-to-device transfer (`backup_rules.xml`, `data_extraction_rules.xml`) |
| Cached DTC assessments (`dtc_results.json`) | App-internal storage | Normal app data |
| Live telemetry (speed, RPM, coolant) | In-memory flows (`ObdDataHolder`) | Never persisted |
| Analytics / ads | Firebase Analytics, AdMob SDK | Per those SDKs' policies |

## The key boundary (AiService)

- **No key in the build.** There is deliberately no `buildConfigField` /
  `local.properties` injection for the AI key — keys baked into BuildConfig
  are extractable from the APK. The key is entered by the user in Settings.
- **No vendor SDKs.** Providers are called over plain `HttpURLConnection`
  (OpenAI/Custom: `/chat/completions`; Gemini: `generateContent`;
  Anthropic: `/v1/messages`). Fewer SDKs means a smaller supply chain.
- **Lenient parsing, strict display.** `extractJsonObject` strips code
  fences and surrounding prose (local models ramble); only the extracted
  object is parsed into `DtpCodeDTO`. Driving-voice cues (Offline AI) are fully
  offline and never touch the network — the key is used ONLY for fault-code
  explanations.
- **Offline-first failure.** Missing key or failed request yields an
  `offline=true` assessment, never a crash or a key leak in an error message.
  Never log the key — if you touch request logging, redact `Authorization`.

## Connection security

| Channel | Properties | Notes |
| ------- | ---------- | ----- |
| Bluetooth SPP | Short-range RFCOMM to a paired adapter | Pair in Android settings; classic adapters often use PIN `1234`/`0000` — change it if yours allows |
| WiFi TCP | Plain TCP to the adapter host/port | Many WiFi ELM327 adapters are **unencrypted** — use them on a trusted network and never leave the adapter's network joined when not diagnosing |
| Demo | Local simulation only | No radio, no network |

- Bluetooth discovery needs location permission on older Android versions;
  this is an Android platform requirement, not location tracking — the app
  does not record your position (unless you choose GPS as the speed source,
  which stays in memory).
- Runtime receivers use `ContextCompat.registerReceiver(...,
  RECEIVER_NOT_EXPORTED)` — plain `registerReceiver` throws on API 33+
  (targetSdk 36) and exported receivers would widen the attack surface.

## Permissions requested (AndroidManifest)

Most are expected for the feature set. A few deserve explicit justification:

- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` (Android 12+) and legacy
  `BLUETOOTH` / `BLUETOOTH_ADMIN` (older devices) — adapter discovery and
  the SPP socket.
- Location (`ACCESS_FINE_LOCATION` and friends) — required by Android for
  Bluetooth discovery on older versions; also backs the optional GPS speed
  source.
- `INTERNET` — AI provider HTTPS calls, ads, analytics.
- `POST_NOTIFICATIONS`, foreground-service / TTS audio — live-data and
  voice-insight features.

## Third-party surface

- **AdMob** (`play-services-ads`, demo ad id included) and **Firebase
  Analytics** — the only bundled third-party SDKs besides AndroidX /
  Navigation / SpeedView / kotlin-obd. `google-services.json` is committed
  by design (Firebase keys are identifiers, not secrets).
- **`kotlin-obd-api`** resolves from **JitPack** (see `settings.gradle.kts`)
  — it is not on Maven Central/Google. Version bumps go through the version
  catalog (`gradle/libs.versions.toml`) and get reviewed like any other
  dependency change.
- Release builds are minified (`isMinifyEnabled = true`) — if a
  reflection-based dependency breaks only in release, check
  `app/proguard-rules.pro`.

## Risk assessment (honest)

| Risk | Severity | Mitigation |
| ---- | -------- | ---------- |
| AI key extracted from a backup or shared prefs on a rooted device | Medium | Backup/transfer exclusions; private prefs; never log the key |
| Key baked into a build by a contributor | High | Hard rule: no BuildConfig/local.properties injection; review build-file changes |
| Cleartext DTC/vehicle data over WiFi adapter TCP | Medium | Documented; trusted-network guidance; nothing sensitive beyond OBD frames |
| Malicious Bluetooth device impersonating an adapter | Low–Medium | User pairs explicitly; ELM327 init is a fixed AT sequence, responses are parsed not executed |
| AI prompt-injection via crafted DTC description text | Low | Model output is parsed as data (`DtpCodeDTO`), never executed; unknown fields ignored |
| Exported component / receiver abuse | Low | `RECEIVER_NOT_EXPORTED`; no exported providers/services beyond what the manifest declares |

## Hard rules for contributors

1. **Never put a secret in the build.** No `buildConfigField`, no
   `local.properties` key injection, no checked-in keys. Ever.
2. **Never log secrets.** Redact `Authorization` headers and key prefs in
   any logging you add.
3. **Keep the backup exclusions.** If you touch `backup_rules.xml`,
   `data_extraction_rules.xml`, or `app_prefs.xml`, keep the exclusions.
4. **Keep receivers non-exported.** Use `ContextCompat.registerReceiver(...,
   RECEIVER_NOT_EXPORTED)`.
5. **Pin dependencies.** New/upgraded libraries go through the version
   catalog. No floating versions.
6. **Keep AI output as data.** Parse it into DTOs; never execute, eval, or
   load URLs from it.
7. **No covert data collection.** Anything network-egress beyond the user's
   AI provider, ads, and analytics must be reviewed and documented.

## Related documents

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — where these components live.
- [docs/AI_PROVIDERS.md](docs/AI_PROVIDERS.md) — provider endpoints and key handling.
- [docs/OBD_CONNECTION.md](docs/OBD_CONNECTION.md) — transports and permissions.
- [CONTRIBUTING.md](CONTRIBUTING.md) — how to contribute safely.
