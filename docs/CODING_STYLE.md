# Coding Style & Conventions

Conventions observed throughout the OBD2AI codebase. Follow these when
contributing so new code reads like the code around it.

## File organization

- Single-module app (`:app` only); all Kotlin lives in
  `app/src/main/java/com/catsmoker/obd2ai/` across exactly three files:
  - `AppCore.kt` — DTOs, OBD commands, helpers, services, prefs keys.
  - `UIComponents.kt` — fragments + RecyclerView adapters.
  - `MainActivity.kt` — wiring; fragments reach helpers via
    `(activity as MainActivity)`.
- Do not split these files or introduce new layers without discussing it
  in an issue first.

## Kotlin / Views

- **Compose is disabled — do not add Compose code.** UI is Views/Fragments
  with Navigation (fragment + ui KTX, Safe Args).
- **State drives UI.** Telemetry and DTC results flow through
  `ObdDataHolder` flows; fragments collect them lifecycle-aware.
- Blocking I/O (Bluetooth sockets, OBD reads, `HttpURLConnection`) goes on
  `Dispatchers.IO`, never the main thread.
- Parsing logic belongs in **companion objects as pure functions** so it is
  JVM-testable without Android imports.

## OBD & demo-mode discipline

- ELM327 init (`ATZ, ATE0, ATL0, ATS0, ATH0, ATSP0, ATAT1`) is a fixed raw
  AT sequence — `ATS0`/`ATH0` make responses deterministic across clones.
  Don't drop entries.
- Only `setupObd` / `setupWifi` / `setupDemo` own `demoMode`.
  `disconnectFromObdDevice()` must **not** reset it (LiveData teardown calls
  it; resetting silently kills demo and every later read fails).
- In demo there is no polling loop — sliders write `ObdDataHolder` flows
  directly.

## AI discipline

- DTC assessments return JSON parsed by `parseErrorInfo`;
  `extractJsonObject` stays lenient (strips fences/prose) because local
  models ramble.
- The API key is used ONLY for fault-code explanations — driving-voice
  cues (Offline AI) stay offline and must never gain a network call.
- Adding a provider = one `AiProvider` enum entry + one `post*` method in
  `AiService` + the Settings spinner wiring. Reuse the legacy
  `openai_api_key` / `openai_model_id` pref keys as-is for migration — don't
  rename them.

## Honesty rules (important)

A reviewer will expect these:

1. **Verify by read-back.** An OBD command that got bytes back is not proof
   the value is valid — validate/parse before displaying it.
2. **Never invent a `0`.** A car at `0` RPM with the ignition on, or `0`
   codes when the read actually failed, does not exist. Report
   unavailable/failed with the real reason instead.
3. **Distinguish "no data" from "failed".** An empty DTC list after a good
   read is healthy; an empty list after a failed read is an error state.
4. **Offline means offline.** Assessments produced without AI carry
   `offline=true` — never present them as AI-generated.
5. **Reversible by design.** Anything engaged at "on" (demo mode, receivers,
   TTS) needs a symmetric "off" that restores the prior state.

## Comments & documentation

- Comments explain *why*, not *what* — especially around ELM327 clone
  quirks, the `ATS0`/`ATH0` determinism assumption, and past regressions
  (e.g. why teardown must not touch `demoMode`).
- After an edit with preservation constraints, re-read the edited region
  before finalizing.

## Testing

- Pure logic gets a **unit test** in `AppCoreTest.kt`, with no Android
  dependencies. Follow the existing companion-object patterns.
- Test JVM cwd is the `app/` module dir — resource-file tests use
  `src/main/res/…` relative paths.
- `org.json` in tests comes from `testImplementation(libs.org.json)`.

## Strings, sizes & localization

- User-visible copy goes in `res/values/strings.xml` — never hardcoded in
  Kotlin or layouts. A unit test pins **key parity across all locale**
  `strings.xml`, so every new string must land in **every** locale file
  (`values`, `values-es`, `values-ar`, `values-zh-rCN`).
- Keep format args identical across locales and avoid apostrophes in
  translations. Use numbered placeholders (`%1$s`) so translations can
  reorder arguments. See [TRANSLATION.md](TRANSLATION.md).
- Sizes live in `values/dimens.xml` (+ `values-sw600dp` tablet overrides) —
  no hardcoded dp in layouts.
- Launcher icons are XML-only (adaptive `mipmap-anydpi-v26` + vectors) —
  don't add PNGs.

## Security

Any code touching connections, keys, prefs backup rules, or receivers must
follow the hard rules in [../SECURITY.md](../SECURITY.md) — no secrets in
the build, no secret logging, keep backup exclusions, keep receivers
non-exported, pin dependencies, and treat AI output as data.
