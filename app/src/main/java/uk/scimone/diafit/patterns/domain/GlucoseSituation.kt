package uk.scimone.diafit.patterns.domain

import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Everything [GlucoseSituation] looks at. All lists are ascending by time; they may reach further back than
 * the rules need. [history] is an optional, thinned (about 5-minute) multi-day CGM series used only for the
 * "recurs at a consistent time" rules.
 */
class SituationInput(
    val nowUtc: Long,
    val zone: ZoneId,
    val readings: List<CgmEntity>,
    val boluses: List<BolusEntity>,
    val meals: List<MealEntity>,
    val exercise: List<ExerciseEntity> = emptyList(),
    val heartRate: List<HeartRateEntity> = emptyList(),
    val asleepNow: Boolean = false,
    val history: List<CgmEntity> = emptyList(),
    val targetLow: Int = 70,
    val targetHigh: Int = 180
)

/**
 * Names what the glucose is doing *right now* with one phrase from a fixed list, computed from the last hours
 * of CGM readings plus whatever context exists (meals, boluses, workouts, sleep, heart rate).
 *
 * Rules are tried in priority order and the first match wins: safety (lows) first, then the immediate
 * consequences of treatment, exercise and insulin, then meals, time of day, long-running highs, variability and
 * finally calm states. Trends are least-squares slopes in mg/dL per minute; "rapid" is 2 mg/dL/min (about
 * 10 mg/dL per 5 min, a double arrow). A rule that needs data that isn't there (no meals logged, no heart
 * rate) simply does not match. "Glucose rise following interrupted insulin delivery" is not detected: pump
 * suspensions aren't captured reliably enough to tell it from a missed bolus.
 */
object GlucoseSituation {
    private const val MIN = 60_000L
    private const val HYPO = 70
    private const val RAPID = 2.0
    private const val MODERATE = 1.0
    private const val FLAT = 0.5
    private const val FASTING_HIGH = 130
    private const val FASTING_AFTER_MIN = 300
    private const val MEAL_MIN_CARBS = 15
    private const val MAX_READING_AGE_MIN = 15
    private fun sit(key: String, vararg args: Any) = msg("sit_$key", *args)

    private const val MAX_GAP_MS = 10 * MIN
    private const val HIGH_CV = 36.0

    /** Phrase for the situation, or null when nothing fits or the newest reading is stale. */
    fun classify(input: SituationInput): Msg? {
        val c = Context(input)
        if (c.g == null || c.stale || c.r == null) return null
        return rules.firstNotNullOfOrNull { it(c) }
    }

    /** Plain description of the trend for moments no named situation fits (null when there are no fresh readings). */
    fun trendFallback(input: SituationInput): Msg? {
        val c = Context(input)
        val r = c.r ?: return null
        if (c.g == null || c.stale) return null
        val trend = when {
            r >= RAPID -> "trend_rising_fast"
            r >= FLAT -> "trend_rising"
            r <= -RAPID -> "trend_falling_fast"
            r <= -FLAT -> "trend_falling"
            else -> "trend_steady"
        }
        val g = c.g
        val level = when {
            g > 250 -> "level_very_high"
            g > c.high -> "level_high"
            g < 54 -> "level_very_low"
            g < c.low -> "level_low"
            else -> "level_normal"
        }
        return sit("level_trend", ref("sit_$level"), ref("sit_$trend"))
    }

    private val rules: List<(Context) -> Msg?> = listOf(
        ::hypoNow, ::afterLow, ::overcorrection, ::exercise, ::insulin, ::meal, ::dawn, ::overnight,
        ::longHigh, ::fastingHigh, ::stress, ::variability, ::recurrent, ::calm
    )

    // ---- safety ---------------------------------------------------------------------------------------------

