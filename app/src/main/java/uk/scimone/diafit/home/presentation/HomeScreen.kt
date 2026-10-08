package uk.scimone.diafit.home.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.core.domain.model.CarbActivity
import uk.scimone.diafit.core.domain.model.InsulinActivity
import uk.scimone.diafit.home.presentation.components.cgmMaxY
import uk.scimone.diafit.home.presentation.components.CGM_MIN_Y
import uk.scimone.diafit.home.presentation.components.ChartEvent
import uk.scimone.diafit.home.presentation.components.ComponentCgmChart
import uk.scimone.diafit.home.presentation.components.ComponentEventActivityChart
import uk.scimone.diafit.home.presentation.components.ComponentRotatingArrowIcon
import uk.scimone.diafit.home.presentation.components.MealDetailSheet
import uk.scimone.diafit.home.presentation.components.MealGroup
import uk.scimone.diafit.home.presentation.components.MealTimeline
import uk.scimone.diafit.home.presentation.components.groupMeals
import uk.scimone.diafit.home.presentation.components.inView
import uk.scimone.diafit.home.presentation.components.caughtBy
import uk.scimone.diafit.home.presentation.components.nearest
import uk.scimone.diafit.home.presentation.components.ActivityReadout
import uk.scimone.diafit.home.presentation.components.ComponentActivityChart
import uk.scimone.diafit.home.presentation.components.readoutAt
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.BasalSegment
import uk.scimone.diafit.home.presentation.components.ComponentBasalChart
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.Sleep
import uk.scimone.diafit.home.presentation.model.CarbsChartData
import uk.scimone.diafit.home.presentation.model.CgmChartData
import uk.scimone.diafit.home.presentation.model.InsulinActivityChartData
import uk.scimone.diafit.home.presentation.utils.ChartGeometry
import uk.scimone.diafit.home.presentation.utils.SHOW_TIME_LABELS
import uk.scimone.diafit.home.presentation.utils.TIME_AXIS_FUTURE_HOURS
import uk.scimone.diafit.home.presentation.utils.ChartTimeWindow
import uk.scimone.diafit.home.presentation.utils.homeTimeWindow
import uk.scimone.diafit.home.presentation.utils.currentMinute
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** A CGM reading this close to the cursor is the one the readout shows (and the cursor snaps to). */
private const val READING_SNAP_MS = 10 * 60_000L
/** Boluses / carbs this close to the cursor are listed in the readout. */
private const val EVENT_NEAR_MS = 15 * 60_000L

