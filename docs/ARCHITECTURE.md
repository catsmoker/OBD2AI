# Architecture

A high-level map of the OBD2AI Android codebase and how the layers fit together.

## Stack

| Concern       | Choice                                                       |
| ------------- | ------------------------------------------------------------ |
| Language      | Kotlin 2.4                                                   |
| UI            | Jetpack Compose (Material 3), single activity — no XML UI    |
| Navigation    | `androidx.navigation:navigation-compose` (route strings)     |
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
├── prefs/            # PrefsKeys, Units, ThemeMode, AppLanguage, NavStyle
├── obd/              # ObdCommands, PidRegistry, ElmProtocol, BluetoothHelper,
│                     # ObdHelper, VehicleTelemetry (ObdDataHolder, DemoObdSource)
├── diagnostics/      # DiagnosticModels, DtcStore
├── vehicle/          # TripComputer
├── ai/               # AiProviders (AiProvider, AiService), OnlineAi, SpeechQueue
├── audio/            # EngineSound (isolated synth hum)
├── speedometers/     # SpeedometerStyle, SpeedometerHost, BaseSpeedometerView + styles
├── instruments/      # RpmGaugeView (tachometer), CoolantGaugeView,
│                     # VoltageGaugeView, FuelGaugeView (cluster instruments)
├── ui/               # Compose UI: one AppRoot + per-package screens and
│                     # destinations (see below)
└── MainActivity.kt   # helpers + setContent { AppRoot(...) }; no fragments
```

### UI layer (`ui/`)

The UI is **Jetpack Compose only**: one activity, one composition, no
Fragments, no `nav_graph.xml`, no XML layouts or menus. There are no
`Fragment` classes anywhere; `viewBinding` is off.

- `ui/AppRoot.kt` — the single-activity root: the Navigation Compose graph
  (routes: `home`, `setup`, `permissions`, `connect`, `live`, `diagnostics`,
  `errorDetail/{code}`, `askAi/{code}`, `trip`, `console`, `settings`,
  `about`, `legal/{page}`), the chrome (phone bottom dock / tablet
  navigation rail with a back-arrow header) and the global ad banner
  (the AdMob `AdView` is hosted with `AndroidView`; `MainActivity` owns the
  instance and the load gate).
- `ui/common/AppNav.kt` — pure route-based navigation logic: chrome
  visibility (`isChromeHidden`), section highlight (`sectionFor`), back-arrow
  rule, cards-mode check. Unit tested.
- `ui/common/ConnectionStateStore.kt` — shared `StateFlow`s (connection
  state, cards mode). Chrome visibility derives from these in composition;
  a transport loss just refreshes the store and Compose recomposes.
- Per-feature packages hold **screens** (stateless-ish composables, e.g.
  `HomeScreen`, `ConnectScreen`, `ErrorOverviewScreen`) and thin
  **destinations** (`*Destination.kt`) that own navigation, DTO lookups and
  dialogs. Destinations reach the shared helpers via the `MainActivity`
  reference passed down from `AppRoot`.
- `ui/dashboard/` — the Live dashboard is split for testability:
  `LiveSession` (polling session, detectors, TTS/sound lifecycle, owned by
  the composition), `LiveSessionAi` (offline/online AI cue pipeline as
  extensions), `LiveLogic` (pure RPM-zone/tier/Easter-egg logic, unit
  tested), `LiveScreenUi` (the gauges/cards UI), `LiveDestination`
  (lifecycle wiring: session starts on composition, tears down on leaving
  it — rotation keeps the socket and the poll loop alive).
- `ui/setup/SetupScreen.kt` — the 3-step first-launch flow (agreement →
  language/theme → navigation style) with `SetupState` (pure, tested);
  language/theme previews apply live without recreation.
- `ui/components/` — shared composables (`ScreenScaffold`, `SectionCard`,
  `ObdButton`, `ErrorDialog`, …); `ui/theme/` — Material 3 theme.
- Custom gauges (`speedometers/`, `instruments/`) remain classic Views,
  hosted through `AndroidView` inside the Compose tree.

## App flow

```
"home" (welcome screen + state-aware main menu, start destination)
 ├── Hero: title, description; entry actions when unlinked
 │   (Connect to OBD Device / Try Demo), disconnect/exit-demo when linked
 ├── ConnectionState (DISCONNECTED: settings+about only; CONNECTED/DEMO:
 │   + dashboard, diagnostics, trip, console)
 ├── Get Started → "permissions" → "connect" ─► (connected)
 ├── Try Demo → setupDemo() in place ─► demo, same screens+data
 └── Cards mode → Home tiles → sections ─► Back ─► home

