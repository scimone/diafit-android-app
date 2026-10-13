package uk.scimone.diafit.dailyrecap.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import uk.scimone.diafit.dailyrecap.domain.DailyRecap
import uk.scimone.diafit.dailyrecap.domain.RecapVisual
import uk.scimone.diafit.dailyrecap.domain.VisualPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Draws the recap's picture: the day's glucose with the target band, meals and boluses underneath, the moments the
 * recap talks about as numbered badges, and the time-in-range bar. It has its own dark card so it reads the same in
 * the notification shade and in the app, in either theme.
 */
object RecapImage {
    const val WIDTH = 1080
    const val HEIGHT = 620

    private val BG = Color.parseColor("#151A22")
    private val GRID = Color.parseColor("#2A313D")
    private val TEXT = Color.parseColor("#F1F4F8")
    private val MUTED = Color.parseColor("#9AA4B2")
    private val IN_RANGE = Color.parseColor("#2ECC9B")
    private val BELOW = Color.parseColor("#EF6C5B")
    private val VERY_LOW = Color.parseColor("#C0392B")
    private val ABOVE = Color.parseColor("#9AA5FF")
    private val VERY_HIGH = Color.parseColor("#7C6CF0")
    private val BOLUS = Color.parseColor("#42A5F5")
    private val CARBS = Color.parseColor("#F59E0B")
    private val ACTIVITY = Color.parseColor("#E0489E")
    private const val VERY_LOW_MGDL = 54
    private const val VERY_HIGH_MGDL = 250

    /** Circled digits, so the lines of the text match the badges on the chart. */
    fun badge(n: Int): String = if (n in 1..9) ('①' + (n - 1)).toString() else "($n)"

    fun render(recap: DailyRecap, zone: ZoneId = ZoneId.systemDefault()): Bitmap {
        val v = recap.visual
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRoundRect(RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()), 44f, 44f, fill(BG))

        header(c, recap, zone)

        // Chart area.
        val left = 86f; val right = WIDTH - 36f; val top = 138f; val bottom = 392f
        val dataMax = v.points.maxOfOrNull { it.g } ?: v.highMgdl
        val dataMin = v.points.minOfOrNull { it.g } ?: v.lowMgdl
        val yMin = minOf(40, dataMin - 10).toFloat()
        val yMax = maxOf(250, dataMax + 15).coerceAtMost(400).toFloat()
        fun x(t: Long) = left + (right - left) * ((t - v.dayStartUtc).toFloat() / (v.dayEndUtc - v.dayStartUtc))
        fun y(g: Int) = bottom - (bottom - top) * ((g - yMin) / (yMax - yMin)).coerceIn(0f, 1f)