@Composable
fun HomeScreen(
    userId: Int,
    onAddCourse: (mealId: Int) -> Unit = {},
    onOpenMeal: (mealId: Int) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // One "now" (minute resolution) for every chart's time axis, see getTimeAxisBounds.
    val nowMinute by produceState(initialValue = currentMinute()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = currentMinute()
        }
    }
    // Pinned inspection time (tap or hold on the charts); null = nothing inspected.
    val selectedTime by viewModel.selectedTime.collectAsStateWithLifecycle()
    var scrubbing by remember { mutableStateOf(false) }
    // Meals grouped into sittings, shared by the pin lane, the meal strip and the detail sheet.
    val mealGroups = remember(state.mealHistory) { groupMeals(state.mealHistory) }
    val focusedMeal = mealGroups.nearest(selectedTime)
    // Remember only which meals are open and re-resolve them from the live list, so a photo added
    // from the sheet shows up immediately.
    var openMealIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val openMeal = state.mealHistory.filter { it.id in openMealIds }.sortedBy { it.mealTimeUtc }
        .takeIf { it.isNotEmpty() }?.let { MealGroup(it) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val window = remember(nowMinute) { homeTimeWindow(nowMinute) }

    // Where the shared time axis sits on screen, reported by the CGM chart on every frame. Read only
    // in draw/layout lambdas or through derivedStateOf, so panning doesn't recompose the screen.
    val geometry = remember { mutableStateOf<ChartGeometry?>(null) }
    var chartsBottomInRoot by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val onGeometry = remember { { g: ChartGeometry -> geometry.value = g } }

    // Shared across every chart so panning/zooming one keeps the others' x-axes in sync. Zoom bounds
    // must be content-independent (not the Zoom.Content default), see CLAUDE.md "Shared time axis".
    // Panning is switched off while scrubbing (Vico's scrollable ignores events consumed by a parent);
    // rememberVicoScrollState updates scrollEnabled in place, so this doesn't reset the position.
    val chartScrollState = rememberVicoScrollState(scrollEnabled = !scrubbing, initialScroll = Scroll.Absolute.End)
    // The Zoom objects MUST be remembered: rememberVicoZoomState keys on them, so fresh lambdas on
    // every recomposition would recreate the state and silently reset the user's zoom.
    val initialZoom = remember { Zoom.x(visibleHoursMillis(DEFAULT_VISIBLE_PAST_HOURS)) }
    val minZoom = remember { Zoom.x(visibleHoursMillis(24)) }
    val maxZoom = remember { Zoom.x(visibleHoursMillis(1)) }
    val chartZoomState = rememberVicoZoomState(
        zoomEnabled = true,
        initialZoom = initialZoom,
        minZoom = minZoom,
        maxZoom = maxZoom
    )

    // Sittings inside the visible window; only changes when one enters or leaves it.
    val mealsInView by remember(mealGroups) {
        derivedStateOf {
            geometry.value?.let { mealGroups.inView(it.visibleStart, it.visibleEnd) }
                ?: mealGroups
        }
    }

    // A reading near the cursor: the readout shows it and the cursor snaps onto it.
    val inspectedReading = selectedTime?.let { t ->
        state.cgmHistory.minByOrNull { abs(it.timeLong - t) }?.takeIf { abs(it.timeLong - t) <= READING_SNAP_MS }
    }
    val cursorTime = inspectedReading?.timeLong ?: selectedTime

    DismissInspectionWhenOffscreen(cursorTime, geometry) { viewModel.onSelectedTimeChange(null) }

    // A light tick whenever the scrub cursor reaches another meal.
    LaunchedEffect(focusedMeal?.key) {
        if (scrubbing && focusedMeal != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun timeAtX(x: Float): Long? = geometry.value?.let { g ->
        g.timeAt(x).coerceIn(g.minX.toLong(), window.now)
    }

    fun revealOnCharts(time: Long) {
        scope.launch { chartScrollState.animateScroll(Scroll.Absolute.x(time.toDouble(), 0.5f)) }
    }

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
                // Fixed, non-scrolling page: the panels share whatever height the photo strip leaves.
                Column(modifier = Modifier.fillMaxSize()) {
                    // Idle: gesture hint + zoom chips. Inspecting: a readout bubble pinned above the cursor.
                    Box(Modifier.fillMaxWidth().height(InspectBarHeight)) {
                        if (selectedTime == null) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Tap or hold a chart to inspect",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.weight(1f))
                                ChartZoomControls(zoomState = chartZoomState, scrollState = chartScrollState)
                            }
                        } else if (cursorTime != null) {
                            InspectReadout(
                                cursorTime = cursorTime,
                                geometry = geometry,
                                reading = inspectedReading,
                                activity = state.activity.readoutAt(cursorTime),
                                bolusUnits = state.insulinActivityHistory
                                    .filter { abs(it.timeLong - cursorTime) <= EVENT_NEAR_MS }.sumOf { it.value.toDouble() },
                                carbGrams = state.carbHistory
                                    .filter { abs(it.timeLong - cursorTime) <= EVENT_NEAR_MS }.sumOf { it.value },
                                lower = state.targetRangeLower,
                                upper = state.targetRangeUpper,
                                onDismiss = { viewModel.onSelectedTimeChange(null) }
                            )
                        }
                    }

                    // The stacked panels share one time axis; the pin lane and cursor overlay follow it.
                    Box(Modifier.weight(1f).onGloballyPositioned { chartsBottomInRoot = it.positionInRoot().y + it.size.height }) {
                        Column(Modifier.fillMaxSize()) {
                            Column(
                                Modifier.fillMaxSize().inspectGestures(
                                    onTap = { pos ->
                                        val currentX = cursorTime?.let { geometry.value?.xOf(it) }
                                        // Tapping the cursor again closes it; anywhere else moves it there.
                                        if (currentX != null && abs(currentX - pos.x) < TapToggleRadiusPx) viewModel.onSelectedTimeChange(null)
                                        else viewModel.onSelectedTimeChange(timeAtX(pos.x))
                                    },
                                    onScrubStart = { pos ->
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        scrubbing = true
                                        viewModel.onSelectedTimeChange(timeAtX(pos.x))
                                    },
                                    onScrub = { pos -> viewModel.onSelectedTimeChange(timeAtX(pos.x)) },
                                    onScrubEnd = { scrubbing = false }
                                )
                            ) {
                                if (state.cgmUi != null) {
                                    CgmChartDisplay(
                                        modifier = Modifier.weight(CgmPanelWeight),
                                        history = state.cgmHistory,
                                        lower = state.targetRangeLower,
                                        upper = state.targetRangeUpper,
                                        scrollState = chartScrollState,
                                        zoomState = chartZoomState,
                                        window = window,
                                        onGeometry = onGeometry
                                    )
                                }
                                ActivityDisplay(
                                    modifier = Modifier.weight(ActivityPanelWeight),
                                    data = state.activity,
                                    connected = state.activityConnected,
                                    window = window,
                                    geometry = geometry
                                )
                                BasalDisplay(
                                    modifier = Modifier.weight(1f),
                                    segments = state.basal,
                                    window = window,
                                    geometry = geometry
                                )
                                InsulinActivityDisplay(
                                    modifier = Modifier.weight(1f),
                                    history = state.insulinActivityHistory,
                                    scrollState = chartScrollState,
                                    zoomState = chartZoomState,
                                    window = window,
                                    highlightTime = cursorTime
                                )
                                CarbActivityDisplay(
                                    modifier = Modifier.weight(1f),
                                    history = state.carbHistory,
                                    scrollState = chartScrollState,
                                    zoomState = chartZoomState,
                                    window = window,
                                    highlightTime = cursorTime
                                )
                            }
                        }
                        InspectCursor(
                            cursorTime = cursorTime,
                            reading = inspectedReading,
                            geometry = geometry,
                            lower = state.targetRangeLower,
                            upper = state.targetRangeUpper,
                            maxY = cgmMaxY(state.cgmHistory, window),
                            modifier = Modifier.matchParentSize()
                        )
                    }

                    MealTimeline(
                        allGroups = mealGroups,
                        inView = mealsInView,
                        highlighted = mealGroups.caughtBy(selectedTime),
                        geometry = geometry,
                        scrollState = chartScrollState,
                        onGroupClick = { group ->
                            openMealIds = group.meals.map { it.id }.toSet()
                            // Bring the meal into view on the charts behind the sheet.
                            revealOnCharts(group.startTime)
                        },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                // One dashed "now" line over the whole screen height, not just inside each panel.
                val nowLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                var canvasTopInRoot by remember { mutableFloatStateOf(0f) }
                Canvas(Modifier.fillMaxSize().onGloballyPositioned { canvasTopInRoot = it.positionInRoot().y }) {
                    val x = geometry.value?.xOf(nowMinute) ?: return@Canvas
                    // Stop above the meal photo row.
                    val lineBottom = (chartsBottomInRoot - canvasTopInRoot).coerceIn(0f, size.height)
                    if (x in 0f..size.width) {
                        drawLine(
                            color = nowLineColor,
                            start = Offset(x, InspectBarHeight.toPx()),
                            end = Offset(x, lineBottom.coerceAtLeast(InspectBarHeight.toPx())),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    }
                }
                openMeal?.let {
                    MealDetailSheet(
                        group = it,
                        onDismiss = { openMealIds = emptySet() },
                        createCameraUri = viewModel::createCameraUriForMeal,
                        onCameraResult = viewModel::onCameraPhotoResult,
                        onPickPhoto = viewModel::attachGalleryPhoto,
                        onAddCourse = { mealId ->
                            openMealIds = emptySet()
                            onAddCourse(mealId)
                        },
                        onOpenFull = { mealId ->
                            openMealIds = emptySet()
                            onOpenMeal(mealId)
                        }
                    )
                }
            }
        }
    }
}

