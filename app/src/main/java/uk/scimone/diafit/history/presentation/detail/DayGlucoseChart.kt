package uk.scimone.diafit.history.presentation.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.history.domain.model.GlucoseSample
import uk.scimone.diafit.history.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.domain.model.GlucoseZone
import uk.scimone.diafit.history.domain.model.READING_GAP_MS
import uk.scimone.diafit.history.domain.model.zoneOf
import uk.scimone.diafit.journal.presentation.components.MealAvatar
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange
import uk.scimone.diafit.ui.theme.targetRangeBandColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val PLOT_HEIGHT = 200.dp
private val LANE_GAP = 10.dp
private val LANE_HEIGHT = 22.dp
private val AXIS_HEIGHT = 20.dp
private val PIN_SIZE = 40.dp
private val PIN_GAP = 6.dp
private const val HOUR_MS = 3_600_000L
private const val Y_MIN = 40f
/** The cursor snaps to a reading this close; further away it shows the time only. */
private const val SNAP_MS = 10 * 60_000L
/** Treatments this close to the cursor are listed in the readout. */
private const val NEARBY_MS = 15 * 60_000L
private const val CARBS_FULL_SIZE_G = 100f
private const val INSULIN_FULL_HEIGHT_U = 10f

fun zoneColor(zone: GlucoseZone): Color = when (zone) {
    GlucoseZone.VERY_LOW, GlucoseZone.LOW -> BelowRange
    GlucoseZone.IN_RANGE -> InRange
    GlucoseZone.HIGH, GlucoseZone.VERY_HIGH -> AboveRange
}

/**
 * The whole day on one 0–24 h axis: glucose trace coloured by range over the target band, a carb lane
 * and an insulin lane underneath, and the meals' photos pinned at their time (tap one to open the meal).
 * Tap the chart to place a cursor, or touch and hold and drag to scrub; the readout above shows the
 * value and the treatments around it.
 */
