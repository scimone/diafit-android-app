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

CGM sync runs as a foreground service (`core/data/service/CgmServiceManager` + `RemoteCgmSyncService`) started from `MainActivity.onCreate()` and restarted whenever the user changes CGM source (`SettingsViewModel.restartCgmServiceEvent`). Broadcast-based sources are handled by the manifest-declared `HealthReceiver` (`core/presentation/receivers`, works regardless of whether any service is running) enqueuing `BolusBroadcastWorker`/`CgmBroadcastWorker` (WorkManager). **Important:** those workers only parse the broadcast if it matches the user's *currently selected* Settings source (`CgmBroadcastWorker.kt`/`BolusBroadcastWorker.kt` call `GetCgmSourceUseCase`/`GetBolusSourceUseCase` and `when` on it) — a broadcast from xDrip/Juggluco is silently dropped (logged, not inserted) if the user hasn't selected that source in Settings, even though the receiver still "received" it.

### Networking

Ktor client built via `core/data/networking/util/HttpClientFactory`; Nightscout REST calls live in `core/data/networking/NightscoutApi.kt` with DTOs in `networking/dto/`. Results use a custom `Result`/`NetworkError` sealed-type wrapper (`core/domain/util/networking/`) rather than exceptions — `safeCall`/`responseToResult` convert Ktor responses into this type.

### Charts

Glucose/activity visualizations use **Vico** (`com.patrykandpatrick.vico`, Compose + M3 theming), under `home/presentation/components/chart/`. Insulin activity-curve math lives in `core/domain/model/InsulinActivity.kt`.

### Persistence

Room database `AppDatabase` (`core/data/local/AppDatabase.kt`) with DAOs for CGM, Bolus, and Meal entities. Schema exports go to `app/schemas` (`room.schemaLocation` kapt arg); migrations are added explicitly (e.g. `MIGRATION_9_10`) in `coreModule.kt` — bump and add a migration when changing any `@Entity`.

## Known issues / tech debt

Found during a senior-level code review (2026-10-05); fixed items are already applied, the rest are open. Check these off as they get addressed instead of re-discovering them.

**Fixed this review:**
- ~~`HealthReceiver` was registered twice (once statically in the manifest, once dynamically in `BroadcastIntentHealthSyncService`), so every CGM/food broadcast was processed twice.~~ Removed the dynamic registration; the manifest receiver alone is sufficient and works regardless of service state.
- ~~`AddMealViewModel.saveMeal()` generated a fresh `imageId` for every save, leaving the original captured/picked photo file orphaned on disk forever.~~ Reuses the original `imageId`; `FileStorageRepositoryImpl.saveImageToLocalFolder` now short-circuits when source and destination already resolve to the same file (needed because naively copying a file onto itself truncates it before the read completes).
- ~~`HomeViewModel.observeLatestCgm()`'s error handler replaced the entire `HomeState` (`_state.value = HomeState(...)`) instead of `.copy()`, wiping cgm/bolus/carb/meal history on any single CGM error.~~ Now uses `.update { it.copy(...) }` like the other flows in the file.

**Still open, in rough priority order:**

1. **Security — exported, unauthenticated health-data receiver.** `HealthReceiver` is `android:exported="true"` with no permission (`AndroidManifest.xml`). Any other app on the device can broadcast `glucodata.Minute` / `BgEstimate` / `NEW_FOOD` with forged extras and have it parsed straight into Room if the matching source happens to be selected in Settings. Consider a signature-level permission or validating the sending package.
2. **Security/privacy — verbose network logging ships in release.** `HttpClientFactory.create()` installs `Logging { level = LogLevel.ALL }` unconditionally, logging full Nightscout request/response bodies and URLs (including the `apiKey` query param) to Logcat in release builds too. Gate behind `BuildConfig.DEBUG`.
3. **Nightscout URL isn't user-configurable.** `BASE_URL`/`API_KEY` are compile-time `buildConfigField`s (`app/build.gradle.kts`) pointing at one hardcoded server. There's a Settings screen and a whole sync-source abstraction, but no per-user Nightscout server URL/secret field — this is almost certainly why a fresh install "receives no data" if the user's own Nightscout isn't hosted at that exact domain. Also: the API key is sent as a `?apiKey=` query param (`constructUrl.kt`) rather than Nightscout's usual hashed `api-secret` header, so even a correctly-pointed instance with auth enabled may reject requests.
4. **`CgmDao.getLatestCgm()` doesn't filter by `userId`**, unlike every other query in the same DAO (`getAllCgmSince`, `getEntriesBetween`). Invisible today only because `userId` is hardcoded to `1` everywhere.
5. **`BolusDao.getAllBolusSince` has no `userId` filter at all**, even though `BolusEntity.userId` exists — bolus history is already effectively global across users.
6. **`userId = 1` hardcoded in half a dozen places**: `MainActivity`, `CgmSyncSourceNightscout`, `CgmSyncSourceXdrip`, `CgmSyncSourceJuggluco`, `BolusSyncSourceAaps`. No single source of truth for "current user".
7. **`BolusSyncSourceAaps` only reads `treatments[0]`** from the AAPS broadcast — any additional treatments in the same batch are silently dropped.
8. **Dead code**: `MealRepository.storeImage` / `MealRepositoryImpl.saveImageToLocalFolder` duplicate `FileStorageRepositoryImpl` and are never called (confirmed via grep — `CreateMealUseCase` only uses `FileStorageRepository`). Safe to delete.
9. **`HealthReceiver.toWorkData()` silently drops unsupported extra types** (anything that isn't `Int`/`Float`/`Long`/`Double`/`String`, e.g. `Boolean`) with no log.
10. **Broad exception swallowing with no logging**: `MealRepositoryImpl` catches generic `Exception` and returns `Result.failure(e)` with no `Log.e`; `CgmRemotePoller.start()` uses `e.printStackTrace()` instead of `Log`, which is invisible in a release build.
11. **`HomeViewModel.observeCarbHistory()` and `observeMealHistory()`** both independently call `getAllMealsSinceUseCase(nowMinus24h, userId)` as separate `Flow` collectors for the same underlying data — collapse into one.
12. **`HomeViewModel.startCountdownUpdater()`** runs `while (true) { delay(1000) }` for the ViewModel's entire lifetime, ticking every second even while the Home tab isn't visible. Should be lifecycle-gated or computed as a derived/UI-side value instead of mutating ViewModel state forever.
13. **`CgmServiceManager.start()`** unconditionally stops+restarts both foreground services on every call, including on every `MainActivity.onCreate()` even when the source hasn't changed — visible service blip on every app relaunch.
14. **`NightscoutApi.getCgmEntries`** requests `count = 10000000` unconditionally instead of a sane page size.
15. **Release build has `isMinifyEnabled = false`** (`app/build.gradle.kts`) — no shrinking/obfuscation on release APKs.
16. `READ_EXTERNAL_STORAGE` is declared without `android:maxSdkVersion`, so it's requested even on API 33+ where `READ_MEDIA_IMAGES` already covers it.
17. Threading style is inconsistent in `MealRepositoryImpl` (some methods wrap in `withContext(Dispatchers.IO)`, `getMealsByUserId` doesn't) — not wrong since Room's suspend DAOs dispatch off-main internally, just inconsistent.
18. README advertises Tidepool, Google Health Connect, and AI-based clustering — none of that exists in code yet. Keep in mind when it's updated so it doesn't mislead contributors.
