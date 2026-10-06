package uk.scimone.diafit.home.presentation.components

import androidx.compose.foundation.shape.CircleShape
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
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import uk.scimone.diafit.core.domain.model.InsulinActivity
import uk.scimone.diafit.home.presentation.model.InsulinActivityChartData
import uk.scimone.diafit.home.presentation.utils.ChartPointSize
import uk.scimone.diafit.home.presentation.utils.ChartPointSpacing
import uk.scimone.diafit.home.presentation.utils.LineChartLayerPadding
import uk.scimone.diafit.home.presentation.utils.SharedStartAxisSize
import uk.scimone.diafit.home.presentation.utils.createTimeAxisRangeProvider
import uk.scimone.diafit.home.presentation.utils.getTimeAxisBounds
import uk.scimone.diafit.home.presentation.utils.getTimeAxisXStep
import uk.scimone.diafit.home.presentation.utils.rememberTimeBottomAxis
import uk.scimone.diafit.home.presentation.utils.timeAxisMaxX
import kotlin.math.abs

@Composable
fun ComponentInsulinActivityChart(
    values: List<InsulinActivityChartData>,
    color: Color,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    // Use reusable time axis helpers
    val (alignedMinTime, _, realTime) = getTimeAxisBounds(hoursBack = 24)

    val timeStepMillis = 5 * 60 * 1000L // 5 min

    val timePoints = generateSequence(alignedMinTime) { prev ->
        val next = prev + timeStepMillis
        if (next > realTime) null else next
    }.toList()

    val activityPoints = timePoints.map { timePoint ->
        val totalActivity = values.sumOf { bolus ->
            val insulinEffect = InsulinActivity.calculate(
                bolusAmount = bolus.value.toDouble(),
                bolusTime = bolus.timeLong,
                time = timePoint
            ).activity
            if (timePoint >= bolus.timeLong && insulinEffect > 0) insulinEffect else 0.0
        }
        InsulinActivityChartPoint(
            time = timePoint,
            activity = totalActivity.toFloat()
        )
    }

    val insulinPoints = values.map { bolus ->
        val nearestPoint = activityPoints.minByOrNull { abs(it.time - bolus.timeLong) }
        InsulinActivityChartPoint(
            time = bolus.timeLong,
            activity = nearestPoint?.activity ?: 0f
        )
    }

    LaunchedEffect(activityPoints, insulinPoints) {
        modelProducer.runTransaction {
            lineSeries {
                // Curve
                series(
                    x = activityPoints.map { it.time },
                    y = activityPoints.map { it.activity }
                )
            }
            lineSeries {
                // Bolus points
                series(
                    x = insulinPoints.map { it.time },
                    y = insulinPoints.map { it.activity }
                )
            }
        }
    }

    if (activityPoints.isNotEmpty()) {
        val onSurface = MaterialTheme.colorScheme.onSurface
        val rangeProvider = createTimeAxisRangeProvider(
            minX = alignedMinTime,
            maxX = timeAxisMaxX(realTime),
            minY = 0.0,
            maxY = (activityPoints.maxOfOrNull { it.activity } ?: 10f) * 1.2
        )
        val lineLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(color))
                )
            ),
            pointSpacing = ChartPointSpacing,
            rangeProvider = rangeProvider
        )

        val pointsLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
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
            ),
            pointSpacing = ChartPointSpacing,
            rangeProvider = rangeProvider
        )

        val chart = rememberCartesianChart(
            lineLayer,
            pointsLayer,
            startAxis = VerticalAxis.rememberStart(
                guideline = rememberLineComponent(fill = Fill(onSurface), thickness = 0.1.dp),
                size = SharedStartAxisSize
            ),
            bottomAxis = rememberTimeBottomAxis(),
            layerPadding = { LineChartLayerPadding },
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

private data class InsulinActivityChartPoint(
    val time: Long,
    val activity: Float
)
