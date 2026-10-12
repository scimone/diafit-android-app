package uk.scimone.diafit.home.presentation

import androidx.compose.ui.res.painterResource
import uk.scimone.diafit.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import uk.scimone.diafit.core.domain.model.ActivityLevel
import uk.scimone.diafit.core.domain.model.at
import uk.scimone.diafit.core.domain.model.currentActivityLevel
import uk.scimone.diafit.core.domain.model.remainingCarbs
import uk.scimone.diafit.core.domain.model.remainingInsulin
import uk.scimone.diafit.home.presentation.components.formatRate
import uk.scimone.diafit.home.presentation.utils.chartPanZoom
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.Basal
import uk.scimone.diafit.ui.theme.Carbs
import kotlin.math.roundToInt
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
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import uk.scimone.diafit.home.presentation.components.EventBubble
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
import androidx.compose.ui.platform.LocalDensity
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
import uk.scimone.diafit.core.domain.model.basalRemainingInsulin
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
    var scrubX by remember { mutableFloatStateOf(0f) }
    // Meals grouped into sittings, shared by the pin lane, the meal strip and the detail sheet.
    val mealGroups = remember(state.mealHistory) { groupMeals(state.mealHistory) }
    val focusedMeal = mealGroups.nearest(selectedTime)
    // Remember only which meals are open and re-resolve them from the live list, so a photo added
    // from the sheet shows up immediately.
    var openMealIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val openMeal = state.mealHistory.filter { it.id in openMealIds }.sortedBy { it.mealTimeUtc }
        .takeIf { it.isNotEmpty() }?.let { MealGroup(it) }
    // Meals picked by tapping a carb bubble or a photo: both are ringed/outlined until the user taps elsewhere.
    var selectedMealKeys by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val selectedEventTimes = remember(selectedMealKeys, mealGroups) {
        mealGroups.filter { it.key in selectedMealKeys }.flatMap { g -> g.meals.map { it.mealTimeUtc } }.toSet()
    }
    // The carb bubbles as drawn (merged for the zoom) and where their panel sits inside the chart column, for tap hit-testing.
    val carbBubbles = remember { arrayOf<List<EventBubble>>(emptyList()) }
    var carbPanelTop by remember { mutableFloatStateOf(0f) }
    var carbPanelBottom by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val densityPx = LocalDensity.current.density
    val window = remember(nowMinute) { homeTimeWindow(nowMinute) }
    val headlines = remember(state.cgmUi, state.basal, state.smbs, state.activity, state.insulinActivityHistory, state.carbHistory, nowMinute, state.targetRangeLower, state.targetRangeUpper) {
        homeHeadlines(state, nowMinute)
    }

    // Where the shared time axis sits on screen, reported by the CGM chart on every frame. Read only
    // in draw/layout lambdas or through derivedStateOf, so panning doesn't recompose the screen.
    val geometry = remember { mutableStateOf<ChartGeometry?>(null) }
    val bolusScale = remember { mutableStateOf<Double?>(null) }
    // The headline values describe "now", so they go away once the now line has been scrolled out of view.
    val nowVisible by remember(window) {
        derivedStateOf { geometry.value?.let { g -> g.xOf(window.now).let { it >= g.left && it <= g.right } } ?: true }
    }
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


    // A light tick whenever the scrub cursor reaches another meal.
    LaunchedEffect(focusedMeal?.key) {
        if (scrubbing && focusedMeal != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun timeAtX(x: Float): Long? = geometry.value?.let { g ->
        g.timeAt(x).coerceIn(g.minX.toLong(), window.now)
    }
    ScrubAutoScroll(scrubbing, { scrubX }, geometry, chartScrollState) { x -> viewModel.onSelectedTimeChange(timeAtX(x)) }

    // A tap on a carb bubble selects the meal(s) it stands for; a tap anywhere else clears the selection.
    fun onChartTap(pos: Offset) {
        val g = geometry.value
        val hit = if (g != null && pos.y in carbPanelTop..carbPanelBottom) {
            carbBubbles[0].minByOrNull { abs(g.xOf(it.time) - pos.x) }
                ?.takeIf { abs(g.xOf(it.time) - pos.x) <= maxOf(it.diameterDp / 2f + 8f, 24f) * densityPx }
        } else null
        selectedMealKeys = if (hit == null) emptySet() else {
            val times = hit.members.map { it.time }.toSet()
            mealGroups.filter { grp -> grp.meals.any { it.mealTimeUtc in times } }.map { it.key }.toSet()
        }
        if (hit != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                                    when {
                                        state.showAgp && state.agpUnavailable -> "AGP needs 5 days of data"
                                        selectedMealKeys.isNotEmpty() -> "Meal selected"
                                        else -> "Hold to inspect"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.weight(1f))
                                AgpToggle(active = state.showAgp, onClick = viewModel::toggleAgp)
                                Spacer(Modifier.width(8.dp))
                                ChartZoomControls(zoomState = chartZoomState, scrollState = chartScrollState)
                            }
                        } else if (cursorTime != null) {
                            InspectReadout(
                                cursorTime = cursorTime,
                                geometry = geometry,
                                reading = inspectedReading,
                                activity = state.activity.readoutAt(cursorTime),
                                activityLevel = activityLevelAt(state.activity, state.activityConnected, cursorTime),
                                basalRate = state.basal.at(cursorTime)?.delivered,
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
                        if (state.activityConnected) uk.scimone.diafit.home.presentation.components.ActivityBackdrop(state.activity, window, geometry, Modifier.matchParentSize())
                        Column(Modifier.fillMaxSize()) {
                            Column(
                                Modifier.fillMaxSize().inspectGestures(
                                    onScrubStart = { pos ->
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        scrubX = pos.x
                                        scrubbing = true
                                        viewModel.onSelectedTimeChange(timeAtX(pos.x))
                                    },
                                    onScrub = { pos -> scrubX = pos.x; viewModel.onSelectedTimeChange(timeAtX(pos.x)) },
                                    onScrubEnd = { scrubbing = false; viewModel.onSelectedTimeChange(null) },
                                    onTap = ::onChartTap
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
                                        onGeometry = onGeometry,
                                        headline = headlines.glucose.takeIf { nowVisible },
                                        agp = state.agp.takeIf { state.showAgp }
                                    )
                                }
                                ActivityDisplay(
                                    modifier = Modifier.weight(ActivityPanelWeight),
                                    data = state.activity,
                                    connected = state.activityConnected,
                                    scrollState = chartScrollState,
                                    zoomState = chartZoomState,
                                    window = window,
                                    geometry = geometry,
                                    headline = headlines.activity.takeIf { nowVisible }
                                )
                                BasalDisplay(
                                    modifier = Modifier.weight(1f),
                                    segments = state.basal,
                                    scrollState = chartScrollState,
                                    zoomState = chartZoomState,
                                    window = window,
                                    geometry = geometry,
                                    headline = headlines.basal.takeIf { nowVisible },
                                    insulinHeadline = headlines.basalInsulin.takeIf { nowVisible },
                                    activityUnitsPerDp = bolusScale.value,
                                    style = state.basalStyle,
                                    smbs = state.smbs
                                )
                                InsulinActivityDisplay(
                                    modifier = Modifier.weight(1f),
                                    history = state.insulinActivityHistory,
                                    scrollState = chartScrollState,
                                    zoomState = chartZoomState,
                                    window = window,
                                    highlightTime = cursorTime,
                                    headline = headlines.bolus.takeIf { nowVisible },
                                    onScale = { bolusScale.value = it }
                                )
                                CarbActivityDisplay(
                                    modifier = Modifier.weight(1f).onGloballyPositioned {
                                        carbPanelTop = it.positionInParent().y
                                        carbPanelBottom = carbPanelTop + it.size.height
                                    },
                                    history = state.carbHistory,
                                    scrollState = chartScrollState,
                                    zoomState = chartZoomState,
                                    window = window,
                                    highlightTime = cursorTime,
                                    headline = headlines.carbs.takeIf { nowVisible },
                                    highlightEventTimes = selectedEventTimes,
                                    onBubbles = { carbBubbles[0] = it }
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
                        highlighted = (mealGroups.caughtBy(selectedTime) + mealGroups.filter { it.key in selectedMealKeys }).distinctBy { it.key },
                        selectedKeys = selectedMealKeys,
                        geometry = geometry,
                        scrollState = chartScrollState,
                        onGroupClick = { group ->
                            // Tapping a photo highlights its data point; details open from the card's button or a long press.
                            selectedMealKeys = if (group.key in selectedMealKeys) emptySet() else setOf(group.key)
                        },
                        onGroupOpen = { group ->
                            openMealIds = group.meals.map { it.id }.toSet()
                            // Bring the meal into view on the charts behind the sheet.
                            revealOnCharts(group.startTime)
                        },
                        onBackgroundTap = { selectedMealKeys = emptySet() },
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

internal val InspectBarHeight = 56.dp
internal const val TapToggleRadiusPx = 48f

/**
 * Splits chart touches into three unambiguous gestures, so looking at past values never fights with
 * panning:
 *  - **drag** (moves past touch slop before the long-press timeout): left alone, Vico pans/zooms;
 *  - **tap**: reported through `onTap` without consuming anything;
 *  - **long press, then drag**: scrub mode; every following event is consumed (Vico can't pan and the
 *    page can't scroll) and the cursor follows the finger until it lifts, when the inspection ends.
 * Runs in the Initial pass so it sees events before the charts do; until scrub mode it consumes nothing.
 * Two fingers (pinch) cancel it immediately.
 */
internal fun Modifier.inspectGestures(
    onScrubStart: (Offset) -> Unit,
    onScrub: (Offset) -> Unit,
    onScrubEnd: () -> Unit,
    /** A plain tap (lifted before the long-press timeout without moving); nothing is consumed. */
    onTap: (Offset) -> Unit = {}
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var aborted = false
        val heldStill = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) { aborted = true; break }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) { aborted = true; break }
                if (change.changedToUpIgnoreConsumed()) {
                    aborted = true
                    if ((change.position - down.position).getDistance() <= viewConfiguration.touchSlop) onTap(change.position)
                    break
                }
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) { aborted = true; break }
            }
        } == null
        if (aborted || !heldStill) return@awaitEachGesture

        onScrubStart(down.position)
        // Always ends the inspection, also when the gesture is torn down without a lift (a finger dragged
        // into the system's edge-swipe zone cancels the touch stream).
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                event.changes.forEach { it.consume() }
                if (change == null || !change.pressed) break
                onScrub(change.position)
            }
        } finally {
            onScrubEnd()
        }
    }
}

