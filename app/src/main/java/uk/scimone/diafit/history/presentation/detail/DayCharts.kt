package uk.scimone.diafit.history.presentation.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import kotlinx.coroutines.delay
import uk.scimone.diafit.home.presentation.ActivityDisplay
import uk.scimone.diafit.home.presentation.ActivityPanelWeight
import uk.scimone.diafit.home.presentation.components.readoutAt
import uk.scimone.diafit.home.presentation.CarbActivityDisplay
import uk.scimone.diafit.home.presentation.CgmChartDisplay
import uk.scimone.diafit.home.presentation.components.cgmMaxY
import uk.scimone.diafit.home.presentation.CgmPanelWeight
import uk.scimone.diafit.home.presentation.ChartZoomControls
import uk.scimone.diafit.home.presentation.InspectBarHeight
import uk.scimone.diafit.home.presentation.InspectCursor
import uk.scimone.diafit.home.presentation.InspectReadout
import uk.scimone.diafit.home.presentation.DismissInspectionWhenOffscreen
import uk.scimone.diafit.home.presentation.InsulinActivityDisplay
import uk.scimone.diafit.home.presentation.TapToggleRadiusPx
import uk.scimone.diafit.home.presentation.components.MealTimeline
import uk.scimone.diafit.home.presentation.components.groupMeals
import uk.scimone.diafit.home.presentation.components.inView
import uk.scimone.diafit.home.presentation.components.caughtBy
import uk.scimone.diafit.home.presentation.inspectGestures
import uk.scimone.diafit.home.presentation.utils.ChartGeometry
import uk.scimone.diafit.home.presentation.utils.currentMinute
import uk.scimone.diafit.home.presentation.utils.dayTimeWindow
import uk.scimone.diafit.home.presentation.visibleHoursMillis
import kotlin.math.abs

/** A CGM reading this close to the cursor is the one the readout shows (and the cursor snaps to). */
private const val READING_SNAP_MS = 10 * 60_000L
/** Boluses / carbs this close to the cursor are listed in the readout. */
private const val EVENT_NEAR_MS = 15 * 60_000L

/**
 * The day on Home's chart stack: glucose, bolus and carb panels on one shared 0–24 h axis (opening
 * on the whole day, pinch or the chips to zoom), the same tap / hold-to-scrub inspection, and the
 * meal photo strip underneath, which follows the visible part of the day. Tapping a meal opens it.
 */