internal val InspectBarHeight = 40.dp
internal const val TapToggleRadiusPx = 48f

/**
 * Splits chart touches into three unambiguous gestures, so looking at past values never fights with
 * panning:
 *  - **drag** (moves past touch slop before the long-press timeout): left alone, Vico pans/zooms;
 *  - **tap**: [onTap], pins the inspection cursor there;
 *  - **long press, then drag**: scrub mode; every following event is consumed (Vico can't pan and the
 *    page can't scroll) and the cursor follows the finger until it lifts. The cursor stays pinned.
 * Runs in the Initial pass so it sees events before the charts do; until scrub mode it consumes nothing.
 * Two fingers (pinch) cancel it immediately.
 */
internal fun Modifier.inspectGestures(
    onTap: (Offset) -> Unit,
    onScrubStart: (Offset) -> Unit,
    onScrub: (Offset) -> Unit,
    onScrubEnd: () -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var tapAt: Offset? = null
        var aborted = false
        val heldStill = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) { aborted = true; break }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) { aborted = true; break }
                if (change.changedToUpIgnoreConsumed()) { tapAt = change.position; break }
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) { aborted = true; break }
            }
        } == null
        tapAt?.let { onTap(it); return@awaitEachGesture }
        if (aborted || !heldStill) return@awaitEachGesture

        onScrubStart(down.position)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            event.changes.forEach { it.consume() }
            if (change == null || !change.pressed) break
            onScrub(change.position)
        }
        onScrubEnd()
    }
}