@Composable
fun DayGlucoseChart(
    readings: List<GlucoseSample>,
    boluses: List<BolusEntity>,
    meals: List<DayMealUi>,
    thresholds: GlucoseThresholds,
    dayStartUtc: Long,
    dayEndUtc: Long,
    onMealClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    nowUtc: Long = System.currentTimeMillis()
) {
    val span = (dayEndUtc - dayStartUtc).toFloat()
    var cursorUtc by remember(dayStartUtc) { mutableStateOf<Long?>(null) }
    val haptic = LocalHapticFeedback.current
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val snapped = cursorUtc?.let { t -> readings.minByOrNull { abs(it.timeUtc - t) }?.takeIf { abs(it.timeUtc - t) <= SNAP_MS } }

    Column(modifier) {
        CursorReadout(cursorUtc, snapped, boluses, meals, thresholds, clock)
        Spacer(Modifier.height(6.dp))

        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        val cursorColor = MaterialTheme.colorScheme.onSurface
        val bandColor = targetRangeBandColor()
        val futureWash = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
        val measurer = rememberTextMeasurer()
        val axisStyle = TextStyle(color = labelColor, fontSize = 10.sp)
        val yStyle = TextStyle(color = labelColor.copy(alpha = 0.8f), fontSize = 9.sp)
        val laneLabelStyle = TextStyle(color = labelColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        val yMax = max(250f, ceil(((readings.maxOfOrNull { it.mgdl } ?: 0) + 10) / 50f) * 50f)
        val totalHeight = PLOT_HEIGHT + LANE_GAP + LANE_HEIGHT * 2 + AXIS_HEIGHT

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .pointerInput(dayStartUtc) {
                    detectTapGestures { offset ->
                        val t = dayStartUtc + (offset.x / size.width * span).toLong()
                        val current = cursorUtc
                        val nearCurrent = current != null && abs((current - dayStartUtc) / span * size.width - offset.x) < 24.dp.toPx()
                        cursorUtc = if (nearCurrent) null else t
                    }
                }
                .pointerInput(dayStartUtc) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            cursorUtc = dayStartUtc + (offset.x / size.width * span).toLong()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val x = change.position.x.coerceIn(0f, size.width.toFloat())
                            cursorUtc = dayStartUtc + (x / size.width * span).toLong()
                        }
                    )
                }
        ) {
            val plotH = PLOT_HEIGHT.toPx()
            fun x(t: Long) = (t - dayStartUtc) / span * size.width
            val topInset = 12.dp.toPx()
            fun y(v: Float) = plotH - (min(v, yMax) - Y_MIN) / (yMax - Y_MIN) * (plotH - topInset)
            val carbLaneTop = plotH + LANE_GAP.toPx()
            val insulinLaneTop = carbLaneTop + LANE_HEIGHT.toPx()
            val axisTop = insulinLaneTop + LANE_HEIGHT.toPx()

            // Target band and the "very" limits
            drawRect(bandColor, Offset(0f, y(thresholds.high.toFloat())), Size(size.width, y(thresholds.low.toFloat()) - y(thresholds.high.toFloat())))
            listOf(thresholds.veryLow, thresholds.veryHigh).forEach { v ->
                drawLine(gridColor, Offset(0f, y(v.toFloat())), Offset(size.width, y(v.toFloat())), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            }

            // Hour grid across plot and lanes, labels every 3 h
            for (h in 0..24 step 3) {
                val gx = h / 24f * size.width
                if (h in 1..23) drawLine(gridColor, Offset(gx, 0f), Offset(gx, axisTop), 1.dp.toPx())
                if (h % 6 != 0) continue
                val label = measurer.measure("%02d:00".format(h), axisStyle)
                val lx = (gx - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(lx, axisTop + 4.dp.toPx()))
            }
            // Lane separators
            drawLine(gridColor, Offset(0f, carbLaneTop - LANE_GAP.toPx() / 2), Offset(size.width, carbLaneTop - LANE_GAP.toPx() / 2), 1.dp.toPx())

            // Today: wash over the part of the day that hasn't happened yet, line at now
            if (nowUtc in dayStartUtc until dayEndUtc) {
                val nx = x(nowUtc)
                drawRect(futureWash, Offset(nx, 0f), Size(size.width - nx, axisTop))
                drawLine(labelColor.copy(alpha = 0.6f), Offset(nx, 0f), Offset(nx, axisTop), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            }

            // Y labels: target bounds and the very-high line
            listOf(thresholds.low, thresholds.high, thresholds.veryHigh).forEach { v ->
                val label = measurer.measure("$v", yStyle)
                drawText(label, topLeft = Offset(3.dp.toPx(), y(v.toFloat()) - label.size.height - 1.dp.toPx()))
            }

            // Glucose trace, each segment coloured by the zone it ends in, broken at sensor gaps
            val stroke = 2.dp.toPx()
            readings.forEachIndexed { i, r ->
                val prev = readings.getOrNull(i - 1)
                val next = readings.getOrNull(i + 1)
                val color = zoneColor(thresholds.zoneOf(r.mgdl))
                if (prev != null && r.timeUtc - prev.timeUtc <= READING_GAP_MS) {
                    drawLine(color, Offset(x(prev.timeUtc), y(prev.mgdl.toFloat())), Offset(x(r.timeUtc), y(r.mgdl.toFloat())), stroke, StrokeCap.Round)
                } else if (next == null || next.timeUtc - r.timeUtc > READING_GAP_MS) {
                    drawCircle(color, stroke, Offset(x(r.timeUtc), y(r.mgdl.toFloat())))
                }
            }

            // Carb lane: a dot per meal (close ones merged), area ∝ grams, with its value
            var labelEnd = -1f
            val clusters = carbClusters(meals)
            val cy = carbLaneTop + LANE_HEIGHT.toPx() / 2
            fun radius(g: Int) = (3.dp.toPx() + 6.dp.toPx() * sqrt(min(g / CARBS_FULL_SIZE_G, 1.5f))).coerceAtMost(LANE_HEIGHT.toPx() / 2)
            clusters.forEachIndexed { i, (time, g) ->
                val cx = x(time)
                val r = radius(g)
                drawCircle(Carbs, r, Offset(cx, cy))
                // A label may run up to the next dot, not over it.
                val limit = clusters.getOrNull(i + 1)?.let { (t, ng) -> x(t) - radius(ng) - 2.dp.toPx() } ?: size.width
                if (g > 0) labelEnd = drawLaneLabel(measurer, "${g}g", laneLabelStyle, cx + r + 2.dp.toPx(), cy, labelEnd, limit)
            }

            // Insulin lane: bars ∝ units (SMBs thin and faded), boluses labelled
            labelEnd = -1f
            val laneBottom = insulinLaneTop + LANE_HEIGHT.toPx() - 3.dp.toPx()
            val maxBar = LANE_HEIGHT.toPx() - 6.dp.toPx()
            boluses.sortedBy { it.timestampUtc }.forEach { b ->
                val bx = x(b.timestampUtc)
                val h = (maxBar * min(b.value / INSULIN_FULL_HEIGHT_U, 1f)).coerceAtLeast(3.dp.toPx())
                val w = if (b.isSmb) 2.dp.toPx() else 4.dp.toPx()
                drawRoundRect(
                    if (b.isSmb) Bolus.copy(alpha = 0.55f) else Bolus,
                    Offset(bx - w / 2, laneBottom - h), Size(w, h), CornerRadius(w / 2)
                )
                if (!b.isSmb && b.value >= 0.1f) {
                    labelEnd = drawLaneLabel(measurer, "${formatUnits(b.value.toDouble())}U", laneLabelStyle, bx + w, laneBottom - maxBar / 2, labelEnd)
                }
            }

            // Cursor
            cursorUtc?.let { t ->
                val cx = x(snapped?.timeUtc ?: t)
                drawLine(cursorColor.copy(alpha = 0.7f), Offset(cx, 0f), Offset(cx, axisTop), 1.dp.toPx())
                snapped?.let { s ->
                    val p = Offset(cx, y(s.mgdl.toFloat()))
                    drawCircle(zoneColor(thresholds.zoneOf(s.mgdl)), 5.dp.toPx(), p)
                    drawCircle(cursorColor, 5.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
                }
            }
        }

        if (meals.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            MealPins(meals, dayStartUtc, span, onMealClick)
        }
    }
}

/** Meals within [CARB_MERGE_MS] of each other share one dot (first meal's time, summed grams), so labels don't collide. */
private fun carbClusters(meals: List<DayMealUi>): List<Pair<Long, Int>> {
    val result = mutableListOf<Pair<Long, Int>>()
    var lastTime = Long.MIN_VALUE
    meals.sortedBy { it.meal.mealTimeUtc }.forEach { m ->
        val t = m.meal.mealTimeUtc
        if (result.isNotEmpty() && t - lastTime <= CARB_MERGE_MS) {
            result[result.lastIndex] = result.last().let { it.first to it.second + m.meal.carbohydrates }
        } else {
            result += t to m.meal.carbohydrates
        }
        lastTime = t
    }
    return result
}

/** Same gap the overview uses to merge treatments into one block. */
private const val CARB_MERGE_MS = uk.scimone.diafit.history.domain.usecase.ClusterTreatmentsUseCase.MAX_GAP_MS

/** Draws [text] left-aligned at [x], vertically centred on [cy], unless it would overlap the previous label or pass [limit]. Returns the new right edge. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLaneLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    cy: Float,
    previousEnd: Float,
    limit: Float = size.width
): Float {
    val layout = measurer.measure(text, style)
    if (x < previousEnd + 2.dp.toPx() || x + layout.size.width > min(limit, size.width)) return previousEnd
    drawText(layout, topLeft = Offset(x, cy - layout.size.height / 2f))
    return x + layout.size.width
}

/** Each meal's photo (or type tile) under the axis at its time. Overlapping pins move to a second row. */
@Composable
private fun MealPins(meals: List<DayMealUi>, dayStartUtc: Long, span: Float, onMealClick: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        val placed = remember(meals, width) {
            val rowEnds = floatArrayOf(-1e9f, -1e9f)
            meals.sortedBy { it.meal.mealTimeUtc }.map { m ->
                val center = (m.meal.mealTimeUtc - dayStartUtc) / span * width.value
                val left = (center - PIN_SIZE.value / 2).coerceIn(0f, width.value - PIN_SIZE.value)
                val row = rowEnds.indexOfFirst { left >= it + PIN_GAP.value }.takeIf { it >= 0 } ?: 0
                rowEnds[row] = left + PIN_SIZE.value
                Triple(m, left, row)
            }
        }
        val rows = (placed.maxOfOrNull { it.third } ?: 0) + 1
        Box(Modifier.fillMaxWidth().height(PIN_SIZE * rows + PIN_GAP * (rows - 1) + 4.dp)) {
            placed.forEach { (m, left, row) ->
                MealAvatar(
                    m.meal,
                    size = PIN_SIZE,
                    modifier = Modifier
                        .offset(x = Dp(left), y = (PIN_SIZE + PIN_GAP) * row)
                        .clip(RoundedCornerShape(PIN_SIZE * 0.28f))
                        .clickable { onMealClick(m.meal.id) }
                )
            }
        }
    }
}