    private fun hypoNow(c: Context): Msg? {
        val g = c.g!!
        if (g >= HYPO) return null
        val lowMin = c.streakMin { it < HYPO }
        if (lowMin >= 90) return sit("persistent_hypo")
        val treated = c.meals.any { it.carbohydrates in 5..30 && it.mealTimeUtc >= c.now - lowMin * MIN - 10 * MIN && it.mealTimeUtc <= c.now - 30 * MIN }
        if (treated) return sit("delayed_recovery")
        if (c.night) return sit("nocturnal_hypo")
        if (c.exerciseActive != null) return sit("decline_exercise")
        if (c.exerciseEndedMin in 60..480) return sit("delayed_postex_decline")
        if (c.stacking) return sit("decline_stacking")
        if ((c.mealAgeMin ?: Int.MAX_VALUE) <= 240) return sit("hypo_meal", c.afterMeal)
        if (c.correctionBolusAgeMin != null) return sit("decline_correction")
        return sit("fasting_hypo")
    }

    private fun afterLow(c: Context): Msg? {
        val g = c.g!!
        val low = c.lastLow(3 * 60) ?: return null // (start, end) of the latest hypo episode of >= 15 min
        val sinceEndMin = ((c.now - low.second) / MIN).toInt()
        if (g > c.high) return sit("rebound_hyper")
        if (sinceEndMin <= 90 && c.r!! >= RAPID) return sit("rapid_rise_hypo_treat")
        if (sinceEndMin <= 90 && (low.second - low.first) / MIN >= 45) return sit("delayed_recovery")
        return null
    }

    private fun overcorrection(c: Context): Msg? {
        val g = c.g!!
        val peak = c.window(180).maxOfOrNull { it.valueMgdl } ?: return null
        val manual = c.boluses.any { !it.isSmb && it.timestampUtc >= c.now - 240 * MIN }
        return if (manual && peak >= c.high && g <= 90 && peak - g >= 100 && c.r!! <= -MODERATE)
            sit("overcorrection") else null
    }

    // ---- exercise & insulin ---------------------------------------------------------------------------------

    private fun exercise(c: Context): Msg? {
        val r = c.r!!
        if (c.exerciseActive != null) {
            if (r <= -FLAT) return sit("decline_exercise")
            if (r >= MODERATE && c.intense) return sit("rise_intense_ex")
        }
        val ended = c.exerciseEndedMin
        if (ended in 60..480 && c.exerciseLongEnough && r <= -FLAT && c.change(120) <= -30) return sit("delayed_postex_decline")
        return null
    }

    private fun insulin(c: Context): Msg? {
        val r = c.r!!
        if (c.stacking && r <= -MODERATE) return sit("decline_stacking")
        val age = c.correctionBolusAgeMin ?: return null
        if (age in 120..360 && c.g!! > c.high && r > -0.3) return sit("hyper_despite_corr")
        if (age in 20..240 && (r <= -0.7 || c.change(60) <= -30)) return sit("decline_correction")
        return null
    }

    // ---- meals ----------------------------------------------------------------------------------------------