private fun glucoseColor(value: Int, lower: Int, upper: Int): Color = when {
    value < lower -> BelowRange
    value > upper -> AboveRange
    else -> InRange
}

/** Clears the inspection (cursor line and readout) once the user pans the inspected time out of the visible chart. */
@Composable
internal fun DismissInspectionWhenOffscreen(cursorTime: Long?, geometry: State<ChartGeometry?>, onDismiss: () -> Unit) {
    val currentCursor by rememberUpdatedState(cursorTime)
    val currentDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        snapshotFlow {
            val g = geometry.value
            val t = currentCursor
            if (g == null || t == null) false else g.xOf(t).let { it < g.left || it > g.right }
        }.collect { offscreen -> if (offscreen) currentDismiss() }
    }
}

/** The cursor line through every panel and the pin lane, with a ring on the inspected CGM reading. */
@Composable
internal fun InspectCursor(
    cursorTime: Long?,
    reading: CgmChartData?,
    geometry: State<ChartGeometry?>,
    lower: Int,
    upper: Int,
    maxY: Float,
    modifier: Modifier
) {
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val ringColor = MaterialTheme.colorScheme.background
    Canvas(modifier) {
        val g = geometry.value ?: return@Canvas
        val time = cursorTime ?: return@Canvas
        val x = g.xOf(time)
        if (x < g.left || x > g.right) return@Canvas
        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
        reading?.let {
            val fraction = ((it.value - CGM_MIN_Y) / (maxY - CGM_MIN_Y)).coerceIn(0f, 1f)
            val y = g.bottom - fraction * (g.bottom - g.top)
            drawCircle(glucoseColor(it.value, lower, upper), radius = 6.dp.toPx(), center = Offset(x, y))
            drawCircle(ringColor, radius = 6.dp.toPx(), center = Offset(x, y), style = Stroke(2.dp.toPx()))
        }
    }
}

