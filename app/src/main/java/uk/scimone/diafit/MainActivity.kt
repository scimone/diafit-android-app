package uk.scimone.diafit

import uk.scimone.diafit.R
import androidx.compose.ui.res.stringResource
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import uk.scimone.diafit.addmeal.presentation.MealEditorScreen
import uk.scimone.diafit.core.domain.usecase.SetMealValidUseCase
import uk.scimone.diafit.logs.presentation.components.NewEntrySheet
import uk.scimone.diafit.logs.presentation.components.OpenMealSummary
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.usecase.GetOpenSittingUseCase
import uk.scimone.diafit.logs.presentation.detail.MealDetailScreen
import uk.scimone.diafit.logs.presentation.model.LogsEntryKind
import uk.scimone.diafit.logs.presentation.model.BolusEntryUi
import uk.scimone.diafit.profile.presentation.ProfileScreen
import uk.scimone.diafit.profile.presentation.ProfileSwitchDetailScreen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Insights
import uk.scimone.diafit.logs.presentation.model.PumpEventUi
import uk.scimone.diafit.logs.presentation.model.GlucoseEpisodeUi
import uk.scimone.diafit.logs.presentation.model.MealEntityUi
import androidx.activity.compose.BackHandler
import uk.scimone.diafit.ui.theme.DiafitTheme
import uk.scimone.diafit.core.presentation.ADD_MEAL_TAB_INDEX
import uk.scimone.diafit.core.presentation.BottomNavigationBar
import org.koin.android.ext.android.inject
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.data.service.CgmServiceManager
import uk.scimone.diafit.logs.presentation.LogsScreen
import uk.scimone.diafit.history.presentation.HistoryScreen
import uk.scimone.diafit.history.presentation.detail.DayDetailScreen
import uk.scimone.diafit.home.presentation.HomeScreen
import uk.scimone.diafit.home.presentation.HomeTitle
import uk.scimone.diafit.home.presentation.HomeViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase
import uk.scimone.diafit.settings.presentation.SettingsPageScreen
import uk.scimone.diafit.settings.presentation.SettingsViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel



class MainActivity : ComponentActivity() {
    private val getCgmSourceUseCase: GetCgmSourceUseCase by inject()
    private val cgmServiceManager: CgmServiceManager by inject()
    private val settingsRepository: uk.scimone.diafit.settings.domain.repository.SettingsRepository by inject()
    private val pumpEventRepair: uk.scimone.diafit.core.data.repository.PumpEventRepair by inject()
    private val mealTypeRepair: uk.scimone.diafit.core.data.repository.MealTypeRepair by inject()
    private val nightscoutSyncScheduler: uk.scimone.diafit.core.data.nightscout.NightscoutSyncScheduler by inject()
    private val deviceExpiryScheduler: uk.scimone.diafit.core.data.worker.DeviceExpiryScheduler by inject()
    private val patternScheduler: uk.scimone.diafit.patterns.data.PatternScheduler by inject()
    /** Link of a tapped push notification, consumed once the UI has opened its page. */
    private val pendingLink = mutableStateOf<String?>(null)
    private val healthConnectSyncer: uk.scimone.diafit.core.data.healthconnect.HealthConnectSyncer by inject()
    private val healthConnectScheduler: uk.scimone.diafit.core.data.healthconnect.HealthConnectScheduler by inject()
    private val setMealValid: SetMealValidUseCase by inject()
    private val getOpenSitting: GetOpenSittingUseCase by inject()
    private val fileStorage: FileStorageRepository by inject()
    private val settingsViewModel: SettingsViewModel by viewModel()
    private val homeViewModel: HomeViewModel by viewModel { parametersOf(userId) }
    private val notificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    private val userId = 1 // replace with real user ID from your auth system
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(uk.scimone.diafit.settings.domain.model.AppLanguage.wrap(newBase))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        val REQUEST_CODE_DATA_SYNC = 1001

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC),
                    REQUEST_CODE_DATA_SYNC
                )
            }
        }


        lifecycleScope.launch {
            val source = getCgmSourceUseCase()
            if (source != null) cgmServiceManager.start(source) else cgmServiceManager.stopAll()  // start service on app launch
            Log.d("MainActivity", "Starting CGM Service with source: $source")
        }

        // Health Connect activity import: keep the routine job scheduled and catch up whenever the app opens.
        lifecycleScope.launch {
            if (settingsRepository.isHealthConnectEnabled()) {
                healthConnectScheduler.schedulePeriodic()
                healthConnectScheduler.syncNow()
            }
        }

        // While the app is visible, refresh heart rate from Health Connect every 3 minutes (the 15 min job is the floor otherwise).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (!settingsRepository.isHealthConnectEnabled()) return@repeatOnLifecycle
                while (true) {
                    healthConnectSyncer.syncRecentHeartRate()
                    kotlinx.coroutines.delay(3 * 60_000L)
                }
            }
        }

        lifecycleScope.launch { pumpEventRepair.runOnce() }
        lifecycleScope.launch { mealTypeRepair.runOnce() }

        // Nightscout treatments (insulin, carbs, profile, device changes): routine job plus a catch-up on open.
        nightscoutSyncScheduler.schedulePeriodic()
        nightscoutSyncScheduler.syncNow()
        deviceExpiryScheduler.checkNow()
        if (!handlePatternDebug(intent)) patternScheduler.checkNow()
        if (uk.scimone.diafit.backendsync.BackendSyncFeature.ENABLED) {
            org.koin.java.KoinJavaComponent.get<uk.scimone.diafit.backendsync.data.BackendSyncScheduler>(
                uk.scimone.diafit.backendsync.data.BackendSyncScheduler::class.java
            ).syncNow()
        }
        pendingLink.value = intent?.getStringExtra(uk.scimone.diafit.notifications.data.AppNotifier.EXTRA_LINK)
        intent?.removeExtra(uk.scimone.diafit.notifications.data.AppNotifier.EXTRA_LINK) // not again on recreate

        // Android 13+: push notifications (device expiry alerts) need the runtime permission. New installs are
        // asked during onboarding instead, at a moment where the reason is explained.
        lifecycleScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && settingsRepository.isOnboardingDone() &&
                ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.restartCgmServiceEvent.collect { source ->
                    if (source != null) cgmServiceManager.start(source) else cgmServiceManager.stopAll()
                }
            }
        }


        enableEdgeToEdge()
        setContent {
            DiafitTheme {
                // null = still reading the setting; false = first run, show the onboarding on top of everything.
                var onboardingDone by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) { onboardingDone = settingsRepository.isOnboardingDone() }
                var selectedTab by remember { mutableStateOf(0) }
                var showNewEntrySheet by remember { mutableStateOf(false) }
                val overlays = remember { mutableStateListOf<Overlay>() }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                // Soft-deleted entries (a course, or every course of a meal) can be brought back from the snackbar.
                val onMealsDeleted: (List<Int>, String) -> Unit = { mealIds, message ->
                    overlays.clear()
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = getString(R.string.action_undo),
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) setMealValid(mealIds, true)
                    }
                }
                val onMealDeleted: (Int) -> Unit = { mealId -> onMealsDeleted(listOf(mealId), getString(R.string.meal_deleted)) }
                val addCourse: (Int) -> Unit = { mealId -> overlays.add(Overlay.MealEditor(mealId = null, addToMealId = mealId)) }
                // What a notification opens: an pattern, the Patterns list, else the Devices page.
                val openLink: (String?) -> Unit = { link ->
                    val patternId = uk.scimone.diafit.notifications.domain.parsePatternLink(link)
                    val recapDay = if (uk.scimone.diafit.dailyrecap.DailyRecapFeature.ENABLED) uk.scimone.diafit.dailyrecap.data.parseRecapLink(link) else null
                    overlays.add(
                        when {
                            recapDay != null -> Overlay.DailyRecap(recapDay)
                            patternId != null -> Overlay.PatternDetail(patternId)
                            uk.scimone.diafit.notifications.domain.isPatternsListLink(link) -> Overlay.Patterns
                            else -> Overlay.Devices
                        }
                    )
                }
                val link by pendingLink
                LaunchedEffect(link) {
                    link?.let { overlays.clear(); openLink(it); pendingLink.value = null }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                if (selectedTab == 0) {
                                    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
                                    HomeTitle(state = homeState)
                                } else {
                                    Text(
                                        when (selectedTab) {
                                            1 -> stringResource(R.string.nav_stats)
                                            2 -> stringResource(R.string.nav_logs)
                                            3 -> stringResource(R.string.nav_history)
                                            else -> "Diafit"
                                        }
                                    )
                                }
                            },
                            actions = {
                                val patternsViewModel: uk.scimone.diafit.patterns.presentation.PatternsViewModel = org.koin.androidx.compose.koinViewModel()
                                val unreadPatterns by patternsViewModel.unreadCount.collectAsState()
                                val notificationsViewModel: uk.scimone.diafit.notifications.presentation.NotificationsViewModel = org.koin.androidx.compose.koinViewModel()
                                val unread by notificationsViewModel.unreadCount.collectAsState()
                                IconButton(onClick = { overlays.add(Overlay.Notifications) }) {
                                    BadgedBox(badge = {
                                        if (unread + unreadPatterns > 0) Badge(containerColor = androidx.compose.ui.graphics.Color(0xFFE53935))
                                    }) {
                                        Icon(imageVector = Icons.Filled.Notifications, contentDescription = stringResource(R.string.notifications_title))
                                    }
                                }
                                IconButton(onClick = { if (overlays.none { it is Overlay.Menu }) overlays.add(Overlay.Menu) }) {
                                    Icon(imageVector = Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.menu_title))
                                }
                            }
                        )
                    },
                    bottomBar = {
                        BottomNavigationBar(
                            selectedItem = selectedTab,
                            onItemSelected = {
                                // The + button opens the entry chooser instead of switching tab.
                                if (it == ADD_MEAL_TAB_INDEX) showNewEntrySheet = true else selectedTab = it
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        when (selectedTab) {
                            0 -> HomeScreen(userId = userId, onAddCourse = addCourse, onOpenMeal = { overlays.add(Overlay.MealDetail(it)) },
                                onSetupData = { overlays.add(Overlay.Settings(uk.scimone.diafit.settings.presentation.SettingsPage.CONNECTED_APPS)) })
                            1 -> uk.scimone.diafit.stats.presentation.StatsScreen(
                                userId = userId,
                                onOpenMetric = { metric, period, offset -> overlays.add(Overlay.StatsDetail(metric, period, offset)) }
                            )
                            2 -> LogsScreen(
                                userId = userId,
                                onOpenEntry = { entry ->
                                    when (entry) {
                                        is MealEntityUi -> overlays.add(Overlay.MealDetail(entry.id))
                                        // A low or high opens its day in History, where the trace around it is visible.
                                        is uk.scimone.diafit.logs.presentation.model.SleepEntryUi -> overlays.add(Overlay.SleepDetail(entry))
                                        is BolusEntryUi, is uk.scimone.diafit.logs.presentation.model.ExerciseEntryUi -> Unit
                                        // Only profile switches reach here; other device events open a dialog in the Logs.
                                        is PumpEventUi -> overlays.add(Overlay.ProfileSwitchDetail(entry.id))
                                        is GlucoseEpisodeUi -> overlays.add(
                                            Overlay.DayDetail(
                                                java.time.Instant.ofEpochMilli(entry.timeUtc)
                                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
                                            )
                                        )
                                    }
                                },
                                onAddEntry = { showNewEntrySheet = true }
                            )
                            3 -> HistoryScreen(userId = userId, onOpenDay = { overlays.add(Overlay.DayDetail(it)) })
                        }
                    }
                }

                if (showNewEntrySheet) {
                    // A meal still in progress is offered first, so the next plate becomes a course of it.
                    val openMeal by produceState<OpenMealSummary?>(null) {
                        value = runCatching { getOpenSitting(userId) }.getOrNull()?.let { sitting ->
                            OpenMealSummary(
                                anchorMealId = sitting.courses.last().id,
                                title = sitting.title,
                                courseCount = sitting.courses.size,
                                totalCarbs = sitting.totalCarbs,
                                startTime = sitting.startTime,
                                coverPhoto = sitting.courses.firstNotNullOfOrNull { c ->
                                    c.photoIds.firstOrNull()?.let(fileStorage::getFileProviderUri)
                                }
                            )
                        }
                    }
                    NewEntrySheet(
                        onDismiss = { showNewEntrySheet = false },
                        onPick = { kind ->
                            showNewEntrySheet = false
                            if (kind == LogsEntryKind.MEAL) overlays.add(Overlay.MealEditor(null))
                        },
                        openMeal = openMeal,
                        onAddCourse = { mealId ->
                            showNewEntrySheet = false
                            addCourse(mealId)
                        }
                    )
                }

                // Full-screen pages (entry detail, editors) stacked above the tabs.
                overlays.forEach { overlay ->
                    key(overlay) {
                        BackHandler(enabled = overlay === overlays.lastOrNull() && (overlay is Overlay.MealDetail || overlay is Overlay.DayDetail || overlay is Overlay.Profile || overlay is Overlay.Devices || overlay is Overlay.Notifications || overlay is Overlay.Patterns || overlay is Overlay.PatternDetail || overlay is Overlay.ProfileSwitchDetail || overlay is Overlay.SleepDetail || overlay is Overlay.StatsDetail || overlay is Overlay.Menu || overlay is Overlay.Settings || overlay is Overlay.DataEditor || overlay is Overlay.Recipes || overlay is Overlay.DailyRecap)) {
                            overlays.remove(overlay)
                        }
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            when (overlay) {
                                is Overlay.StatsDetail -> uk.scimone.diafit.stats.presentation.StatsDetailScreen(
                                    userId = userId,
                                    metric = overlay.metric,
                                    period = overlay.period,
                                    offset = overlay.offset,
                                    onBack = { overlays.remove(overlay) }
                                )
                                is Overlay.SleepDetail -> uk.scimone.diafit.logs.presentation.detail.SleepDetailScreen(overlay.entry, onBack = { overlays.remove(overlay) })
                                is Overlay.MealDetail -> MealDetailScreen(
                                    userId = userId,
                                    mealId = overlay.mealId,
                                    onBack = { overlays.remove(overlay) },
                                    onEdit = { overlays.add(Overlay.MealEditor(it)) },
                                    onAddCourse = addCourse,
                                    onDeleted = { ids -> onMealsDeleted(ids, if (ids.size > 1) getString(R.string.meal_deleted_whole) else getString(R.string.meal_deleted)) }
                                )
                                is Overlay.Notifications -> uk.scimone.diafit.notifications.presentation.NotificationsScreen(
                                    onBack = { overlays.remove(overlay) },
                                    onOpen = { link -> overlays.remove(overlay); openLink(link) },
                                    onOpenPattern = { id -> overlays.remove(overlay); overlays.add(Overlay.PatternDetail(id)) }
                                )
                                is Overlay.Patterns -> uk.scimone.diafit.patterns.presentation.PatternsScreen(
                                    onBack = { overlays.remove(overlay) },
                                    onOpen = { id -> overlays.add(Overlay.PatternDetail(id)) }
                                )
                                is Overlay.PatternDetail -> uk.scimone.diafit.patterns.presentation.PatternDetailScreen(
                                    id = overlay.id,
                                    onBack = { overlays.remove(overlay) },
                                    onOpenDay = { overlays.add(Overlay.DayDetail(it)) }
                                )
                                is Overlay.Menu -> uk.scimone.diafit.menu.presentation.MenuScreen(
                                    userId = userId,
                                    onBack = { overlays.remove(overlay) },
                                    onOpenDevices = { overlays.add(Overlay.Devices) },
                                    onOpenTreatmentPlan = { overlays.add(Overlay.Profile) },
                                    onOpenPatterns = { overlays.add(Overlay.Patterns) },
                                    onOpenDataEditor = { overlays.add(Overlay.DataEditor) },
                                    onOpenRecipes = { overlays.add(Overlay.Recipes) },
                                    onOpenSettings = { overlays.add(Overlay.Settings(it)) }
                                )
                                is Overlay.DailyRecap -> uk.scimone.diafit.dailyrecap.presentation.DailyRecapScreen(epochDay = overlay.epochDay, onBack = { overlays.remove(overlay) })
                                is Overlay.Recipes -> uk.scimone.diafit.recipes.presentation.RecipesScreen(
                                    onBack = { overlays.remove(overlay) },
                                    onOpenRecipe = { overlays.add(Overlay.RecipeEditor(it)) }
                                )
                                is Overlay.RecipeEditor -> uk.scimone.diafit.recipes.presentation.RecipeEditorScreen(
                                    userId = userId, recipeId = overlay.recipeId, onClose = { overlays.remove(overlay) }
                                )
                                is Overlay.DataEditor -> uk.scimone.diafit.dataeditor.presentation.DataEditorScreen(userId = userId, onBack = { overlays.remove(overlay) })
                                is Overlay.Settings -> SettingsPageScreen(
                                    page = overlay.page,
                                    onBack = { overlays.remove(overlay) },
                                    onOpenPage = { overlays.add(Overlay.Settings(it)) },
                                    onRequestIgnoreBatteryOptimizations = {
                                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    }
                                )
                                is Overlay.Devices -> uk.scimone.diafit.devices.presentation.DevicesScreen(userId = userId, onBack = { overlays.remove(overlay) })
                                is Overlay.Profile -> ProfileScreen(userId = userId, onBack = { overlays.remove(overlay) })
                                is Overlay.ProfileSwitchDetail -> ProfileSwitchDetailScreen(
                                    userId = userId,
                                    eventId = overlay.eventId,
                                    onBack = { overlays.remove(overlay) },
                                    onDeleted = { overlays.remove(overlay) }
                                )
                                is Overlay.DayDetail -> DayDetailScreen(
                                    userId = userId,
                                    initialEpochDay = overlay.epochDay,
                                    onBack = { overlays.remove(overlay) },
                                    onOpenMeal = { overlays.add(Overlay.MealDetail(it)) }
                                )
                                is Overlay.MealEditor -> MealEditorScreen(
                                    userId = userId,
                                    mealId = overlay.mealId,
                                    addToMealId = overlay.addToMealId,
                                    onClose = { overlays.remove(overlay) },
                                    onSaved = { result ->
                                        overlays.remove(overlay)
                                        when {
                                            // Stay where the user is: they'll likely add more plates.
                                            result.addedCourse -> scope.launch { snackbarHostState.showSnackbar(getString(R.string.meal_course_added)) }
                                            result.wasNew -> {
                                                selectedTab = 2
                                                scope.launch { snackbarHostState.showSnackbar(getString(R.string.meal_added)) }
                                            }
                                        }
                                    },
                                    onDeleted = onMealDeleted
                                )
                            }
                        }
                    }
                }

                // First run: the onboarding covers the app until it is finished or skipped.
                if (onboardingDone != true) {
                    if (onboardingDone == false) {
                        uk.scimone.diafit.onboarding.presentation.OnboardingScreen(
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onOpenBatterySettings = { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                            onFinished = { onboardingDone = true }
                        )
                    } else {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(uk.scimone.diafit.notifications.data.AppNotifier.EXTRA_LINK)?.let { pendingLink.value = it }
        handlePatternDebug(intent)
    }

    /** Debug builds only: `--el pattern_as_of <epoch ms>` previews the patterns as of that moment, `--ez pattern_reset true` goes back to real data. */
    private fun handlePatternDebug(intent: Intent?): Boolean {
        if (!BuildConfig.DEBUG || intent == null) return false
        if (intent.getBooleanExtra("pattern_reset", false)) { patternScheduler.reset(); return true }
        // `--el recap_epoch_day <n>`: writes that day's recap now (today's = so far) and pushes it.
        val recapDay = intent.getLongExtra("recap_epoch_day", Long.MIN_VALUE)
        if (uk.scimone.diafit.dailyrecap.DailyRecapFeature.ENABLED && recapDay != Long.MIN_VALUE) {
            org.koin.java.KoinJavaComponent.get<uk.scimone.diafit.dailyrecap.data.DailyRecapScheduler>(uk.scimone.diafit.dailyrecap.data.DailyRecapScheduler::class.java).runNow(recapDay)
            return true
        }
        // `--el recap_run_at <epoch ms>`: the next daily recap runs at that moment instead of the usual time, once.
        val recapAt = intent.getLongExtra("recap_run_at", 0L)
        if (uk.scimone.diafit.dailyrecap.DailyRecapFeature.ENABLED && recapAt > 0) {
            org.koin.java.KoinJavaComponent.get<uk.scimone.diafit.dailyrecap.data.DailyRecapStore>(uk.scimone.diafit.dailyrecap.data.DailyRecapStore::class.java).update { it.copy(enabled = true, nextRunOverrideUtc = recapAt) }
            org.koin.java.KoinJavaComponent.get<uk.scimone.diafit.dailyrecap.data.DailyRecapScheduler>(uk.scimone.diafit.dailyrecap.data.DailyRecapScheduler::class.java).reschedule()
            return true
        }
        val asOf = intent.getLongExtra("pattern_as_of", 0L)
        if (asOf > 0) { patternScheduler.simulate(asOf); return true }
        return false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "GLUCOSE_SYNC_CHANNEL",
                getString(R.string.sync_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.sync_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

/** A full-screen page shown above the tabs. */
private sealed interface Overlay {
    /** The insulin profile AAPS is running (read-only). */
    data object Profile : Overlay
    data object Devices : Overlay
    data object Notifications : Overlay
    /** The person icon's page: devices, therapy, data and app settings. */
    data object Menu : Overlay
    /** Correct or delete any stored record. */
    data object DataEditor : Overlay
    /** The user's recipes, and one recipe being created (null) or edited. */
    data object Recipes : Overlay
    data class RecipeEditor(val recipeId: Int?) : Overlay
    /** The optional LLM morning recap (see DailyRecapFeature). */
    data class DailyRecap(val epochDay: Long?) : Overlay
    data class Settings(val page: uk.scimone.diafit.settings.presentation.SettingsPage) : Overlay
    /** The pattern cards (recurring glucose patterns with tips). */
    data object Patterns : Overlay
    data class PatternDetail(val id: String) : Overlay
    /** One Profile Switch as a before/after diff. */
    data class ProfileSwitchDetail(val eventId: Int) : Overlay
    /** One Stats metric over a week / month / year ([offset] pages back from the current one). */
    data class StatsDetail(
        val metric: uk.scimone.diafit.stats.domain.StatsMetric,
        val period: uk.scimone.diafit.stats.domain.StatsPeriod,
        val offset: Int
    ) : Overlay
    data class SleepDetail(val entry: uk.scimone.diafit.logs.presentation.model.SleepEntryUi) : Overlay
    data class MealDetail(val mealId: Int) : Overlay
    /** One day of History in full; swipeable to neighbouring days. */
    data class DayDetail(val epochDay: Long) : Overlay
    /** [mealId] == null creates a new meal, or a new course of [addToMealId]'s meal when that is set. */
    data class MealEditor(val mealId: Int?, val addToMealId: Int? = null) : Overlay
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "You selected: $name",
        modifier = modifier.padding(16.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DiafitTheme {
        Greeting("Home")
    }
}