    private fun meal(c: Context): Msg? {
        val age = c.mealAgeMin ?: return null
        val start = c.mealStart!!
        if (age > 360) return null
        val g = c.g!!
        val r = c.r!!
        val base = c.valueAround(start, 15)
        val sinceMeal = c.readings.filter { it.timestamp >= start }
        val peak = sinceMeal.maxOfOrNull { it.valueMgdl } ?: g
        val rise = base?.let { g - it }

        if (r <= -RAPID) {
            return if (base != null && peak - base >= 50 && age <= 240) sit("spike_decline", c.afterMeal)
            else if (age <= 240) sit("rapid_decline_meal", c.afterMeal) else null
        }
        // Dipped early, then climbed back: a bolus that peaked before the food did.
        if (base != null && age in 90..300) {
            val early = sinceMeal.filter { it.timestamp <= start + 90 * MIN }
            val trough = early.minOfOrNull { it.valueMgdl }
            if (trough != null && trough <= base - 20 && g >= trough + 20 && r >= FLAT && c.mealCarbs >= MEAL_MIN_CARBS)
                return sit("early_decline_late_rise")
        }
        // Rising, and the insulin for it wasn't there on time.
        if (c.mealCarbs >= 20 && age in 20..180 && !c.bolusAtMeal && r >= FLAT && (rise ?: 0) >= 30)
            return sit("missed_bolus")
        if (age <= 120 && r >= RAPID) return sit("rapid_rise_meal", c.afterMeal)
        if (age in 90..360 && r >= FLAT && (rise ?: 0) >= 30 && c.highFat) return sit("rise_highfat")
        if (age in 90..300 && r >= 0.7 && (rise ?: 0) >= 30) return sit("delayed_rise_meal", c.afterMeal)
        if (age >= 120 && c.streakMin { it > c.high } >= 120) return sit("prolonged_hyper_meal", c.afterMeal)
        if (age in 180..360 && g <= c.high && c.minutesAbove(start) >= 120) return sit("slow_recovery_meal", c.afterMeal)
        // Nothing more specific fits yet (e.g. right after eating): still say it is the meal.
        if (age <= 180 && r >= FLAT) return sit("rise_meal", c.afterMeal)
        if (age <= 240 && r <= -FLAT) return sit("decline_meal", c.afterMeal)
        return null
    }

    // ---- time of day ----------------------------------------------------------------------------------------

    private fun dawn(c: Context): Msg? {
        val local = Instant.ofEpochMilli(c.now).atZone(c.zone)
        val minuteOfDay = local.hour * 60 + local.minute
        if (minuteOfDay !in 210..540 || (c.mealAgeMin ?: Int.MAX_VALUE) < 240) return null
        if (c.boluses.any { it.timestampUtc >= c.now - 120 * MIN }) return null
        val midnight = local.toLocalDate().atStartOfDay(c.zone).toInstant().toEpochMilli()
        val nadir = c.readings.filter { it.timestamp in midnight..(midnight + 5 * 60 * MIN) }.minOfOrNull { it.valueMgdl } ?: return null
        return if (c.g!! - nadir >= 20 && c.r!! >= 0.2 && c.g > nadir) sit("dawn") else null
    }

    private fun overnight(c: Context): Msg? {
        if (!c.night || (c.mealAgeMin ?: Int.MAX_VALUE) < 180) return null
        if (c.r!! >= RAPID || c.r <= -RAPID) return null
        val change = c.change(120)
        val win = c.window(120)
        if (win.size < 6) return null
        if (change >= 30) return sit("night_rise")
        if (change <= -30) return sit("night_decline")
        val range = win.maxOf { it.valueMgdl } - win.minOf { it.valueMgdl }
        return if (range <= 30 && abs(c.r) < FLAT && c.g!! in c.low..c.high) sit("night_stable") else null
    }

    private fun fastingHigh(c: Context): Msg? {
        if ((c.mealAgeMin ?: Int.MAX_VALUE) < FASTING_AFTER_MIN) return null
        if (c.boluses.any { it.timestampUtc >= c.now - 240 * MIN }) return null
        return if (c.g!! > FASTING_HIGH && c.streakMin { it > FASTING_HIGH } >= 60) sit("fasting_hyper") else null
    }

    private fun longHigh(c: Context): Msg? {
        val above = c.streakMin { it > c.high }
        if (above >= 360) return sit("persistent_hyper")
        if (above >= 120) return sit("prolonged_high")
        if (c.g!! < c.low && c.streakMin { it < c.low } >= 60) return sit("prolonged_low")
        return null
    }

    // ---- unexplained movements ------------------------------------------------------------------------------

    private fun stress(c: Context): Msg? {
        if ((c.mealAgeMin ?: Int.MAX_VALUE) < 240 || c.exerciseActive != null || c.asleep) return null
        if (c.change(90) < 40 || c.r!! < FLAT) return null
        val hr = c.heartRate
        if (hr.size < 100) return null
        val resting = hr.map { it.bpm }.sorted().let { it[it.size / 5] }
        val recent = hr.filter { it.timestamp >= c.now - 30 * MIN }.map { it.bpm }
        return if (recent.size >= 10 && recent.average() >= resting + 20) sit("stress") else null
    }