private fun glucoseColor(value: Int, lower: Int, upper: Int): Color = when {
    value < lower -> BelowRange
    value > upper -> AboveRange
    else -> InRange
}

/**
 * While scrubbing, a finger held near the left or right edge of the charts scrolls them (faster the
 * closer to the edge), and the cursor keeps following the finger over the content moving beneath it.
 */
@Composable
internal fun ScrubAutoScroll(
    scrubbing: Boolean,
    fingerX: () -> Float,
    geometry: State<ChartGeometry?>,
    scrollState: VicoScrollState,
    onTime: (Float) -> Unit
) {
    val density = LocalDensity.current
    val currentOnTime by rememberUpdatedState(onTime)
    LaunchedEffect(scrubbing) {
        if (!scrubbing) return@LaunchedEffect
        val zone = with(density) { 56.dp.toPx() }
        val maxStep = with(density) { 10.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val g = geometry.value ?: continue
            val x = fingerX()
            val step = when {
                x < g.left + zone -> -maxStep * ((g.left + zone - x) / zone).coerceIn(0f, 1f)
                x > g.right - zone -> maxStep * ((x - (g.right - zone)) / zone).coerceIn(0f, 1f)
                else -> 0f
            }
            if (step != 0f) {
                scrollState.scroll(com.patrykandpatrick.vico.compose.cartesian.Scroll.Relative.pixels(step))
                currentOnTime(x)
            }
        }
    }
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
    /** How active the person was at the cursor (shown unless a workout/sleep label already says it). */
    activityLevel: ActivityLevel? = null,
    /** Basal rate (U/h) running at the cursor. */
    basalRate: Double? = null,
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
                // One line while it fits; the items wrap onto a second line only when they don't.
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                    }
                    // Order: glucose (above), carbs, bolus, basal, activity.
                    if (carbGrams > 0) ReadoutEvent(R.drawable.ic_meal_type_snack, Carbs, "$carbGrams g")
                    if (bolusUnits > 0) ReadoutEvent(R.drawable.ic_readout_bolus, Bolus, formatAmount(bolusUnits) + " U")
                    basalRate?.let { ReadoutEvent(R.drawable.ic_readout_basal, Basal, "${formatRate(it)} U/h") }
                    val doing = listOfNotNull(
                        activity?.label ?: activityLevel?.label,
                        activity?.bpm?.let { "$it bpm" }
                    )
                    if (doing.isNotEmpty()) {
                        val asleep = activity?.label?.let { it.startsWith("Sleep") || it == "Asleep" } == true
                        ReadoutEvent(R.drawable.ic_readout_activity, if (asleep) Sleep else Activity, doing.joinToString(" · "))
                    }
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
private fun ReadoutEvent(@androidx.annotation.DrawableRes icon: Int, color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
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

/** Small quick button that shows or hides the two-week AGP behind the glucose curve. */
@Composable
private fun AgpToggle(active: Boolean, onClick: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.semantics { contentDescription = if (active) "Hide typical day (AGP)" else "Show typical day (AGP)" }
        ) {
            Text("AGP", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

/** The latest value of a panel, shown next to its heading in the colour of its category: "113" + "mg/dL". */
data class PanelValue(val text: String, val unit: String, val color: Color)

/** Small muted heading in a panel's top-left corner, with the panel's latest [value] (if any) beside it. */
@Composable
private fun BoxScope.PanelTitle(text: String, value: PanelValue? = null, extra: PanelValue? = null) {
    // Aligned on the text baseline: the label and the bigger bold value have different line boxes,
    // so aligning their bottoms left the value a little off the label.
    Row(modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 2.dp)) {
        Text(text = text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
        listOfNotNull(value, extra).forEach { v ->
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)) { append(v.text) }
                    if (v.unit.isNotEmpty()) withStyle(SpanStyle(fontSize = 10.sp)) { append(" ${v.unit}") }
                },
                color = v.color,
                modifier = Modifier.alignByBaseline().padding(start = 6.dp)
            )
        }
    }
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
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    window: ChartTimeWindow,
    geometry: State<ChartGeometry?>,
    headline: PanelValue? = null
) {
    Box(modifier = modifier.fillMaxWidth().trimBottom(PanelGapTrim).fillMaxHeight().chartPanZoom(scrollState, zoomState, geometry)) {
        ComponentActivityChart(data = data, window = window, geometry = geometry, modifier = Modifier.fillMaxSize())
        PanelTitle("Activity", headline)
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
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    window: ChartTimeWindow,
    geometry: State<ChartGeometry?>,
    headline: PanelValue? = null,
    /** Basal insulin on board, shown after [headline]. */
    insulinHeadline: PanelValue? = null,
    /** The bolus panel's y scale, so the basal's insulin activity curve is drawn at the same scale. */
    activityUnitsPerDp: Double? = null,
    style: uk.scimone.diafit.settings.domain.model.BasalStyle = uk.scimone.diafit.settings.domain.model.BasalStyle.RATE,
    smbs: List<uk.scimone.diafit.core.domain.model.SmbMark> = emptyList()
) {
    Box(modifier = modifier.fillMaxWidth().trimBottom(PanelGapTrim).fillMaxHeight().chartPanZoom(scrollState, zoomState, geometry)) {
        ComponentBasalChart(segments = segments, window = window, geometry = geometry, modifier = Modifier.fillMaxSize(), activityUnitsPerDp = activityUnitsPerDp, style = style, smbs = smbs)
        PanelTitle("Basal", headline, insulinHeadline)
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
    onGeometry: (ChartGeometry) -> Unit,
    headline: PanelValue? = null,
    agp: uk.scimone.diafit.patterns.domain.PatternAgp? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .trimBottom(PanelGapTrim)
            .fillMaxHeight()
    ) {
        ComponentCgmChart(
            agp = agp,
            values = history,
            lowerBound = lower,
            upperBound = upper,
            scrollState = scrollState,
            zoomState = zoomState,
            window = window,
            onGeometry = onGeometry
        )
        PanelTitle("Glucose", headline)
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
    highlightTime: Long? = null,
    headline: PanelValue? = null,
    onScale: ((Double) -> Unit)? = null
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
            highlightTime = highlightTime,
            onScale = onScale
        )
        PanelTitle("Bolus", headline)
    }
}