"diagnostics" (reads DTCs, assesses each code via AI in parallel;
button-driven, fails gracefully when unlinked)
                                               ↓
                          "errorDetail/{code}" ←→ "live"
                          (cached dtc_results.json  (gauges + AI
                           fallback)                  voice insight)
```

State notes: `demoMode`/`isConnected` (ObdHelper) are the only connection
truth — no parallel model. Demo branches every read (DemoObdSource), so
demo reuses the exact same screens. Leaving Live tears down *real*
transports (demo survives by design); the Home destination re-reads the
state on resume, so a dead connection honestly shows the disconnected menu
again. Explicit disconnect/exit-demo goes through
`ObdHelper.disconnectAll()`; passive teardown (`disconnectFromObdDevice`,
Live only) never clears demo.

First launch shows the setup flow (`ui/setup/SetupScreen.kt`, start
destination while the setup is incomplete): agreement (Terms acceptance
required, optionals separate), language, theme and navigation-style
choices — all written to the same prefs Settings uses and applied through
the same MainActivity methods. Continue records the choice and pops back to
the menu; SDKs stay gated by the choice rules until then.

Section jumps (dock/rail) collapse the back stack to the home root
(`popUpTo("home")`, single top), so Back always returns toward the menu.
The visible back arrow (rail header on tablets) calls `navigateUp()` — the
same action as system Back / back gesture — and is hidden on the home root
and the transient setup screens (permissions, connect), where the whole
chrome hides. In bar mode the dock tabs are **Console / Live / Diagnostics
/ Trip** — there is no Home tab: a successful connect or Try Demo lands
straight on the dashboard, and Home survives only as the disconnected
connect screen (reachable via system back). In cards mode (chosen at first
start, changeable in Settings) chrome never shows; Home tiles drive
everything.

Tablets (width ≥ 600 dp) navigate top-level destinations with a navigation
rail (Console, Dashboard, Diagnostics, Trip, plus a header back arrow on
secondary screens); phones use the bottom dock with the same tabs.

Speed source can be the OBD device or the phone GPS (`speed_source` pref).

## Dependency direction

UI destinations/screens depend on the helper packages, not the other way
around:

```
ui.* (destinations/screens) ──►  obd / ai / diagnostics / prefs / vehicle / audio / speedometers / instruments
            MainActivity (wiring) ──►  obd / ai
                                       ──►  Android framework + 3rd-party SDKs
