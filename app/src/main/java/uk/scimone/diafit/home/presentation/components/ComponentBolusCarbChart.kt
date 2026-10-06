package uk.scimone.diafit.home.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import uk.scimone.diafit.home.presentation.model.ChartData
import uk.scimone.diafit.home.presentation.utils.ChartColumnThickness
import uk.scimone.diafit.home.presentation.utils.ChartColumnSpacing
import uk.scimone.diafit.home.presentation.utils.ColumnChartLayerPadding
import uk.scimone.diafit.home.presentation.utils.SharedStartAxisSize
import uk.scimone.diafit.home.presentation.utils.createTimeAxisRangeProvider
import uk.scimone.diafit.home.presentation.utils.getTimeAxisBounds
import uk.scimone.diafit.home.presentation.utils.getTimeAxisXStep
import uk.scimone.diafit.home.presentation.utils.rememberTimeBottomAxis
import uk.scimone.diafit.home.presentation.utils.timeAxisMaxX

@Composable
fun ComponentBolusCarbChart(
    values: List<ChartData>,
    barColor: Color,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState
) {
    val minY = 0f
    val maxY = (values.maxOfOrNull { it.value.toFloat() } ?: 10f) * 1.2f  // 20% headroom
    val modelProducer = remember { CartesianChartModelProducer() }

    val (alignedMinTime, _, realTime) = getTimeAxisBounds(hoursBack = 24)

    val filteredValues = values.filter { it.timeLong in alignedMinTime..realTime }

    LaunchedEffect(filteredValues) {
        if (filteredValues.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries {
                    series(
                        x = filteredValues.map { it.timeLong },
                        y = filteredValues.map { it.value.toFloat() }
                    )
                }
            }
        }
    }

    if (filteredValues.isNotEmpty()) {
        val onSurface = MaterialTheme.colorScheme.onSurface
        val chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = Fill(barColor), thickness = ChartColumnThickness)
                ),
                columnCollectionSpacing = ChartColumnSpacing,
                rangeProvider = createTimeAxisRangeProvider(
                    minX = alignedMinTime,
                    maxX = timeAxisMaxX(realTime),
                    minY = minY.toDouble(),
                    maxY = maxY.toDouble()
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                guideline = rememberLineComponent(fill = Fill(onSurface), thickness = 0.1.dp),
                size = SharedStartAxisSize
            ),
            bottomAxis = rememberTimeBottomAxis(),
            layerPadding = { ColumnChartLayerPadding },
            getXStep = { _ -> getTimeAxisXStep() },
        )

        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            zoomState = zoomState,
            scrollState = scrollState
        )
    }
}
