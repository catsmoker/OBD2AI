# Architecture

A high-level map of the OBD2AI Android codebase and how the layers fit together.

## Stack

| Concern       | Choice                                                          |
| ------------- | --------------------------------------------------------------- |
| Language      | Kotlin 2.4.21                                                    |
| UI            | Jetpack Compose 1.13 (Material 3 Expressive), single activity     |
| Glass         | `io.github.kyant0:backdrop` 2.0.1, gated by API level             |
| Navigation    | `androidx.navigation:navigation-compose` (route strings)          |
| DI            | Hilt 2.60.1 + KSP; `ObdHelper`/`BluetoothHelper` are `@Singleton` |
| Async         | Kotlin Coroutines + Flow                                          |
| JSON          | `org.json` with lenient fence/prose stripping                     |
| OBD           | `kotlin-obd-api` via JitPack + raw ELM327 AT strings               |
| AI            | Plain `HttpURLConnection`, no vendor SDK                          |
| Min / target  | API 24 (Android 7.0) / API 36                                     |
| Compile SDK   | 37 (minor 2), build-tools 36.1.0, Java 17                         |

## Module layout

Five Gradle modules. Pure logic lives in the `core` libraries so it can be
unit-tested on the JVM; `:app` keeps UI, the Activity and the View-hosted
gauges.

```
:app ─ UI + wiring (MainActivity, ui/, audio/, speedometers/, instruments/, ads/)
:core:prefs        # PrefsKeys, Units, ThemeMode, AppLanguage
:core:obd          # ObdCommands, PidRegistry, ElmProtocol, BluetoothHelper,
                   # ObdHelper, VehicleTelemetry (ObdDataHolder, SlowTelemetry,
                   # DemoObdSource), vehicle/TripComputer + TripStore
:core:diagnostics  # DiagnosticModels (DtpCodeDTO, DtcSource, ErrorSeverity,
                   # FreezeFrame, DtcDictionary, MilStatus), DtcStore, ScanCache
:core:ai           # AiProviders (AiProvider, AiService), OnlineAi (manager,
                   # personality, frequency), SpeechQueue
```

Each module owns its `src/test`. Because package directories were moved
mechanically, some `core` sources sit one level deeper than their package
(`core/obd/.../obd/obd/`, `core/obd/.../vehicle/vehicle/`) — harmless, but
don't copy the pattern for new files.

## UI layer (`:app`)

Compose only: one activity, one composition, no Fragments, no
`nav_graph.xml`, no XML layouts or menus, `viewBinding` off.

- `ui/AppRoot.kt` — `Column { NavHost(weight 1); AdBanner }`. Routes:
  `home`, `setup`, `permissions`, `connect`, `live`, `diagnostics`,
  `errorDetail/{errorCode}`, `askAi/{errorCode}`, `trip`, `console`,
  `settings`, `about`, `legal/{page}`. The AdMob `AdView` is hosted with
  `AndroidView`; `MainActivity` owns the instance and the load gate.
- **Navigation model: Home is the only hub.** There is no bottom bar and no
  rail (deliberately removed, not hidden). Home shows the connection hero
  plus a tile grid (Dashboard / Diagnostic Report / Trip / Console) once
  connected or in demo, and a "Saved fault codes" tile while disconnected
  when a saved scan exists. Settings/About are header icons. Every
  secondary screen carries the in-layout back arrow plus system Back.
- `ui/home/HomeDestination.kt` — reads `ConnectionStateStore`, starts/leaves
  demo, routes the connect-gated drill-downs (`openConnected`), and loads
  whether a saved scan exists (`HomeTiles.showSavedDiagnostics`).
- `ui/common/` — `ConnectionState` (pure, tested), `ConnectionStateStore`
  (shared `StateFlow`s: connection state + `lossReason`), `DemoSession`
  (remembers that demo was running so a relaunch can restore it),
  `UiSettings`.
