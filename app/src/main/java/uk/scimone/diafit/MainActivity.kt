package uk.scimone.diafit

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import uk.scimone.diafit.addmeal.presentation.MealEditorScreen
import uk.scimone.diafit.core.domain.usecase.SetMealValidUseCase
import uk.scimone.diafit.journal.presentation.components.NewEntrySheet
import uk.scimone.diafit.journal.presentation.components.OpenMealSummary
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.usecase.GetOpenSittingUseCase
import uk.scimone.diafit.journal.presentation.detail.MealDetailScreen
import uk.scimone.diafit.journal.presentation.model.JournalEntryKind
import uk.scimone.diafit.journal.presentation.model.BolusEntryUi
import uk.scimone.diafit.profile.presentation.ProfileScreen
import androidx.compose.material.icons.filled.Person
import uk.scimone.diafit.journal.presentation.model.PumpEventUi
import uk.scimone.diafit.journal.presentation.model.GlucoseEpisodeUi
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import androidx.activity.compose.BackHandler
import uk.scimone.diafit.ui.theme.DiafitTheme
import uk.scimone.diafit.core.presentation.ADD_MEAL_TAB_INDEX
import uk.scimone.diafit.core.presentation.BottomNavigationBar
import uk.scimone.diafit.core.presentation.SETTINGS_TAB_INDEX
import org.koin.android.ext.android.inject
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.data.service.CgmServiceManager
import uk.scimone.diafit.journal.presentation.JournalScreen
import uk.scimone.diafit.history.presentation.HistoryScreen
import uk.scimone.diafit.history.presentation.detail.DayDetailScreen
import uk.scimone.diafit.home.presentation.HomeScreen
import uk.scimone.diafit.home.presentation.HomeTitle
import uk.scimone.diafit.home.presentation.HomeViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase
import uk.scimone.diafit.settings.presentation.SettingsScreen
import uk.scimone.diafit.settings.presentation.SettingsViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel



class MainActivity : ComponentActivity() {
    private val getCgmSourceUseCase: GetCgmSourceUseCase by inject()
    private val cgmServiceManager: CgmServiceManager by inject()
    private val setMealValid: SetMealValidUseCase by inject()
    private val getOpenSitting: GetOpenSittingUseCase by inject()
    private val fileStorage: FileStorageRepository by inject()
    private val settingsViewModel: SettingsViewModel by viewModel()
    private val homeViewModel: HomeViewModel by viewModel { parametersOf(userId) }
    private val userId = 1 // replace with real user ID from your auth system
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
            cgmServiceManager.start(source)  // start service on app launch
            Log.d("MainActivity", "Starting CGM Service with source: $source")
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.restartCgmServiceEvent.collect { source ->
                    cgmServiceManager.start(source)
                }
            }
        }


        enableEdgeToEdge()
        setContent {
            DiafitTheme {
                var selectedTab by remember { mutableStateOf(0) }
                var overflowMenuExpanded by remember { mutableStateOf(false) }
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
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) setMealValid(mealIds, true)
                    }
                }
                val onMealDeleted: (Int) -> Unit = { mealId -> onMealsDeleted(listOf(mealId), "Deleted") }
                val addCourse: (Int) -> Unit = { mealId -> overlays.add(Overlay.MealEditor(mealId = null, addToMealId = mealId)) }

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
                                            2 -> "Journal"
                                            3 -> "History"
                                            else -> "Diafit"
                                        }
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { overflowMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "More options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = overflowMenuExpanded,
                                    onDismissRequest = { overflowMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Profile") },
                                        leadingIcon = { Icon(imageVector = Icons.Filled.Person, contentDescription = null) },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            overlays.add(Overlay.Profile)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.SettingsIcon,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            selectedTab = SETTINGS_TAB_INDEX
                                        }
                                    )
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
                            0 -> HomeScreen(userId = userId, onAddCourse = addCourse, onOpenMeal = { overlays.add(Overlay.MealDetail(it)) })
                            1 -> Greeting("Summary")
                            2 -> JournalScreen(
                                userId = userId,
                                onOpenEntry = { entry ->
                                    when (entry) {
                                        is MealEntityUi -> overlays.add(Overlay.MealDetail(entry.id))
                                        // A low or high opens its day in History, where the trace around it is visible.
                                        is BolusEntryUi, is PumpEventUi -> Unit
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
                            SETTINGS_TAB_INDEX -> SettingsScreen(
                                onRequestIgnoreBatteryOptimizations = {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    startActivity(intent)
                                }
                            )
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
                            if (kind == JournalEntryKind.MEAL) overlays.add(Overlay.MealEditor(null))
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
                        BackHandler(enabled = overlay === overlays.lastOrNull() && (overlay is Overlay.MealDetail || overlay is Overlay.DayDetail || overlay is Overlay.Profile)) {
                            overlays.remove(overlay)
                        }
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            when (overlay) {
                                is Overlay.MealDetail -> MealDetailScreen(
                                    userId = userId,
                                    mealId = overlay.mealId,
                                    onBack = { overlays.remove(overlay) },
                                    onEdit = { overlays.add(Overlay.MealEditor(it)) },
                                    onAddCourse = addCourse,
                                    onDeleted = { ids -> onMealsDeleted(ids, if (ids.size > 1) "Meal deleted" else "Deleted") }
                                )
                                is Overlay.Profile -> ProfileScreen(userId = userId, onBack = { overlays.remove(overlay) })
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
                                            result.addedCourse -> scope.launch { snackbarHostState.showSnackbar("Course added to the meal") }
                                            result.wasNew -> {
                                                selectedTab = 2
                                                scope.launch { snackbarHostState.showSnackbar("Meal added") }
                                            }
                                        }
                                    },
                                    onDeleted = onMealDeleted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "GLUCOSE_SYNC_CHANNEL",
                "CGM Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for CGM sync service notifications"
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
