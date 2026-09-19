# Reference Inventory — OBD2AI vs `referance-obd2ai` (17 projects)

Research date: 2026-09-18. Read-only inspection; licenses were stripped from
the reference collection, so **check each upstream repo's terms before reusing
code**. Study the mechanism, write our own implementation.

Reference lives at `C:\Users\Lenovo\StudioProjects\referance-obd2ai`
(note the spelling). Each folder's short `README.md` is the index.
Extra deep docs: `carsense-app/MVI_ARCHITECTURE.md`,
`kotlin-obd-api/SUPPORTED_COMMANDS.md`,
`ExpeditionGauge/docs/help/ANDROID_AUTO.md`, `android-auto-torque/docs/`,
`ObdMetrics/doc/`.

## Where OBD2AI stands (baseline for the gaps below)

Single-module `:app`, Kotlin + Views/Fragments, 3 source files. Bluetooth SPP
+ WiFi TCP + demo, raw AT init `ATZ/ATE0/ATL0/ATS0/ATH0/ATSP0/ATAT1`,
stored/pending/permanent DTC + MIL + `dtc_results.json` cache, 4 AI providers
with offline fallback, Offline AI (fully offline voice+banner) + Online AI
(network brain + chat card), LiveData gauges + shift cue + edge-flash + siren,
4-card Settings, 5 locales, 3 themes, pure-logic unit tests in AppCoreTest.

---

## 1. AndrOBD — Android Java ELM327 app + library + demo

1. User-disableable AT commands + custom init injection (`ElmProt.java`) →
   "clone-compat" escape hatch for our hardcoded init.
2. `RSP_ID` + `STAT` taxonomy wider than NO DATA/ERROR (BUSERROR,
   BUFFERFULL, RXERROR…) → our honesty rule needs this granularity.
3. Software adaptive timing learner → faster polling on good cars.
4. Multi-frame reassembly (length header, `0:/1:/2:` lines, ISO stitching).
5. ECU discovery by sniffing `0100` with headers on → explains ghost dupes.
6. Virtual CAN-monitor service (`ATMA`) → future labs mode.
7. CSV PID + conversion catalog (432 PIDs, metric/imperial) → add PIDs faster.
8. `LinearConversion` with dynamic factor + limits.
9. Fixed-PID fast loop + per-PID deadlines → model for fast/slow split.
10. NRC `7F` matrix (display + retry/skip/reset reaction).
11. Per-item `MAX_ERROR_COUNT=3` → `n/a`, never `0` (our honesty rule).
12. Demo thread with real-issue vectors → regression fixtures for tests.
13. SPP fallback reflection + 500 ms post-connect delay (clones need it).
14. DTC 03/07/0A + freeze-frame 02 + mode-09 in one service switch.

## 2. android-obd-reader — Mode-01 library + trip stats

1. Blocking `run()` until `>` with SEARCHING strip.
2. Ordered `ERROR_CLASSES` check (`?` last — it matches everything).
3. `PersistentCommand` = cacheable-query marker (VIN + 0100 never re-query).
4. Supported-PIDs payload starts after 4-char `4100` echo.
5. DTC triple-branch decode + `P0000` terminator.
6. `DtcNumber 01 01`: MIL + count in one byte (cheap pre-check).
7. Init watchdog: 15 s join around blocking AT sequence.
8. Single-list round-robin poll with wrap (simplest correct model).
9. `AT ST` units are x4 ms (`Timeout(125)` = 500 ms).
10. Trip idle/drive split + distance from average speed (no GPS needed).
11. Hard accel/brake counter (`SPEED_GAP=20`) → offline driving score.
12. MAF fuel math + per-fuel tables + IMAP fallback (L/100km w/o MAF PID).
13. `SystemOfUnits` imperial switch at display layer (keep stored SI).

## 3. kotlin-obd-api — pure-Kotlin ELM327 library (our dependency)

