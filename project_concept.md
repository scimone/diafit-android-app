# diafit — Project Concept

This document is a synthesis of the planning notes in this Obsidian vault
(`diafit-obsidian`), written for a coding agent that will implement the
**diafit** Android app in Kotlin. It consolidates scattered notes into one
reference. Where the vault notes left a decision open or contradictory, that
is called out explicitly as an **open decision** rather than invented.

Package name in existing code: `uk.scimone.diafit`.

> **Consistency review (2026-10-06, Claude Code against the code as it stands today):**
> This document predates some finished work and describes a package/schema layout the code never
> adopted. Flagged inline below with **[REVIEW]** callouts rather than silently rewritten, per
> this doc's own "don't invent" rule. Summary:
> - **Already done, doc is stale:** DI is 100% Koin — there is no Dagger anywhere in the project,
>   the migration isn't "pending" (§3, §14). Meal photo storage is fully implemented
>   (`FileStorageRepositoryImpl`, internal storage) — not a TODO (§7.6, §8, §14). Navigation (a
>   `MainActivity` tab switcher + `BottomNavigationBar`) is working, not unimplemented (§2).
> - **Real architectural mismatch, needs a decision before building against it:** the doc's
>   schemas (§6) specify `UUID` primary keys and a universal `is_valid` soft-delete flag. The
>   actual Room entities (`CgmEntity`, `BolusEntity`, `MealEntity`) use `Int` auto-increment ids
>   and `Int userId`; only `MealEntity` has `isValid`. Adopting the doc's schema as-is means a real
>   migration (ids, multi-user keys, soft-delete everywhere) — see CLAUDE.md's `userId = 1`
>   tech-debt item, which is the same problem at smaller scale.
> - **Feature packages (§4) don't match current code.** Actual packages are `addmeal / core /
>   debugintent / home / journal / settings / ui` (data/domain/presentation + a Koin module each).
>   `home` ≈ the doc's `overview24h`, `journal` ≈ `logbook`; `summary`, `statistics`, `compare`,
>   `profile`, `highlights` don't exist yet. New features should follow the existing convention,
>   not necessarily the doc's package names.
> - **Bottom nav (§9) doesn't match current code.** Actual tabs today: Home, Journal, Settings,
>   Add-meal (4 tabs) — no Summary/History tab exists yet; the doc's "Home · Summary · Journal ·
>   History" nav is aspirational, from an old wireframe.
> - **Unverified, not contradicted:** §2's "Juggluco inserts CGM values twice" and "`0` glucose
>   value on app restart" bugs aren't tracked in CLAUDE.md's known-issues list, and `CgmEntity`
>   already has a unique index on `timestamp` with `OnConflictStrategy.REPLACE`, which should
>   prevent true duplicate rows. Don't assume either bug is still live, or that it's fixed —
>   confirm on-device before prioritizing.

---

## 1. Purpose & vision

diafit is a personal diabetes-management companion app (type 1 diabetes,
looping with AndroidAPS/OmniPod Dash). It pulls together glucose, insulin,
carbs, activity, and sleep data from the devices/apps the author already
uses, and turns it into one coherent timeline and set of insights — rather
than having to cross-reference AAPS, Nightscout, xDrip/Juggluco and a
fitness tracker separately.

Stated purpose (from `frontend/ui design brainstorm.md`), in the author's
own words:

- Understand how meals affect blood glucose (BG).
- Get reminders for complicated meals (e.g. pizza — long, fatty, delayed BG
  impact).
- Understand how activity affects BG.
- Get reminders to be more active, with visualization of the possible
  positive effect.
- Always have an overview of how well things are going (today's time in
  range, total food and insulin, total activity).
- Understand the effect of sleep on BG.
- Optimize the insulin profile (basal rates, carb ratios, sensitivity).

### MVP (build this first)

The vault explicitly scopes an MVP, smaller than the full vision above:

1. Add a meal by taking/choosing a photo.
2. Send the photo to the OpenAI API and get back nutrient info (carbs,
   protein, fat, calories) and an insulin recommendation + reasoning.
3. A logbook ("Journal") listing all meals.

Everything else below (charts, clustering, optimizer, cloud sync) is the
longer-term vision. When in doubt about priority, favor finishing the MVP
loop (photo → AI estimate → saved meal → visible in journal → glucose impact
visible on the Home timeline) over any of the fancier charts.

---

## 2. Current implementation status

Do not treat this as a greenfield project — there is already a working
Android app with:

- CGM values arriving live via broadcast intents (`CGMReceiver`) and stored
  in a local Room/SQLite database (not SharedPreferences — that was an
  earlier approach and has been replaced).
- Treatments (carbs, bolus, temp basal, SMB correction boluses) arriving via
  `NSClientReceiver` broadcast intents from AndroidAPS and written to the
  DB.
- A live "Home" chart (CGM line) restricted to the last 24 hours, colored by
  value, with a rotating trend-arrow icon, a "time since last reading"
  countdown, and stale-value styling (cross out values older than 15 min).
- A settings screen to pick the active CGM/treatment data source
  (Juggluco/xDrip added already).
- A working add-meal flow with a glycemic-impact input field; not yet wired
  to AI estimation or autocomplete.
- Clean-architecture restructuring already done once (see §4).

Known rough edges already identified in the notes (carry these forward,
don't silently "fix" them into something different without flagging it):

- **Bug**: Juggluco CGM values are sometimes inserted twice into the DB.
- **Bug**: on app restart, a `0` glucose value gets inserted into the DB.
- Several ViewModels still reach into the wrong repository for their
  feature (e.g. `JournalViewModel` still uses `MealRepository` for
  `observeMealsByUserId`; `AddMealViewModel` uses `FileStorageRepository`
  directly) — use cases aren't consistently the single entry point into
  data yet.
- No `user_id` plumbed consistently through use cases yet — multi-user
  support is intended (see `profiles.user_id` etc. in the schemas) but not
  finished.
- No automated tests yet.
- ~~Navigation between screens is not yet fully implemented.~~ **[REVIEW]** Done: `MainActivity`
  holds a `selectedTab` int and switches between `HomeScreen`/`JournalScreen`/`SettingsScreen`/
  `AddMealScreen`, driven by `BottomNavigationBar`.
- ~~DI currently uses Dagger; there's a standing intent to migrate to **Koin**
  (tracked as a TODO, not done).~~ **[REVIEW]** Done: the project is 100% Koin (see `*/di/*Module.kt`
  per feature, wired up in `DiafitApp.onCreate()`); there's no Dagger dependency anywhere in
  `build.gradle.kts`. Don't re-plan this migration.

---

## 3. Tech stack & key library decisions

| Concern | Decision | Notes |
|---|---|---|
| Language | Kotlin | |
| Multiplatform | **Kotlin Multiplatform — confirmed as a good fit**, not yet acted on | `architecture/multiplatform_compatible.md`: research done, decision made that KMP suits the app. The migration off Dagger to Koin this was supposed to be a prerequisite for is already complete (**[REVIEW]** see DI row below) — KMP itself hasn't been started. Treat the app as Android-first today, but avoid Android-only patterns where a KMP-friendly alternative is equally easy. |
| UI | Jetpack Compose | All screens/components referenced in notes are Compose (`AddMealScreen`, `HomeScreen`, etc.) |
| DI | ~~Dagger today → **migrating to Koin**~~ **[REVIEW] Already fully Koin** — no Dagger dependency exists in the project. Don't add new Dagger wiring; this isn't a decision left to make. |
| Local persistence | **Room (SQLite)** | Chosen over plain SharedPreferences for anything beyond simple settings. Existing DAOs: `BolusDao`, `CarbsDao`, `CGMDao`. SharedPreferences/DataStore still appropriate for simple settings (e.g. selected data source). |
| Image storage | Device file storage (internal storage preferred for anything not meant to be shared; cache dir acceptable if images are reproducible/non-critical) | ~~Not yet implemented for meal photos — still a TODO.~~ **[REVIEW] Already implemented**: `FileStorageRepositoryImpl` writes meal photos to internal storage (`filesDir/meal_images`), served via `FileProvider`, wired into `CreateMealUseCase`. |
| Charting | **Vico** (`com.patrykandpatrick.vico`) | Evaluated against MPAndroidChart, HelloCharts, GraphView, AnyChart, AAChartCore, yCharts — Vico is the one actually in use (see chart-specific gotchas in §10). It has no built-in pan/zoom; that would need custom gesture handling via `pointerInput`/`detectTransformGestures` if pursued. |
| Backend / cloud | **Open decision** — Postgres vs MongoDB for a cloud DB is explicitly unresolved. | See §8. A *separate*, more advanced Django backend prototype exists in the author's research notes (management commands for weekly/monthly/quarterly/rolling statistical summaries, Postgres, Docker Compose, Django-Q) — that is speculative/future "visual storytelling" research, **not** a dependency of the Kotlin app's MVP. Don't build against it unless asked. |
| AI (meal photo → nutrients) | OpenAI API (vision + text) | See §11 for the exact prompt already drafted. |

---

## 4. Architecture: Clean Architecture, feature-first

> **[REVIEW]** The tree below is not what the code actually has today. Actual top-level packages
> are `addmeal / core / debugintent / home / journal / settings / ui`, each (except `core`/`ui`/
> `debugintent`) split into `data/domain/presentation` plus its own `di/<feature>Module.kt` — the
> same per-feature layering this section describes, just different names and a smaller feature
> set. `home` ≈ `overview24h`, `journal` ≈ `logbook`; `highlights`, `summary`, `statistics`,
> `compare`, and `profile` don't exist as packages yet. Treat the tree below as a naming proposal
> to revisit, not as the current layout — new features should follow the convention already
> established by `home`/`journal`/`addmeal`/`settings` (including the per-feature Koin module,
> not a shared top-level `di/`).

The target structure (as of the most recent restructuring note,
2025-05-25) organizes the app **by feature**, each with the same three
layers:

```
uk.scimone.diafit
├── addmeal
│   ├── data
│   ├── domain
│   └── presentation
├── highlights
│   ├── data
│   ├── domain
│   └── presentation
├── summary
│   ├── data
│   ├── domain
│   └── presentation
├── statistics
│   ├── data
│   ├── domain
│   └── presentation
├── overview24h
│   ├── data
│   ├── domain
│   └── presentation
├── logbook
│   ├── data
│   ├── domain
│   └── presentation
├── compare
│   ├── data
│   ├── domain
│   └── presentation
├── profile
│   ├── data
│   ├── domain
│   └── presentation
├── core
│   ├── utils
│   ├── navigation
│   └── ui
└── di
```

This supersedes an earlier (2024-07-14) structure that organized by
technical layer first (`core/data`, `core/domain`, `features/home`,
`receivers/`, `ui/theme`) — that structure is documented in
`architecture/folder_structure_clean_architecture.md` for historical
reference only; new work should follow the feature-first layout above.

### Per-feature layer contract

Worked example from `architecture/addmeal package/addmeal.md` — apply this
same shape to every feature package:

**`data/`** — fetches/stores raw data.
- `model/` — DTOs mapping closely to API/DB shapes (e.g. `MealDto`,
  `NutrientDto`).
- `repository/` — implementations of the domain repository interfaces
  (e.g. `AddMealRepositoryImpl`, combining camera, AI API, and local DB).
- `source/` — concrete data sources (e.g. `RemoteMealDataSource` for the AI
  API, `LocalMealDataSource` for Room, `CameraSource` for camera/gallery
  access).

**`domain/`** — business logic, no Android/UI dependency.
- `model/` — clean business entities (e.g. `Meal`, `Nutrient`).
- `repository/` — interfaces only (e.g. `AddMealRepository` with
  `uploadMealPhoto()`, `getMealAnalysis()`).
- `usecase/` — one use case per action, the **only** thing presentation
  should call (e.g. `AnalyzeMealUseCase`, `AddMealUseCase`). This is the
  layer the current code violates in a few places (§2) — when touching
  those features, route through use cases instead of repositories
  directly.

**`presentation/`** — Compose UI + state.
- `screens/` — Compose screens (e.g. `AddMealScreen`, `CameraScreen`,
  `GalleryScreen`).
- `viewmodel/` — one ViewModel per screen/feature, calling only use cases,
  exposing UI state + actions.

---

## 5. System / data-flow overview

Translated from `data_transfer/_data_transfer.canvas`, this is the intended
end-to-end data flow:

```
                        ┌───────────────────────┐
                        │   google health api    │── send fitness data ──┐
                        └───────────────────────┘                        │
┌───────────┐  broadcast bg   ┌───────────┐                             │
│  Juggluco │ ───────────────▶│            │                             │
│   xdrip   │  backfill bg    │            │                             ▼
└───────────┘ ───────────────▶│   SQLite    │◀── send profile ── ┌──────────────┐
                        │  (local DB) │                     │ Nightscout api│
┌───────────┐ broadcast       │            │                     └──────────────┘
│   AAPS    │  treatments ───▶│            │
└───────────┘                 └─────┬──────┘
      ▲                             │ sync (app → cloud)
      │ send bolus                  ▼
┌──────────────┐              ┌───────────┐   get data    ┌──────────┐
│    Diafit    │              │ Cloud DB  │──────────────▶│ Backend  │
│  (the app)   │              └───────────┘◀───────────────┤ (server) │
└──────────────┘               ▲              send calcs  └──────────┘
                                │
                     Device Storage (images)
```

Reading it as flows:

- **Inputs** (populate local SQLite): Juggluco/xDrip (CGM, live + backfill),
  AAPS (treatments: carbs, bolus, temp basal, SMB corrections), Nightscout
  (profile: basal/carb-ratio/sensitivity), Google Health (fitness: steps,
  heart rate, sleep, activity — not yet implemented), user (meal photos →
  device storage).
- **Output back to the loop**: diafit → AAPS, "send bolus" (meant for
  bolus-wizard-style recommendations the app computes from meal photos —
  **mechanism not understood yet**, see §7.5).
- **Cloud sync** (not implemented, optional/future): local DB syncs to a
  cloud DB; a backend server reads from the cloud DB to run heavier
  calculations (AGP stats, clustering, summaries) and writes results back.
  Treat this whole right-hand side as **out of scope for MVP**.

---

## 6. Core domain data schemas

> **[REVIEW]** This section's "prefer the row below, adjust the Room entity to match" instruction
> has NOT been followed, and doing it now would be a real migration, not a quick patch — don't
> silently do it as a side effect of an unrelated feature. The actual Room entities (`CgmEntity`,
> `BolusEntity`, `MealEntity`) use `@PrimaryKey(autoGenerate = true) val id: Int` and `val userId:
> Int`, not `UUID`. Only `MealEntity` has an `isValid` soft-delete flag — `CgmEntity`/`BolusEntity`
> have none. This is the same root problem as CLAUDE.md's "`userId = 1` hardcoded" tech-debt item,
> just at full schema scale: multi-user support needs this reconciled (UUID vs. Int ids,
> universal soft-delete or not) as its own planned piece of work, with the author's sign-off on
> the migration strategy (existing rows, DB migration, multi-device sync implications) before any
> code changes.

These are logical schemas (used both for the local Room DB and as the
target shape for an eventual cloud DB). Where a column list differs between
the local Room entity and the row below, prefer the row below as source of
truth and adjust the Room entity to match, since these schema notes are
more recent.

Conventions used throughout (see also §15): `UUID` ids, timestamps stored
as UTC, and an `is_valid` boolean used as a **soft-delete flag** (rows are
marked invalid rather than hard-deleted, to stay consistent with how
Nightscout/AAPS treatments behave).

### `cgm`

| Column | Type | Description |
|---|---|---|
| `id` | UUID | Unique CGM reading ID |
| `user_id` | UUID | Owning user |
| `timestamp_utc` | TIMESTAMP | Time of reading, UTC |
| `value_mgdl` | FLOAT | Glucose value in mg/dL |
| `rate_mgdl_5min` | FLOAT | Rate of change (mg/dL per 5 min) |
| `trendarrow` | TEXT | Direction code (e.g. `"fortyfiveup"`) |
| `device` | TEXT | Reporting device (e.g. `"xDrip"`) |
| `source_id` | TEXT | Data source (e.g. `"Nightscout"`) |

Helper functions expected somewhere in the `cgm` domain/use-case layer:
`getTrendArrow()` (trend code → symbol/icon — already built on the UI side
as a continuously-rotating arrow, see §2), `convertToMmol()` (`value_mgdl *
0.0555`), `calculateRate()` (derive rate from the last 2–3 points if the
source doesn't supply one).

### `bolus`

| Column | Type | Description |
|---|---|---|
| `id` | UUID | Unique bolus ID |
| `created_at_utc` | TIMESTAMP | When logged |
| `meal_time_utc` | TIMESTAMP | Associated meal time, if any |
| `value` | FLOAT | Insulin units |
| `user_id` | UUID | Owner |
| `is_smb` | BOOLEAN | Super-micro-bolus (automated correction) vs. manual |
| `is_valid` | BOOLEAN | Soft-delete flag |
| `pump_type` | TEXT | e.g. `"OMNIPOD_DASH"` |

### `temp_basal`

| Column | Type | Description |
|---|---|---|
| `id` | UUID | Unique ID |
| `user_id` | UUID | Owner |
| `created_at_utc` | TIMESTAMP | Start time |
| `duration_minutes` | INTEGER | Duration |
| `rate` | FLOAT | Units/hour |
| `pump_type` | TEXT | e.g. `"OMNIPOD_DASH"` |
| `is_valid` | BOOLEAN | Soft-delete flag |

### `meals` (planned rename → `food`)

Current schema:

| Column | Type | Description |
|---|---|---|
| `id` | UUID | Unique meal ID |
| `user_id` | UUID | Owner |
| `description` | TEXT | Free-text description |
| `created_at_utc` | TIMESTAMP | Logged time |
| `meal_time_utc` | TIMESTAMP | Eaten time |
| `carbs` | FLOAT | grams |
| `protein` | FLOAT | grams |
| `fat` | FLOAT | grams |
| `is_valid` | BOOL | Soft-delete flag |
| `image_id` | UUID | Points at the meal image on device (possibly == meal id) |
| `recommendation` | TEXT | AI-generated insulin recommendation |
| `reasoning` | TEXT | AI-generated reasoning behind the recommendation |

A linked table captures the glycemic impact curve actually observed after
a meal, sampled every 30 minutes for 6 hours:

**`meals_glycemic_profiles`**: `meal_id` (UUID, FK) + `impact_0`,
`impact_0_5`, `impact_1`, … `impact_5_5`, `impact_6` (FLOAT, BG value at
that many hours after the meal).

**Planned refactor** (not yet done — treat as the next-iteration schema,
confirm with the author before committing to it): rename table to `food`;
add a `glycemic_index` concept, either as a coarse enum (`fast`/`medium`/
`slow`) or by storing a full glycemic-response profile (the
`meals_glycemic_profiles` table above is effectively this); logic question
still open whether the user manually enters glycemic index, or the app
estimates protein/fat from a food database and leaves BG-impact curve
capture as the real signal.

### `profiles` + time-segmented settings

A user's insulin profile is **time-of-day segmented** (not a single flat
number), matching how AAPS/Nightscout profiles work:

**`profiles`** (metadata): `id` (UUID), `user_id` (UUID), `name` (TEXT, e.g.
pump profile name like `"Anna"`), `start_date_utc` (TIMESTAMP, when this
profile version becomes active), `created_at_utc` (TIMESTAMP).

**`basal_segments`**: `id` (UUID), `profile_id` (FK), `start_time` (TIME,
e.g. `06:00`), `rate` (FLOAT, units/hour).

**`carb_ratios`**: `id` (UUID), `profile_id` (FK), `start_time` (TIME),
`ratio` (FLOAT, grams of carbs per unit of insulin).

**`sensitivities`**: `id` (UUID), `profile_id` (FK), `start_time` (TIME),
`value` (FLOAT, mg/dL drop per unit of insulin).

This schema backs the **Profile** screen and the (future) profile
optimizer (§9, §12).

---

## 7. External integrations

### 7.1 Juggluco / xDrip — CGM

- **Live values**: via Android broadcast intents (already implemented —
  `CGMReceiver`).
- **Backfill**: via the Juggluco/xDrip local web server
  (`https://www.juggluco.nl/Juggluco/webserver.html`). Not yet implemented.
  Design question still open: build a UI button to trigger a backfill and
  consolidate entries on push, and/or detect gaps of >5 minutes in the
  local CGM table and backfill exactly those gaps.
- **Known bug to fix when implementing backfill**: duplicate inserts from
  Juggluco (§2) — backfill logic must dedupe against existing rows (by
  timestamp + source, not by relying on the source's own id, since ids seem
  unreliable across fetches based on the Nightscout sample payloads below).

Real Nightscout-shaped SGV payload (`sgv` endpoint,
`https://gluco.mooo.com/api/v1/entries.json`), full and the trimmed shape
actually needed:

```json
// full
{
  "_id": "682cbbeae167ac3215ad7f67",
  "device": "xDrip-LibreReceiver",
  "date": 1747762153786,
  "dateString": "2025-05-20T17:29:13.786Z",
  "sgv": 159,
  "delta": 2.997,
  "direction": "Flat",
  "type": "sgv",
  "filtered": 156000,
  "unfiltered": 156000,
  "rssi": 100,
  "noise": 1,
  "sysTime": "2025-05-20T17:29:13.786Z",
  "utcOffset": 120,
  "mills": 1747762153786
}

// fields actually needed (map into `cgm`)
{
  "device": "xDrip-LibreReceiver",
  "date": 1747762153786,
  "sgv": 159,
  "delta": 2.997,
  "direction": "Flat",
  "type": "sgv"
}
```

Important parsing notes called out in the vault:
- **Must filter `type == "sgv"`** — the entries endpoint returns mixed
  types.
- Query only for entries since the last locally-stored timestamp (avoid
  re-fetching the whole history every sync). Example filter query:
  `.../entries/sgv.json?find[dateString][$gte]=2025-05-23&count=1000`, or
  with both bounds:
  `find[dateString][$gte]=...&find[dateString][$lte]=...&count=100000`.

### 7.2 AAPS — treatments (broadcast intents, already implemented for live data)

Delivered as broadcast intents captured in logcat as
`info.nightscout.client.NEW_FOOD` (misleadingly named — carries all
treatment types, not just food) handled by `NSClientReceiver`. Each of these
real captured payloads should inform the DTOs:

**Carb correction** (feeds `meals`/`food`):
```json
{"eventType":"Carb Correction","carbs":1,"notes":"","created_at":"2024-06-29T16:40:29.000Z","isValid":true,"date":1719679229000,"_id":"66803902fe3f79032a517960"}
```

**Temp Basal**:
```json
{
  "_id": "68300f03bedbf7031b10b21e",
  "created_at": "2025-05-23T06:00:35.299Z",
  "enteredBy": "openaps://AndroidAPS",
  "eventType": "Temp Basal",
  "isValid": true,
  "duration": 3,
  "durationInMilliseconds": 181782,
  "type": "NORMAL",
  "rate": 1.2,
  "absolute": 1.2,
  "pumpId": 1708112682518,
  "pumpType": "OMNIPOD_DASH",
  "pumpSerial": "4241",
  "endId": 1708112682520,
  "carbs": null,
  "insulin": null
}
```

**Bolus Wizard** (informational only — the two "Meal Bolus" events below
carry the actual data that gets persisted; the wizard event's
`bolusCalculatorResult` is a JSON-encoded *string* with the full inputs IC,
ISF, IOB, COB, glucose, trend, target range, etc. — useful context for a
future "why did AAPS recommend X units" explainer, but not required for the
MVP):
```json
{
  "_id": "68300f3ebedbf7031b10b220",
  "eventType": "Bolus Wizard",
  "created_at": "2025-05-23T06:01:34.325Z",
  "isValid": true,
  "bolusCalculatorResult": "{\"basalIOB\":0.243,\"bolusIOB\":0.0,\"carbs\":20.0,\"carbsInsulin\":2.5,\"cob\":0.0,\"cobInsulin\":0.0,...,\"ic\":8.0,...,\"isf\":53.704402515723224,...,\"profileName\":\"Anna\",...,\"targetBGHigh\":90.0,\"targetBGLow\":90.0,...,\"totalInsulin\":2.85,...}",
  "date": 1747980094325,
  "glucose": 112,
  "units": "mg/dl",
  "notes": "",
  "carbs": null,
  "insulin": null
}
```

**Meal Bolus: carbs half** (feeds `meals.carbs`):
```json
{"_id":"68300fb6bedbf7031b10b224","eventType":"Meal Bolus","carbs":20,"notes":"","created_at":"2025-05-23T06:01:34.325Z","isValid":true,"date":1747980094325,"insulin":null}
```

**Meal Bolus: insulin half** (feeds `bolus`):
```json
{"_id":"68300f3fbedbf7031b10b221","eventType":"Meal Bolus","insulin":2.85,"created_at":"2025-05-23T06:01:35.537Z","date":1747980095537,"type":"NORMAL","isValid":true,"isSMB":false,"pumpId":1708112682519,"pumpType":"OMNIPOD_DASH","pumpSerial":"4241","carbs":null}
```

**Correction Bolus (SMB)** (feeds `bolus`, `is_smb = true`):
```json
{"_id":"68301740bedbf7031b10b249","eventType":"Correction Bolus","insulin":0.05,"created_at":"2025-05-23T06:35:43.661Z","date":1747982143661,"type":"SMB","isValid":true,"isSMB":true,"pumpId":1708112682526,"pumpType":"OMNIPOD_DASH","pumpSerial":"4241","carbs":null}
```

Important: a "Meal Bolus" event always arrives as **two separate
treatments** (one with `carbs`, one with `insulin`) sharing a
`created_at`/`date` — they are not atomic and must be correlated/merged
(e.g. by matching `date` + proximity) when building a single `meals` row
with both carbs and an associated bolus. The standalone "Bolus Wizard"
event is redundant with these two and does not need separate persistence.

**Remaining input TODOs** (not yet implemented): temp target, profile
switch, pump site/cartridge change, sensor change. These are lower priority
than the above.

### 7.3 Nightscout — profile

Via the Nightscout REST API: basal rate, carb ratio, insulin sensitivity
(and potentially carb absorption time). Feeds the `profiles` /
`basal_segments` / `carb_ratios` / `sensitivities` schema in §6. Access to
the Nightscout API is already working for CGM backfill; profile fetching
specifically is not yet implemented, and it's still unclear exactly how
xDrip/AAPS call the Nightscout API for this (open investigation item).

### 7.4 Google Health Connect — fitness data

Via Google's Health/Fitness REST API: heart rate, steps, activities, sleep
(and potentially meals, though meals will primarily come from the app's own
flow). Not implemented. Reference implementation to study: xDrip's own
integration,
[`HealthGamut.java`](https://github.com/NightscoutFoundation/xDrip/blob/f418d5b3ea06bc36a1d35f0eeb459d875814cb46/app/src/main/java/com/eveningoutpost/dexdrip/healthconnect/HealthGamut.java).
Open question: whether offline access to Health Connect data is possible
(needs research before committing to this as a real-time source).

### 7.5 Output: diafit → AAPS (send bolus)

Not implemented, and explicitly flagged in the notes as not understood yet:
sending a bolus recommendation from diafit back into the loop likely
requires changes on the **AAPS side**, specifically around
`aaps/preferences/nsclient/synchronization` (receiving temp targets,
profile switches, insulin, carbs, therapy events). Do not assume this is a
simple REST call — budget research time, and check with the author before
starting implementation here since it may require patching the AAPS fork
itself.

### 7.6 User-uploaded meal photo

**[REVIEW]** The capture/storage half of this is done: the `+` add-meal flow, camera access
(`AddMealViewModel.createCameraImageUri()`/`copyGalleryImageToPrivateStorage()`), and writing the
photo to local file storage (`FileStorageRepositoryImpl`) all work today. What's still genuinely
missing — and is the real remaining MVP gap — is sending the photo for AI analysis (§11): nothing
calls the OpenAI API yet. `MealEntity.recommendation`/`.reasoning` columns exist but nothing
populates them; `AddMealViewModel.saveMeal()` only saves user-typed carbs/protein/fat/impact.

---

## 8. Local & cloud storage

**Local (decided):**
- **Room over SQLite** for structured data (`cgm`, `bolus`, `temp_basal`,
  `meals`, `profiles`, etc. — one DAO per entity, following the existing
  `BolusDao`/`CarbsDao`/`CGMDao` pattern).
- **SharedPreferences or Jetpack DataStore** for simple key-value settings
  (e.g. selected CGM/treatment data source). DataStore is the more modern
  Compose/coroutine-friendly choice if starting fresh; either is acceptable
  given the existing settings screen already works.
- **Internal app storage** for meal photos by default (private to the app;
  appropriate since these are personal health-adjacent photos). Cache
  directory is acceptable only if photos are treated as reproducible/
  disposable (they are not — don't put meal photos in cache unless there's
  a separate durable copy, e.g. after upload for AI analysis).

**Cloud (open decisions — do not build without confirming):**
- Whether to sync at all yet. The sync strategy itself
  (`data_storage/synchronization_between_cloud_and_local.md`) is an
  unresolved research topic: offline-first with background sync via
  WorkManager, sync-on-connectivity, and conflict resolution are all named
  as *general* patterns to draw from, but no decision has been made for
  diafit specifically.
- **Cloud DB engine**: Postgres vs. MongoDB — explicitly undecided
  (`data_storage/cloud_storage/cloud_db.md`: *"mongodb or postgresql? i also
  need to store images"*). Note the image-storage requirement should factor
  into this choice (e.g. object storage alongside whichever DB is picked,
  rather than blobs in the DB).
- **Cloud server / backend**: no decision recorded in this app's own notes.
  (The separate Django research prototype under
  `backend/visual_storytelling/` assumes Postgres + Docker Compose +
  Django-Q, but that's exploratory research for a different, web-based
  "AGP storytelling" concept — see §13 — not a settled choice for diafit's
  own backend.)

Given these are unresolved, **treat cloud sync as out of scope** until the
author decides; build the local-first flows so they don't assume a backend
exists.

---

## 9. Screens / navigation

> **[REVIEW]** Until 2026-10-06 the actual bottom nav was Home · Journal · Settings · Add-meal (4
> tabs) — Settings as a bottom tab, no History tab, no overflow menu. That's now been changed to
> match this section's own wireframe-derived spec: Settings moved behind the existing top-right
> overflow menu (`⋮`, already present on Home), and the freed bottom-nav slot now holds a blank
> **History** placeholder tab. Summary was already a tab (unlike the doc's "secondary screen"
> suggestion for it) — left as-is since it was already built as a tab, not reassigned.

Bottom navigation (from the home-screen wireframe,
`_obsidian/attachments/WhatsApp Image 2024-07-14 at 16.29.12.jpeg`):
**Home · Summary · Journal · History**, plus a large circular **`+`** button
for adding an entry (meal, primarily). A bell (notifications) and overflow
menu (`⋮`) sit in the top-right of Home. Likely-secondary screens reachable
from there (not in the bottom bar): **Insights**, **Profile**, **Settings**
— inferred from the feature packages and the views canvas layout, not from
an explicit nav spec, so confirm placement with the author rather than
treating it as fixed.

### Home (`views_home.md` / feature package `overview24h`)

The primary live view — a stacked, time-aligned, **last-24-hours**
timeline. From the wireframe:

- Header: large current glucose value, rotating trend-arrow icon, "time
  since last reading" (e.g. `1m 35s`).
- **Glucose** band: line chart, colored by value (teal = in range, purple =
  high, orange = low), with a dashed vertical "now" line; a hand-drawn-style
  dotted "swoosh" to the right of now suggests a forecast/prediction
  affordance (not necessarily literal — treat as an idea, not a spec).
- **Activity** band: sleep stages (Awake/REM/Light/Deep) as stacked
  horizontal segments, plus discrete activity bars (e.g. "Run"), aligned to
  the same time axis as glucose.
- **Loop** band: temp-basal-vs-baseline shown as a bar/area chart with
  small triangle markers above and below the zero line (likely marking
  temp-basal start/end or over/under-baseline events).
- **Bolus** band: a smoothed "mountain" / area shape over time with discrete
  unit-amount bubbles (e.g. `5U`, `2U`) anchored at bolus times.
- **Carbohydrates** band: meal photo thumbnail(s) with a decaying triangular
  curve and a gram label (e.g. `43g`), representing carb absorption over
  time.

All bands share one horizontal time axis so a meal, its bolus, and the
resulting glucose/loop response are visually aligned — this cross-band time
alignment is the core visual idea of the screen and should be preserved
even if individual chart implementations change.

### Journal (`views_journal.md`, feature package likely `logbook`)

A reverse-chronological feed of logged events as compact cards (from
`_obsidian/attachments/WhatsApp Image 2024-07-14 at 16.29.13.jpeg`). Each
card shows:
- An icon/thumbnail for the event (meal photo; a "Zzz" tile for sleep).
- A small glucose-impact sparkline for the event's time window, with
  before/during markers (e.g. a triangle at the event start, an
  inverted-triangle at the peak/trough).
- A compact strip of colored bars underneath indicating related activity/
  bolus/carb events in that window.
- Two small indicators on the right: a ring/donut chart with a percentage
  (meaning not specified in the notes — plausibly time-in-range or
  glycemic-impact score for that event; confirm with the author) and two
  solid color swatches (likely carbs/insulin totals, matching the
  attribute color coding in §15).

This is effectively the **logbook** called for in the project purpose:
"logbook with all events (meals, sleep, activity) with filter for event
type and free text search" — filter/search UI is not yet designed, just
named as a requirement.

### Summary (`views_summary.md`, feature package `summary`/`statistics`)

Period overview. Metrics called out explicitly: mean & std of glucose,
time-in-range (chart only, with details on click/tap), carbs, insulin
(bolus and basal totals), steps, calories and nutrients. Possibly uses the
pentagon/hexagon multi-metric chart (§10) — placement undecided.

### History (`views_history.md`, feature package `statistics`/`compare`)

Multi-day view, two zoom levels:
- **Day**: similar stacked layout to Home, plus per-day statistics.
- **Multiple days**: an AGP (ambulatory glucose profile) chart + a
  "horizon" chart, both with a weekday filter (e.g. compare all Mondays).
  Open design questions noted: whether to also show stacked TIR charts per
  day in a tabular layout, and/or a separate detail view using a
  GitHub-contributions-style heatmap per metric per day.

### New / Add (`views_new.md`, feature package `addmeal`)

The screen behind the `+` button. Primary flow for MVP: capture or pick a
meal photo → send to AI → show estimated nutrients + insulin
recommendation + reasoning → let the user confirm/edit → save. (See §4 for
the addmeal package's internal layer structure, and §11 for the AI prompt.)

### Profile (`views_profile.md`, feature package `profile`)

Shows basal rate, carb ratio, insulin sensitivity, carb absorption —
backed by the `profiles`/`basal_segments`/`carb_ratios`/`sensitivities`
schema (§6). Whether the optimizer (§12) lives on this screen or a separate
one is explicitly left open in the notes.

### Settings (`views_settings.md`)

Data-source selection (already implemented for CGM: Juggluco/xDrip). Extend
here for future sources (AAPS/Nightscout toggles, Google Health
Connect) and account/user settings as multi-user support lands.

### Insights (`views_insights.md`, feature package `highlights`)

AI-detected pattern highlights. No content specified yet beyond the name —
this is explicitly later-stage, tied to the glucose-clustering AI feature
in §11.

### Compare (feature package `compare`, no dedicated view file yet)

"Screen where I can compare multiple events and understand their effect,
combined with some kind of pattern recognition" — named as a goal in the
project purpose but has no view note or wireframe yet. Needs design before
implementation.

---

## 10. Charts

Library: **Vico**. No native pan/zoom support — if that's needed, implement
via `Modifier.pointerInput { detectTransformGestures { ... } }` driving
Vico's own scale/translation, not a Vico built-in.

| Chart | Where used | Purpose | Notes / gotchas already discovered |
|---|---|---|---|
| CGM line chart | Home, History (day view) | Glucose over time | Scoped to last 24h (not "today"). Marker drawn as a line (not a dot). Y-axis labels placed *inside* the chart area to save horizontal space. X-axis label rotation and alignment have been fiddly (`labelRotationDegrees = -30f`, custom `valueFormatter` to avoid duplicate/misaligned date labels — see the working Vico config snippet captured in `chart_cgm.md`, reuse it rather than re-deriving from scratch). Color above/below target range via a top/bottom shader gradient (Vico discussion #483). Known open bug: marker rendering has an unresolved visual bug; x-axis label alignment also still an open bug. |
| Bolus & carb curves | Home, day view | Smoothed "mountain" visualization of bolus/carb activity over time | Area/mountain shape, not simple bars — matches the Home wireframe. |
| Bolus & carb **events** | Home, day view | Discrete event markers (not curves) | Option under consideration: let the user bundle multiple close-together entries into a single "meal" (not yet decided/implemented). |
| Activity chart | Home, day view | Sleep stages + activity bars | Includes heart rate, steps, sleep, activities. |
| Loop chart | Home, day view | Temp basal vs. baseline | No separate note content beyond the wireframe. |
| Food image chart | Home, day view (carbs band) | Shows meal photo + decay curve + gram label | |
| AGP (ambulatory glucose profile) | History (multi-day) | Percentile-band glucose profile across many days | Backing calculation task tracked as `backend/tasks/calculation_chart_agp.md` (no implementation yet — just a placeholder referencing this chart). Reference implementation worth studying: xDrip's [`PercentileView.java`](https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/stats/PercentileView.java#L31). |
| Horizon chart | History (multi-day), with weekday filter | Compact multi-day glucose overview | Backing calc tracked as `backend/tasks/calculation_chart_horizon.md`, no implementation yet. |
| Time-in-range (TIR) | Summary | % time in/above/below range | Undecided chart type: stacked bar, or a pie/donut with the number in the middle. |
| Pentagon/hexagon chart | Summary (placement undecided) | Multi-metric snapshot (mean/std, carbs, insulin, steps, calories) in one shape | Not started; exact metric set and shape (5 vs 6 axes) still open. |
| Statistics heatmap | History (detail view, maybe) | GitHub-contributions-style heatmap, one per metric, per day | Explicitly marked "not sure yet if makes sense" — lowest priority / validate the idea before investing in it. |
| Glucose cluster chart | Insights | Visualizes clusters of similar daily glucose curves | Depends on the clustering backend feature in §11; "not sure yet where to put" in the nav. |

---

## 11. AI features

### 11.1 Meal photo → nutrients (MVP — build this)

Send the meal photo to a vision-capable LLM (OpenAI API). Draft prompt
already written (reuse/adapt rather than rewrite from scratch):

```
You are a highly accurate and detailed food recognition and nutrition analysis expert.

Given the following image of a meal, provide a structured JSON output containing the following information:

1. Dish Name: (Most likely name of the entire meal, e.g., "Chicken Caesar Salad", "Pasta Bolognese", or "Mixed Vegetable Curry"). Be as specific as possible.

2. Ingredients: (A list of all identifiable ingredients in the image, including sauces/seasonings/garnishes, as granular as possible, each with an estimated quantity/weight).

3. Macronutrient Breakdown (per serving): Calories (kcal), Protein (g), Carbohydrates (g), Fat (g) [with Saturated/Unsaturated split], Fiber (g), Sugar (g), Sodium (mg).

4. Reasoning: a precise and analytical breakdown of the carb calculation.

5. Meal impact duration: "SHORT" (e.g. dextrose/juice), "MEDIUM", or "LONG" (e.g. a cheesy pizza) — an estimate of how long the meal will affect blood sugar.

Output Format: JSON, e.g.:
{
  "dish_name": "",
  "ingredients": [{"name": "", "quantity": ""}],
  "macronutrients": {
    "calories": "", "protein": "", "carbohydrates": "", "fat": "",
    "saturated_fat": "", "unsaturated_fat": "", "fiber": "", "sugar": "", "sodium": ""
  },
  "Reasoning": "",
  "Meal impact duration": "SHORT | MEDIUM | LONG"
}
```

Map the response into `meals` (`carbs`/`protein`/`fat`, `recommendation`,
`reasoning`) and `meals_glycemic_profiles` is filled in later from actually
observed CGM data post-meal, not from this AI call.

### 11.2 Glucose clustering (far future, not MVP)

Group similar daily glucose curves using **dynamic time warping (DTW) +
DBSCAN**, to surface in the Glucose cluster chart (§10) and Insights/
highlights screen. Needs its own calculation task (currently just a
placeholder note, `backend/ai_features/calculation_chart_glucose_cluster.md`,
with no algorithmic detail decided yet).

### 11.3 LLM cluster description (far future, not MVP)

Once clusters exist, call an LLM to generate a human-readable description
of what characterizes each cluster (e.g. "mornings after a run tend to run
lower"). Noted as "probably not free" — factor API cost into any design.

### 11.4 Closed-loop reinforcement learning (explicitly "far far future")

Mentioned once (`backend/ai_features/loop_with_reinforcement_learning.md`)
with zero elaboration beyond the "far far future" label. **Do not design or
implement anything here** without a specific, separate request — it's
recorded purely as a long-term idea.

---

## 12. Profile optimizer

`backend/tasks/calculation_profile_optimizer.md` only says this feeds
`views_profile`; no algorithm or approach has been decided. Treat "optimize
my profile" (basal/carb-ratio/sensitivity suggestions from historical data)
as a named goal with no design yet — needs a design discussion before
implementation.

---

## 13. Explicitly out of scope right now

- **Cloud sync / cloud DB / backend server** — storage engine undecided,
  sync strategy undecided (§8). Don't build against a backend that doesn't
  exist yet.
- **The Django "AGP storytelling" web app** under
  `backend/visual_storytelling/` — this is a separate, speculative research
  track about presenting AGP data as a scroll-driven narrative web page
  (Plotly + Django + IntersectionObserver), explored independently of the
  Kotlin app. It assumes its own stack (Postgres, Docker Compose, Django-Q,
  a REST API) that has nothing to do with diafit's Android codebase. Only
  relevant if/when the author decides to actually integrate a cloud backend
  — not a dependency for anything in this document.
- **Reinforcement-learning closed loop** (§11.4).
- **AAPS-side changes** for sending boluses back into the loop (§7.5) — treat
  as a research spike, not an implementation task, until scoped.
- **Kotlin Multiplatform migration itself** — confirmed as a good direction,
  but no migration has started; don't restructure modules for KMP unless
  asked, though avoiding gratuitously Android-only APIs in new code is
  reasonable.

---

## 14. Known TODOs / bugs to track (carried over from `tasks.md` and journal entries)

- Fix: Juggluco CGM duplicate inserts.
- Fix: spurious `0` glucose value inserted on app restart.
- Wire `AddMealViewModel`/`JournalViewModel` (and others) to use cases
  instead of reaching into repositories directly (§2).
- Thread `user_id` through all use cases consistently (multi-user support).
- Implement CGM backfill (gap-detection + Juggluco webserver fetch).
- Implement AAPS temp target, profile switch, pump site/cartridge/sensor
  change ingestion.
- Implement Nightscout profile fetch.
- Implement Google Health Connect integration.
- Implement meal photo capture → storage → AI analysis → save flow (MVP).
- Add glycemic-impact and meal-type autocompletion in the add-meal form.
- Add unit tests (none exist yet).
- Finish app navigation between screens.
- Migrate DI from Dagger to Koin.
- Fix CGM chart marker rendering bug and x-axis label alignment bug.
- Decide TIR chart type (bar vs. donut) and pentagon/hexagon metric set.
- Decide meal-event bundling UX (combine nearby entries into one meal?).

---

## 15. Conventions to follow

- **IDs**: `UUID` for all entity primary keys.
- **Timestamps**: store in **UTC**; the data sources (Nightscout/AAPS) send
  UTC with separate `utcOffset`/`sysTime` fields when local time matters for
  display — keep the UTC value as the source of truth and apply offset only
  at render time.
- **Soft deletes**: an `is_valid` boolean flag, not row deletion — matches
  how upstream Nightscout/AAPS treatments already behave (they also carry
  `isValid`).
- **Colors**: attribute-based color coding is a settled decision (not just
  a style preference) — carbs, bolus, and glucose range (high/normal/low)
  each have a distinct, consistent color used across every chart and card
  (see the teal/purple/orange glucose coloring and the blue bolus /
  orange-brown carb coloring in the Home and Journal wireframes). When
  adding any new chart or card, reuse these existing colors rather than
  introducing new ones for the same attributes.
- **Wikilinks**: this vault's own notes cross-reference with
  `[[note_name]]` — irrelevant to app code, but if you're asked to update
  the *notes* alongside the code, preserve that convention (see this
  repo's `CLAUDE.md`).