/**
 * Floating readout above the charts, horizontally centred on the cursor (clamped to the screen).
 * Shows the inspected time, glucose, and any bolus / carbs near it. Tapping it closes the inspection.
 * Its x is read from [geometry] at placement time, so it tracks panning without recomposing.
 */
@Composable
internal fun InspectReadout(
    cursorTime: Long,
    geometry: State<ChartGeometry?>,
    reading: CgmChartData?,
    activity: ActivityReadout? = null,
    bolusUnits: Double,
    carbGrams: Int,
    lower: Int,
    upper: Int,
    onDismiss: () -> Unit
) {
    val time = remember(cursorTime) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(cursorTime)) }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    if (reading != null) {
                        Text(
                            "${reading.value}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = glucoseColor(reading.value, lower, upper)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text("mg/dL", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("no reading", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    activity?.bpm?.let { ReadoutEvent(Activity, "$it bpm") }
                    activity?.label?.let { ReadoutEvent(Sleep.takeIf { _ -> it.startsWith("Sleep") || it == "Asleep" } ?: Activity, it) }
                    if (bolusUnits > 0) ReadoutEvent(Bolus, formatAmount(bolusUnits) + " U")
                    if (carbGrams > 0) ReadoutEvent(Carbs, "$carbGrams g")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Close, contentDescription = "Close inspection",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val bubble = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val cx = geometry.value?.xOf(cursorTime) ?: (constraints.maxWidth / 2f)
            val x = (cx - bubble.width / 2f).roundToInt().coerceIn(0, (constraints.maxWidth - bubble.width).coerceAtLeast(0))
            bubble.place(x, (constraints.maxHeight - bubble.height) / 2)
        }
    }
}

