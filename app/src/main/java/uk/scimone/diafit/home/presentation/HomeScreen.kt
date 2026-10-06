package uk.scimone.diafit.home.presentation

import android.net.Uri
import androidx.compose.foundation.layout.*
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
    val chartZoomState = rememberVicoZoomState(
        zoomEnabled = true,
        initialZoom = Zoom.fixed(2f),
        minZoom = Zoom.fixed(1f),
        maxZoom = Zoom.fixed(10f)
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
                Column {
                    if (state.cgmUi != null) {
                        CgmDisplay(cgm = state.cgmUi!!)
                        Spacer(modifier = Modifier.height(16.dp))
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
                        BolusChartDisplay(
                            history = state.bolusHistory,
                            scrollState = chartScrollState,
                            zoomState = chartZoomState
                        )
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
fun BolusChartDisplay(
    history: List<BolusChartData>,
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
            barColor = Bolus,
            scrollState = scrollState,
            zoomState = zoomState
        )
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
            .height(90.dp)
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
