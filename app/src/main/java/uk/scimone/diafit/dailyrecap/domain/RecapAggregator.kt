package uk.scimone.diafit.dailyrecap.domain

import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.ActivityDayStats
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.DayGlucoseStats
import uk.scimone.diafit.core.domain.model.GlucoseEpisode
import uk.scimone.diafit.core.domain.model.GlucoseSample
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.core.domain.model.GlucoseZone
import uk.scimone.diafit.core.domain.model.MealOutcome
import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.model.SleepStage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Everything [RecapAggregator] needs, already loaded. */
class RecapSource(
    val date: LocalDate,
    val zone: ZoneId,
    val nowUtc: Long,
    val target: GlucoseTargetRange,
    /** The day's readings plus a few hours after midnight (for late meals' outcomes), oldest first. */
    val readings: List<CgmEntity>,
    /** The days before [date], oldest first, one list of readings each (an empty list = no data that day). */
    val previousDays: List<List<GlucoseSample>>,
    /** The day's boluses with a margin either side. */
    val boluses: List<BolusEntity>,
    val basalUnits: Double?,
    val sittings: List<MealSitting>,
    val activity: ActivityData,
    val knownPatterns: List<String> = emptyList(),
    val devicesDueSoon: List<String> = emptyList()
)

/** Boils one day down to a [RecapInput] (what the LLM sees) and a [RecapVisual] (what the chart draws). */
object RecapAggregator {
    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private const val TEN_MIN = 10 * 60_000L
    private const val MAX_EPISODES = 5
    private const val MAX_MEALS = 8
    private const val MIN_COVERAGE_FOR_COMPARISON = 0.5

    private fun Long.hhmm(zone: ZoneId) = HH_MM.format(Instant.ofEpochMilli(this).atZone(zone))
    private fun Double.one() = Math.round(this * 10) / 10.0
    private fun Double.pct() = (this * 100).roundToInt()

    fun dayStart(date: LocalDate, zone: ZoneId) = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun dayEnd(date: LocalDate, zone: ZoneId) = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun build(s: RecapSource): Pair<RecapInput, RecapVisual> {
        val start = dayStart(s.date, s.zone)
        val end = dayEnd(s.date, s.zone)
        val until = minOf(end, s.nowUtc)
        val partial = s.nowUtc < end
        val thresholds = GlucoseThresholds.from(s.target)

        val dayReadings = s.readings.filter { it.timestamp in start until until }
        val samples = dayReadings.map { GlucoseSample(it.timestamp, it.valueMgdl) }
        val stats = DayGlucoseStats.from(samples, thresholds)
        val elapsedMs = (until - start).coerceAtLeast(1)

        val dayBoluses = s.boluses.filter { it.timestampUtc in start until until }
        val manual = dayBoluses.filter { !it.isSmb }
        val smbUnits = dayBoluses.filter { it.isSmb }.sumOf { it.value.toDouble() }
        val meals = s.sittings.filter { it.startTime in start until until }
        val workouts = s.activity.exercise.filter { it.startUtc in start until until }

        val previous = comparison(s, stats, thresholds)
        val input = RecapInput(
            date = s.date.toString(),
            weekday = s.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH),
            partialDayUntil = if (partial) until.hhmm(s.zone) else null,
            targetMgdl = "${s.target.lowerBound}-${s.target.upperBound}",
            glucose = stats?.let { st ->
                GlucoseSummary(
                    inRangePct = st.inRangeShare.pct(), belowPct = (st.share(GlucoseZone.LOW)).pct(),
                    veryLowPct = st.share(GlucoseZone.VERY_LOW).pct(),
                    abovePct = st.share(GlucoseZone.HIGH).pct(), veryHighPct = st.share(GlucoseZone.VERY_HIGH).pct(),
                    meanMgdl = st.meanMgdl.roundToInt(), cvPct = st.cvPercent.roundToInt(),
                    lowest = "${st.minMgdl} at ${samples.first { it.mgdl == st.minMgdl }.timeUtc.hhmm(s.zone)}",
                    highest = "${st.maxMgdl} at ${samples.first { it.mgdl == st.maxMgdl }.timeUtc.hhmm(s.zone)}",
                    coveragePct = (st.coveredMs.toDouble() / elapsedMs).coerceAtMost(1.0).pct()
                )
            },
            vsPrevious7Days = previous,
            blocks = blocks(s, start, until, thresholds),
            episodes = stats?.episodes.orEmpty().sortedWith(
                compareByDescending<GlucoseEpisode> { it.isSevere }.thenByDescending { it.durationMs }
            ).take(MAX_EPISODES).sortedBy { it.startUtc }.map { episode(it, s, meals) },
            meals = meals.take(MAX_MEALS).map { mealSummary(it, s, thresholds) },
            insulin = InsulinSummary(
                mealAndCorrectionBolusU = manual.sumOf { it.value.toDouble() }.one(),
                autoBolusSmbU = smbUnits.one(),
                basalU = s.basalUnits?.one(),
                bolusCount = manual.size,
                carbsTotalG = meals.sumOf { it.totalCarbs }
            ).takeIf { it.bolusCount > 0 || it.autoBolusSmbU > 0 || it.carbsTotalG > 0 || it.basalU != null },
            activity = activity(s, start, end, workouts),
            knownPatterns = s.knownPatterns,
            devicesDueSoon = s.devicesDueSoon
        )