@Composable
private fun ReadoutEvent(color: Color, text: String) {
    Spacer(Modifier.width(10.dp))
    Box(Modifier.size(8.dp).background(color, CircleShape))
    Spacer(Modifier.width(4.dp))
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

private fun formatAmount(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

/** Reports [amount] less height than the content measures, so the next sibling overlaps the slack. */
private fun Modifier.trimBottom(amount: Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    layout(p.width, (p.height - amount.roundToPx()).coerceAtLeast(0)) { p.place(0, 0) }
}

/** The app bar title on Home: always the live glucose reading with its trend arrow and age. */
@Composable
fun HomeTitle(state: HomeState) {
    val cgm = state.cgmUi ?: return Text("Diafit")
    Row(verticalAlignment = Alignment.CenterVertically) {
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
        Text("${cgm.timeSince} ago", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun PlaceholderPanel(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().trimBottom(PanelGapTrim).fillMaxHeight()) {
        PanelTitle(title)
    }
}

/** Heart rate, steps, sleep and exercise (from Health Connect) on the shared time axis. */
@Composable
internal fun ActivityDisplay(
    modifier: Modifier,
    data: ActivityData,
    connected: Boolean,
    window: ChartTimeWindow,
    geometry: State<ChartGeometry?>
) {
    Box(modifier = modifier.fillMaxWidth().trimBottom(PanelGapTrim).fillMaxHeight()) {
        ComponentActivityChart(data = data, window = window, geometry = geometry, modifier = Modifier.fillMaxSize())
        PanelTitle("Activity")
        if (data.isEmpty) {
            Text(
                if (connected) "No activity data in this period" else "No activity data · connect Health Connect in Settings",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/** Basal rate (profile schedule and the loop's temp basals) on the shared time axis. */
@Composable
internal fun BasalDisplay(
    modifier: Modifier,
    segments: List<BasalSegment>,
    window: ChartTimeWindow,
    geometry: State<ChartGeometry?>
) {
    Box(modifier = modifier.fillMaxWidth().trimBottom(PanelGapTrim).fillMaxHeight()) {
        ComponentBasalChart(segments = segments, window = window, geometry = geometry, modifier = Modifier.fillMaxSize())
        PanelTitle("Basal")
        if (segments.isEmpty()) {
            Text(
                "No basal data yet · run a profile switch in AAPS",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/** The activity panel is a little taller than the others: a compact heart-rate plot plus the sleep/exercise lane. */
internal const val ActivityPanelWeight = 1.2f

@Composable
fun CgmChartDisplay(
    modifier: Modifier,
    history: List<CgmChartData>,
    lower: Int,
    upper: Int,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    window: ChartTimeWindow,
    onGeometry: (ChartGeometry) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .trimBottom(PanelGapTrim)
            .fillMaxHeight()
    ) {
        ComponentCgmChart(
            values = history,
            lowerBound = lower,
            upperBound = upper,
            scrollState = scrollState,
            zoomState = zoomState,
            window = window,
            onGeometry = onGeometry
        )
        PanelTitle("Glucose")
    }
}

/** The glucose panel's share of the chart height relative to the other panels (1 each). */
internal const val CgmPanelWeight = 1.5f
/** Vico leaves a few dp of inset under every chart; trimming it makes the panels touch. */
private val PanelGapTrim = 5.dp

@Composable
fun InsulinActivityDisplay(
    modifier: Modifier,
    history: List<InsulinActivityChartData>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    window: ChartTimeWindow,
    highlightTime: Long? = null
) {
    val events = remember(history) { history.map { ChartEvent(it.timeLong, it.value.toDouble()) } }
    BoxWithConstraints(modifier = modifier.fillMaxWidth().trimBottom(PanelGapTrim).fillMaxHeight()) {
        ComponentEventActivityChart(
            plotHeightDp = maxHeight.value,
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
            window = window,
            highlightTime = highlightTime
        )
        PanelTitle("Bolus")
    }
}

@Composable
fun CarbActivityDisplay(
    modifier: Modifier,
    history: List<CarbsChartData>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    window: ChartTimeWindow,
    highlightTime: Long? = null
) {
    val events = remember(history) { history.map { ChartEvent(it.timeLong, it.value.toDouble(), it.durationMinutes) } }
    BoxWithConstraints(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        ComponentEventActivityChart(
            plotHeightDp = maxHeight.value - if (SHOW_TIME_LABELS) 22f else 0f,
            events = events,
            activityOf = { e, t -> CarbActivity.calculate(e.value, e.time, t, e.durationMinutes) },
            color = Carbs,
            valueUnit = "g",
            bubbleRefValue = 10.0,
            showTimeLabels = true,
            scrollState = scrollState,
            zoomState = zoomState,
            window = window,
            highlightTime = highlightTime
        )
        PanelTitle("Carbohydrates")
    }
}

private const val DEFAULT_VISIBLE_PAST_HOURS = 6

/** X-range (ms) to show: [pastHours] of history plus [futureHours] of headroom (Home's shared future by default). */
internal fun visibleHoursMillis(pastHours: Int, futureHours: Int = TIME_AXIS_FUTURE_HOURS): Double =
    (pastHours + futureHours) * 3_600_000.0

/**
 * One-tap zoom presets for the shared chart zoom (pinching also works). On Home each is followed by a
 * jump back to "now" ([scrollToEnd]) so the newest data stays in view.
 */
@Composable
fun ChartZoomControls(
    zoomState: VicoZoomState,
    scrollState: VicoScrollState,
    modifier: Modifier = Modifier,
    futureHours: Int = TIME_AXIS_FUTURE_HOURS,
    scrollToEnd: Boolean = true
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drop Material's 48dp minimum touch target so the buttons can actually be small.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        listOf(3, 6, 12, 24).forEach { hours ->
            Surface(
                onClick = {
                    scope.launch {
                        zoomState.animateZoom(Zoom.x(visibleHoursMillis(hours, futureHours)))
                        if (scrollToEnd) scrollState.animateScroll(Scroll.Absolute.End)
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