    private fun variability(c: Context): Msg? {
        val day = c.window(24 * 60)
        if (day.size < 100) return null
        val episodes = c.episodes(day)
        if (episodes.size >= 3 && episodes.zipWithNext().all { it.first != it.second } && 'L' in episodes && 'H' in episodes)
            return sit("alternating")
        val swings = zigzag(c.window(12 * 60).map { it.valueMgdl }, 60)
        if (swings >= 4) return sit("spikes_drops")
        val cv = cv(day.map { it.valueMgdl })
        if (cv >= HIGH_CV && abs(c.r!!) >= MODERATE) return sit("rapid_fluct")
        return null
    }

    /** Today's direction at this clock time repeated on most of the previous days. */
    private fun recurrent(c: Context): Msg? {
        val r = c.r!!
        if (abs(r) < FLAT || c.history.size < 288) return null
        val now = c.change(60)
        if (abs(now) < 30) return null
        val rising = now > 0
        var days = 0
        var same = 0
        for (d in 1..13) {
            val t = c.now - d * 24 * 60 * MIN
            val end = c.nearHistory(t) ?: continue
            val start = c.nearHistory(t - 60 * MIN) ?: continue
            days++
            val delta = end - start
            if ((rising && delta >= 20) || (!rising && delta <= -20)) same++
        }
        return if (days >= 5 && same >= 0.6 * days)
            if (rising) sit("recurrent_rise") else sit("recurrent_decline")
        else null
    }

    // ---- calm -----------------------------------------------------------------------------------------------

    private fun calm(c: Context): Msg? {
        val g = c.g!!
        val win = c.window(60)
        if (win.size < 4 || abs(c.r!!) >= FLAT) return null
        val flat = win.maxOf { it.valueMgdl } - win.minOf { it.valueMgdl } <= 30
        if (flat && g in c.low..c.high && (c.mealAgeMin ?: Int.MAX_VALUE) >= 180) {
            return if (c.night) sit("night_stable") else sit("stable_between")
        }
        val three = c.window(180)
        if (three.size >= 20 && three.all { it.valueMgdl in c.low..c.high } && cv(three.map { it.valueMgdl }) < 20 && g in c.low..c.high)
            return sit("stable_part", c.partOfDay)
        if (cv(c.window(24 * 60).map { it.valueMgdl }) >= HIGH_CV) return sit("unpredictable")
        return null
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private fun cv(values: List<Int>): Double {
        if (values.size < 10) return 0.0
        val mean = values.average()
        if (mean <= 0) return 0.0
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size) / mean * 100
    }

    /** Number of reversals larger than [prominence] in the series (peak-to-trough swings). */
    private fun zigzag(values: List<Int>, prominence: Int): Int {
        if (values.isEmpty()) return 0
        var swings = 0
        var extreme = values[0]
        var up: Boolean? = null
        for (v in values) {
            when (up) {
                null -> if (abs(v - extreme) >= prominence) { up = v > extreme; swings++; extreme = v }
                true -> if (v > extreme) extreme = v else if (extreme - v >= prominence) { up = false; swings++; extreme = v }
                false -> if (v < extreme) extreme = v else if (v - extreme >= prominence) { up = true; swings++; extreme = v }
            }
        }
        return swings
    }