- `ui/connect/` — `ConnectScreen` + `ConnectViewModel` (one button that
  rescans or stops scanning, named progress, sticky failure card),
  `ConnectFailure` (`ConnectFailureKind`, pure, tested) and
  `TransportMemory` (advisory "last connected via" hint).
- `ui/glass/` — `AppBackdropHost`, `Modifier.glass`, `GlassCard`. The
  recorded backdrop layer must never contain the glass surfaces that sample
  it (render-node cycle → HWUI stack overflow). Glass degrades to a tinted
  surface below API 31 and skips refraction below API 33.
- `ui/theme/` — the design system: `Color`, `Shape`, `Type`, `Motion`,
  `Spacing`. Spacing/radii come from these tokens; 48 dp minimum targets.
- `ui/dashboard/` — `LiveSession` (polling, detectors, TTS/sound lifecycle),
  `LiveSessionAi`, `LiveLogic` (pure RPM zone/tier/Easter-egg logic),
  `LiveScreenUi`, `LiveDestination`.
- `ui/diagnostics/` — `ErrorOverviewScreen` (+ViewModel), `ErrorDetailScreen`,
  `AskAiScreen` + `AskAiState` (rotation-safe codec), `FreezeFrameCache`,
  `DiagnosticsSelection`, `CachedScanLabel`.
- `ui/trip/` — `TripSession` (app-wide trip), `TripScreen`, `TripSample`
  (what the timer may believe), `TripNotice`.
- `ui/setup/SetupScreen.kt` — two-step first launch (agreement → language +
  theme) with `SetupState` (pure, tested).
- Custom gauges (`speedometers/`, `instruments/`) stay classic Views, hosted
  through `AndroidView`.

## App flow

```
"setup" (first launch only: agreement → language + theme)
   └─► "home"
"home"  ─ Connected / Demo ─► tiles ─► live | diagnostics | trip | console
        └ Disconnected + saved scan ─► diagnostics (offline review)
        └ Connect ─► permissions (if needed) ─► "connect" ─► connected
        └ Try demo ─► demo (same screens, simulated data)
        header icons ─► settings | about
"diagnostics" ─► "errorDetail/{code}" ─► "askAi/{code}"
```

State notes: `demoMode`/`isConnected` (ObdHelper) are the only connection
truth — no parallel model. Only `setupObd`/`setupWifi`/`setupDemo`/
`disconnectAll` own `demoMode`; `disconnectFromObdDevice()` must not reset it
(LiveData teardown calls it, and resetting kills demo silently).

## Failure honesty

One vocabulary for every transport problem:

- `ConnectFailureKind` (UNREACHABLE / NOT_PAIRED / BLUETOOTH_UNAVAILABLE /
  GENERIC) titles the Connect card, the Live notice
  (`TransportFailureNotice`), the Diagnostics scan panel and `ErrorDialog`.
  Unrecognised text stays GENERIC; the raw message is always kept as the
  second line.
- A socket that dies mid-session calls `onTransportLoss(reason)` (timeouts
  are excluded); `ConnectionStateStore.reportLoss` keeps the reason while
  disconnected and Home shows "Connection lost: …".
- When live monitoring gives up, `ObdDataHolder.liveErrorFlow` carries the
  reason and the dashboard offers Retry (only while the adapter is still
  connected).
- Demo has no DTC source: Diagnostics disables Scan/Clear there and says so,
  rather than inventing codes or failing after a pointless read.
- Trip distance is sampled only from a live source (`TripSample`), so a
  stopped poll cannot integrate a stale speed into distance.

## Key components

### `BluetoothHelper` (`core:obd`)
Permission handling, discovery, pairing and the RFCOMM socket (SPP UUID
`00001101-0000-1000-8000-00805F9B34FB`).

