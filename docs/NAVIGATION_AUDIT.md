# Navigation Audit Report — OBD2AI (com.catsmoker.obd2ai)

## 1. NAVIGATION INVENTORY

### Destinations (nav_graph.xml — 15 destinations)
| ID | Fragment | Purpose | Top-Level? | Entry Points |
|---|---|---|---|---|
| `onboardingFragment` | Welcome/Home Menu | **Hub** — connection state, feature cards | ✅ Home dock | Start destination, Connect→return |
| `setupFragment` | First-launch consent + settings | One-time setup | ❌ Modal transient | MainActivity.onCreate (consent not set) |
| `permissionsFragment` | Bluetooth permission request | Permission gate | ❌ Modal transient | openConnect() → no BT perms |
| `connectFragment` | BT/WiFi/Demo connect | Adapter connection UI | ❌ Modal transient | openConnect(), openConnected(), disconnectAll |
| `liveDataFragment` | Dashboard gauges | Live telemetry | ✅ Live dock | Onboarding card, ErrorOverview quick-nav |
| `errorOverviewFragment` | DTC list | Fault code overview | ✅ Faults dock | Onboarding card, LiveData quick-nav |
| `errorDetailFragment` | Single DTC detail | Code info & assessment | ❌ Nested under diagnostics | ErrorOverview tile tap |
| `askAiFragment` | AI chat for code | Question about specific fault | ❌ Nested under diagnostics | ErrorDetail "Ask AI" button |
| `tripFragment` | Trip computer summary | Session stats | ✅ Trip dock | Onboarding card, ErrorOverview quick-nav |
| `consoleFragment` | Raw OBD commands | Diagnostics console | ✅ More sheet | Onboarding card, More sheet |
| `settingsFragment` | Settings categories | All app preferences | ✅ More sheet | Onboarding card, More sheet |
| `aboutFragment` | Version + legal links | App info | ✅ More sheet | Onboarding card, More sheet |
| `legalFragment` | Privacy/Terms text | Legal pages | ❌ Nested leaf | Settings buttons, About buttons, Setup button |

### Chrome Components
- **Phone**: BottomNavigationView (5 items: Home, Live, Faults, Trip, More) + ad banner below
- **Tablet**: NavigationRailView (6 items: Live, Faults, Trip, Console, Settings, About) + header back arrow
- **Both**: AdView at bottom

### Navigation Menu Items
**Phone dock (5 items)**:
```
🏠 Home        → onboardingFragment
⚡ Live        → liveDataFragment
🔧 Faults      → errorOverviewFragment
📊 Trip        → tripFragment
••• More       → opens MoreBottomSheet overlay
```

**Tablet rail (6 items)**:
```
⚡ Live         → liveDataFragment
🔧 Faults       → errorOverviewFragment
📊 Trip         → tripFragment
📟 Console      → consoleFragment
⚙️ Settings     → settingsFragment
ℹ️ About        → aboutFragment
```

### Current Actions (from nav_graph.xml)
1. `action_onboardingFragment_to_permissionsFragment`
2. `action_onboardingFragment_to_connectFragment`
3. `action_onboardingFragment_to_liveDataFragment`
4. `action_onboardingFragment_to_errorOverviewFragment`
5. `action_onboardingFragment_to_tripFragment`
6. `action_onboardingFragment_to_consoleFragment`
7. `action_onboardingFragment_to_settingsFragment`
8. `action_onboardingFragment_to_aboutFragment`
9. `action_permissions_to_connectFragment` (popUpTo inclusive)
10. `action_connectFragment_to_onboardingFragment` (singleTop + popUpTo)
11. `action_errorOverviewFragment_to_errorDetailFragment`
12. `action_errorOverviewFragment_to_liveDataFragment`
13. `action_errorOverviewFragment_to_consoleFragment`
14. `action_errorOverviewFragment_to_tripFragment`
15. `action_errorDetailFragment_to_askAiFragment`
16. `action_settingsFragment_to_aboutFragment`
17. `action_settingsFragment_to_legalFragment`
18. `action_aboutFragment_to_legalFragment`
19. `action_setupFragment_to_legalFragment`

---

## 2. PROBLEM AUDIT

### Problem #1: OnboardingFragment is a Fake Hub
**Severity: HIGH**  
The welcome screen (`onboardingFragment`) doubles as both a **home menu** AND the **navigation root**. This violates Material Design's separation of concerns:
- It has direct navigation actions to every other top-level screen (8 actions).
- Users expect bottom nav tabs to navigate directly, not route through a hub first.
- The tab system highlights `onboardingFragment` as "Home" but all actual data screens are children in the nav stack with separate IDs.
- When switching tabs via the dock, `goSection()` uses `popUpTo(onboardingFragment)` which may destroy state.