@Composable
internal fun DayCharts(state: DayDetailState, onOpenMeal: (Int) -> Unit) {
    // While the day is still running, "now" moves on (data and the faded activity tails follow it).
    val nowMinute by produceState(initialValue = currentMinute()) {
        while (System.currentTimeMillis() < state.dayEndUtc) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = currentMinute()
        }
    }
    val window = remember(state.dayStartUtc, nowMinute) { dayTimeWindow(state.dayStartUtc, state.dayEndUtc, nowMinute) }

    var selectedTime by remember { mutableStateOf<Long?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val geometry = remember { mutableStateOf<ChartGeometry?>(null) }
    val onGeometry = remember { { g: ChartGeometry -> geometry.value = g } }

    // As on Home: one scroll/zoom state shared by every panel, content-independent zoom bounds,
    // remembered Zoom objects (see CLAUDE.md "Shared time axis").
    val scrollState = rememberVicoScrollState(scrollEnabled = !scrubbing, initialScroll = Scroll.Absolute.Start)
    val wholeDay = remember { Zoom.x(visibleHoursMillis(24, futureHours = 0)) }
    val maxZoom = remember { Zoom.x(visibleHoursMillis(1, futureHours = 0)) }
    val zoomState = rememberVicoZoomState(zoomEnabled = true, initialZoom = wholeDay, minZoom = wholeDay, maxZoom = maxZoom)

    val mealGroups = remember(state.timelineMeals) { groupMeals(state.timelineMeals) }
    val mealsInView by remember(mealGroups) {
        derivedStateOf { geometry.value?.let { mealGroups.inView(it.visibleStart, it.visibleEnd) } ?: mealGroups }
    }
    val inspectedReading = selectedTime?.let { t ->
        state.cgm.minByOrNull { abs(it.timeLong - t) }?.takeIf { abs(it.timeLong - t) <= READING_SNAP_MS }
    }
    val cursorTime = inspectedReading?.timeLong ?: selectedTime
    val lower = state.target.lowerBound
    val upper = state.target.upperBound

    DismissInspectionWhenOffscreen(cursorTime, geometry) { selectedTime = null }

    fun timeAtX(x: Float): Long? = geometry.value?.let { g -> g.timeAt(x).coerceIn(window.minX, window.now) }

    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp)) {
        Box(Modifier.fillMaxWidth().height(InspectBarHeight)) {
            if (selectedTime == null) {
                Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tap or hold a chart to inspect", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    ChartZoomControls(zoomState = zoomState, scrollState = scrollState, futureHours = 0, scrollToEnd = false)
                }
            } else if (cursorTime != null) {
                InspectReadout(
                    cursorTime = cursorTime,
                    geometry = geometry,
                    reading = inspectedReading,
                    activity = state.activity.readoutAt(cursorTime),
                    bolusUnits = state.insulin.filter { abs(it.timeLong - cursorTime) <= EVENT_NEAR_MS }.sumOf { it.value.toDouble() },
                    carbGrams = state.carbs.filter { abs(it.timeLong - cursorTime) <= EVENT_NEAR_MS }.sumOf { it.value },
                    lower = lower,
                    upper = upper,
                    onDismiss = { selectedTime = null }
                )
            }
        }

        Box(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize().inspectGestures(
                    onTap = { pos ->
                        val currentX = cursorTime?.let { geometry.value?.xOf(it) }
                        selectedTime = if (currentX != null && abs(currentX - pos.x) < TapToggleRadiusPx) null else timeAtX(pos.x)
                    },
                    onScrubStart = { pos ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        scrubbing = true
                        selectedTime = timeAtX(pos.x)
                    },
                    onScrub = { pos -> selectedTime = timeAtX(pos.x) },
                    onScrubEnd = { scrubbing = false }
                )
            ) {
                CgmChartDisplay(
                    modifier = Modifier.weight(CgmPanelWeight),
                    history = state.cgm,
                    lower = lower,
                    upper = upper,
                    scrollState = scrollState,
                    zoomState = zoomState,
                    window = window,
                    onGeometry = onGeometry
                )
                ActivityDisplay(
                    modifier = Modifier.weight(ActivityPanelWeight),
                    data = state.activity,
                    connected = state.activityConnected,
                    window = window,
                    geometry = geometry
                )
                InsulinActivityDisplay(
                    modifier = Modifier.weight(1f),
                    history = state.insulin,
                    scrollState = scrollState,
                    zoomState = zoomState,
                    window = window,
                    highlightTime = cursorTime
                )
                CarbActivityDisplay(
                    modifier = Modifier.weight(1f),
                    history = state.carbs,
                    scrollState = scrollState,
                    zoomState = zoomState,
                    window = window,
                    highlightTime = cursorTime
                )
            }
            InspectCursor(
                cursorTime = cursorTime,
                reading = inspectedReading,
                geometry = geometry,
                lower = lower,
                upper = upper,
                maxY = cgmMaxY(state.cgm, window),
                modifier = Modifier.matchParentSize()
            )
        }

        MealTimeline(
            allGroups = mealGroups,
            inView = mealsInView,
            highlighted = mealGroups.caughtBy(selectedTime),
            geometry = geometry,
            onGroupClick = { group -> onOpenMeal(group.meals.first().id) },
            modifier = Modifier.padding(top = 6.dp),
            noMealsMessage = "No meals on this day."
        )
    }
}
