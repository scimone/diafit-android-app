package uk.scimone.diafit.home.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberPoint
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.HorizontalLayout
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.axis.Axis.Position
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarkerValueFormatter
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shape.Shape
import uk.scimone.diafit.home.presentation.components.util.CustomCgmAxisItemPlacer
import uk.scimone.diafit.home.presentation.model.CgmChartData
import uk.scimone.diafit.home.presentation.utils.createTimeAxisValueOverrider
import uk.scimone.diafit.home.presentation.utils.getTimeAxisBounds
import uk.scimone.diafit.home.presentation.utils.getTimeAxisXStep
import uk.scimone.diafit.home.presentation.utils.rememberTimeBottomAxis
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.InRange
import java.text.DecimalFormat

@Composable
fun ComponentCgmChart(
    values: List<CgmChartData>,
    lowerBound: Int,
    upperBound: Int
) {
    val minY = 40f
    val maxY = 250f
    val modelProducer = remember { CartesianChartModelProducer.build() }

    val (alignedMinTime, alignedMaxTime, realTime) = getTimeAxisBounds(hoursBack = 24)

    val filteredValues = values.filter {
        it.timeLong in alignedMinTime..realTime
    }

    val lineColors = mutableListOf<Color>()
    val lineData = mutableListOf<Pair<List<Long>, List<Int>>>()

    val below = filteredValues.filter { it.value < lowerBound }
    if (below.isNotEmpty()) {
        lineData.add(Pair(below.map { it.timeLong }, below.map { it.value }))
        lineColors.add(BelowRange)
    }

    val inRange = filteredValues.filter { it.value in lowerBound..upperBound }
    if (inRange.isNotEmpty()) {
        lineData.add(Pair(inRange.map { it.timeLong }, inRange.map { it.value }))
        lineColors.add(InRange)
    }

    val above = filteredValues.filter { it.value > upperBound }
    if (above.isNotEmpty()) {
        lineData.add(Pair(above.map { it.timeLong }, above.map { it.value }))
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
                            fill = LineCartesianLayer.LineFill.single(fill = fill(Color.Transparent)),
                            areaFill = LineCartesianLayer.AreaFill.single(fill = fill(Color.Transparent))
                        )
                    }
                ),
                verticalAxisPosition = Position.Vertical.Start,
                axisValueOverrider = createTimeAxisValueOverrider(
                    minX = alignedMinTime,
                    maxX = realTime,
                    minY = minY.toDouble(),
                    maxY = maxY.toDouble()
                )
            ),
            startAxis = rememberStartAxis(
                horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside,
                guideline = LineComponent(
                    color = MaterialTheme.colorScheme.onSurface.toArgb(),
                    thicknessDp = .1f
                ),
                itemPlacer = remember { CustomCgmAxisItemPlacer(lowerBound.toDouble(), upperBound.toDouble()) }
            ),
            bottomAxis = rememberTimeBottomAxis(),
            getXStep = { getTimeAxisXStep() },

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
            ),
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
