package uk.scimone.diafit.home.presentation

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import uk.scimone.diafit.home.presentation.utils.TIME_AXIS_FUTURE_HOURS
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import uk.scimone.diafit.home.presentation.components.ComponentBolusCarbChart
import uk.scimone.diafit.home.presentation.components.ComponentInsulinActivityChart
import uk.scimone.diafit.home.presentation.components.ComponentMealImage
import uk.scimone.diafit.home.presentation.model.BolusChartData
import uk.scimone.diafit.home.presentation.model.CarbsChartData
import uk.scimone.diafit.home.presentation.model.InsulinActivityChartData
import uk.scimone.diafit.home.presentation.model.MealEntityUi
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
            .padding(24.dp),
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
                        CgmDisplay(cgm = state.cgmUi!!)
                        Spacer(modifier = Modifier.height(8.dp))
                        ChartZoomControls(zoomState = chartZoomState, scrollState = chartScrollState)
                        CgmChartDisplay(
                            history = state.cgmHistory,
                            lower = state.targetRangeLower,
                            upper = state.targetRangeUpper,
                            scrollState = chartScrollState,
                            zoomState = chartZoomState
                        )
                    } else {
                        Text("No CGM data available")
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (state.bolusHistory.isNotEmpty()) {
                        InsulinActivityDisplay(
                            history = state.insulinActivityHistory,
                            scrollState = chartScrollState,
                            zoomState = chartZoomState
                        )
                    }

                    if (state.mealHistory.isNotEmpty()) {
                        CarbsChartDisplay(
                            history = state.carbHistory,
                            scrollState = chartScrollState,
                            zoomState = chartZoomState
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        MealImagesRow(meals = state.mealHistory)
                    }

                }
            }
        }
    }
}


@Composable
fun CgmDisplay(cgm: CgmEntityUi) {
    Column {
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

@Composable
fun CgmChartDisplay(
    history: List<CgmChartData>,
    lower: Int,
    upper: Int,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        ComponentCgmChart(
            values = history,
            lowerBound = lower,
            upperBound = upper,
            scrollState = scrollState,
            zoomState = zoomState
        )
    }
}

@Composable
fun MealImagesRow(meals: List<MealEntityUi>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(meals) { meal ->
            ComponentMealImage(meal)
        }
    }
}

@Composable
fun InsulinActivityDisplay(
    history: List<InsulinActivityChartData>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        ComponentInsulinActivityChart(
            values = history,
            color = Bolus,
            scrollState = scrollState,
            zoomState = zoomState
        )
    }
}

@Composable
fun CarbsChartDisplay(
    history: List<CarbsChartData>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
    ) {
        ComponentBolusCarbChart(
            values = history,
            barColor = Carbs,
            scrollState = scrollState,
            zoomState = zoomState
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
        Text("Zoom", style = MaterialTheme.typography.labelMedium)
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
