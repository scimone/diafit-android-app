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
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.data.AxisValueOverrider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.shape.Shape
import uk.scimone.diafit.home.presentation.model.ChartData
import java.util.Calendar

@Composable
fun ComponentBolusCarbChart(
    values: List<ChartData>,
    barColor: Color
) {
    val minY = 0f
    val maxY = (values.maxOfOrNull { it.value.toFloat() } ?: 10f) * 1.2f  // 20% headroom
    val modelProducer = remember { CartesianChartModelProducer.build() }
    val currentTime = System.currentTimeMillis()
    val oneDayAgo = currentTime - 24 * 60 * 60 * 1000

    val filteredValues = values.filter { it.timeFloat >= oneDayAgo && it.timeFloat <= currentTime }

    LaunchedEffect(filteredValues) {
        if (filteredValues.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries {
                    series(
                        x = filteredValues.map { it.timeFloat },
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
                axisValueOverrider = AxisValueOverrider.fixed(
                    minX = oneDayAgo.toDouble(),
                    maxX = currentTime.toDouble(),
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
            bottomAxis = rememberBottomAxis(
                guideline = LineComponent(
                    color = MaterialTheme.colorScheme.onSurface.toArgb(),
                    thicknessDp = .1f
                ),
                label = rememberAxisLabelComponent(
                    textAlignment = Layout.Alignment.ALIGN_CENTER,
                    textSize = 10.sp
                ),
                valueFormatter = { value, _, _ ->
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = value.toLong()
                    }
                    val hours = calendar.get(Calendar.HOUR_OF_DAY)
                    String.format("%02d", hours)
                }
            ),
            getXStep = { 3600000.0 } // 1 hour
        )

        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            zoomState = rememberVicoZoomState(
                zoomEnabled = true,
                initialZoom = { _, _, _ -> 2f }
            ),
            scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)
        )
    }
}

