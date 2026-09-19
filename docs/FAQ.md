# FAQ

Common questions about building, running, and contributing to OBD2AI.

## Why does the app need Bluetooth + location permissions?

Bluetooth SPP is how the app reaches the ELM327 adapter. Android additionally
requires location permission for Bluetooth discovery on older versions, and
`BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` on Android 12+. The app does not track
your position — location is only used for discovery (and the optional GPS
speed source, which stays in memory).

## Do I need a car / adapter to try the app?

No. Use **Demo mode** from the connect screen: a simulated adapter drives
the whole flow, and sliders feed the live-data gauges directly. Perfect for
development and screenshots.

## Do I need an AI API key?

No. Without a key (or when a request fails) every code still gets an
**offline assessment** (`offline=true`) with system + generic/manufacturer
origin per SAE J2012. The key only unlocks the richer AI explanations.
Local servers (Ollama, LM Studio, …) need no key at all — leave it blank.

## Which AI providers work?

OpenAI, Google Gemini, Anthropic, or any OpenAI-compatible server via the
Custom provider. Details and defaults in [AI_PROVIDERS.md](AI_PROVIDERS.md).

## Can I use it with an emulator?

Partly. Emulators generally lack Bluetooth hardware, so use **Demo mode**
or a WiFi adapter reachable from the emulator. Everything else (DTC UI, AI
assessments, gauges, settings) works normally.

## My adapter connects but returns no data. Is that a bug?

Usually not. Check: ignition on, adapter firmly seated, correct transport
(Bluetooth vs WiFi), and logcat for the AT init exchange. Many clones are
picky about timing — `ATAT1` adaptive timing is already enabled for that
reason. See [OBD_CONNECTION.md](OBD_CONNECTION.md).

## The UI shows an offline assessment — did AI break?

That is the design, not a failure mode: no key, no network, or a failed
request falls back to the offline assessment so diagnostics keep working.
Check [AI_PROVIDERS.md](AI_PROVIDERS.md) troubleshooting if you expected an
AI result.

## Why does Live Data speak without an API key?

Driving-voice cues (Offline AI) are fully offline: bundled `res/raw` clips with
on-device TTS fallback. The API key is used ONLY for fault-code
explanations on the Diagnostic Report screen.

## How do I build the debug APK?

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```

Full details in [BUILD.md](BUILD.md).

## My PR adds a user-visible string — what must I do?

Add it to **every** locale `strings.xml` (`values`, `values-es`,
`values-ar`, `values-zh-rCN`) with identical format args — a unit test pins
key parity and fails otherwise. Sizes go in `dimens.xml`, not hardcoded.
See [TRANSLATION.md](TRANSLATION.md) and [CODING_STYLE.md](CODING_STYLE.md).

## Is my API key safe?

It lives in the app's private `SharedPreferences`, excluded from Android
backup and device transfer, and is never baked into the build or logged.
See [../SECURITY.md](../SECURITY.md).

## Is the AI diagnosis trustworthy?

It is **advisory only** — a starting point, not a repair order. Always have
a qualified mechanic confirm before replacing parts or clearing codes you
don't understand.