        // Target band and the hours not yet lived.
        c.drawRect(left, y(v.highMgdl), right, y(v.lowMgdl), fill(withAlpha(IN_RANGE, 0.13f)))
        if (v.shownUntilUtc < v.dayEndUtc) c.drawRect(x(v.shownUntilUtc), top, right, bottom, fill(withAlpha(Color.WHITE, 0.04f)))
        val dash = stroke(withAlpha(IN_RANGE, 0.55f), 2f).apply { pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f) }
        c.drawLine(left, y(v.highMgdl), right, y(v.highMgdl), dash)
        c.drawLine(left, y(v.lowMgdl), right, y(v.lowMgdl), dash)
        val axis = text(MUTED, 24f)
        axis.textAlign = Paint.Align.RIGHT
        c.drawText(v.highMgdl.toString(), left - 12f, y(v.highMgdl) + 8f, axis)
        c.drawText(v.lowMgdl.toString(), left - 12f, y(v.lowMgdl) + 8f, axis)

        // Hour grid: every six hours of the local day.
        val hourLabel = text(MUTED, 24f).apply { textAlign = Paint.Align.CENTER }
        val dayStart = Instant.ofEpochMilli(v.dayStartUtc).atZone(zone)
        for (h in listOf(0, 6, 12, 18, 24)) {
            val t = dayStart.plusHours(h.toLong()).toInstant().toEpochMilli()
            val gx = x(t.coerceAtMost(v.dayEndUtc))
            c.drawLine(gx, top, gx, bottom, stroke(GRID, 1.5f))
            c.drawText("%02d".format(h), gx.coerceIn(left + 10f, right - 10f), bottom + 32f, hourLabel)
        }

        // Workouts as a strip along the bottom of the chart.
        v.workouts.forEach { w ->
            c.drawRoundRect(RectF(x(w.from), bottom - 14f, maxOf(x(w.to), x(w.from) + 6f), bottom), 7f, 7f, fill(withAlpha(ACTIVITY, 0.85f)))
        }

        // Glucose line, coloured by zone; a sensor gap breaks it.
        val line = stroke(IN_RANGE, 6f).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        v.points.zipWithNext().forEach { (a, b) ->
            if (b.t - a.t > 35 * 60_000L) return@forEach
            line.color = zoneColor((a.g + b.g) / 2, v)
            c.drawLine(x(a.t), y(a.g), x(b.t), y(b.g), line)
        }

        // Meals and boluses.
        val lane1 = 452f
        val lane2 = 522f
        c.drawText("g", 40f, lane1 + 8f, text(CARBS, 26f).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER })
        c.drawText("U", 40f, lane2 + 8f, text(BOLUS, 26f).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER })
        val small = text(TEXT, 22f).apply { textAlign = Paint.Align.CENTER }
        var lastLabel = -1000f
        v.meals.sortedBy { it.t }.forEach { m ->
            val r = 12f + sqrt(m.carbsG.toFloat()) * 1.5f
            c.drawCircle(x(m.t), lane1, r, fill(withAlpha(CARBS, 0.9f)))
            if (x(m.t) - lastLabel > 70f) {
                small.color = Color.parseColor("#1B1200")
                val label = text(Color.parseColor("#1B1200"), 20f).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
                if (r >= 20f) c.drawText(m.carbsG.toString(), x(m.t), lane1 + 7f, label)
                else { small.color = MUTED; c.drawText(m.carbsG.toString(), x(m.t), lane1 + r + 22f, small) }
                lastLabel = x(m.t)
            }
        }
        lastLabel = -1000f
        v.boluses.sortedBy { it.t }.forEach { b ->
            val h = 8f + sqrt(b.units) * 14f
            c.drawRoundRect(RectF(x(b.t) - 4f, lane2 + 14f - h, x(b.t) + 4f, lane2 + 14f), 4f, 4f, fill(BOLUS))
            if (x(b.t) - lastLabel > 64f && b.units >= 1f) {
                small.color = MUTED
                c.drawText(trimUnits(b.units), x(b.t), lane2 + 14f - h - 8f, small)
                lastLabel = x(b.t)
            }
        }

        // Numbered moments (went well, then went wrong, matching the text).
        val moments = recap.wentWell.map { it to true } + recap.wentWrong.map { it to false }
        moments.forEachIndexed { i, (point, good) ->
            val t = point.timeUtc ?: return@forEachIndexed
            val g = glucoseAt(v.points, t) ?: return@forEachIndexed
            val px = x(t).coerceIn(left + 18f, right - 18f)
            val py = (y(g) - 36f).coerceAtLeast(top + 20f).let { if (y(g) - it < 26f) y(g) + 36f else it }
            c.drawLine(px, y(g), px, py, stroke(withAlpha(Color.WHITE, 0.55f), 2f))
            c.drawCircle(px, py, 19f, fill(if (good) IN_RANGE else CARBS))
            c.drawCircle(px, py, 19f, stroke(BG, 3f))
            val n = text(Color.parseColor("#0E1217"), 26f).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
            c.drawText((i + 1).toString(), px, py + 9f, n)
        }

        rangeBar(c, v, left, right, 568f)
        return bmp
    }

    private fun header(c: Canvas, recap: DailyRecap, zone: ZoneId) {
        val v = recap.visual
        val date = Instant.ofEpochMilli(v.dayStartUtc).atZone(zone).toLocalDate()
        val title = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(date)
        c.drawText(title, 40f, 66f, text(TEXT, 40f).apply { typeface = Typeface.DEFAULT_BOLD })
        if (recap.partialDay) {
            val until = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
                .format(Instant.ofEpochMilli(v.shownUntilUtc).atZone(zone))
            c.drawText("→ $until", 40f, 104f, text(MUTED, 26f))
        }
        val tir = text(IN_RANGE, 60f).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        c.drawText("${(v.inRange * 100).roundToInt()}%", WIDTH - 40f, 70f, tir)
        v.baselineInRange?.let { base ->
            val delta = ((v.inRange - base) * 100).roundToInt()
            val sign = when { delta > 0 -> "▲ +$delta"; delta < 0 -> "▼ $delta"; else -> "= 0" }
            val color = when { delta >= 3 -> IN_RANGE; delta <= -3 -> CARBS; else -> MUTED }
            c.drawText("$sign", WIDTH - 40f, 104f, text(color, 26f).apply { textAlign = Paint.Align.RIGHT; typeface = Typeface.DEFAULT_BOLD })
        }
    }

    /** Stacked very low / low / in range / high / very high bar with the larger shares written in it. */
    private fun rangeBar(c: Canvas, v: RecapVisual, left: Float, right: Float, top: Float) {
        val colors = listOf(VERY_LOW, BELOW, IN_RANGE, ABOVE, VERY_HIGH)
        val shares = v.zoneShares.takeIf { it.size == 5 && it.sum() > 0f } ?: return
        val clip = Path().apply { addRoundRect(RectF(left, top, right, top + 34f), 17f, 17f, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        var x0 = left
        val label = text(Color.parseColor("#0E1217"), 22f).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        val total = shares.sum()
        shares.forEachIndexed { i, s ->
            val w = (right - left) * s / total
            c.drawRect(x0, top, x0 + w, top + 34f, fill(colors[i]))
            if (s >= 0.08f) c.drawText("${(s * 100).roundToInt()}", x0 + w / 2, top + 25f, label)
            x0 += w
        }
        c.restore()
    }

    private fun zoneColor(g: Int, v: RecapVisual) = when {
        g < VERY_LOW_MGDL -> VERY_LOW
        g < v.lowMgdl -> BELOW
        g > VERY_HIGH_MGDL -> VERY_HIGH
        g > v.highMgdl -> ABOVE
        else -> IN_RANGE
    }

    /** The glucose at [t], interpolated between the neighbouring points; null when the nearest data is over half an hour away. */
    internal fun glucoseAt(points: List<VisualPoint>, t: Long): Int? {
        val next = points.indexOfFirst { it.t >= t }
        val a = if (next <= 0) points.firstOrNull() else points[next - 1]
        val b = if (next == -1) points.lastOrNull() else points[next]
        if (a == null || b == null) return null
        val nearest = minOf(abs(a.t - t), abs(b.t - t))
        if (nearest > 30 * 60_000L) return null
        if (a.t == b.t || t <= a.t) return a.g
        if (t >= b.t) return b.g
        return (a.g + (b.g - a.g) * ((t - a.t).toFloat() / (b.t - a.t))).roundToInt()
    }

    private fun trimUnits(u: Float) = if (u % 1f == 0f) u.toInt().toString() else "%.1f".format(Locale.ROOT, u)
    private fun withAlpha(color: Int, alpha: Float) = Color.argb((alpha * 255).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))
    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = width }
    private fun text(color: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = Typeface.DEFAULT }
}