```

Pure logic lives in `object`s/companion objects so it runs as JVM unit tests
without Android (tests mirror the packages, e.g.
`src/test/.../obd/ObdCommandsTest.kt`, `src/test/.../ui/dashboard/LiveDataLogicTest.kt`
targets `LiveLogic`).

## Key components

### `BluetoothHelper` (`obd/`)
Permission handling, discovery, pairing and establishing the RFCOMM socket
(SPP UUID `00001101-0000-1000-8000-00805F9B34FB`). Its permission `LiveData`
is observed in Compose with `observeAsState`.

### `ObdHelper` (`obd/`)
Owns the OBD connection and commands. Sends the fixed ELM327 init sequence
`ATZ, ATE0, ATL0, ATS0, ATH0, ATSP0, ATAT1` — `ATS0`/`ATH0` make
spaces/headers deterministic across clones, which the parsers assume; don't
drop them. Custom commands (`MySpeedCommand`, …) cover speed/RPM/coolant.
All reads branch on `demoMode`. A non-timeout `IOException` during a read
triggers `onTransportLoss()` (passive, demo-preserving teardown +
`onConnectionLost`); plain timeouts never end the session. See
[OBD_CONNECTION.md](OBD_CONNECTION.md).

### `AiService` / `AiProvider` (`ai/`)
Sends the same "expert mechanic" prompt to the selected provider and parses
the JSON assessment into `DtpCodeDTO`. The API key is used ONLY for these
fault-code explanations — driving-voice cues (Offline AI) are fully offline
and never touch the network. See [AI_PROVIDERS.md](AI_PROVIDERS.md).

### `ObdDataHolder` + `DtcStore` (`obd/` + `diagnostics/`)
- `ObdDataHolder` — in-memory flows for live telemetry and DTC results.
- `DtcStore` — persists assessments to internal file `dtc_results.json`;
  the error-detail destination falls back to it when the holder is empty.
- `DtpCodeDTO.offline=true` marks the no-key/offline fallback assessment.

### `DemoObdSource` (`obd/`)
Simulated adapter. `setupDemo()` sets `ObdHelper.demoMode`; in demo, the
demo control sheet (Compose `ModalBottomSheet`) writes `ObdDataHolder`
flows directly — there is no demo polling loop.

### Two assistants, kept independent (`ai/` + `ui/dashboard/`)
- **Offline AI** — offline event system: `OfflineAiEvent` (shift point, high
  RPM/speed, coolant, new fault codes, connection lost/restored, engine
  started/stopped) with per-event enable switches and thresholds, master
  switch, voice/text outputs and volume; every cue is spoken with on-device
  TTS and shown in the bottom banner card. No key, no network, ever.
  Optional community Easter eggs (`values/arrays.xml` pools +
  `LiveLogic.easterPoolConfig` odds) fire probabilistically after normal
  warnings with their own cooldowns and no-repeat tracking — Offline AI only.
- **Online AI** — online diagnostic companion: master switch plus voice/text
  outputs (at least one stays on), `OnlineAiManager` (event/decision layer
  with cooldowns) → `AiService.chatText` (same provider/key as fault
  explanations) → bottom Online-AI card + `SpeechQueue` (device voice only —
  no TTS keys, no cloud voice). Personality reactions (`OnlineAiPersonality`
  occasions: speed tiers, RPM spike/high, warm coolant, new/returned faults,
  combos, 2% ultra) are generated live by the brain from actual readings and
  always go out at INFO severity, so faults/warnings preempt or evict them.
  Slow telemetry (`readSlowTelemetry`: DTCs, voltage, fuel) polls immediately
  on entry then ~every 30 s; the fast gauge loop only gained engine load
  (Mode 01 PID 04).

## Concurrency model

- Blocking Bluetooth/socket I/O runs on `Dispatchers.IO`, never the main thread.
- Live telemetry and DTC results are exposed as flows; screens collect them
  with `collectAsStateWithLifecycle` (or a session scope in `LiveSession`).
- DTC AI assessments fan out per code in parallel coroutines and rejoin for display.

## Persistence

- `SharedPreferences` (`app_prefs.xml`) — AI provider/key/model/base URL,
  theme (`theme_mode`), language (`app_language`), speed source, navigation
  style (`nav_style`). The file is **excluded** from Android backup and
  device transfer (`backup_rules.xml`, `data_extraction_rules.xml`).
- `dtc_results.json` — cached DTC assessments in internal storage.
- Theme is a three-way pref (`system`/`light`/`dark`, `ThemeMode`); the
  legacy `dark_mode` bool migrates on first launch and is applied in
  `MainActivity.onCreate` before content.
- Language override (`system`/`en`/`es`/`ar`/`zh-CN`, `AppLanguage`) via
  `AppCompatDelegate.setApplicationLocales` + `locales_config.xml`; the
  manifest declares `uiMode|locale|layoutDirection` config changes so
  setup previews arrive without recreation.

## See also

- [OBD_CONNECTION.md](OBD_CONNECTION.md)
- [AI_PROVIDERS.md](AI_PROVIDERS.md)
- [BUILD.md](BUILD.md)
- [CODING_STYLE.md](CODING_STYLE.md)
- [TRANSLATION.md](TRANSLATION.md)
- [NAVIGATION_AUDIT.md](NAVIGATION_AUDIT.md)
- [../SECURITY.md](../SECURITY.md)
