# Translation & Localization

Current state of internationalization (i18n) in OBD2AI and how to add or
change strings.

## TL;DR

- User-visible copy lives in `app/src/main/res/values/strings.xml` and is
  translated in `values-es`, `values-ar` and `values-zh-rCN`.
- A **unit test pins key parity** across all locale `strings.xml` — a
  missing key in any locale fails the build.
- The app has a runtime language override (`app_language`:
  `system`/`en`/`es`/`ar`/`zh-CN`) via
  `AppCompatDelegate.setApplicationLocales`, backed by
  `res/xml/locales_config.xml` + the manifest `localeConfig`.

## How strings flow today

Fragments/layouts read through resources, never hardcoded literals:

```kotlin
context.getString(R.string.some_key, arg)
```

```xml
android:text="@string/some_key"
```

Formatting placeholders (`%1$s`, `%2$d`) are numbered so translations can
reorder arguments per language.

## Rules for every string change

1. **Same keys everywhere.** A new string must land in **all four** files:
   `values/strings.xml`, `values-es/strings.xml`, `values-ar/strings.xml`,
   `values-zh-rCN/strings.xml` — with identical names.
2. **Same format args.** Placeholders must match exactly across locales
   (same count, same types); only their order may change.
3. **Avoid apostrophes in translations.** They break Android string parsing
   unless escaped — rephrase instead.
4. **Only runtime-varying values become args.** Never split one sentence
   into two resources; that defeats per-language reordering.
5. **Don't translate identifiers.** App name, URLs, package names, OBD
   command strings and pref keys stay identical.
6. **No hardcoded dp/strings in layouts.** Sizes live in
   `values/dimens.xml` (+ `values-sw600dp` tablet overrides).

## Adding a new locale

1. Create `app/src/main/res/values-<qualifier>/strings.xml` (e.g.
   `values-fr/strings.xml`) mirroring **all** base keys.
2. Add the language to the `AppLanguage` options, the Settings language
   list, and `res/xml/locales_config.xml`.
3. Run `:app:testDebugUnitTest` — the parity test must pass.

## Adding a new string — checklist

1. Add to `values/strings.xml` (+ the matching `dimens.xml` entry if it
   needs sizing).
2. Mirror it in `values-es`, `values-ar`, `values-zh-rCN` (translate, or at
   minimum carry the English text so parity holds — then ask for a proper
   translation in the PR).
3. Reference via `@string/<name>` in XML or `getString(R.string.<name>)`
   in Kotlin.
4. Run the unit tests to confirm parity.

## Useful tooling

- Android Studio's **Extract string resource** refactoring (Alt+Enter on a
  hardcoded literal) is the fastest compliant way to add strings.
- The Translations Editor CSV round-trip works for team-managed
  translations.
- `HardcodedText` lint warnings: treat them as errors in new code.

## When hardcoded strings are acceptable

A short, deliberate allowlist:

- Version literals (`v${BuildConfig.VERSION_NAME}`).
- Dynamic values (DTC codes, adapter names, URLs) — use placeholders where
  they appear inside translatable sentences.
- Technical identifiers (AT commands, pref keys, package names).

Everything else user-facing must be a resource.