### `ObdHelper` (`core:obd`)
Owns the connection and commands. Sends the fixed ELM327 init sequence
`ATZ, ATE0, ATL0, ATS0, ATH0, ATSP0, ATAT1` — `ATS0`/`ATH0` make
spaces/headers deterministic across clones, which the parsers assume; don't
drop them. All reads branch on `demoMode`. See [OBD_CONNECTION.md](OBD_CONNECTION.md).

### `AiService` / `AiProvider` (`core:ai`)
Sends the same "expert mechanic" prompt to the selected provider and parses
the JSON assessment into `DtpCodeDTO`. The API key is used **only** for
fault-code explanations. See [AI_PROVIDERS.md](AI_PROVIDERS.md).

### `ObdDataHolder` + `DtcStore` (`core:obd` + `core:diagnostics`)
- `ObdDataHolder` — in-memory flows for live telemetry, DTC results,
  `liveErrorFlow`, and the `isMonitoring` flag.
- `DtcStore` — persists assessments to `dtc_results.json` as
  `{savedAtMs, codes}` (legacy bare arrays still parse); `ScanCache` keeps the
  parsed copy in-process. The overview restores the saved scan and labels it
  as saved; details fall back to it when the holder is empty.
- `DtpCodeDTO.offline=true` marks the no-key/offline fallback assessment.

### Two assistants, kept independent (`core:ai` + `ui/dashboard/`)
- **Offline AI** — offline event system (`OfflineAiEvent`: shift point, high
  RPM/speed, coolant, new fault codes, connection lost/restored, engine
  started/stopped) with per-event switches and thresholds, master switch,
  voice/text outputs, volume; every cue uses on-device TTS and the banner
  card. No key, no network, ever. Easter eggs live in `values/arrays.xml`
  and fire probabilistically after normal warnings.
- **Online AI** — master switch plus voice/text outputs (at least one stays
  on), `OnlineAiManager` → `AiService.chatText` → Online-AI card +
  `SpeechQueue` (device voice only). Personality reactions always go out at
  INFO severity.
- The two masters are mutually exclusive; the first Settings open normalises
  a legacy both-ON state to Offline.

## Concurrency model

- Blocking Bluetooth/socket I/O runs on `Dispatchers.IO`, never the main
  thread; disk writes (trip checkpoint, DTC cache) too.
- Live telemetry and DTC results are flows, collected with
  `collectAsStateWithLifecycle` (or a session scope in `LiveSession`).
- Polling runs only while the dashboard is open; leaving Live tears the
  monitor down (rotation keeps it via `isChangingConfigurations`).
- DTC assessments fan out per code in parallel coroutines and rejoin.

## Persistence

- `SharedPreferences` (`app_prefs.xml`) — AI provider/key/model/base URL,
  theme (`theme_mode`), language (`app_language`), speed source, AI switches,
  `demo_session_active`, `last_transport`. Excluded from backup and device
  transfer (`backup_rules.xml`, `data_extraction_rules.xml`).
- `dtc_results.json` — cached DTC assessments plus their save time.
- `trip_state.json` — the trip in progress (`TripStore`), written every
  ~10 samples and on `onStop`; a trip resumes only if its last sample is
  recent, otherwise it returns stopped with its totals.
- Theme is a three-way pref (`ThemeMode`); legacy `dark_mode` migrates on
  first launch and is applied in `MainActivity.onCreate` before content.
- Language override (`AppLanguage`) via
  `AppCompatDelegate.setApplicationLocales` + `locales_config.xml`; the
  manifest declares `uiMode|locale|layoutDirection` config changes so previews
  arrive without recreation.

## See also

- [NAVIGATION.md](NAVIGATION.md)
- [OBD_CONNECTION.md](OBD_CONNECTION.md)
- [AI_PROVIDERS.md](AI_PROVIDERS.md)
- [UI_DESIGN.md](UI_DESIGN.md)
- [BUILD.md](BUILD.md)
- [CODING_STYLE.md](CODING_STYLE.md)
- [TRANSLATION.md](TRANSLATION.md)
- [../SECURITY.md](../SECURITY.md)
