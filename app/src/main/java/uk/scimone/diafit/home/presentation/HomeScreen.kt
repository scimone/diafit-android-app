package uk.scimone.diafit.home.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.scimone.diafit.home.presentation.utils.currentMinute
import uk.scimone.diafit.home.presentation.utils.TIME_AXIS_FUTURE_HOURS
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.home.presentation.components.ComponentCgmChart
import uk.scimone.diafit.home.presentation.components.ComponentRotatingArrowIcon
import uk.scimone.diafit.home.presentation.model.CgmChartData
import uk.scimone.diafit.home.presentation.model.CgmEntityUi
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import androidx.compose.runtime.Composable
import uk.scimone.diafit.home.presentation.components.ChartEvent
import uk.scimone.diafit.home.presentation.components.ComponentEventActivityChart
import uk.scimone.diafit.home.presentation.components.MealDetailSheet
import uk.scimone.diafit.home.presentation.components.MealGroup
import uk.scimone.diafit.home.presentation.utils.SHOW_TIME_LABELS
import uk.scimone.diafit.home.presentation.components.MealTimeline
import uk.scimone.diafit.home.presentation.components.groupMeals
import uk.scimone.diafit.home.presentation.components.nearest
import uk.scimone.diafit.core.domain.model.CarbActivity
import uk.scimone.diafit.core.domain.model.InsulinActivity
import uk.scimone.diafit.home.presentation.model.CarbsChartData
import uk.scimone.diafit.home.presentation.model.InsulinActivityChartData
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs

@Composable
fun HomeScreen(
    userId: Int,
    viewModel: HomeViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Shared across every chart below so panning/zooming one keeps the others' x-axes in sync.
    // minZoom/maxZoom must be content-independent (not the Zoom.Content default) — otherwise each
    // chart recomputes its own valueRange from its own data extent (e.g. the insulin-activity
    // chart synthesizes points across the full 24h while the CGM/bolus charts only have points
    // where real readings exist), and the shared zoom value gets clamped differently per chart,
    // silently desyncing their visible time windows even though the raw scroll/zoom state is shared.
    // One "now" (minute resolution) for every chart's time axis, see getTimeAxisBounds.
    val nowMinute by produceState(initialValue = currentMinute()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = currentMinute()
        }
    }
    // Time (x) the user is scrubbing on the CGM chart; every chart draws the cursor line, and the
    // header swaps the live reading for the reading at that time.
    val selectedTime by viewModel.selectedTime.collectAsStateWithLifecycle()
    // Meals grouped into sittings, shared by the header preview, the meal strip and the detail sheet.
    val mealGroups = remember(state.mealHistory) { groupMeals(state.mealHistory) }
    val focusedMeal = mealGroups.nearest(selectedTime)
    // Remember only which meal is open and re-resolve it from the live list, so a photo added from
    // the sheet shows up immediately.
    var openMealId by remember { mutableStateOf<Int?>(null) }
    val openMeal = openMealId?.let { id -> mealGroups.firstOrNull { g -> g.meals.any { it.id == id } } }
    val scope = rememberCoroutineScope()
    val chartScrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)
    // The Zoom objects MUST be remembered: rememberVicoZoomState keys on them, so fresh lambdas on
    // every recomposition would recreate the state and silently reset the user's zoom (HomeScreen
    // recomposes every second via the countdown).
    val initialZoom = remember { Zoom.x(visibleHoursMillis(DEFAULT_VISIBLE_PAST_HOURS)) }
    // Pinch range: the whole 24h+2h axis (zoomed out) down to 3h. Content-independent on purpose.
    val minZoom = remember { Zoom.x(visibleHoursMillis(24)) }
    val maxZoom = remember { Zoom.x(visibleHoursMillis(1)) }
    val chartZoomState = rememberVicoZoomState(
        zoomEnabled = true,
        initialZoom = initialZoom,
        minZoom = minZoom,
        maxZoom = maxZoom
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator()
            }

            state.error != null -> {
                Text(
                    text = "Error: ${state.error}",
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ChartZoomControls(zoomState = chartZoomState, scrollState = chartScrollState)

                    // Stacked panels share one time axis.
                    if (state.cgmUi != null) {
                        CgmChartDisplay(
                            history = state.cgmHistory,
                            lower = state.targetRangeLower,
                            upper = state.targetRangeUpper,
                            scrollState = chartScrollState,
                            zoomState = chartZoomState,
                            nowMinute = nowMinute,
                            selectedTime = selectedTime,
                            onSelectedTimeChange = viewModel::onSelectedTimeChange
                        )
                    }
                    // Placeholders for upcoming graphs.
                    PlaceholderPanel("Activity")
                    PlaceholderPanel("Basal")
                    InsulinActivityDisplay(
                        history = state.insulinActivityHistory,
                        scrollState = chartScrollState,
                        zoomState = chartZoomState,
                        nowMinute = nowMinute,
                        selectedTime = selectedTime,
                        onSelectedTimeChange = viewModel::onSelectedTimeChange
                    )
                    CarbActivityDisplay(
                        history = state.carbHistory,
                        scrollState = chartScrollState,
                        zoomState = chartZoomState,
                        nowMinute = nowMinute,
                        selectedTime = selectedTime,
                        onSelectedTimeChange = viewModel::onSelectedTimeChange
                    )

                    MealTimeline(
                        groups = mealGroups,
                        highlighted = focusedMeal,
                        onGroupClick = { group ->
                            openMealId = group.meals.first().id
                            // Bring the meal into view on the charts behind the sheet.
                            scope.launch { chartScrollState.animateScroll(Scroll.Absolute.x(group.startTime.toDouble(), 0.5f)) }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
                openMeal?.let {
                    MealDetailSheet(
                        group = it,
                        onDismiss = { openMealId = null },
                        createCameraUri = viewModel::createCameraUriForMeal,
                        onCameraResult = viewModel::onCameraPhotoResult,
                        onPickPhoto = viewModel::attachGalleryPhoto
                    )
                }
            }
        }
    }
}


