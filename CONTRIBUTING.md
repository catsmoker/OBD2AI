# Contributing

Thanks for wanting to help OBD2AI. This project is released under the
**Apache License 2.0** (see [LICENSE](LICENSE)), so please read that before
contributing — it affects what you can do with your contribution.

## Before you start

- **Open an issue first** for large changes or new features. This lets
  maintainers weigh in before you invest the time (and before a big PR shows
  up unannounced).
- Check the [open issues](https://github.com/catsmoker/OBD2AI/issues)
  for overlap — someone may already be working on it.
- Bug reports go to the [Issue Tracker](https://github.com/catsmoker/OBD2AI/issues).
  Include: device model + Android version, adapter model + connection type
  (Bluetooth/WiFi/demo), AI provider + model, and reproduction steps with a
  logcat snippet where possible.

## Development setup

See [docs/BUILD.md](docs/BUILD.md) for prerequisites and commands.
Quick version (Windows PowerShell — this repo's shell has no `&&`, use `;`):

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug          # build
.\gradlew.bat :app:testDebugUnitTest # run unit tests
```

Background reading for new contributors:

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — where everything lives.
- [docs/OBD_CONNECTION.md](docs/OBD_CONNECTION.md) — transports, ELM327 init, demo mode.
- [docs/AI_PROVIDERS.md](docs/AI_PROVIDERS.md) — providers and how to add one.
- [docs/CODING_STYLE.md](docs/CODING_STYLE.md) — conventions reviewers expect.
- [docs/FAQ.md](docs/FAQ.md) — common questions.

## What we look for in a PR

- Pure logic (parsers, DTO mapping, prompt/JSON handling) goes in a
  companion object with **no Android imports** and gets a **unit test** in
  the matching package's test file (e.g. `src/test/.../obd/ObdCommandsTest.kt`).
  There are existing patterns to copy in `obd/` and `ai/`.
- Follow the conventions in [docs/CODING_STYLE.md](docs/CODING_STYLE.md):
  no Jetpack Compose (Views/Fragments only), sizes in `dimens.xml`, strings
  in every locale file, and never report an invented `0` as a measurement.
- Keep the UI honest: if an adapter refuses a command or AI is unreachable,
  surface the real reason (or the offline assessment) instead of claiming
  success.
- Keep changes focused with small, clear commits. One feature per PR.

## Safety expectations

This app talks to a moving vehicle and sends data to AI providers.
Contributions must:

- **Never distract the driver.** No new UI that demands attention while
  driving; keep setup flows parked-car friendly.
- **Keep AI advisory.** Assessments must stay suggestions, never
  instructions that could cause unsafe repairs. Keep the disclaimer true.
- **Revert cleanly.** Anything a feature engages at "on" (demo mode,
  receivers, TTS) must have a symmetric "off". In particular: only
  `setupObd` / `setupWifi` / `setupDemo` own `demoMode` — teardown code must
  not reset it (see [docs/OBD_CONNECTION.md](docs/OBD_CONNECTION.md)).
- **Not introduce data collection.** The only network traffic is the user's
  own AI provider, ads (AdMob) and analytics (Firebase) — see
  [SECURITY.md](SECURITY.md).

## Secrets & API keys

- **No API key in the build.** Never add a `buildConfigField` /
  `local.properties` injection for the AI key — keys baked into BuildConfig
  are extractable from the APK. Keys are entered at runtime in Settings and
  live in private `SharedPreferences` only.
- Do not commit secrets of any kind. If you accidentally push one, rotate it
  immediately and tell a maintainer.

## License

By submitting a PR you agree to license your contribution under the
project's license terms (Apache-2.0). If your change adds a new dependency,
note its license in the PR — it must be compatible with Apache-2.0.
