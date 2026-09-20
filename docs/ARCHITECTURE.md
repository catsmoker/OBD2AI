# Architecture

A high-level map of the OBD2AI Android codebase and how the layers fit together.

## Stack

| Concern       | Choice                                                       |
| ------------- | ------------------------------------------------------------ |
| Language      | Kotlin 2.4                                                   |
| UI            | Android Views / Fragments + Navigation (Compose disabled)    |
| Navigation    | `androidx.navigation` (fragment + ui KTX, Safe Args)         |
| Async         | Kotlin Coroutines + Flow                                     |
| JSON          | `org.json` with lenient fence/prose stripping                |
| OBD           | `kotlin-obd-api` via JitPack + raw ELM327 AT strings         |
| AI            | Plain `HttpURLConnection`, no vendor SDK                     |
| Min / target  | API 24 (Android 7.0) / API 36                                |
| Compile SDK   | 37, build-tools 36.1.0, Java 17                              |

## Module layout

Single application module (`:app` only). Sources are split by responsibility
under `app/src/main/java/com/catsmoker/obd2ai/` — there is no `core`/`feature`
Gradle split:

```
app/src/main/java/com/catsmoker/obd2ai/
├── prefs/            # PrefsKeys, Units, ThemeMode, AppLanguage
├── obd/              # ObdCommands, PidRegistry, ElmProtocol, BluetoothHelper,
│                     # ObdHelper, VehicleTelemetry (ObdDataHolder, DemoObdSource)
├── diagnostics/      # DiagnosticModels, DtcStore
├── vehicle/          # TripComputer
├── ai/               # AiProviders (AiProvider, AiService), OnlineAi, SpeechQueue
├── audio/            # EngineSound (isolated synth hum)
├── speedometers/     # SpeedometerStyle, SpeedometerHost, BaseSpeedometerView + styles
├── instruments/      # RpmGaugeView (tachometer), CoolantGaugeView,
│                     # VoltageGaugeView, FuelGaugeView (cluster instruments)
├── ui/               # dashboard|settings|connect|diagnostics|trip|console|
│                     # onboarding|common (one fragment per file + its adapters)
└── MainActivity.kt   # wires helpers together
```

Fragments reach the shared helpers via `(activity as MainActivity)`.

## App flow

```
Onboarding ─┬─ Get Started → Permissions → ConnectFragment ─┬─► ErrorOverviewFragment
            │      (Bluetooth / WiFi / Demo)                 │      (reads DTCs, assesses each
            └─ Try Demo → LiveDataFragment (demo) ───────────┘       code via AI in parallel)
                                              ↓
                               ErrorDetailFragment ←→ LiveDataFragment
                               (cached dtc_results.json   (gauges + AI
                                fallback)                  voice insight)
```

Tablets (sw600dp) navigate top-level destinations with a navigation rail
(Dashboard, Diagnostics, Trip, Console, Settings); phones use the floating
global settings shortcut. About is a standalone destination from Settings.

Speed source can be the OBD device or the phone GPS (`speed_source` pref).

## Dependency direction

UI fragments depend on the helper packages, not the other way around:

```
ui.* (fragments/adapters)  ──►  obd / ai / diagnostics / prefs / vehicle / audio / speedometers / instruments
            MainActivity (wiring) ──►  obd / ai
                                       ──►  Android framework + 3rd-party SDKs
```

Pure parsing logic lives in companion objects so it runs as JVM unit tests
without Android (tests mirror the packages, e.g. `src/test/.../obd/ObdCommandsTest.kt`).

## Key components

### `BluetoothHelper` (`obd/`)
Permission handling, discovery, pairing and establishing the RFCOMM socket
(SPP UUID `00001101-0000-1000-8000-00805F9B34FB`).

### `ObdHelper` (`obd/`)
Owns the OBD connection and commands. Sends the fixed ELM327 init sequence
`ATZ, ATE0, ATL0, ATS0, ATH0, ATSP0, ATAT1` — `ATS0`/`ATH0` make
spaces/headers deterministic across clones, which the parsers assume; don't
drop them. Custom commands (`MySpeedCommand`, …) cover speed/RPM/coolant.
All reads branch on `demoMode`. See [OBD_CONNECTION.md](OBD_CONNECTION.md).

