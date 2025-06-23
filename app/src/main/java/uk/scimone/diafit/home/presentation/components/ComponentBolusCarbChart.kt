package uk.scimone.diafit.home.presentation.components

import android.text.Layout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.HorizontalLayout
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.shape.Shape
import uk.scimone.diafit.home.presentation.model.ChartData
import uk.scimone.diafit.home.presentation.utils.createTimeAxisValueOverrider
import uk.scimone.diafit.home.presentation.utils.getTimeAxisBounds
import uk.scimone.diafit.home.presentation.utils.getTimeAxisXStep
import uk.scimone.diafit.home.presentation.utils.rememberTimeBottomAxis

@Composable
fun ComponentBolusCarbChart(
    values: List<ChartData>,
    barColor: Color
) {
    val minY = 0f
    val maxY = (values.maxOfOrNull { it.value.toFloat() } ?: 10f) * 1.2f  // 20% headroom
    val modelProducer = remember { CartesianChartModelProducer.build() }

    // ✅ Use same reusable time axis bounds
    val (alignedMinTime, alignedMaxTime, realTime) = getTimeAxisBounds(hoursBack = 24)

    // ✅ Use timeLong now (instead of timeFloat)
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
        val chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer.ColumnProvider.series(
                    columns = listOf(
                        LineComponent(
                            color = barColor.toArgb(),
                            thicknessDp = 3f,
                            shape = Shape.Rectangle
                        )
                    )
                ),
                axisValueOverrider = createTimeAxisValueOverrider(
                    minX = alignedMinTime,
                    maxX = realTime,
                    minY = minY.toDouble(),
                    maxY = maxY.toDouble()
                )
            ),
            startAxis = rememberStartAxis(
                horizontalLabelPosition = com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis.HorizontalLabelPosition.Inside,
                guideline = LineComponent(
                    color = MaterialTheme.colorScheme.onSurface.toArgb(),
                    thicknessDp = .1f
                )
            ),
            // ✅ Use same reusable time bottom axis
            bottomAxis = rememberTimeBottomAxis(),
            getXStep = { getTimeAxisXStep() },
            horizontalLayout = HorizontalLayout.FullWidth(),
            )

        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            zoomState = rememberVicoZoomState(zoomEnabled = true, initialZoom = { _, _, _ -> 2f }),
            scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)
        )
    }
}