/** Reports [amount] less height than the content measures, so the next sibling overlaps the slack. */
private fun Modifier.trimBottom(amount: Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    layout(p.width, (p.height - amount.roundToPx()).coerceAtLeast(0)) { p.place(0, 0) }
}

/**
 * The app bar title on Home: the live glucose reading with its trend arrow and age. While the user
 * scrubs the charts it swaps to the reading at that time, deliberately in a lighter, muted look with
 * a "past reading" tag, so nobody mistakes it for the current glucose.
 */
@Composable
fun HomeTitle(state: HomeState, selectedTime: Long?) {
    val cgm = state.cgmUi ?: return Text("Diafit")
    val pastReading = selectedTime?.let { t -> state.cgmHistory.minByOrNull { abs(it.timeLong - t) } }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (pastReading != null) {
            val time = remember(pastReading.timeLong) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(pastReading.timeLong))
            }
            Text("${pastReading.value}", fontSize = 28.sp, fontWeight = FontWeight.Light, color = muted)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.History, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("past reading · $time", fontSize = 12.sp, color = muted)
        } else {
            Text(
                text = "${cgm.value}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    (cgm.value ?: 0) <= 70 -> BelowRange
                    (cgm.value ?: 0) >= 180 -> AboveRange
                    else -> MaterialTheme.colorScheme.onBackground
                },
                textDecoration = if (cgm.isStale) TextDecoration.LineThrough else TextDecoration.None
            )
            ComponentRotatingArrowIcon(inputValue = cgm.rate, size = 40.dp)
            Text("${cgm.timeSince} ago", fontSize = 12.sp, color = muted)
        }
    }
}

/** Small muted heading in a panel's top-left corner. */
@Composable
private fun BoxScope.PanelTitle(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 2.dp)
    )
}

/** Reserved space (with its heading) for a graph that doesn't exist yet. */
@Composable
private fun PlaceholderPanel(title: String) {
    Box(modifier = Modifier.fillMaxWidth().trimBottom(PanelGapTrim).height(EventPanelHeight)) {
        PanelTitle(title)
    }
}