@Composable
fun CarbActivityDisplay(
    modifier: Modifier,
    history: List<CarbsChartData>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    window: ChartTimeWindow,
    highlightTime: Long? = null,
    headline: PanelValue? = null,
    highlightEventTimes: Set<Long> = emptySet(),
    onBubbles: ((List<EventBubble>) -> Unit)? = null
) {
    val events = remember(history) { history.map { ChartEvent(it.timeLong, it.value.toDouble(), curve = it.curve) } }
    BoxWithConstraints(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        ComponentEventActivityChart(
            plotHeightDp = maxHeight.value - if (SHOW_TIME_LABELS) 22f else 0f,
            events = events,
            // The carb panel draws the meal's carbohydrate absorption rate (g/h), the same quantity the old curve showed.
            activityOf = { e, t -> e.curve?.carbsRateGphAt(t) ?: 0.0 },
            color = Carbs,
            valueUnit = "g",
            bubbleRefValue = 10.0,
            showTimeLabels = true,
            scrollState = scrollState,
            zoomState = zoomState,
            window = window,
            highlightTime = highlightTime,
            highlightEventTimes = highlightEventTimes,
            onBubbles = onBubbles
        )
        PanelTitle("Carbohydrates", headline)
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


/** The latest value of each Home panel, shown by its heading. */
internal data class HomeHeadlines(
    val glucose: PanelValue? = null,
    val activity: PanelValue? = null,
    val basal: PanelValue? = null,
    /** Basal insulin on board (temp basal differences + SMBs), shown after the rate. */
    val basalInsulin: PanelValue? = null,
    val bolus: PanelValue? = null,
    val carbs: PanelValue? = null
)

internal fun homeHeadlines(state: HomeState, now: Long): HomeHeadlines {
    val glucose = state.cgmUi?.takeIf { !it.isStale }?.value?.let {
        PanelValue("$it", "mg/dL", glucoseColor(it, state.targetRangeLower, state.targetRangeUpper))
    }
    // Nothing recent from a connected source (Health Connect) means nothing was going on: Low. Without a source, no value.
    val activityLevel = activityLevelAt(state.activity, state.activityConnected, now)
    val activity = activityLevel?.let { PanelValue(it.label, "", Activity) }
    val basal = state.basal.lastOrNull()?.takeIf { now - it.endUtc <= 2 * 60_000L }?.let { PanelValue(formatRate(it.delivered), "U/h", Basal) }
    val basalInsulin = state.basal.takeIf { it.isNotEmpty() || state.smbs.isNotEmpty() }
        ?.let { PanelValue("%.2f".format(java.util.Locale.US, basalRemainingInsulin(it, now, state.smbs)), "U", Bolus) }
    val insulin = remainingInsulin(state.insulinActivityHistory.map { it.timeLong to it.value.toDouble() }, now)
    val carbs = remainingCarbs(state.carbHistory.map { it.curve }, now)
    return HomeHeadlines(
        glucose = glucose,
        activity = activity,
        basal = basal,
        basalInsulin = basalInsulin,
        bolus = PanelValue("%.2f".format(java.util.Locale.US, insulin), "U", Bolus),
        carbs = PanelValue("${carbs.roundToInt()}", "g", Carbs)
    )
}

/** The activity level at [time] (Low when a connected source has logged nothing then); null without a source. */
internal fun activityLevelAt(data: ActivityData, connected: Boolean, time: Long): ActivityLevel? =
    currentActivityLevel(data, time) ?: ActivityLevel.LOW.takeIf { connected }
