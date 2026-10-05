# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow

- Commit every change (even small fixes) with `git commit` — don't leave edits uncommitted at the end of a turn.
- After any change that affects app code, rebuild and reinstall on the connected phone so it can be tried immediately:
  `.\gradlew.bat installDebug` (falls back to `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices` to confirm a device is attached if `adb` isn't on `PATH`).

## Project

Diafit is an Android app (Kotlin, Jetpack Compose) for people with diabetes. It aggregates CGM, nutrition, activity, and sleep data from remote sources (Nightscout, Tidepool, Google Health Connect) and local sources (xDrip, AndroidAPS/AAPS, Juggluco), and applies visual analytics / AI to support glucose management.

- Package namespace: `uk.scimone.diafit`
- `minSdk` 28, `targetSdk`/`compileSdk` 35, JVM target 11
- Base API URL is hardcoded in `app/build.gradle.kts` as a `buildConfigField` (`BASE_URL` = `https://gluco.mooo.com`); `API_KEY` is also defined there (currently empty)

## Commands

Use the Gradle wrapper (`gradlew.bat` on Windows, PowerShell/cmd):

```
.\gradlew.bat assembleDebug          # build debug APK
.\gradlew.bat test                   # run JVM unit tests (app/src/test)
.\gradlew.bat connectedAndroidTest   # run instrumented tests on a device/emulator (app/src/androidTest)
.\gradlew.bat testDebugUnitTest --tests "uk.scimone.diafit.SomeClassTest"   # run a single unit test class
.\gradlew.bat lint                   # Android lint
```

There is no CI workflow in this repo (no `.github/workflows`) and no instrumented/unit tests beyond the default Android template stubs (`ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`) as of now — treat test coverage as sparse and don't assume conventions exist that aren't visible in the code.

## Architecture

Clean Architecture + MVVM, organized as **feature packages** under `app/src/main/java/uk/scimone/diafit/`, each typically split into `di/`, `presentation/` (Compose screens + ViewModels), `domain/` (use cases, models, repository interfaces), and `data/` (repository impls, local/remote sources):

- `core/` — shared infrastructure used by every feature: Room database (`core/data/local`), Ktor networking to Nightscout (`core/data/networking`), repository interfaces/impls for CGM/Bolus/Meal (`core/domain/repository`, `core/data/repository`), shared use cases, and the health-sync-source abstraction (see below).
- `home/`, `journal/`, `addmeal/`, `settings/` — individual feature modules, each wired up via its own Koin module in `<feature>/di/`.
- `ui/` — Compose theme.
- `debugintent/` — manual debug entry point for simulating external intents.

There is no navigation library: `MainActivity` holds a single `selectedTab` int and switches between feature screens (`HomeScreen`, `JournalScreen`, `SettingsScreen`, `AddMealScreen`) in a `when` block, with `BottomNavigationBar` driving tab selection. `userId` is currently hardcoded to `1` in `MainActivity` (no auth system yet).

### Dependency injection (Koin)

All DI modules are registered in `DiafitApp.onCreate()` via `startKoin { modules(...) }`. Feature ViewModels that need per-instance params (e.g. `userId`) are registered with `viewModel { (userId: Int) -> ... }` and resolved with `koinViewModel { parametersOf(userId) }` at the call site.

### Sync source abstraction

External data sources (Nightscout, xDrip, Juggluco, AAPS) implement `HealthSyncSource` (`core/domain/repository/syncsource/HealthSyncSource.kt`), with `IntentHealthSyncSource` for sources that receive data via broadcast `Intent` (xDrip, Juggluco, AAPS) rather than polling. Implementations live under `core/data/repository/syncsource/{cgmsyncsource,bolussyncsource}/`.

Sources are bound in `core/di/syncModule.kt` using **named Koin qualifiers** (`named("NIGHTSCOUT")`, `named("XDRIP")`, `named("JUGGLUCO")`, `named("AAPS")`), then collected into a `sources: Map<CgmSource/BolusSource, HealthSyncSource>` passed to `SyncCgmDataUseCase` / `SyncBolusDataUseCase`. The active source is a user setting (`settings/domain/model/CgmSource`, `BolusSource`) read via `GetCgmSourceUseCase`/`GetBolusSourceUseCase`, and chosen by the user on the Settings tab.

CGM sync runs as a foreground service (`core/data/service/CgmServiceManager` + `RemoteCgmSyncService`) started from `MainActivity.onCreate()` and restarted whenever the user changes CGM source (`SettingsViewModel.restartCgmServiceEvent`). Broadcast-based sources are handled by the manifest-declared `HealthReceiver` (`core/presentation/receivers`, works regardless of whether any service is running) enqueuing `BolusBroadcastWorker`/`CgmBroadcastWorker` (WorkManager, now requesting expedited execution). **Important:** those workers only parse the broadcast if it matches the user's *currently selected* Settings source (`CgmBroadcastWorker.kt`/`BolusBroadcastWorker.kt` call `GetCgmSourceUseCase`/`GetBolusSourceUseCase` and `when` on it) — a broadcast from xDrip/Juggluco is silently dropped (logged, not inserted) if the user hasn't selected that source in Settings, even though the receiver still "received" it.

**Overnight background reliability (added 2026-10-05):** the test device (Xiaomi, Android 16) stopped receiving CGM values after being left idle overnight — most likely the OS/OEM killing the foreground sync service or deferring WorkManager jobs during Doze, since MIUI/HyperOS is known for aggressive background app killing even beyond stock Android's battery optimization. Mitigations in place: `RemoteCgmSyncService`/`BroadcastIntentHealthSyncService` return `START_STICKY`; `CgmServiceManager.start()` is idempotent and gained `ensureRunning()`, which checks whether the CGM sync notification (`CgmServiceManager.CGM_SYNC_NOTIFICATION_ID`) is still active and force-restarts only if it isn't; a new periodic `CgmServiceWatchdogWorker` (registered in `DiafitApp.onCreate()`, minimum WorkManager interval of 15 min) calls `ensureRunning()` for whatever source is currently selected, which self-heals even after a full process death since WorkManager persists across that. None of this can fully override OEM-specific background-kill policies — the Settings screen's "Background reliability" section links straight to MIUI/HyperOS's Autostart management activity on Xiaomi devices (`com.miui.securitycenter`/`com.miui.permcenter.autostart.AutoStartManagementActivity`, via an unofficial but widely-used component name — wrapped in try/catch since it isn't guaranteed stable across MIUI/HyperOS versions), since enabling Autostart is a user-granted permission no code change can substitute for. Not yet verified whether these mitigations actually survive a full overnight test — reconfirm with the user after a night's use.

**Known gotcha (confirmed on-device 2026-10-05):** Juggluco's "Data exchange" settings has independent toggles for several broadcast formats ("XDrip broadcast", "Glucodata broadcast", "Patched Libre", "LibreView", "Eversense") and can fire more than one concurrently, on independent per-minute cadences. If xDrip+ (`com.eveningoutpost.dexdrip`) is *also* installed and receiving Juggluco's "XDrip broadcast" output, it will separately re-broadcast as `com.eveningoutpost.dexdrip.BgEstimate` (with `SourceDesc: Other App` in the extras — that's the tell it's a relay, not Juggluco talking directly). Don't assume which format is "the" Juggluco broadcast from one sample; a short (<2 min) logcat capture can plausibly miss the `glucodata.Minute` tick entirely and show only the xDrip-relayed one, which looks identical to a real mismatch. Capture at least 2–3 minutes (`adb logcat "HealthReceiver:D" "CgmSync*:D" "CgmInsertWorker:D" "*:S"`) before concluding a source is wrong. In practice: selecting "JUGGLUCO" in Settings correctly parses Juggluco's native `glucodata.Minute` broadcast with no xDrip+ dependency — confirmed with consecutive successful inserts one minute apart. **Setup step required in Juggluco itself:** its "Glucodata broadcast" toggle isn't a single global on/off switch — Juggluco → Settings → Data exchange → Glucodata broadcast opens a per-app allow-list of installed apps; `uk.scimone.diafit` (Diafit) must be individually switched on in that list, or the broadcast never reaches Diafit even though Juggluco's own UI shows readings updating every minute. A more robust fix would have `CgmBroadcastWorker` dispatch by the intent's actual `action` (each `IntentHealthSyncSource` already exposes its own `ACTION` constant) instead of gating on the Settings selection at all, so a stray broadcast from an unselected/relaying source is cleanly ignored rather than logged as a confusing "unexpected action"; not done yet.

### Networking

Ktor client built via `core/data/networking/util/HttpClientFactory`; Nightscout REST calls live in `core/data/networking/NightscoutApi.kt` with DTOs in `networking/dto/`. Results use a custom `Result`/`NetworkError` sealed-type wrapper (`core/domain/util/networking/`) rather than exceptions — `safeCall`/`responseToResult` convert Ktor responses into this type.

### Charts

Glucose/activity visualizations use **Vico** (`com.patrykandpatrick.vico`, Compose + M3 theming), under `home/presentation/components/chart/`. Insulin activity-curve math lives in `core/domain/model/InsulinActivity.kt`.

### UI

`BottomNavigationBar` (`core/presentation`) uses Material3 `NavigationBar` with filled/outlined icon pairs per tab (`material-icons-extended` dependency) plus a custom raised circular button for the Add-meal action (tab index `ADD_MEAL_TAB_INDEX = 4`). **Gotcha:** don't put `fillMaxHeight()` on a custom child inside `NavigationBar`'s row — Material3's `NavigationBar` only sets a *minimum* row height (not a max/fixed one via `defaultMinSize`), and `Scaffold` gives the `bottomBar` slot loose/tall height constraints so custom bars can be as tall as needed; a `fillMaxHeight()` child makes the whole bar balloon to nearly fill the screen, squeezing all page content into near-zero height. Use `Modifier.align(Alignment.CenterVertically)` (a `RowScope` extension) instead to vertically center a custom item without affecting the row's height. This exact bug shipped once during development (2026-10-05) and was caught by rendering the app and screenshotting via `adb shell screencap` — always do that after a bottom-bar/Scaffold layout change rather than trusting it compiles.

`SettingsScreen` is organized into `SettingsSection` cards (icon + title + content), each holding a logical group (CGM source, Bolus source, glucose target range, background reliability).

### Persistence

Room database `AppDatabase` (`core/data/local/AppDatabase.kt`) with DAOs for CGM, Bolus, and Meal entities. Schema exports go to `app/schemas` (`room.schemaLocation` kapt arg); migrations are added explicitly (e.g. `MIGRATION_9_10`) in `coreModule.kt` — bump and add a migration when changing any `@Entity`.

## Known issues / tech debt

Found during a senior-level code review (2026-10-05); fixed items are already applied, the rest are open. Check these off as they get addressed instead of re-discovering them.

**Fixed:**
- ~~`HealthReceiver` was registered twice (once statically in the manifest, once dynamically in `BroadcastIntentHealthSyncService`), so every CGM/food broadcast was processed twice.~~ Removed the dynamic registration; the manifest receiver alone is sufficient and works regardless of service state.
- ~~`AddMealViewModel.saveMeal()` generated a fresh `imageId` for every save, leaving the original captured/picked photo file orphaned on disk forever.~~ Reuses the original `imageId`; `FileStorageRepositoryImpl.saveImageToLocalFolder` now short-circuits when source and destination already resolve to the same file (needed because naively copying a file onto itself truncates it before the read completes).
- ~~`HomeViewModel.observeLatestCgm()`'s error handler replaced the entire `HomeState` (`_state.value = HomeState(...)`) instead of `.copy()`, wiping cgm/bolus/carb/meal history on any single CGM error.~~ Now uses `.update { it.copy(...) }` like the other flows in the file.
- ~~`HealthReceiver.toWorkData()` silently drops unsupported extra types~~ (2026-10-05) — now handles `Boolean` and logs anything else it drops.
- ~~`CgmServiceManager.start()` unconditionally stops+restarts both foreground services on every call~~ (2026-10-05) — now idempotent; also gained `ensureRunning()` for the overnight-reliability watchdog (see Sync source abstraction section above).
- ~~Bottom nav icons didn't match their tabs (Email for "Summary", Create for "Journal", DateRange for "Settings") and Settings was a flat unstyled list~~ (2026-10-05) — overhauled both; see the UI section above.
- ~~`CgmDao.getLatestCgm()` didn't filter by `userId`~~, unlike every other query in the same DAO (2026-10-05) — now takes a `userId` param, threaded through `CgmRepository`/`GetLatestCgmUseCase`/`HomeViewModel`/`CgmSyncSourceNightscout`.
- ~~`BolusDao.getAllBolusSince` had no `userId` filter at all~~ (2026-10-05) — now takes a `userId` param, threaded through `BolusRepository`/`GetAllBolusSinceUseCase`/`HomeViewModel`.
- ~~Dead code: `MealRepository.storeImage` / `MealRepositoryImpl.saveImageToLocalFolder` duplicated `FileStorageRepositoryImpl` and were never called~~ (2026-10-05) — deleted, along with `MealRepositoryImpl`'s now-unused `Context` constructor param.
- ~~Broad exception swallowing with no logging in `MealRepositoryImpl` / `CgmRemotePoller.start()`~~ (2026-10-05) — both now `Log.e` the caught exception.
- ~~`HomeViewModel.observeCarbHistory()` and `observeMealHistory()` independently called `getAllMealsSinceUseCase` as separate `Flow` collectors for the same data~~ (2026-10-05) — collapsed into a single `observeMealData()`.
- ~~Home screen's CGM/bolus/insulin-activity/carb charts each kept an independent Vico scroll/zoom state, so panning or zooming one didn't move the others even though all plot the same last-24h time axis~~ (2026-10-05) — `HomeScreen` now hoists one shared `VicoScrollState`/`VicoZoomState` and passes it to every chart.

**Still open, in rough priority order:**

1. **Security — exported, unauthenticated health-data receiver.** `HealthReceiver` is `android:exported="true"` with no permission (`AndroidManifest.xml`). Any other app on the device can broadcast `glucodata.Minute` / `BgEstimate` / `NEW_FOOD` with forged extras and have it parsed straight into Room if the matching source happens to be selected in Settings. Consider a signature-level permission or validating the sending package.
2. **Security/privacy — verbose network logging ships in release.** `HttpClientFactory.create()` installs `Logging { level = LogLevel.ALL }` unconditionally, logging full Nightscout request/response bodies and URLs (including the `apiKey` query param) to Logcat in release builds too. Gate behind `BuildConfig.DEBUG`.
3. **Nightscout URL isn't user-configurable.** `BASE_URL`/`API_KEY` are compile-time `buildConfigField`s (`app/build.gradle.kts`) pointing at one hardcoded server. There's a Settings screen and a whole sync-source abstraction, but no per-user Nightscout server URL/secret field — this is almost certainly why a fresh install "receives no data" if the user's own Nightscout isn't hosted at that exact domain. Also: the API key is sent as a `?apiKey=` query param (`constructUrl.kt`) rather than Nightscout's usual hashed `api-secret` header, so even a correctly-pointed instance with auth enabled may reject requests.
4. **`userId = 1` hardcoded in half a dozen places**: `MainActivity`, `CgmSyncSourceNightscout`, `CgmSyncSourceXdrip`, `CgmSyncSourceJuggluco`, `BolusSyncSourceAaps`. No single source of truth for "current user".
5. **`BolusSyncSourceAaps` only reads `treatments[0]`** from the AAPS broadcast — any additional treatments in the same batch are silently dropped.
6. **`HomeViewModel.startCountdownUpdater()`** runs `while (true) { delay(1000) }` for the ViewModel's entire lifetime, ticking every second even while the Home tab isn't visible. Should be lifecycle-gated or computed as a derived/UI-side value instead of mutating ViewModel state forever.
7. **`NightscoutApi.getCgmEntries`** requests `count = 10000000` unconditionally instead of a sane page size.
8. **Release build has `isMinifyEnabled = false`** (`app/build.gradle.kts`) — no shrinking/obfuscation on release APKs.
9. `READ_EXTERNAL_STORAGE` is declared without `android:maxSdkVersion`, so it's requested even on API 33+ where `READ_MEDIA_IMAGES` already covers it.
10. Threading style is inconsistent in `MealRepositoryImpl` (some methods wrap in `withContext(Dispatchers.IO)`, `getMealsByUserId` doesn't) — not wrong since Room's suspend DAOs dispatch off-main internally, just inconsistent.
11. README advertises Tidepool, Google Health Connect, and AI-based clustering — none of that exists in code yet. Keep in mind when it's updated so it doesn't mislead contributors.