1. One-class-per-PID pattern (`tag/mode/pid/handler/format`).
2. `ObdDeviceConnection.run()` serialized + dual timeouts + cache.
3. `ObdRawResponse` pipeline + `bufferedValue` (ATS0/ATH0 assumption here).
4. Exception order + sanitize + `7F 0x 11/12` + digit check.
5. `bytesToInt / calculatePercentage / getBitAt` single source.
6. Supported-PIDs bitmask (`00/20/40/60/80`, chained bit-32).
7. DTC 03/07/0A framing + `P0000` cut.
8. `0101` MIL + count + `0121/0131/014D/014E` context PIDs.
9. Monitor status spark vs compression decode → readiness UI.
10. VIN dual-stack parse (CAN multi-line + legacy).
11. AT catalog + recommended init (matches ours) + `ATRV`/`ATDPN` freebies.
12. Full Mode-01 formula table (copy-ready; verify `RuntimeCommand` PID).
13. `ObdProtocols/Switcher/AdaptiveTimingMode/Monitors` enums.

## 4. ObdMetrics — Java telemetry framework

1. JSON PID catalog (formula/min/max/priority/cache/alert flags).
2. `SupportedPIDsCodec` bitmask (confirms 15-char truncation).
3. Canonical init `ATZ/H0/L0/E0/AL/AT2` (validates we keep `ATS0/ATH0/SP0`).
4. Recovery groups for `CANERROR` (`ATWS`) and `STOPPED` (`\r`+`E0`).
5. Batch queries (`STPX`) → 2-3x poll rate on STN chips.
6. `Adjustments` feature flags → Settings checklist.
7. `CapabilitiesReader` accumulates supported PIDs → "ECU supports N PIDs".
8. UDS `19 02` DTC parser with status mask (fallback path).
9. DTC dictionary + translations → offline descriptions.
10. `AdapterErrorType` incl `LVRESET`/`FCRXTIMEOUT` (re-init vs retry).
11. Formula-driven mock with sawtooth strategies → realistic demo.
12. Rate + histogram diagnostics per metric (stuck-sensor detection).
13. Per-PID alert thresholds → declarative Offline AI rules.
14. TCP transport shares stream abstraction (validates our WiFi design).

## 5. carsense-app — Compose/MVI/Hilt diagnostics

1. Feature-based Clean structure (model IF 3-file split ever approved).
2. MVI Intent→reducer (portable as pure `reduce()` + test, no Compose).
3. Typed navigation single-top + clear-stack on disconnect.
4. Dashboard-as-hub with weighted Primary/Secondary cards.
5. Customizable gauge slots (add/remove grid) — our dashboard goal.
6. Dynamic gauge ranges with opt-out list.
7. Per-sensor gauge widgets (coolant/fuel/% need own arcs).
8. DTC 4-state screen (scanning/lost/healthy/error) — honesty rule.
9. Cached DTCs + silent backend upload (diagnostic UUID chain).
10. OBD console + sequential command tester (clone debugging).
11. Mileage-gated diagnostic creation (odometer context).
12. Vehicle profiles with swipe-restore (per-car thresholds).
13. Snapshot collector + upload badge (freshness-dot pattern).
14. Central error taxonomy + lifecycle-aware polling.

## 6. OBDvis — single-module explainable health rules

1. No-arch-framework discipline (closest cousin to our `:app`).
2. `DiagnosticsInterpreter` stateless rule engine → Offline AI evidence.
3. Fuel-trim severity + vacuum-leak/fuel-delivery/MAF cross-patterns.
4. Full-system rules (O2/catalyst/EGR/EVAP/voltage/thermostat/fuel-loop).
5. `OperatingState` detection with quantization guard (gate alerts).
6. `FindingStateManager` PENDING→ACTIVE→FADING 15 s debounce.
7. Post-drive + drive-history summaries (we have none).
8. Fuel-trim dive chart with limit lines.
9. `PriorityPollScheduler` weighted polling.
10. 60+ PID registry as data (formulas/colors/ranges in one table).
11. Stored-vs-pending pills + educational clear-confirm + offline `DtcInfo`.
12. CSV export via `CreateDocument` (share logs + AI assessments).
13. Physics-based 60 s `DemoSession` (richer than our sliders).
14. Read-only Android Auto tabs (phase-1 Auto target).

## 7. ExpeditionGauge — offline HUD + recording + Auto

