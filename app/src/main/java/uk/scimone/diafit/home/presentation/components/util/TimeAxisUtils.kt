package uk.scimone.diafit.home.presentation.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import java.util.Calendar

/** How far past "now" every Home chart's x axis extends, so data never touches the right edge. */
const val TIME_AXIS_FUTURE_HOURS = 2

/**
 * Returns aligned time boundaries (minX, alignedMaxX, realTime). [realTime] is floored to the
 * minute so every chart computing it independently in the same frame gets the same value.
 */
fun getTimeAxisBounds(hoursBack: Int = 24): Triple<Long, Long, Long> {
    val realTime = System.currentTimeMillis() / 60_000L * 60_000L
    val alignedMaxTime = Calendar.getInstance().apply {
        timeInMillis = realTime
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val alignedMinTime = Calendar.getInstance().apply {
        timeInMillis = alignedMaxTime
        add(Calendar.HOUR_OF_DAY, -hoursBack)
    }.timeInMillis

    return Triple(alignedMinTime, alignedMaxTime, realTime)
}

/** The x-axis maximum shared by all charts: [TIME_AXIS_FUTURE_HOURS] past [realTime]. */
fun timeAxisMaxX(realTime: Long): Long = realTime + TIME_AXIS_FUTURE_HOURS * 3_600_000L

/** Provides a reusable range provider for time-based charts (fixed x range, fixed y range). */
fun createTimeAxisRangeProvider(
    minX: Long,
    maxX: Long,
    minY: Double,
    maxY: Double,
): CartesianLayerRangeProvider = CartesianLayerRangeProvider.fixed(
    minX = minX.toDouble(),
    maxX = maxX.toDouble(),
    minY = minY,
    maxY = maxY
)

/**
 * Fixed width every Home-screen chart's start (vertical) axis reserves, regardless of its own
 * label content, so the plot areas of the stacked charts start at the same x pixel.
 */
val SharedStartAxisSize: BaseAxis.Size = BaseAxis.Size.Fixed(36.dp)

val ChartPointSize: Dp = 6.dp
val ChartColumnThickness: Dp = 3.dp

/** Same x-spacing (pixels per x step at zoom 1) for line and column layers, or zoom scales differ. */
val ChartXSpacing: Dp = 38.dp
val ChartPointSpacing: Dp = ChartXSpacing - ChartPointSize
val ChartColumnSpacing: Dp = ChartXSpacing - ChartColumnThickness

/**
 * Line layers pad their ends by half a point marker (unscalable); column layers pad by half a
 * column (scalable, i.e. multiplied by zoom). Each layer type is given the *other's* padding via
 * `layerPadding`, so all four charts end up with identical scalable + unscalable padding and the
 * time axes line up exactly at every zoom level.
 */
val LineChartLayerPadding = CartesianLayerPadding(
    scalableStart = ChartColumnThickness / 2,
    scalableEnd = ChartColumnThickness / 2,
)
val ColumnChartLayerPadding = CartesianLayerPadding(
    unscalableStart = ChartPointSize / 2,
    unscalableEnd = ChartPointSize / 2,
)

/**
 * Provides a reusable BottomAxis with time labels, guidelines, and default settings
 */
@Composable
fun rememberTimeBottomAxis(): HorizontalAxis<Axis.Position.Horizontal.Bottom> {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return HorizontalAxis.rememberBottom(
        guideline = rememberLineComponent(fill = Fill(onSurface), thickness = 0.1.dp),
        label = rememberAxisLabelComponent(
            style = TextStyle(
                color = onSurface,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        ),
        itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned() },
        valueFormatter = remember {
            CartesianValueFormatter { _, value, _ ->
                val calendar = Calendar.getInstance().apply { timeInMillis = value.toLong() }
                String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
            }
        }
    )
}

/**
 * Provides the default X step for time axis: 1 hour in ms
 */
fun getTimeAxisXStep(): Double = 3_600_000.0 // 1 hour