@Composable
private fun CursorReadout(
    cursorUtc: Long?,
    snapped: GlucoseSample?,
    boluses: List<BolusEntity>,
    meals: List<DayMealUi>,
    thresholds: GlucoseThresholds,
    clock: SimpleDateFormat
) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        if (cursorUtc == null) {
            Text(
                "Tap the chart, or touch and hold to scrub",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            return@Row
        }
        val t = snapped?.timeUtc ?: cursorUtc
        Text(clock.format(Date(t)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        if (snapped != null) {
            Text(
                "${snapped.mgdl}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = zoneColor(thresholds.zoneOf(snapped.mgdl))
            )
            Text(" mg/dL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("No reading", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val carbs = meals.filter { abs(it.meal.mealTimeUtc - t) <= NEARBY_MS }.sumOf { it.meal.carbohydrates }
        val units = boluses.filter { abs(it.timestampUtc - t) <= NEARBY_MS }.sumOf { it.value.toDouble() }
        if (carbs > 0) {
            Spacer(Modifier.width(12.dp))
            Text("$carbs g", style = MaterialTheme.typography.labelLarge, color = Carbs)
        }
        if (units > 0.05) {
            Spacer(Modifier.width(12.dp))
            Text("${formatUnits(units)} U", style = MaterialTheme.typography.labelLarge, color = Bolus)
        }
    }
}

/** "4", "4.5": one decimal, without a trailing ".0". */
fun formatUnits(units: Double): String {
    val r = Math.round(units * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}