@Composable
fun CgmChartDisplay(
    history: List<CgmChartData>,
    lower: Int,
    upper: Int,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    nowMinute: Long,
    selectedTime: Long?,
    onSelectedTimeChange: (Long?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .trimBottom(PanelGapTrim)
            .height(115.dp)
    ) {
        ComponentCgmChart(
            values = history,
            lowerBound = lower,
            upperBound = upper,
            scrollState = scrollState,
            zoomState = zoomState,
            nowMinute = nowMinute,
            selectedTime = selectedTime,
            onSelectedTimeChange = onSelectedTimeChange
        )
        PanelTitle("Glucose")
    }
}

/** Height of one panel's plot area; the last panel gets extra room for the shared hour labels. */
private val EventPanelHeight = 90.dp
private val TimeLabelsHeight = if (SHOW_TIME_LABELS) 22.dp else 0.dp
/** Vico leaves a few dp of inset under every chart; trimming it makes the panels touch. */
private val PanelGapTrim = 5.dp

@Composable
fun InsulinActivityDisplay(
    history: List<InsulinActivityChartData>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    nowMinute: Long,
    selectedTime: Long?,
    onSelectedTimeChange: (Long?) -> Unit
) {
    val events = remember(history) { history.map { ChartEvent(it.timeLong, it.value.toDouble()) } }
    Box(modifier = Modifier.fillMaxWidth().trimBottom(PanelGapTrim).height(EventPanelHeight)) {
        ComponentEventActivityChart(
            events = events,
            activityOf = { e, t ->
                if (t >= e.time) InsulinActivity.calculate(bolusAmount = e.value, bolusTime = e.time, time = t).activity else 0.0
            },
            color = Bolus,
            valueUnit = "U",
            bubbleRefValue = 1.0,
            showTimeLabels = false,
            scrollState = scrollState,
            zoomState = zoomState,
            nowMinute = nowMinute,
            selectedTime = selectedTime,
            onSelectedTimeChange = onSelectedTimeChange
        )
        PanelTitle("Bolus")
    }
}

@Composable
fun CarbActivityDisplay(
    history: List<CarbsChartData>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    nowMinute: Long,
    selectedTime: Long?,
    onSelectedTimeChange: (Long?) -> Unit
) {
    val events = remember(history) { history.map { ChartEvent(it.timeLong, it.value.toDouble(), it.durationMinutes) } }
    Box(modifier = Modifier.fillMaxWidth().height(EventPanelHeight + TimeLabelsHeight)) {
        ComponentEventActivityChart(
            events = events,
            activityOf = { e, t -> CarbActivity.calculate(e.value, e.time, t, e.durationMinutes) },
            color = Carbs,
            valueUnit = "g",
            bubbleRefValue = 10.0,
            showTimeLabels = true,
            scrollState = scrollState,
            zoomState = zoomState,
            nowMinute = nowMinute,
            selectedTime = selectedTime,
            onSelectedTimeChange = onSelectedTimeChange
        )
        PanelTitle("Carbohydrates")
    }
}

private const val DEFAULT_VISIBLE_PAST_HOURS = 6

/** X-range (ms) to show: [pastHours] of history plus the shared future headroom. */
private fun visibleHoursMillis(pastHours: Int): Double =
    (pastHours + TIME_AXIS_FUTURE_HOURS) * 3_600_000.0

/**
 * One-tap zoom presets for the shared chart zoom (pinching also works), each followed by a jump back
 * to "now" so the newest data stays in view.
 */
@Composable
fun ChartZoomControls(zoomState: VicoZoomState, scrollState: VicoScrollState) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drop Material's 48dp minimum touch target so the buttons can actually be small.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        listOf(3, 6, 12, 24).forEach { hours ->
            Surface(
                onClick = {
                    scope.launch {
                        zoomState.animateZoom(Zoom.x(visibleHoursMillis(hours)))
                        scrollState.animateScroll(Scroll.Absolute.End)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = "${hours}h",
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        }
    }
}
