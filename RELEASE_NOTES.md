# Release notes

## 1.3.1 (versionCode 4)

The Compose rewrite lands as a finished product: one Home hub instead of dock
tabs, a Material 3 Expressive design system with Liquid Glass, pure logic
moved into testable modules, and a pass over every place the app used to show
data it had not actually read.

### UI and navigation

- **Home is the only hub.** The phone bottom bar and the tablet navigation
  rail are gone, not hidden. Home shows the connection hero plus a tile grid
  (Dashboard, Diagnostic Report, Trip, Console) once connected or in demo;
  Settings and About are header icons; every secondary screen carries an
  in-layout back arrow.
- **Material 3 Expressive design system** — colour, shape, type, spacing and
  spring motion in `ui/theme`, with a reduce-motion path that follows the
  system animator scale.
- **Liquid Glass** (`io.github.kyant0:backdrop`) on hero and floating
  surfaces. It degrades to a tinted surface below API 31 and skips refraction
  below API 33 — `minSdk` stays 24.
- Redesigned Home, Connect, Live dashboard, fault report, code detail, Ask AI,
  Trip, Console, Settings (collapsible sections), About, Legal, Setup and
  Permissions.
- Setup is a two-step flow (agreement → language + theme) with live previews
  and no activity recreation.
- Connect screen: one button that rescans or stops scanning, named progress
  ("Connecting to 10.0.2.2:35000…"), an empty state that explains how to pair,
  and an "last connected via" hint that never auto-connects.

### Reliability and honesty

- **Every failure names its cause.** `ConnectFailureKind` classifies the
  message once, so Connect, Live data, the fault report and error dialogs say
  "Could not reach the adapter" with the raw text ("Broken pipe") demoted to a
  detail line, instead of a bare "ERROR" or a generic toast.
- **Live data says why it stopped** and offers Retry; retrying is refused when
  the adapter is genuinely gone.
- **Connection loss is visible on Home** ("Connection lost: …") and is cleared
  by reconnecting, entering demo, or an explicit disconnect.
- **No invented numbers.** Trip distance is only integrated from a live
  source, so a stopped poll can no longer turn a stale "62 Km/h" into
  kilometres nobody drove.
- **State survives process death.** The trip persists to `trip_state.json`
  (checkpointed every ~10 samples and on `onStop`); a trip resumes only if its
  last sample is recent, otherwise it returns stopped with its totals.
- **Demo resumes** after a relaunch instead of silently dropping the user into
  "Not connected".

### Diagnostics

- Stored, pending and permanent codes are grouped with collapsible sections
  and per-source chips; severity counts and MIL state sit above the list.
- The assessed list — the part paid AI calls produced — is cached with its
  save time and **reviewable offline**: a disconnected Home offers a "Saved
  fault codes" tile and the report states "Saved scan from …", never presenting
  a restored list as a live reading.
- Demo mode has no fault-code source, so Scan and Clear are disabled there and
  the screen says why instead of failing after a pointless read.
- Ask AI survives rotation (state codec + retry on interruption); freeze frames
  are cached per scan and invalidated by scan or clear.

### Build and modules

- `:app` split into `:core:prefs`, `:core:obd`, `:core:diagnostics` and
  `:core:ai`; every module owns its unit tests — **257 tests, 0 failures**.
- `compileSdk 37` (minor 2), `targetSdk 36`, `minSdk 24`, Java 17, AGP 9.5.2,
  Kotlin 2.4.21, Compose BOM `2026.11.01`, Hilt 2.60.1.
- `ObdHelper` and `BluetoothHelper` are process-wide singletons; duplicate
  instances previously produced a phantom "OBD connection not established".
- Package directories flattened back to their declared packages; the stray
  root-level `temp_settings.kt` removed.
- Docs refreshed: `ARCHITECTURE.md`, `NAVIGATION.md` (new), `BUILD.md`,
  `UI_DESIGN.md`, `REFERENCE_INVENTORY.md`, `README.md`, plus current
  screenshots.

### Upgrading

- No user action needed. Preferences carry over; the old navigation-style keys
  (`nav_style`, `home_nav_hint`) are simply ignored, and a legacy
  both-AI-assistants-ON state is normalised to Offline AI on the first Settings
  open.
- The release build still signs with the debug keystore (placeholder until a
  real keystore exists).

### Known limitations

- Demo mode cannot produce fault codes (no adapter transport), by design.
- The trip only accumulates distance while live polling runs (the dashboard is
  open); the Trip screen states this.
- Freeze frames are live ECU data and are never persisted across process death.
- Without an AI key, assessments are marked as offline info.