1. Single `TelemetryBus` for UI/recording/alerts (no flow drift).
2. Declarative `DashboardPreset` + per-vehicle profile.
3. Recording (Room) + single-clock `PlaybackEngine`.
4. Threshold alerts spec (beep|TTS, 1 s repeat, snooze, history).
5. Auto full-bleed Surface HUD + DTC footer (phase-2 Auto target).
6. WiFi `tcp:host:port` validation + private-host guard (port as companion).
7. Live telemetry P2P (reserve extension point; do NOT build now).
8. BLE TPMS advertisement-first (only path to tire data; GATT budget note).
9. Visual contract tokens (black HUD, thick rings, edge numerals).
10. Lap/sector timing + offline export (trip computer package).
11. FOSS compliance (keep network users to AI + AdMob/Firebase only).

## 8. android-auto-torque — Torque Pro Auto frontend

1. AIDL IPC to Torque (possible 4th transport later; not v1).
2. 300 ms staggered polling + honest `--until-good-read`.
3. Dashboard density: 3 gauges + 4 displays + collapsible chart.
4. Up to 10 persisted dashboards (start JSON-in-prefs).
5. Per-PID editor (settings gold standard — copy field order).
6. EvalEx custom formulas (`a` variable, sandboxed, never crash).
7. Reorderable alarm editor (GT/GTE/EQ/LT/LTE + color).
8. 25+ OEM themes + brand fonts (layer skins later).
9. 22 s normalized 0-100 % chart (cross-unit trends).
10. Driver-safe swipe/rotary/chart-toggle + read-only-while-driving chat.
11. Phone settings rails (preview/export/logs/update split).
12. 16-locale precedent (process scales; keep our parity test).

## 9. Bluetooth-OBD-II-Diagnostic-Tool — thesis app, frame decoding

1. Per-mode `*ResponseCalculator` dispatcher (Mode 02/05/06/09 template).
2. One-class-per-PID formula library (~60, SAE-verifiable).
3. Supported-PID bitmask decoder (0100/0120/0140 + 0900).
4. Monitor-status/MIL/count + readiness bitfields.
5. Fuel-system dual-bank status (loop state for AI context).
6. DTC 2-byte bit-split decoder (cross-check for our parse).
7. Stored (`03`) vs pending (`07`) split (early-warning banners).
8. Freeze-frame Mode 02 reusing Mode 01 parsers (add only PID `02`).
9. Mode 09 sanitizer + VIN/IPT/CID/CVN/ECU-name (real clone mess).
10. Mode 05/06 stubs = negative lesson (implement fully or omit).
11. Per-mode frame gate + whitespace/prompt stripping.
12. `InvalidResponses` filter (preserve real reason for error display).
13. Enum-driven PID picker UX (add enum entry = add UI).
14. Raw terminal activity (we lack one; hidden debug screen).
15. Anti-patterns: hardcoded MAC, per-activity sockets, voltage bug (add test).

## 10. HonDash-android — Honda K-Pro USB cluster (NOT ELM327)

Cluster/HUD/serial ideas only: bulk poll cycle; byte-index table; pure
snapshot builder + `EMPTY` sentinel; isolated unit conversions; timeout +
empty-on-fail transport; USB permission version-branch pattern (copy for
future receivers); manifest auto-connect shape (future auto-reconnect);
backoff poll loop (3 strikes → error); keep-screen-on + identity line;
4-level warn/alarm bands persisted as JSON; live-gated alarm flash
(250 ms cadence); paged grid + add-slot; validated threshold dialog;
scripted simulator; reserved switch bitfield.

## 11. Hiworld-Can-Box — Flutter head-unit CAN listener

20-action candidate table; listen-only-while-subscribed + meta event;
generic extras→map payload; type-whitelist sanitizer; fuzzy RPM/speed
normalizer; dual-path emulator test (real path + direct inject);
sticky header with honest unknown; bounded 200-message ring log;
registration meta-event; minimal manifest; stale widget test (neg. lesson).
Note: uses deprecated `registerReceiver` — must upgrade to
`RECEIVER_NOT_EXPORTED` before any reuse (targetSdk 36).

## 12. M-Gauge — ESP32 CAN gauge (config/UX reference only, no source)

