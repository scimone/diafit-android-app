package uk.scimone.diafit.home.presentation.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import uk.scimone.diafit.ui.theme.targetRangeBandColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import uk.scimone.diafit.home.presentation.utils.ChartGeometry
import uk.scimone.diafit.home.presentation.utils.GeometryProbe
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import uk.scimone.diafit.home.presentation.components.util.CustomCgmAxisItemPlacer
import uk.scimone.diafit.home.presentation.model.CgmChartData
import uk.scimone.diafit.home.presentation.utils.ChartPointSize
import uk.scimone.diafit.home.presentation.utils.ChartPointSpacing
import uk.scimone.diafit.home.presentation.utils.LineChartLayerPadding
import uk.scimone.diafit.home.presentation.utils.SharedStartAxisSize
import uk.scimone.diafit.home.presentation.utils.createTimeAxisRangeProvider
import uk.scimone.diafit.home.presentation.utils.ChartTimeWindow
import uk.scimone.diafit.home.presentation.utils.getTimeAxisXStep
import uk.scimone.diafit.home.presentation.utils.rememberTimeBottomAxis
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.home.presentation.utils.TargetRangeDecoration
import uk.scimone.diafit.ui.theme.InRange

/** Fixed glucose (y) range of the CGM panel, mg/dL. */
const val CGM_MIN_Y = 40f
const val CGM_MAX_Y = 250f

/** The top of the glucose axis for the readings shown: 250, or the next 50 above the highest reading. */
fun cgmMaxY(values: List<CgmChartData>, window: ChartTimeWindow): Float {
    val peak = values.filter { it.timeLong in window.minX..window.now }.maxOfOrNull { it.value } ?: return CGM_MAX_Y
    return maxOf(CGM_MAX_Y, kotlin.math.ceil(peak / 50f) * 50f)
}

/**
 * The glucose panel. It has no Vico marker: inspecting values is handled by HomeScreen's own gesture
 * layer (so a drag can pan without also moving a tooltip), which needs [onGeometry] to map x <-> time.
 */
@Composable
fun ComponentCgmChart(
    values: List<CgmChartData>,
    lowerBound: Int,
    upperBound: Int,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    window: ChartTimeWindow,
    onGeometry: (ChartGeometry) -> Unit,
    showTimeLabels: Boolean = false
) {
    val minY = CGM_MIN_Y
    val maxY = cgmMaxY(values, window)
    val modelProducer = remember { CartesianChartModelProducer() }

    val filteredValues = values.filter {
        it.timeLong in window.minX..window.now
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

    // Without a single reading there would be no chart, so no geometry for the panels that are drawn
    // through it (activity); a hidden point keeps the time axis alive on days with no glucose data.
    if (lineColors.isEmpty()) {
        lineData.add(Pair(listOf(window.minX), listOf(minY.toInt())))
        lineColors.add(Color.Transparent)
    }

    LaunchedEffect(filteredValues) {
        modelProducer.runTransaction {
            lineSeries {
                lineData.forEach { (x, y) ->
                    series(x, y)
                }
            }
        }
    }

    if (lineColors.isNotEmpty()) {
        val targetBand = targetRangeBandColor()
        val onBackground = MaterialTheme.colorScheme.onBackground
        val chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    lineColors.map { color ->
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
                            pointProvider = LineCartesianLayer.PointProvider.single(
                                LineCartesianLayer.Point(
                                    rememberShapeComponent(fill = Fill(color), shape = CircleShape),
                                    ChartPointSize
                                )
                            )
                        )
                    }
                ),
                verticalAxisPosition = Axis.Position.Vertical.Start,
                pointSpacing = ChartPointSpacing,
                rangeProvider = createTimeAxisRangeProvider(
                    minX = window.minX,
                    maxX = window.maxX,
                    minY = minY.toDouble(),
                    maxY = maxY.toDouble()
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                line = null,
                tick = null,
                guideline = null,
                label = null,
                itemPlacer = remember { CustomCgmAxisItemPlacer(lowerBound.toDouble(), upperBound.toDouble()) },
                size = SharedStartAxisSize,
                horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside
            ),
            bottomAxis = rememberTimeBottomAxis(showLabels = showTimeLabels),
            layerPadding = { LineChartLayerPadding },
            decorations = listOfNotNull(
                TargetRangeDecoration(
                    lower = lowerBound.toDouble(),
                    upper = upperBound.toDouble(),
                    minY = minY.toDouble(),
                    maxY = maxY.toDouble(),
                    color = targetBand
                ),
                remember(onGeometry) { GeometryProbe(onGeometry) }
            ),
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