### `AiService` / `AiProvider` (`ai/`)
Sends the same "expert mechanic" prompt to the selected provider and parses
the JSON assessment into `DtpCodeDTO`. The API key is used ONLY for these
fault-code explanations — driving-voice cues (Offline AI) are fully offline
and never touch the network. See [AI_PROVIDERS.md](AI_PROVIDERS.md).

### `ObdDataHolder` + `DtcStore` (`obd/` + `diagnostics/`)
- `ObdDataHolder` — in-memory flows for live telemetry and DTC results.
- `DtcStore` — persists assessments to internal file `dtc_results.json`;
  `ErrorDetailFragment` falls back to it when the holder is empty.
- `DtpCodeDTO.offline=true` marks the no-key/offline fallback assessment.

### `DemoObdSource` (`obd/`)
Simulated adapter. `setupDemo()` sets `ObdHelper.demoMode`; in demo, the
LiveData sliders write `ObdDataHolder` flows directly — there is no demo
polling loop.

### Two assistants, kept independent (`ai/` + `ui/dashboard/`)
- **Offline AI** — offline event system: `OfflineAiEvent` (shift point, high
  RPM/speed, coolant, new fault codes, connection lost/restored, engine
  started/stopped) with per-event enable switches and thresholds, master
  switch, voice/text outputs and volume; every cue is spoken with on-device
  TTS, bottom `offlineAiBanner`. No key, no network, ever. Optional
  community Easter eggs (`values/arrays.xml` pools + `easterPoolConfig`
  odds) fire probabilistically after normal warnings with their own
  cooldowns and no-repeat tracking — Offline AI only.
- **Online AI** — online diagnostic companion: master switch plus voice/text
  outputs (at least one stays on), `OnlineAiManager` (event/decision layer
  with cooldowns) → `AiService.chatText` (same provider/key as fault
  explanations) → bottom `onlineAiCard` + `SpeechQueue` (device voice only —
  no TTS keys, no cloud voice). Personality reactions (`OnlineAiPersonality`
  occasions: speed tiers, RPM spike/high, warm coolant, new/returned faults,
  combos, 2% ultra) are generated live by the brain from actual readings and
  always go out at INFO severity, so faults/warnings preempt or evict them.
  Slow telemetry (`readSlowTelemetry`: DTCs, voltage, fuel) polls immediately
  on entry then ~every 30 s; the fast gauge loop only gained engine load
  (Mode 01 PID 04).

## Concurrency model

- Blocking Bluetooth/socket I/O runs on `Dispatchers.IO`, never the main thread.
- Live telemetry and DTC results are exposed as flows so fragments collect
  them lifecycle-aware.
- DTC AI assessments fan out per code in parallel coroutines and rejoin for display.

## Persistence

- `SharedPreferences` (`app_prefs.xml`) — AI provider/key/model/base URL,
  theme (`theme_mode`), language (`app_language`), speed source. The file is
  **excluded** from Android backup and device transfer (`backup_rules.xml`,
  `data_extraction_rules.xml`).
- `dtc_results.json` — cached DTC assessments in internal storage.
- Theme is a three-way pref (`system`/`light`/`dark`, `ThemeMode`); the
  legacy `dark_mode` bool migrates on first launch and is applied in
  `MainActivity.onCreate` before content.
- Language override (`system`/`en`/`es`/`ar`/`zh-CN`, `AppLanguage`) via
  `AppCompatDelegate.setApplicationLocales` + `locales_config.xml`.

## See also

- [OBD_CONNECTION.md](OBD_CONNECTION.md)
- [AI_PROVIDERS.md](AI_PROVIDERS.md)
- [BUILD.md](BUILD.md)
- [CODING_STYLE.md](CODING_STYLE.md)
- [TRANSLATION.md](TRANSLATION.md)
- [../SECURITY.md](../SECURITY.md)