    private class Context(input: SituationInput) {
        val now = input.nowUtc
        val zone = input.zone
        val readings = input.readings.filter { it.timestamp <= now }.sortedBy { it.timestamp }
        val boluses = input.boluses.filter { it.timestampUtc <= now }
        val heartRate = input.heartRate.filter { it.timestamp <= now }
        val history = input.history
        val low = input.targetLow
        val high = input.targetHigh
        private val latest = readings.lastOrNull()
        val g: Int? = latest?.valueMgdl
        val stale = latest == null || now - latest.timestamp > MAX_READING_AGE_MIN * MIN
        val meals = input.meals.filter { it.isValid && it.mealTimeUtc <= now }
        val asleep = input.asleepNow
        /** Reads after "stable glucose": "in the morning", "at night" (a reference to the string). */
        val partOfDay: String = when (Instant.ofEpochMilli(now).atZone(zone).hour) {
            in 5..11 -> ref("sit_in_morning")
            in 12..16 -> ref("sit_in_afternoon")
            in 17..21 -> ref("sit_in_evening")
            else -> ref("sit_in_night")
        }
        val night: Boolean = asleep || Instant.ofEpochMilli(now).atZone(zone).hour.let { it >= 22 || it < 6 }

        /** Least-squares slope over the last 20 minutes in mg/dL per minute. */
        val r: Double? = slope(20)

        fun window(minutes: Int) = readings.filter { it.timestamp >= now - minutes * MIN }

        private fun slope(minutes: Int): Double? {
            val pts = window(minutes)
            if (pts.size < 3) return null
            val t0 = pts.first().timestamp
            val xs = pts.map { (it.timestamp - t0) / MIN.toDouble() }
            val ys = pts.map { it.valueMgdl.toDouble() }
            val mx = xs.average()
            val my = ys.average()
            val den = xs.sumOf { (it - mx) * (it - mx) }
            if (den == 0.0) return null
            return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den
        }

        /** Newest reading minus the one closest to [minutes] ago (0 when there is none). */
        fun change(minutes: Int): Int {
            val past = valueAround(now - minutes * MIN, 10) ?: return 0
            return (g ?: return 0) - past
        }

        fun valueAround(t: Long, toleranceMin: Int): Int? =
            readings.filter { abs(it.timestamp - t) <= toleranceMin * MIN }.minByOrNull { abs(it.timestamp - t) }?.valueMgdl

        fun nearHistory(t: Long): Int? =
            history.filter { abs(it.timestamp - t) <= 10 * MIN }.minByOrNull { abs(it.timestamp - t) }?.valueMgdl

        /** Minutes the newest readings have continuously matched [pred] (0 when the newest one doesn't). */
        fun streakMin(pred: (Int) -> Boolean): Long {
            var earliest = latest?.timestamp ?: return 0
            var prev = earliest
            if (!pred(latest.valueMgdl)) return 0
            for (rd in readings.asReversed().drop(1)) {
                if (prev - rd.timestamp > MAX_GAP_MS || !pred(rd.valueMgdl)) break
                earliest = rd.timestamp
                prev = rd.timestamp
            }
            return (now - earliest) / MIN
        }

        /** Latest hypo episode of at least 15 minutes that is over, ended within [withinMin]. */
        fun lastLow(withinMin: Int): Pair<Long, Long>? {
            var end: Long? = null
            var start = 0L
            var prevT = 0L
            for (rd in readings.asReversed()) {
                if (rd.timestamp < now - withinMin * MIN) break
                val isLow = rd.valueMgdl < HYPO
                if (end == null) {
                    if (isLow) { end = rd.timestamp; start = rd.timestamp; prevT = rd.timestamp }
                } else if (isLow && prevT - rd.timestamp <= MAX_GAP_MS) {
                    start = rd.timestamp; prevT = rd.timestamp
                } else break
            }
            val e = end ?: return null
            return if (e - start >= 15 * MIN) start to e else null
        }

        /** Minutes above the target high since [since] (readings weighted by the gap they cover, gaps capped). */
        fun minutesAbove(since: Long): Long {
            val pts = readings.filter { it.timestamp >= since }
            var ms = 0L
            for (i in 0 until pts.size - 1) if (pts[i].valueMgdl > high) ms += minOf(pts[i + 1].timestamp - pts[i].timestamp, MAX_GAP_MS)
            return ms / MIN
        }