`SIGNAL:` DSL; `ROLE:` indirection (shift cue/siren follow roles);
`BIT:` status flags as tiles; `VALUE:`/`UNIT:` conditional text
(`0→N`, failure states); `ERROR:` custom fault text (user DTC notes);
`INTERNAL:` phone-sensors-as-inputs (generalizes in-memory GPS rule);
`SPEED:`+`FILTER:` CAN setup; endian + float32 per ECU; 3 ECU profiles;
per-signal alarm + 10-slot global monitor; full `setup.conf` knob
inventory; SD-override + web config deployment; custom art constraints;
firmware-only distribution (behavior ideas reusable, code is not).

## 13. Speedometer — gauge-view library (no OBD)

`Section` zones (data-driven SHIFT_RPM); 4 pure scope-math functions;
4 decoration archetypes = 4 styles from one core; 7 indicator styles;
normalized ticks + minor marks; digital unit layout params; animated
`setSpeed` with `onEnd`; legacy **Views** tick renderer (directly
portable); dual-arc ring + angle mapping; percentage-gauge gradient;
XML attrs + setters API; defensive `require()` guards.

## 14. Android-Speedometer — minimal Views gauge (best port fit)

Segmented 180-dash sport arc; on/off color duality + XML; `ShadowLayer`
glow (free HUD/neon); curved legend via `drawTextOnPath`; jitter-free
centered digital readout; clamping setter; square measure + preferred
size; listener bridge; ±demo buttons; layout-preview attrs;
zero-dependency (fix deprecated `MATRIX_SAVE_FLAG` if vendoring).

## 15. obdium — Rust/Tauri desktop (workflows, not code)

Tiered polling (500 ms/1 s/4 s/once + pause during DTC scan); `Command`
enum incl. arbitrary Mode 22; multi-ECU responses + A–E accessors; DTC
permanent flag + offline descriptions; readiness split; freeze-frame
redirect + banner; record/replay demo; custom PID with equation
validation; **hold-to-clear + DTC export**; 4x time-series graphs;
dim-to-disable cards + pause; offline VIN checksum + partial-mask
privacy + central unit conversion.

## 16. SwiftOBD2 — Swift ELM327 (logic ports; Apple APIs do not)

Minimal init comparison (keep our `ATH0`; theirs needs `ATH1`);
3-stage protocol detection + manual fallback; ECU map engine-vs-tx;
exhaustive Mode-01 table with `live` flags (= polling list + caps);
`commands.json` catalog; UAS units table + imperial; 20+ typed decoders
+ DTC bit math; CAN vs legacy parsers (legacy fixes J1850/14230);
**batched multi-PID one round-trip** (shift-cue latency); connection
states + timing log; pluggable transports; realistic mock; garage +
VIN + categorized redacted logging.

## 17. LTSupportAutomotive — Obj-C library (same portability note)

Fullest init (`ATRV` + `ATIGN` pre-checks; "ignition off" vs "no data");
**clone-war input filter** (biggest robustness gap); slow-init
per-protocol fallback; 11-state machine (`IgnitionOff`,
`UnsupportedProtocol` strings); serial command queue (single `Mutex`;
polling + DTC scan can collide today); protocol→decoder binding +
4.5 s heartbeat during long assessments; PID class hierarchy + support
cache; Mode 05/06/07/09/0A coverage (Mode 06 needs no AI); per-ECU DTC
+ localized explanations; ISO-3779 VIN model; capture-file sessions
(extend `DtcStore` pattern); voltage sanity range; BTLE-serial bridge
(pattern only — no BLE work needed on Android).

---

## Gap analysis (have vs missing)

| Area | Have | Missing |
|---|---|---|
| Diagnostics | 03/07/0A + MIL + AI JSON | Freeze-frame 02, readiness, Mode 06, offline DTC dict, per-ECU DTC, Mode 09 VIN/IPT |
| Robustness | fixed init, basic errors | clone filter, adaptive timing, STOPPED/CANERROR recovery, heartbeat, protocol fallback, batching |
| Dashboard | fixed gauges + shift cue + flash + siren | styles/presets, per-PID editor, custom PIDs, dynamic ranges, warn/alarm bands, graphs |
| Trips/history | last-scan cache | trip computer, history, post-drive summary, recording/playback, CSV/export |
| Tools | demo sliders | raw console, hold-to-clear, DTC export, capture replay, scripted demo |
| AI | per-code assessment, 2 assistants | **Ask-AI follow-up chat** (greenfield — no reference has it), rule evidence in prompts |
| Platform | phone, BT+WiFi | BLE/USB, auto-reconnect, Android Auto, units toggle, vehicle profiles, maintenance |

