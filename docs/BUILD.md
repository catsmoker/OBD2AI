# Build From Source

Requirements and commands for building OBD2AI locally.

## Prerequisites

- **Android Studio** (recent stable; the bundled JBR is the JDK).
- **JDK 17** — set `JAVA_HOME` before any Gradle call (it is not set in
  this environment):

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:Path="$env:JAVA_HOME\bin;$env:Path"
```

- **Android SDK** with the platform/build-tools versions below.
- A physical Android device with Bluetooth (recommended) — emulators
  generally lack Bluetooth hardware (use demo mode there).

> Shell note: this repo documents Windows PowerShell 5.1, which has no
> `&&` — chain with `;` or `if ($?) { ... }`.

## Key versions

| Item          | Value                                    |
| ------------- | ---------------------------------------- |
| AGP           | 9.4.0                                    |
| Kotlin        | 2.4.20                                   |
| Compile SDK   | 37                                       |
| Target SDK    | 36                                       |
| Min SDK       | 24 (Android 7.0)                         |
| Build-tools   | 36.1.0                                   |
| Java          | 17                                       |
| Navigation    | 2.10.1                                   |
| kotlin-obd-api| 1.4.1 (via JitPack)                      |
| Version       | 1.0.0 (code 1)                           |

All dependency versions live in `gradle/libs.versions.toml` (version
catalog) — never edit them as string literals in `app/build.gradle.kts`.
`gradle.properties` pins `android.newDsl=false`.

## Build

From the repository root:

```powershell
.\gradlew.bat assembleDebug
```

(On Unix/macOS: `./gradlew assembleDebug`.)

The debug APK is produced at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install it with:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Running unit tests

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Single class / method (backtick test names contain spaces — use a wildcard):

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.catsmoker.obd2ai.AppCoreTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.catsmoker.obd2ai.AppCoreTest.splitErrors*"
```

Full check:

```powershell
.\gradlew.bat assembleDebug assembleRelease :app:testDebugUnitTest
```

Tests live in `app/src/test/...` (`AppCoreTest.kt`) and cover pure parsing
logic in companion objects. Notes:

- `android.util.Log` is a no-op on the JVM
  (`unitTests.isReturnDefaultValues = true`).
- `org.json` comes from `testImplementation(libs.org.json)` because the
  `android.jar` stubs throw "not mocked".
- The test JVM working directory is the `app/` module dir, so resource-file
  tests use `src/main/res/…` relative paths.
- Bluetooth, UI, TTS and AI-provider network code have **no tests** — don't
  claim changes there are "safe"; build + review instead.

## Lint

There is no lint/typecheck task — `assembleDebug` (Kotlin compile) plus the
unit tests is the verification.

## Release notes

- `isMinifyEnabled = true` + `isShrinkResources = true` for release; if a
  reflection-based dependency breaks only in release, check
  `app/proguard-rules.pro`.
- The release build type currently signs with the **debug keystore**
  (placeholder until a real keystore exists).
- `google-services.json` is committed by design (Firebase keys are
  identifiers, not secrets).

## Troubleshooting

- **SDK platform/build-tools missing** — install API 37 platform +
  build-tools 36.1.0 via SDK Manager and re-sync.
- **kotlin-obd-api not resolving** — it comes from **JitPack**, not Maven
  Central/Google (see `settings.gradle.kts`); check network access to
  `https://jitpack.io`.
- **`kotlinOptions` / `returnDefaultValues` errors** — with
  `android.newDsl=false` use `kotlin { compilerOptions { } }` and
  `unitTests.isReturnDefaultValues`; the old names do not resolve.
- **Bulk renames corrupting type names** — PowerShell `-replace` is
  case-insensitive; use `-creplace` or type names get lowercased and the
  build breaks.
- **Plain `registerReceiver` crashing on API 33+** — runtime receivers must
  use `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`.