**Impact**: Every top-level switch creates/destroys the home fragment unnecessarily. State isn't shared between tabs.

### Problem #2: Phone vs Tablet Menu Inconsistency
**Severity: MEDIUM**  
- **Phone dock (5)**: Home / Live / Faults / Trip / More
- **Tablet rail (6)**: Live / Faults / Trip / Console / Settings / About

Console, Settings, and About only appear in the tablet rail or phone's "More" overlay. These should be consistently discoverable:
- On phones, users must know about the hidden "More" sheet to reach Console, Settings, or About.
- On tablets, all 6 are equally visible.
- Violates **Consistency and Standards** principle.

### Problem #3: ErrorOverview Quick-Navigation Sprawl
**Severity: MEDIUM**  
ErrorOverviewFragment has 4 extra navigation actions beyond its primary child (errorDetail):
- `→ liveDataFragment` (back to dashboard)
- `→ consoleFragment` (raw commands)
- `→ tripFragment` (trip stats)

These shortcuts create an inconsistent UX pattern: some screens provide "jump elsewhere" buttons while others don't. A user deep in the diagnostics flow can suddenly switch to any top-level section without navigating through the bottom bar.

**Violation**: **Progressive Disclosure** — the back arrow should lead somewhere meaningful, not a surprise shortcut.

### Problem #4: Settings → About Action Exists But Is Never Used
**Severity: LOW**  
`action_settingsFragment_to_aboutFragment` is declared in nav_graph.xml but nowhere in SettingsFragment or SettingsScreen.kt calls it. This dead code path suggests intent that was never implemented, leaving the Settings screen disconnected from About despite having the infrastructure.

**Violation**: Users who land in Settings have no way to reach About without going Back → tapping the About card on Home.

### Problem #5: Back Arrow Semantics Are Inconsistent
**Severity: MEDIUM**  
- `showBackArrow()` returns true for Settings, About, and ALL chromed screens except onboarding.
- For leaf screens (Settings, About), "Up" returns to the Home menu — but the bottom dock is still visible.
- For detail screens (ErrorDetail, AskAi, Legal), "Up" returns to parent via `navigateUp()`.
- Result: Users can't tell from the chrome alone whether "Back" means "Go to parent" or "Go to Home."

**Violation**: **Recognition Over Recall** — the same visual control does two different things depending on context.

### Problem #6: Deep Nesting Creates Confusing Stacks
**Severity: LOW**  
Setup → Legal is one level deep. Connect → Onboarding (via `popUpTo`) resets to home. But if the user navigates:
```
Home → ErrorOverview → ErrorDetail → AskAi → Legal
```
And presses Back from Legal, they return to AskAi, then ErrorDetail, then ErrorOverview — not to where they came from. Each intermediate screen was reached via a **top-level tab**, not a parent-child relationship.

**Violation**: **Principle of Least Surprise** — Back should return to where you were, not walk through unrelated sections.

### Problem #7: No Predictive Back Gesture Handling
**Severity: LOW**  
No predictive back gesture callbacks are implemented. Android 13+ swipe-back gestures show partial preview but have no custom dismiss behavior for bottom sheets or dialogs.

**Violation**: Modern Android expectations — predictive back is standard since API 31.

### Problem #8: Bottom Nav Highlights Wrong Tab on Detail Screens
**Severity: LOW**  
When viewing `errorDetailFragment` or `askAiFragment`, `sectionFor()` maps them to `errorOverviewFragment` (Faults tab). That's correct — but when viewing `legalFragment`, `sectionFor()` returns null, so NO tab highlights. The user sees no active tab while reading legal text.

**Violation**: **Law of Proximity** — related content should share navigation state.

---

## 3. REDESIGNED INFORMATION ARCHITECTURE