## Roadmap (phases, each reviewable)

Shipped 2026-09-18 (all test-first, full build green, 4-locale parity kept):
- **Ask-AI follow-up** (greenfield): detail-screen button + chat screen with
  full diagnostic context (code, MIL, VIN, capped history).
- **Phase 0**: `ElmSanitizer` + `ElmStatus`/`ElmRecovery` taxonomy,
  `PidHealthTracker` 3-strike per-PID budget wired into the fast loop,
  ATRV adapter voltage in the health line.
- **Pills + console**: `DtcSource` Stored/Pending/Permanent pills on the
  report + detail screens; raw OBD console (`sendRaw`, `ConsoleLog` ring,
  `ioMutex` serializing all socket traffic).
- **Phase 1**: bundled offline DTC dictionary (`res/raw/dtc_generic.json`,
  English-only like the Easter eggs) enriching offline assessments;
  mode-02 freeze-frame viewer on the detail screen; plain-text shareable
  diagnostic report.
- **Phase 2**: typed `recoveryFor` mapping (re-init on bus failures) in the
  live loop; batch-first fast reads (`010C0D0504` + `parseFastBatch`) with
  single-read fallback.
- **Phase 3**: metric/imperial display units (SI stays underneath),
  keep-screen-on toggle, RPM-driven `EngineSound` synthesizer (isolated,
  dies with mute).
- **Phase 4**: `TripComputer` + trip screen (time/distance/average/top
  speed/top RPM/idle, OBD- or GPS-fed, nothing persisted).

Deferred (need hardware, large surfaces, or backends — not started):
adaptive-timing UI, manual protocol fallback, readiness/Mode-06 screens,
OEM themes, Android Auto, BLE/USB transports, vehicle profiles,
maintenance reminders, recording/playback, CSV export, custom-PID editor,
per-PID gauge editor, dynamic ranges, dashboard presets.

Original phase plan (kept for reference):

- **0 — safety net:** clone filter, ordered error taxonomy, per-PID
  3-strike disable, ATRV/ATDPN in health line. Companions + tests.
- **1 — diagnostics depth:** pending/permanent pills, 0101/0131/014E
  header, offline DTC dict, readiness, freeze-frame, hold-to-clear +
  export, **Ask-AI follow-up**.
- **2 — robustness:** adaptive timing + ATST, auto-recovery, heartbeat,
  protocol fallback, batched reads, capability-gated poll lists.
- **3 — dashboard:** style switcher, warn/alarm bands, dynamic ranges,
  3+4+chart layout, presets, units toggle, normalized chart.
- **4 — trips & memory:** trip computer, history, post-drive summary, CSV,
  recording/playback v1, scripted demo, profiles, reminders.
- **5 — platform:** raw console, auto-reconnect, BLE then USB, Auto
  read-only tabs → Surface HUD, custom-PID editor.
- **Entertainment (after 1):** RPM-driven engine-sound simulator, isolated
  module behind its own switch, killed by mute.

- **0 — safety net:** clone filter, ordered error taxonomy, per-PID
  3-strike disable, ATRV/ATDPN in health line. Companions + tests.
- **1 — diagnostics depth:** pending/permanent pills, 0101/0131/014E
  header, offline DTC dict, readiness, freeze-frame, hold-to-clear +
  export, **Ask-AI follow-up**.
- **2 — robustness:** adaptive timing + ATST, auto-recovery, heartbeat,
  protocol fallback, batched reads, capability-gated poll lists.
- **3 — dashboard:** style switcher, warn/alarm bands, dynamic ranges,
  3+4+chart layout, presets, units toggle, normalized chart.
- **4 — trips & memory:** trip computer, history, post-drive summary, CSV,
  recording/playback v1, scripted demo, profiles, reminders.
- **5 — platform:** raw console, auto-reconnect, BLE then USB, Auto
  read-only tabs → Surface HUD, custom-PID editor.
- **Entertainment (after 1):** RPM-driven engine-sound simulator, isolated
  module behind its own switch, killed by mute.
