package uk.scimone.diafit.home.presentation.components

import android.text.Layout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberPoint
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.Axis.Position
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.AxisValueOverrider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarkerValueFormatter
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shape.Shape
import uk.scimone.diafit.home.presentation.model.CgmChartData
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.InRange
import java.text.DecimalFormat
import java.util.Calendar

@Composable
fun ComponentCgmChart(values: List<CgmChartData>) {
    val minY = 40f
    val maxY = 250f
    val modelProducer = remember { CartesianChartModelProducer.build() }
    val currentTime = System.currentTimeMillis()
    val oneDayAgo = currentTime - 24 * 60 * 60 * 1000 // 24 hours in milliseconds

    val filteredValues = values.filter { it.timeFloat >= oneDayAgo && it.timeFloat <= currentTime }

    val lineColors = mutableListOf<Color>()
    val lineData = mutableListOf<Pair<List<Float>, List<Int>>>()

    val below = filteredValues.filter { it.value < 70 }
    if (below.isNotEmpty()) {
        lineData.add(Pair(below.map { it.timeFloat }, below.map { it.value }))
        lineColors.add(BelowRange)
    }

    val inRange = filteredValues.filter { it.value in 70..180 }
    if (inRange.isNotEmpty()) {
        lineData.add(Pair(inRange.map { it.timeFloat }, inRange.map { it.value }))
        lineColors.add(InRange)
    }

    val above = filteredValues.filter { it.value > 180 }
    if (above.isNotEmpty()) {
        lineData.add(Pair(above.map { it.timeFloat }, above.map { it.value }))
        lineColors.add(AboveRange)
    }

    LaunchedEffect(filteredValues) {
        if (filteredValues.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    lineData.forEach { (x, y) ->
                        series(x, y)
                    }
                }
            }
        }
    }

    if (lineColors.isNotEmpty()) {
        val chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    lines = lineColors.map { color ->
                        rememberLine(
                            pointProvider = LineCartesianLayer.PointProvider.single(
                                rememberPoint(
                                    component = ShapeComponent(
                                        shape = Shape.Pill,
                                        color = color.toArgb()
                                    ),
                                    size = 4.dp
                                )
                            ),
                            thickness = 0.dp,
                            fill = LineCartesianLayer.LineFill.single(fill = fill(Color.Transparent))
                        )
                    }
                ),
                verticalAxisPosition = Position.Vertical.Start,
                axisValueOverrider = AxisValueOverrider.fixed(
                    minX = oneDayAgo.toDouble(),
                    maxX = currentTime.toDouble(),
                    minY = minY.toDouble(),   // fixed vertical axis minimum
                    maxY = maxY.toDouble()    // fixed vertical axis maximum
                )
            ),
            startAxis = rememberStartAxis(
                horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside,
                guideline = LineComponent(
                    color = MaterialTheme.colorScheme.onSurface.toArgb(),
                    thicknessDp = .1f
                ),
                itemPlacer = remember { CustomAxisItemPlacer() }
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
            getXStep = { 3600000.0 }, // 1 hour in ms
            marker = rememberDefaultCartesianMarker(
                label = TextComponent(
                    color = MaterialTheme.colorScheme.onBackground.toArgb(),
                    textSizeSp = 10f
                ),
                labelPosition = DefaultCartesianMarker.LabelPosition.AbovePoint,
                indicatorSize = 10.dp,
                guideline = LineComponent(
                    color = MaterialTheme.colorScheme.onBackground.toArgb(),
                    thicknessDp = .5f
                ),
                valueFormatter = DefaultCartesianMarkerValueFormatter(
                    decimalFormat = DecimalFormat("#.## mg/dl"),
                    colorCode = false
                )
            )
        )

        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            zoomState = rememberVicoZoomState(zoomEnabled = true, initialZoom = { _, _, _ -> 2f }), // Zoom.Content for default zoom
            scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.Companion.End)
        )
    }
}