        val visual = RecapVisual(
            dayStartUtc = start, dayEndUtc = end, shownUntilUtc = until,
            lowMgdl = s.target.lowerBound, highMgdl = s.target.upperBound,
            points = thin(dayReadings),
            meals = meals.map { VisualMeal(it.startTime, it.totalCarbs) },
            boluses = manual.map { VisualBolus(it.timestampUtc, it.value) },
            workouts = workouts.map { VisualSpan(it.startUtc, it.endUtc, it.title ?: "") },
            zoneShares = listOf(GlucoseZone.VERY_LOW, GlucoseZone.LOW, GlucoseZone.IN_RANGE, GlucoseZone.HIGH, GlucoseZone.VERY_HIGH)
                .map { (stats?.share(it) ?: 0.0).toFloat() },
            baselineInRange = previous?.inRangePctAvg?.let { it / 100f }
        )
        return input to visual
    }

    /** One reading per 10 minutes (the mean of that slot), so a day is at most 144 points. */
    internal fun thin(readings: List<CgmEntity>): List<VisualPoint> =
        readings.groupBy { it.timestamp / TEN_MIN }.toSortedMap().map { (slot, rows) ->
            VisualPoint(slot * TEN_MIN + TEN_MIN / 2, rows.map { it.valueMgdl }.average().roundToInt())
        }

    private fun comparison(s: RecapSource, today: DayGlucoseStats?, t: GlucoseThresholds): Comparison? {
        val days = s.previousDays.mapNotNull { DayGlucoseStats.from(it, t) }
            .filter { it.coveredMs >= MIN_COVERAGE_FOR_COMPARISON * 24 * 3_600_000L }
        if (days.size < 3) return null
        val tir = days.map { it.inRangeShare }.average()
        return Comparison(
            inRangePctAvg = tir.pct(),
            meanMgdlAvg = days.map { it.meanMgdl }.average().roundToInt(),
            cvPctAvg = days.map { it.cvPercent }.average().roundToInt(),
            lowEpisodesPerDayAvg = days.map { d -> d.episodes.count { it.isLow } }.average().one(),
            highEpisodesPerDayAvg = days.map { d -> d.episodes.count { !it.isLow } }.average().one(),
            daysWithData = days.size,
            inRangeDeltaPoints = today?.let { ((it.inRangeShare - tir) * 100).roundToInt() } ?: 0
        )
    }

    private val BLOCKS = listOf("night 00-06" to 0, "morning 06-12" to 6, "afternoon 12-18" to 12, "evening 18-24" to 18)

    private fun blocks(s: RecapSource, start: Long, until: Long, t: GlucoseThresholds): List<BlockSummary> {
        val zoneStart = s.date.atStartOfDay(s.zone)
        return BLOCKS.mapNotNull { (name, hour) ->
            val from = zoneStart.plusHours(hour.toLong()).toInstant().toEpochMilli()
            val to = minOf(zoneStart.plusHours(hour + 6L).toInstant().toEpochMilli(), until)
            if (to <= from) return@mapNotNull null
            val rows = s.readings.filter { it.timestamp in from until to }.map { GlucoseSample(it.timestamp, it.valueMgdl) }
            // Less than half a block of data says nothing reliable about it.
            if (rows.isEmpty()) return@mapNotNull null
            val st = DayGlucoseStats.from(rows, t) ?: return@mapNotNull null
            if (st.coveredMs < (to - from) / 2) return@mapNotNull null
            BlockSummary(name, st.inRangeShare.pct(), st.meanMgdl.roundToInt(), st.minMgdl, st.maxMgdl)
        }
    }

    private fun episode(e: GlucoseEpisode, s: RecapSource, meals: List<MealSitting>): EpisodeSummary {
        val before = mutableListOf<String>()
        meals.filter { it.startTime in (e.startUtc - 4 * 3_600_000L)..e.startUtc }.maxByOrNull { it.startTime }?.let { m ->
            val units = s.boluses.filter { it.timestampUtc in (m.startTime - 30 * 60_000L)..(m.endTime + 30 * 60_000L) }.sumOf { it.value.toDouble() }
            before += "${m.courses.first().mealType.type.lowercase()} at ${m.startTime.hhmm(s.zone)}, ${m.totalCarbs} g carbs" +
                if (units > 0) ", ${units.one()} U" else ", no insulin logged"
        }
        s.boluses.filter { !it.isSmb && it.timestampUtc in (e.startUtc - 3 * 3_600_000L)..e.startUtc }
            .takeIf { it.isNotEmpty() && before.isEmpty() }
            ?.let { list -> before += "bolus ${list.sumOf { it.value.toDouble() }.one()} U at ${list.last().timestampUtc.hhmm(s.zone)} without a logged meal" }
        s.activity.exercise.filter { it.endUtc in (e.startUtc - 8 * 3_600_000L)..e.startUtc || (it.startUtc <= e.startUtc && it.endUtc >= e.startUtc) }
            .maxByOrNull { it.endUtc }?.let { w ->
                before += "workout ${(w.title ?: "activity")} ${w.startUtc.hhmm(s.zone)}-${w.endUtc.hhmm(s.zone)}"
            }
        return EpisodeSummary(
            kind = if (e.isLow) "low" else "high", from = e.startUtc.hhmm(s.zone),
            minutes = (e.durationMs / 60_000).toInt(), extremeMgdl = e.extremeMgdl, severe = e.isSevere, before = before
        )
    }

    private fun mealSummary(m: MealSitting, s: RecapSource, t: GlucoseThresholds): MealSummary {
        val outcome = MealOutcome.of(m, s.readings, s.boluses, GlucoseTargetRange(t.low, t.high), s.nowUtc)
        // Protein and fat only when the course carries them (imported carb entries don't).
        val known = m.courses.filter { it.components.isNotEmpty() || (it.proteins ?: 0) > 0 || (it.fats ?: 0) > 0 }
        return MealSummary(
            time = m.startTime.hhmm(s.zone),
            type = m.courses.first().mealType.type,
            name = m.title.take(40),
            carbsG = m.totalCarbs,
            proteinG = known.takeIf { it.isNotEmpty() }?.sumOf { it.proteins ?: 0 },
            fatG = known.takeIf { it.isNotEmpty() }?.sumOf { it.fats ?: 0 },
            insulinU = outcome.insulinUnits.one(),
            glucoseAtStart = outcome.startMgdl,
            peak = outcome.peakMgdl,
            peakAfterMin = outcome.peakTimeUtc?.let { ((it - m.startTime) / 60_000).toInt() },
            inRange4hPct = outcome.timeInRange.roundToInt().takeIf { outcome.status == MealOutcome.Status.READY }
        )
    }

    private fun activity(s: RecapSource, start: Long, end: Long, workouts: List<uk.scimone.diafit.core.domain.model.ExerciseEntity>): ActivitySummary? {
        val stats = ActivityDayStats.from(s.activity, start, end)
        val sleep = stats?.takeIf { it.hasSleep }?.let {
            val h = it.sleepMs / 3_600_000.0
            val deep = it.stageMs[SleepStage.DEEP]?.let { d -> ", deep ${(d / 60_000)} min" } ?: ""
            "${h.one()} h$deep"
        }
        val summary = ActivitySummary(
            steps = stats?.steps?.takeIf { it > 0 },
            workouts = workouts.map { "${it.title ?: "activity"} ${it.startUtc.hhmm(s.zone)}, ${it.durationMs / 60_000} min" },
            sleepBeforeTheDay = sleep,
            restingHeartRate = stats?.restingBpm
        )
        return summary.takeIf { it.steps != null || it.workouts.isNotEmpty() || it.sleepBeforeTheDay != null || it.restingHeartRate != null }
    }
}
