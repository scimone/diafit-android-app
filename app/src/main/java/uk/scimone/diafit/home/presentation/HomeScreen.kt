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
    var selectedTime by remember { mutableStateOf<Long?>(null) }
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
                    if (state.cgmUi != null) {
                        val pastReading = selectedTime?.let { t -> state.cgmHistory.minByOrNull { abs(it.timeLong - t) } }
                        Row(
                            modifier = Modifier.fillMaxWidth().height(84.dp).padding(start = 10.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pastReading != null) {
                                PastCgmDisplay(reading = pastReading, modifier = Modifier.weight(1f))
                            } else {
                                CgmDisplay(cgm = state.cgmUi!!, modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        Text("No CGM data available", modifier = Modifier.padding(start = 10.dp))
                    }
                    ChartZoomControls(zoomState = chartZoomState, scrollState = chartScrollState)

                    // Stacked panels share one time axis; only the last one draws the hour labels.
                    if (state.cgmUi != null) {
                        CgmChartDisplay(
                            history = state.cgmHistory,
                            lower = state.targetRangeLower,
                            upper = state.targetRangeUpper,
                            scrollState = chartScrollState,
                            zoomState = chartZoomState,
                            nowMinute = nowMinute,
                            selectedTime = selectedTime,
                            onSelectedTimeChange = { selectedTime = it }
                        )
                    }
                    InsulinActivityDisplay(
                        history = state.insulinActivityHistory,
                        scrollState = chartScrollState,
                        zoomState = chartZoomState,
                        nowMinute = nowMinute,
                        selectedTime = selectedTime,
                        onSelectedTimeChange = { selectedTime = it }
                    )
                    CarbActivityDisplay(
                        history = state.carbHistory,
                        scrollState = chartScrollState,
                        zoomState = chartZoomState,
                        nowMinute = nowMinute,
                        selectedTime = selectedTime,
                        onSelectedTimeChange = { selectedTime = it }
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


@Composable
fun CgmDisplay(cgm: CgmEntityUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${cgm.value}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    (cgm.value ?: 0) <= 70 -> BelowRange
                    (cgm.value ?: 0) >= 180 -> AboveRange
                    else -> MaterialTheme.colorScheme.onBackground
                },
                textDecoration = if (cgm.isStale) TextDecoration.LineThrough else TextDecoration.None
            )
            ComponentRotatingArrowIcon(inputValue = cgm.rate)
        }
        Text(text = "${cgm.timeSince} ago")
    }
}

/** Reports [amount] less height than the content measures, so the next sibling overlaps the slack. */
private fun Modifier.trimBottom(amount: Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    layout(p.width, (p.height - amount.roundToPx()).coerceAtLeast(0)) { p.place(0, 0) }
}

/**
 * The header reading while scrubbing: deliberately *not* the live look - lighter, italic, muted,
 * with a "PAST READING" tag and its clock time, so nobody mistakes it for the current glucose.
 */
@Composable
fun PastCgmDisplay(reading: CgmChartData, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val time = remember(reading.timeLong) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reading.timeLong))
    }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${reading.value}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = muted
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.History, contentDescription = null, tint = muted, modifier = Modifier.size(22.dp))
        }
        Text(
            text = "PAST READING · $time",
            fontSize = 12.sp,
            letterSpacing = 0.8.sp,
            color = muted,
            modifier = Modifier
                .border(1.dp, muted.copy(alpha = 0.5f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 1.dp)
        )
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
    }
}

/** Height of one panel's plot area; the last panel gets extra room for the shared hour labels. */
private val EventPanelHeight = 90.dp
private val TimeLabelsHeight = 22.dp
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(3, 6, 12, 24).forEach { hours ->
            AssistChip(
                onClick = {
                    scope.launch {
                        zoomState.animateZoom(Zoom.x(visibleHoursMillis(hours)))
                        scrollState.animateScroll(Scroll.Absolute.End)
                    }
                },
                label = { Text("${hours}h") }
            )
        }
    }
}