        /** Alternating run of excursions ('L' low, 'H' high) in the last 24 h, each at least 15 min long. */
        fun episodes(day: List<CgmEntity>): List<Char> {
            val out = ArrayList<Char>()
            var kind: Char? = null
            var from = 0L
            var last = 0L
            fun close() { if (kind != null && last - from >= 15 * MIN && out.lastOrNull() != kind) out += kind!!; kind = null }
            for (rd in day) {
                val k = when { rd.valueMgdl < HYPO -> 'L'; rd.valueMgdl > high -> 'H'; else -> null }
                if (k != kind || rd.timestamp - last > MAX_GAP_MS) { close(); if (k != null) { kind = k; from = rd.timestamp } }
                last = rd.timestamp
            }
            close()
            return out
        }

        // ---- meals (a "meal" is the latest chain of courses; each course within 60 min of the last) ----
        private val mealChain: List<MealEntity> = run {
            val sorted = meals.filter { it.carbohydrates >= MEAL_MIN_CARBS }.sortedBy { it.mealTimeUtc }
            val chain = ArrayList<MealEntity>()
            for (m in sorted.asReversed()) {
                if (chain.isEmpty() || chain.last().mealTimeUtc - m.mealTimeUtc <= 60 * MIN) chain += m else break
            }
            chain.reversed()
        }
        val mealStart: Long? = mealChain.firstOrNull()?.mealTimeUtc
        val mealAgeMin: Int? = mealStart?.let { ((now - it) / MIN).toInt() }
        val mealCarbs: Int = mealChain.sumOf { it.carbohydrates }
        /** The kind of the latest meal in the user's words ("breakfast", "snack"...). */
        val mealWord: String = (mealChain.firstOrNull()?.mealType ?: MealType.SNACK).type.lowercase()
        /** "after breakfast" / "after a snack"... (a reference to the string). */
        val afterMeal: String get() = ref("sit_after_$mealWord")
        val highFat: Boolean = mealChain.sumOf { it.fats ?: 0 } >= 25 || mealChain.any { it.impactType == ImpactType.LONG }

        /** A manual bolus within 30 min before and 20 min after the meal began. */
        val bolusAtMeal: Boolean = mealStart?.let { s -> boluses.any { !it.isSmb && it.timestampUtc in (s - 30 * MIN)..(s + 20 * MIN) } } ?: false

        private val manual = boluses.filter { !it.isSmb && it.value >= 0.5f }

        /** Age of the newest manual bolus that wasn't given for food (no meal of 10 g+ within 30 min). */
        val correctionBolusAgeMin: Int? = manual.lastOrNull { b ->
            b.timestampUtc >= now - 360 * MIN && meals.none { it.carbohydrates >= 10 && abs(it.mealTimeUtc - b.timestampUtc) <= 30 * MIN }
        }?.let { ((now - it.timestampUtc) / MIN).toInt() }

        /** Two manual doses 20+ min apart in the last 3 h, the later one not for food. */
        val stacking: Boolean = run {
            val recent = manual.filter { it.timestampUtc >= now - 180 * MIN }
            recent.size >= 2 && recent.last().timestampUtc - recent.first().timestampUtc >= 20 * MIN &&
                meals.none { it.carbohydrates >= 10 && abs(it.mealTimeUtc - recent.last().timestampUtc) <= 30 * MIN }
        }

        // ---- exercise ----
        val exerciseActive: ExerciseEntity? = input.exercise.firstOrNull { it.startUtc <= now && it.endUtc >= now - 10 * MIN }
        private val lastExercise = input.exercise.filter { it.endUtc < now - 10 * MIN }.maxByOrNull { it.endUtc }
        val exerciseEndedMin: Int = lastExercise?.let { ((now - it.endUtc) / MIN).toInt() } ?: Int.MAX_VALUE
        val exerciseLongEnough: Boolean = (lastExercise?.durationMs ?: 0) >= 20 * MIN
        val intense: Boolean = heartRate.filter { it.timestamp >= now - 15 * MIN }.map { it.bpm }.let { it.size >= 5 && it.average() >= 120 }
    }
}