```
┌─────────────────────────────────────────────────────┐
│                  APP HIERARCHY                       │
├─────────────────────────────────────────────────────┤
│                                                      │
│  TOP-LEVEL DESTINATIONS (Bottom Nav / Rail)          │
│                                                      │
│  ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌─────────────────────┐   │
│  │Home│ │Live│ │Diag│ │Trip│ │  More Sheet       │   │
│  └───┘ └───┘ └───┘ └───┘ │                     │   │
│                           │ • Console           │   │
│                           │ • Settings          │   │
│                           │ • About             │   │
│                           └─────────────────────┘   │
│                                                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  HOME (Welcome Menu)                                 │
│  ├───[ CONNECT ]──────────────────────────────────┐  │
│  ├── [ TRY DEMO ]                                  │  │
│  │                                                 │  │
│  ├── FEATURES GRID (when connected)                │  │
│  │   ├─ Dashboard → Live tab                      │  │
│  │   ├─ Diagnostics → Diagnostics tab             │  │
│  │   ├─ Trip Computer → Trip tab                  │  │
│  │   ├─ OBD Console → More > Console              │  │
│  │   ├─ Settings → More > Settings                │  │
│  │   └─ About → More > About                      │  │
│  │                                                 │  │
│  └── Status Badge (connected/demo/disconnected)    │  │
│                                                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  LIVE (Dashboard)                                    │
│  ├── Gauge cluster (AndroidView composites)          │
│  ├── Telemetry tiles                                │
│  ├── Offline AI event banners (pinned)              │
│  ├── Online AI response card                        │
│  └── Demo ControlsSheet (demo mode only)            │
│                                                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  DIAGNOSTICS                                         │
│  ├── ErrorOverview (DTC list)                        │
│  │   └── ErrorDetail (single code)                   │
│  │       └── AskAi (chat interface)                  │
│  └── Console (separate tab in More)                  │
│                                                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  TRIP                                                  │
│  └── Session statistics                              │
│                                                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  MORE SHEET                                          │
│  ├── Console                                        │
│  ├── Settings                                       │
│  │   └── Legal (Privacy/Terms) ← via Settings       │
│  └── About                                          │
│      └── Legal (Privacy/Terms) ← via About          │
│                                                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  TRANSIENT FLOWS (chrome hidden)                     │
│  ├── Setup (first launch)                             │
│  │   └── Legal                                       │
│  ├── Permissions (Bluetooth grant)                    │
│  └── Connect (BT/WiFi adapter config)               │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### Key Design Decisions

1. **Home is a static hub** — Bottom nav always shows, Home is always highlighted when on the welcome screen. Switching tabs pops to home cleanly using existing `goSection()` logic with `popUpTo/onboardingFragment/singleTop`.

2. **"More" stays as an overlay** — Console, Settings, and About remain accessible via MoreBottomSheet for phones. On tablets they're directly in the rail.

3. **Navigation Bar = Major Sections Only** (4 items):
   - **Home** — Always visible. Acts as the app center.
   - **Live** — Dashboard with gauges. Primary driving view.
   - **Diagnostics** — Shows ErrorOverview. Details nest here.
   - **Trip** — Trip computer.
   
   *Removed*: "Faults" label renamed to "Diagnostics" for clarity. "More" stays as a separate trigger, not a tab item.

4. **Nested navigation under Diagnostics** follows a strict parent→child hierarchy:
   ```
   Diagnostics (ErrorOverview)
   └── Error Detail (single DTC)
       └── Ask AI (chat about that code)
   ```
   No cross-links between these levels. Each screen's Up/Back arrow returns to the logical parent.

5. **Legal fragments are always nested under their entry point** — Settings → Legal, About → Legal, Setup → Legal. Back always returns to the screen that launched Legal.

6. **ErrorOverview keeps its quick-nav buttons** — but they become contextual chips/buttons within the screen, not replacing proper navigation semantics.

---

## 4. IMPLEMENTATION PLAN

### Phase 1: Consolidate Navigation (MainActivity.kt)
- Replace current multi-function local helper pattern with clean class methods
- Keep bottom nav with 5 tabs (Home/Live/Diagnostics/Trip/More) for now — rename Faults→Diagnostics
- Ensure `sectionFor()` properly handles all destinations including Legal → Diagnostics

### Phase 2: Fix AppNav Logic (AppNav.kt)
- Map Legal → parent section (where it was opened from)
- Handle detail screens consistently
- Ensure chrome visibility is correct for all cases

### Phase 3: Clean Up Unused Actions (nav_graph.xml)
- Remove dead `action_settingsFragment_to_aboutFragment` (never called)
- Remove ErrorOverview→Console/Trip/Live shortcuts (inconsistent pattern)
- Keep only necessary parent→child relationships

### Phase 4: Standardize Back Behavior Across All Fragments
- Ensure every fragment uses `findNavController().navigateUp()` for consistent back
- Verify predictive back gesture works (Material components handle this automatically)
- Test that each destination chain preserves state correctly

### Phase 5: Label Consistency
- Rename "Faults" tab to "Diagnostics" across all strings and locales
- Update nav_rail.xml titles to match
- Verify RTL/large-translation string wrapping

---

## 5. PREDICTED OUTCOMES

After implementation:
- ✅ Navigation follows Jakob's Law — Android users know exactly what each control does
- ✅ Hick's Law applied — 4 top-level tabs reduce cognitive load; secondary features in More
- ✅ Progressively disclosed — DTC details require drilling into diagnostics, no shortcuts
- ✅ Recognition over recall — Labels say exactly where they go
- ✅ Back always goes to logical parent, never to random screens
- ✅ Settings and About are discoverable without knowing about hidden menus
- ✅ Consistent chrome across all screens — what you see matches where you are

---

## 6. IMPLEMENTATION & VERIFICATION LOG (2025-11-20)

All phases implemented and verified on device (POCO X3 NFC, Android 15):

- Bottom dock (Home/Live/Diagnostics/Trip/More) live on phones; "More" opens
  the MoreBottomSheet in a `ComponentDialog` (plain `Dialog` crashes Compose —
  no ViewTreeLifecycleOwner; default dialog sizing also collapsed the sheet to
  0 width — fixed with an explicit full-screen window layout + scrim).
- More-tab re-taps handled via `setOnItemReselectedListener` (the sheet must
  reopen even when More is already checked, e.g. inside Settings).
- Rail reduced to 4 items mirroring the dock; `dock_faults` renamed to
  "Diagnostics" in all 4 locales; dead nav actions removed from nav_graph.xml.
- `sectionFor`: Console/Settings/About/Legal → More tab; setup/connect
  destinations hide the dock.

### On-device verification results (all pass)
| Flow | Result |
| --- | --- |
| More tap from Home → sheet opens | ✅ |
| Sheet rows → Console / Settings / About navigate + More highlights | ✅ |
| More re-tap from About/Settings/Console reopens sheet | ✅ |
| Scrim tap dismisses sheet, highlight restored | ✅ |
| Home tiles (demo mode): Dashboard/Live, Diagnostics, Trip, Console drill-downs | ✅ each highlights its dock tab |
| About → Privacy Policy → Legal highlights More; back returns to About | ✅ |
| Connect flow hides dock; back restores dock + Home highlight | ✅ |
| Demo exit restores disconnected Home | ✅ |
| Release build + 211 unit tests | ✅ |
Follow-up: About also moved out of the tile grid into the Home header
(info icon beside the settings gear). Connected Home is now a clean 2×2 tile
grid (Dashboard/Diagnostics/Trip/Console); disconnected Home shows only the
connection card — the empty Features section is hidden. `home_card_about_desc`
removed from all locales. Verified on device.

### Tablet pass (later on 2025-11-20)
Verified the tablet (sw600dp) layout on the POCO by temporarily overriding
density to 200 (1080px / 1.25 = 864dp >= 600dp), then resetting. Two real
findings, both fixed:
1. **Launch crash on tablets**: `MainActivity` wired the phone dock
   unconditionally; `bottom_nav` does not exist in the sw600dp layout, so
   every tablet launch NPE'd. Dock wiring is now null-safe (mirrors the rail
   pattern). This bug predates the de-duplication pass — it came in with the
   original dock migration.
2. **Orphan tile**: `values-sw600dp` set `home_grid_columns` to 3, which
   stranded the 4th tile alone once About moved to the header. Now 2 → clean
   2×2 at tablet width.
Tablet checks passed: rail renders with 4 items, rail taps navigate and
highlight correctly, header About/Settings icons render, demo tiles form a
2×2 grid, no crash. Phone layout re-verified after density reset.

### Progressive-disclosure chrome (later on 2025-11-20)
Per user request, the dock/rail now appear only once the app has a data world:
a disconnected app shows just the Home connection card (Connect / Try Demo)
with no bar; starting demo or connecting makes the bar appear in place (no
navigation needed), and disconnecting hides it again. Implemented as
`AppNav.isChromeHidden(dest, linked)` + `MainActivity.updateNavChrome()`,
called from the destination listener and the Home menu's `refreshState()`.
Chrome views promoted to class properties (tablet null-safety preserved).
Verified on device: hidden when disconnected, appears on demo start, hides on
demo exit, stays hidden through a connect round-trip, works while connected.

### First-start navigation choice (later on 2025-11-20)
Setup is now three steps: agreement → language/appearance → **navigation
style**. The user picks between the persistent bar (dock/rail, default) and
Cards (hub-and-spoke: no bar ever, everything opens from Home cards, back
returns). Stored as `nav_style` (NavStyle enum, prefs package); step 2's
Continue now advances to step 3, step 3's Continue completes setup; the
shared Back/Continue row moved out of the step-2 container so both steps
share it. `AppNav.isChromeHidden(dest, linked, cardsMode)` + tests; state
machine tests updated for 3 steps (33/66/100 progress). Verified on device
by resetting consent_set via run-as (prefs backed up first): Step 1→2→3
walk, bar pre-selected, Cards selectable, completion lands on Home with no
bar even in demo, tile navigation + back works in cards mode, and flipping
the pref to bar restores the dock.

### Settings override (later on 2025-11-20)
The navigation style is now also switchable after setup: a "Navigation"
dropdown in Settings' General card (same labels as the setup step — one
concept, one name). Selecting a style writes `nav_style` and calls
`MainActivity.updateNavChrome()`, so the change applies as soon as the user
leaves Settings. Verified on device both directions with demo running:
Bar → Cards hides the dock on return; Cards → Bar restores it.
