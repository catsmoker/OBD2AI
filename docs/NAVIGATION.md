# Navigation

The shipped navigation model. Home is the only hub: there is no bottom bar,
no navigation rail and no drawer. This replaced the earlier dock/rail design
on purpose — the old audit of that model is gone, and `AppNav`-style chrome
logic no longer exists.

## Destinations

All routes live in `ui/AppRoot.kt` (`NavHost`), each with a thin
`*Destination` wrapper that owns navigation and dialogs.

| Route | Screen | Kind | Reached from |
| --- | --- | --- | --- |
| `setup` | `ui/setup/SetupScreen` | transient | first launch (`startOnSetup`) |
| `home` | `ui/home/HomeScreen` | **hub** | start destination, back from sections |
| `permissions` | `ui/permissions/PermissionsScreen` | transient | Home Connect, Connect screen |
| `connect` | `ui/connect/ConnectScreen` | transient | Home Connect, a gated tile while unlinked |
| `live` | `ui/dashboard/LiveScreenUi` | section | Home tile |
| `diagnostics` | `ui/diagnostics/ErrorOverviewScreen` | section | Home tile, saved-scan tile |
| `errorDetail/{errorCode}` | `ui/diagnostics/ErrorDetailScreen` | leaf | Diagnostics row tap |
| `askAi/{errorCode}` | `ui/diagnostics/AskAiScreen` | leaf | Error detail |
| `trip` | `ui/trip/TripScreen` | section | Home tile |
| `console` | `ui/console/ConsoleScreen` | section | Home tile |
| `settings` | `ui/settings/SettingsScreen` | section | Home header icon |
| `about` | `ui/settings/AboutScreen` | section | Home header icon |
| `legal/{page}` | `ui/settings/LegalScreen` | leaf | Setup, Settings, About |

## Home

- **Hero card**: title, description, and one dominant action.
  - Disconnected → "Connect to OBD2 Device" (permissions are checked first)
    and "Try Demo".
  - Connected → "Disconnect"; demo → "Exit demo".
- **Tile grid** (2 columns by default, `R.integer.home_grid_columns`):
  Dashboard, Diagnostic Report, Trip, Console — shown only while connected or
  in demo, because each opens onto live adapter data.
- **Saved-scan tile**: while disconnected *and* a saved scan exists on disk, a
  "Saved fault codes" tile appears and opens Diagnostics directly. A saved
  scan is a document, so it needs no adapter.
- **Header icons**: Settings and About. There is no second hub and no
  duplicated entry point.

## Rules

- Every secondary screen shows the in-layout back arrow (`CircleIconButton` +
  `ic_arrow_back`) which calls `navigateUp()` — the same result as system Back.
- Section navigation collapses the back stack to Home
  (`NavOptionsBuilder.sectionOptions()`: `launchSingleTop`, `popUpTo("home")`),
  so Back always walks toward the hub instead of replaying tabs.
- Finishing setup cannot `popBackStack("home")` (there is no entry to pop):
  it falls back to `navigate("home") { popUpTo("setup") }`.
- Connection-gated routes (`live`, `diagnostics`, `trip`, `console`) go through
  `openConnected(route)`, which detours to `connect` when the adapter is gone.
- Never add a tab bar, rail or drawer without an explicit product decision;
  the hub plus tiles is the model.
