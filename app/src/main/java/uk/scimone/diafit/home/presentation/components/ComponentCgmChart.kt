package uk.scimone.diafit.home.presentation.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import uk.scimone.diafit.home.presentation.utils.SelectionDecoration
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
import uk.scimone.diafit.home.presentation.utils.NowDecoration
import uk.scimone.diafit.home.presentation.utils.SharedStartAxisSize
import uk.scimone.diafit.home.presentation.utils.createTimeAxisRangeProvider
import uk.scimone.diafit.home.presentation.utils.getTimeAxisBounds
import uk.scimone.diafit.home.presentation.utils.getTimeAxisXStep
import uk.scimone.diafit.home.presentation.utils.rememberTimeBottomAxis
import uk.scimone.diafit.home.presentation.utils.timeAxisMaxX
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.InRange

@Composable
fun ComponentCgmChart(
    values: List<CgmChartData>,
    lowerBound: Int,
    upperBound: Int,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    nowMinute: Long,
    selectedTime: Long?,
    onSelectedTimeChange: (Long?) -> Unit,
    showTimeLabels: Boolean = false
) {
    val minY = 40f
    val maxY = 250f
    val modelProducer = remember { CartesianChartModelProducer() }

    val (alignedMinTime, _, realTime) = getTimeAxisBounds(nowMinute, hoursBack = 24)

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
        val onSurface = MaterialTheme.colorScheme.onSurface
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
                    minX = alignedMinTime,
                    maxX = timeAxisMaxX(realTime),
                    minY = minY.toDouble(),
                    maxY = maxY.toDouble()
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                guideline = rememberLineComponent(fill = Fill(onSurface), thickness = 0.1.dp),
                label = rememberAxisLabelComponent(style = TextStyle(color = onSurface.copy(alpha = 0.45f), fontSize = 11.sp)),
                itemPlacer = remember { CustomCgmAxisItemPlacer(lowerBound.toDouble(), upperBound.toDouble()) },
                size = SharedStartAxisSize,
                horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside
            ),
            bottomAxis = rememberTimeBottomAxis(showLabels = showTimeLabels),
            layerPadding = { LineChartLayerPadding },
            decorations = listOfNotNull(
                NowDecoration(
                    nowX = realTime.toDouble(),
                    lineColor = onSurface.copy(alpha = 0.7f),
                    washColor = MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
                ),
                selectedTime?.let { SelectionDecoration(it.toDouble(), onSurface.copy(alpha = 0.9f)) }
            ),
            getXStep = { _ -> getTimeAxisXStep() },
            marker = remember { object : CartesianMarker {} },
            markerVisibilityListener = remember(onSelectedTimeChange) {
                object : CartesianMarkerVisibilityListener {
                    override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) =
                        onSelectedTimeChange(targets.firstOrNull()?.x?.toLong())
                    override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) =
                        onSelectedTimeChange(targets.firstOrNull()?.x?.toLong())
                    override fun onHidden(marker: CartesianMarker) = onSelectedTimeChange(null)
                }
            },
        )

        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            zoomState = zoomState,
            scrollState = scrollState
        )
    }
}
