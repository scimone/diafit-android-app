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
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import java.util.Calendar

/** How far past "now" every Home chart's x axis extends, so data never touches the right edge. */
const val TIME_AXIS_FUTURE_HOURS = 2

/** "Now" floored to the minute. */
fun currentMinute(): Long = System.currentTimeMillis() / 60_000L * 60_000L

/**
 * Returns aligned time boundaries (minX, alignedMaxX, realTime) for the given [nowMinute]. It MUST be
 * computed once by the screen and handed to every chart: charts recompose at different moments
 * (the CGM chart with each new reading, the others rarely), so a per-chart "now" drifts apart and
 * the stacked time axes stop lining up.
 */
fun getTimeAxisBounds(nowMinute: Long, hoursBack: Int = 24): Triple<Long, Long, Long> {
    val realTime = nowMinute
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

/**
 * The x range a stack of time charts shares: [minX]..[maxX]. Data is shown up to [now]; anything
 * after it (insulin/carb activity tails) is drawn as a faded forecast. Every chart of one stack must
 * get the same instance, see [getTimeAxisBounds].
 */
data class ChartTimeWindow(val minX: Long, val maxX: Long, val now: Long)

/** Home's window: the last 24 h (from a whole hour) up to [nowMinute], plus [TIME_AXIS_FUTURE_HOURS] of headroom. */
fun homeTimeWindow(nowMinute: Long): ChartTimeWindow {
    val (minX, _, realTime) = getTimeAxisBounds(nowMinute, hoursBack = 24)
    return ChartTimeWindow(minX, timeAxisMaxX(realTime), realTime)
}

/** A whole past (or the current) day: data up to the end of the day, or up to now while it is still running. */
fun dayTimeWindow(dayStartUtc: Long, dayEndUtc: Long, nowMinute: Long): ChartTimeWindow =
    ChartTimeWindow(dayStartUtc, dayEndUtc, nowMinute.coerceIn(dayStartUtc, dayEndUtc))

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
val SharedStartAxisSize: BaseAxis.Size = BaseAxis.Size.Fixed(4.dp)

val ChartPointSize: Dp = 6.dp
/** Same x-spacing (pixels per x step at zoom 1) on every chart's layers, or their zoom scales differ. */
val ChartXSpacing: Dp = 38.dp
val ChartPointSpacing: Dp = ChartXSpacing - ChartPointSize

/** All Home charts are line layers with the same point size, so no extra padding is needed. */
val LineChartLayerPadding = CartesianLayerPadding(unscalableEnd = -ChartPointSize / 2)

/**
 * Provides a reusable BottomAxis with time labels, guidelines, and default settings
 */
/** Timestamps under the charts are switched off for now; flip to bring them (and the carb panel's label strip) back. */
const val SHOW_TIME_LABELS = false

@Composable
fun rememberTimeBottomAxis(showLabels: Boolean = true, showLine: Boolean = true): HorizontalAxis<Axis.Position.Horizontal.Bottom> {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return HorizontalAxis.rememberBottom(
        line = null,
        guideline = null,
        label = if (showLabels && SHOW_TIME_LABELS) {
            rememberAxisLabelComponent(
                style = TextStyle(color = onSurface, fontSize = 10.sp, textAlign = TextAlign.Center)
            )
        } else null,
        // Same tick component on every chart (its thickness feeds the layer margin, which must match
        // for the stacked axes to align); it is just zero-length where labels are hidden.
        tick = rememberAxisTickComponent(),
        tickLength = if (showLabels && SHOW_TIME_LABELS) 4.dp else 0.dp,
        itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(addExtremeLabelPadding = false) },
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
