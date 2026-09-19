# AI Providers

How OBD2AI turns a fault code into a human-friendly assessment, which
providers are supported, and how to add one.

## Overview

`AiService` sends the same "expert mechanic" prompt to whichever provider
the user picked in Settings, then parses the returned JSON assessment into
`DtpCodeDTO` (severity, title, details, implications, suggested actions).
Each DTC is assessed **in parallel** on `Dispatchers.IO` and the results
rejoin for display in `ErrorOverviewFragment`.

Calls go over plain `HttpURLConnection` — there is deliberately **no vendor
SDK** (smaller supply chain, no key handling inside third-party code).

## Supported providers

| Provider | Key | Default model | Endpoint style |
| -------- | --- | ------------- | -------------- |
| OpenAI | OpenAI API key (required, no free tier) | `gpt-4o-mini` (cheapest) | `/chat/completions` |
| Google Gemini | AI Studio API key (required, free tier) | `gemini-3.1-flash-lite` (free tier) | `generateContent` |
| Anthropic | API key (required, paid-only) | `claude-haiku-4-5` (cheapest) | `/v1/messages` |
| Custom (OpenAI-compatible) | Optional (blank for keyless local servers) | `llama3.2` (free local) | `/chat/completions` at the user base URL |

Custom covers Ollama, LM Studio, llama.cpp, OpenRouter and anything else
speaking the `/chat/completions` API — e.g. `http://192.168.1.10:11434/v1`.
The server must be reachable from the phone (same Wi-Fi/host, correct port
and `/v1` path).

## Settings & prefs keys

Configured at runtime in **Settings** (nothing is baked into the build):

- `ai_provider` — selected provider.
- `ai_base_url` — custom provider base URL.
- `openai_api_key` / `openai_model_id` — legacy key names **reused as-is**
  for migration. Don't rename them; existing installs depend on them.

The key lives in private `SharedPreferences` (`app_prefs.xml`), excluded
from Android backup and device transfer. See [../SECURITY.md](../SECURITY.md).

Online AI extras (same screen, AI settings card): `real_ai_enabled`,
`ai_frequency` (low/normal/high), `ai_personality` (normal/funny/
professional), `ai_volume` (0–100).
No key is ever hard-coded; all of these are user-entered at runtime.

## Online AI voice: device only

Spoken replies use the Android device voice (volume + Test voice button in
Settings). There are no TTS provider keys to configure — Online AI needs an
API key only for its brain (fault explanations + event commentary), and
every reply also appears as text on the `onlineAiCard`.

## Online AI personality (brain-generated)

Personality reactions are NOT static lines: `OnlineAiPersonality` defines
*occasions* (speed tiers, RPM spike/high, warm coolant, new/returned
faults, combos, ultra-rare) with rarity, probability and cooldown, and the
brain writes fresh words from live readings guided by per-occasion style
examples. Frequency setting gates rarity (LOW keeps RARE+ only); a global
cooldown plus per-event cooldowns plus recent-line dedup keep it quiet.
Everything goes out at INFO severity through the normal card + queue, so
warnings/faults always win. To moderate, edit `styleExamples`; to retune,
edit the occasion table — the manager needs no changes.

## Parsing: lenient by design

Model output is messy — especially from local models — so assessment JSON
parsing stays lenient:

- `extractJsonObject` strips ``` fences and surrounding prose before parsing.
- Unknown fields are ignored; the result maps onto `DtpCodeDTO`.
- If no key is set, or a request fails, the code gets an **offline
  assessment** (`DtpCodeDTO.offline=true`: system + generic/manufacturer
  origin per SAE J2012) instead of an error. Bluetooth/OBD functionality is
  unaffected.

## Driving voice (Offline AI) is offline

The API key is used ONLY for fault-code explanations above. Offline AI is
an event system (shift point, high RPM/speed, coolant, new fault codes,
connection and engine state) that is fully offline: every cue is spoken
with on-device TTS and shown as a text banner (`res/raw` holds only short
warning tones now). It never touches the network and needs no key.
Offline AI and Online AI are mutually exclusive master switches in
Settings; each alert has its own enable switch and threshold.

## Adding a provider

1. Add one `AiProvider` enum entry.
2. Add one `post*` method in `AiService` for its HTTP shape + response extraction.
3. Wire it into the Settings provider spinner.
4. Reuse the existing pref keys above; add a unit-testable pure function
   (companion object) for any new parsing, with a test in `AppCoreTest.kt`.

## Troubleshooting

- **401/403** — wrong or revoked key; re-enter it in Settings.
- **Timeout / unreachable (Custom)** — server URL wrong, phone on a
  different network, or missing `/v1` path segment.
- **Garbled assessment** — local model too small or wrong format; try a
  larger instruct model. The lenient parser handles fences/prose, not
  completely unstructured rambling.
- **Everything offline** — check connectivity and logcat for the HTTP
  status; OBD reading keeps working regardless.

## See also

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [OBD_CONNECTION.md](OBD_CONNECTION.md)
- [FAQ.md](FAQ.md)
- [../SECURITY.md](../SECURITY.md)
