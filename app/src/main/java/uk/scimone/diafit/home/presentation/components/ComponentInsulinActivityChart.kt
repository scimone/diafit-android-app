package uk.scimone.diafit.home.presentation.components

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
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.common.component.LineComponent
import uk.scimone.diafit.core.domain.model.InsulinActivity
import uk.scimone.diafit.home.presentation.model.InsulinActivityChartData
import java.util.Calendar
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.shape.Shape
import kotlin.math.abs

@Composable
fun ComponentInsulinActivityChart(
    values: List<InsulinActivityChartData>,
    color: Color
) {
    val modelProducer = remember { CartesianChartModelProducer.build() }

    val currentTime = System.currentTimeMillis()
    val oneDayAgo = currentTime - 24 * 60 * 60 * 1000

    val timeStepMillis = 5 * 60 * 1000L // 5 min

    val timePoints = generateSequence(oneDayAgo) { prev ->
        val next = prev + timeStepMillis
        if (next > currentTime) null else next
    }.toList()

    val activityPoints = timePoints.map { timePoint ->
        val totalActivity = values.sumOf { bolus ->
            val insulinEffect = InsulinActivity.calculate(
                bolusAmount = bolus.value.toDouble(),
                bolusTime = bolus.timeMillis,
                time = timePoint
            ).activity
            // Only count insulin activity if timePoint is >= bolusTime
            // and insulinEffect is > 0 (active)
            if (timePoint >= bolus.timeMillis && insulinEffect > 0) insulinEffect else 0.0
        }
        InsulinActivityChartPoint(
            time = timePoint,
            activity = totalActivity.toFloat()
        )
    }

    // Points exactly at insulin administration timestamps (x = bolus time, y = total activity at that time)
    val insulinPoints = values.map { bolus ->
        val nearestPoint = activityPoints.minByOrNull { abs(it.time - bolus.timeMillis) }
        InsulinActivityChartPoint(
            time = bolus.timeMillis,   // keep the exact bolus time
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
                )}
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
        val lineLayer = rememberLineCartesianLayer(
            LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(fill(Color(color.toArgb()))))
            )
        )

        val pointsLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                lines = listOf(
                    rememberLine(
                        pointProvider = LineCartesianLayer.PointProvider.single(
                            rememberPoint(
                                component = ShapeComponent(
                                    shape = Shape.Pill,
                                    color = color.toArgb()
                                ),
                                size = 10.dp
                            )
                        ),
                        thickness = 0.dp,
                        fill = LineCartesianLayer.LineFill.single(fill = fill(Color.Transparent))
                    )
                )
            )
        )
        val chart = rememberCartesianChart(
            lineLayer,
            pointsLayer,
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
                    textAlignment = android.text.Layout.Alignment.ALIGN_CENTER,
                    textSize = 10.sp
                ),
                valueFormatter = { value, _, _ ->
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = value.toLong()
                    }
                    val hours = calendar.get(Calendar.HOUR_OF_DAY)
                    val minutes = calendar.get(Calendar.MINUTE)
                    String.format("%02d:%02d", hours, minutes)
                }
            ),
            getXStep = { 3600000.0 }, // 1 hour
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

private data class InsulinActivityChartPoint(
    val time: Long,
    val activity: Float
)

