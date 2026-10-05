package uk.scimone.diafit.home.presentation.utils

import android.text.Layout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.AxisValueOverrider
import com.patrykandpatrick.vico.core.common.component.LineComponent
import java.util.Calendar

/**
 * Returns aligned time boundaries (minX, alignedMaxX, realTime)
 */
fun getTimeAxisBounds(hoursBack: Int = 24): Triple<Long, Long, Long> {
    val realTime = System.currentTimeMillis()
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

/**
 * Provides a reusable AxisValueOverrider for time-based charts
 */
fun createTimeAxisValueOverrider(
    minX: Long,
    maxX: Long,
    minY: Double,
    maxY: Double,
): AxisValueOverrider {
    return AxisValueOverrider.fixed(
        minX = minX.toDouble(),
        maxX = maxX.toDouble(),
        minY = minY,
        maxY = maxY
    )
}

/**
 * Fixed width every Home-screen chart's start (vertical) axis reserves, regardless of its own
 * label content. Without this, each chart's auto-sized axis column ends up a different width
 * (e.g. CGM's "70"/"180"/"250" labels vs. a bolus chart's unit labels), so even though every
 * chart shares the same time range and now scrolls/zooms in sync, their plot areas started at
 * different x pixels and the time gridlines didn't line up when the charts were stacked.
 */
val SharedStartAxisSizeConstraint: BaseAxis.SizeConstraint = BaseAxis.SizeConstraint.Exact(32f)

/**
 * Provides a reusable BottomAxis with time labels, guidelines, and default settings
 */
@Composable
fun rememberTimeBottomAxis(): HorizontalAxis<Axis.Position.Horizontal.Bottom> {
    return rememberBottomAxis(
        guideline = LineComponent(
            color = MaterialTheme.colorScheme.onSurface.toArgb(),
            thicknessDp = .1f
        ),
        label = rememberAxisLabelComponent(
            textAlignment = Layout.Alignment.ALIGN_CENTER,
            textSize = 10.sp
        ),
        itemPlacer = remember {
            HorizontalAxis.ItemPlacer.default(
                addExtremeLabelPadding = true,
                shiftExtremeTicks = true
            )
        },
        valueFormatter = { value, _, _ ->
            val calendar = Calendar.getInstance().apply {
                timeInMillis = value.toLong()
            }
            val hours = calendar.get(Calendar.HOUR_OF_DAY)
            val minutes = calendar.get(Calendar.MINUTE)
            String.format("%02d:%02d", hours, minutes)
        }
    )
}


/**
 * Provides the default X step for time axis: 1 hour in ms
 */
fun getTimeAxisXStep(): Double = 3_600_000.0 // 1 hour
