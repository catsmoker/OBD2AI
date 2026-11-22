# OBD2AI visual design

[Home, Live data and the fault report, captured on an API 37 phone emulator
(1080x2400) against a simulated ELM327 adapter with no AI key configured —
hence the offline assessments and the unknown voltage/fuel readings](instrument-preview.webp)

Settings and About both open from the Home header icons:

![The Settings list and the About screen](about-in-settings.webp)

The visual model is a rally instrument book: warm paper for reading, graphite
instruments for measurements, and phosphor lettering for the main readout. The
16-pin socket illustration on Home and setup is decorative; its pins never
indicate connection status.

Home is the only hub, so it indexes destinations as a tile grid that reflows
between one and two columns by available width (`R.integer.home_grid_columns`).
There is no bottom bar and no rail: Settings and About are header icons, and
every secondary screen carries an in-layout back arrow. Live uses segmented
scales; Trip puts elapsed time above a ruled timing sheet; the fault report
distinguishes a fresh scan, a saved scan and an unread vehicle; the console has
a dedicated monospace transcript.

Rounding, spacing and motion come from `ui/theme` (`ObdSpacing`, `ObdRadius`,
`ObdShapes`, `Motion`). Frosted glass (`ui/glass`) is reserved for floating and
hero surfaces — never for every list card — and always degrades: below API 31 it
becomes a tinted surface, below API 33 it skips refraction. Text on glass must
stay readable in both themes; blur alone is never a contrast source.

The day/night colors in `app/src/main/res/values/colors.xml` and `values-night/colors.xml`
are shared by Views and the Compose theme. Compose derives surface tones from
the current primary and surface colors. Gauge warning colors remain separate.

| Reference | Application |
| --- | --- |
| [Android Liquid Glass](https://github.com/Kyant0/AndroidLiquidGlass) | Adopted as the `io.github.kyant0:backdrop` dependency for hero/floating surfaces, API-gated with a tinted fallback; the instrument face stays opaque for in-car readability. |
| [Material Design 3 UI Skill](https://github.com/skydashnet/material-design-3-ui-skill) | A prominent connection action, quieter destination tiles, semantic section headings and wrapping summaries. |
| [Material 3 Expressive List](https://github.com/NicosNicolaou16/Material_3_Expressive_List) | A connected single-choice button group for metric and imperial units. |
| [Unified Expressive Settings](https://github.com/JulesPvx/compose_unified_material_expressive_settings) | General preferences form a single group: rounded outer ends, tight inner corners and a narrow gap between rows. |
| [JustForPixel ExpressiveLab](https://github.com/mohdamaan1/JustForPixel-ExpressiveLab) | Filled and outlined actions compress slightly on press without relaying out their neighbours. |
| [Material 3 Expressive Catalog](https://github.com/cavin-macwan/material-3-expressive-catalog) | Button emphasis distinguishes the primary connection action from trying a demo. |
| [AndroidX](https://github.com/androidx/androidx) | Native Compose Material controls supply selection, switch semantics, ripple and animation through the existing BOM. |
