package uk.scimone.diafit.history.domain.model

import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import kotlin.math.min
import kotlin.math.sqrt

/** Readings further apart than this are a sensor gap: nothing is assumed about the time in between. */
const val READING_GAP_MS = 15 * 60_000L

/** A reading's own slot when the source's interval can't be told (a single reading). */
private const val DEFAULT_READING_MS = 5 * 60_000L

/** The source's usual spacing (median of gap-free intervals): what a reading before a gap, or the last one, stands for. */
private fun typicalInterval(samples: List<GlucoseSample>): Long {
    val deltas = samples.zipWithNext { a, b -> b.timeUtc - a.timeUtc }.filter { it in 1..READING_GAP_MS }.sorted()
    return if (deltas.isEmpty()) DEFAULT_READING_MS else deltas[deltas.size / 2].coerceAtMost(DEFAULT_READING_MS)
}

/** Out-of-range stretches shorter than this are noise, not an episode worth listing. */
const val MIN_EPISODE_MS = 15 * 60_000L

/** One CGM value, reduced to what the statistics need. */
data class GlucoseSample(val timeUtc: Long, val mgdl: Int)

/** Where a reading sits relative to the target range and the consensus "very" limits. */
enum class GlucoseZone { VERY_LOW, LOW, IN_RANGE, HIGH, VERY_HIGH }

fun GlucoseThresholds.zoneOf(mgdl: Int): GlucoseZone = when {
    mgdl < veryLow -> GlucoseZone.VERY_LOW
    mgdl < low -> GlucoseZone.LOW
    mgdl > veryHigh -> GlucoseZone.VERY_HIGH
    mgdl > high -> GlucoseZone.HIGH
    else -> GlucoseZone.IN_RANGE
}

/** A continuous stretch below or above the target range. */
data class GlucoseEpisode(
    val isLow: Boolean,
    val startUtc: Long,
    val endUtc: Long,
    /** The lowest value of a low, the highest of a high. */
    val extremeMgdl: Int,
    /** Reached the "very" zone (< 54 / > 250 mg/dL). */
    val isSevere: Boolean
) {
    val durationMs: Long get() = endUtc - startUtc
}

/**
 * Summary of one day's CGM trace. Zone shares are **time-weighted** (each reading stands for the time
 * until the next one, capped at [READING_GAP_MS]) so a 1-minute source (Juggluco) and a 5-minute one
 * give the same answer.
 */
data class DayGlucoseStats(
    val readingCount: Int,
    val meanMgdl: Double,
    val sdMgdl: Double,
    val minMgdl: Int,
    val maxMgdl: Int,
    /** Time covered by readings, in ms. */
    val coveredMs: Long,
    /** Share 0..1 of [coveredMs] spent in each zone. */
    val zoneShare: Map<GlucoseZone, Double>,
    val episodes: List<GlucoseEpisode>
) {
    /** Coefficient of variation in %, the usual glycaemic variability measure (target ≤ 36 %). */
    val cvPercent: Double get() = if (meanMgdl > 0) sdMgdl / meanMgdl * 100 else 0.0

    fun share(zone: GlucoseZone): Double = zoneShare[zone] ?: 0.0
    val inRangeShare: Double get() = share(GlucoseZone.IN_RANGE)
    val belowShare: Double get() = share(GlucoseZone.LOW) + share(GlucoseZone.VERY_LOW)
    val aboveShare: Double get() = share(GlucoseZone.HIGH) + share(GlucoseZone.VERY_HIGH)

    companion object {
        /** Null when there are no readings. [samples] must be sorted by time. */
        fun from(samples: List<GlucoseSample>, thresholds: GlucoseThresholds): DayGlucoseStats? {
            if (samples.isEmpty()) return null
            val interval = typicalInterval(samples)
            val weights = samples.mapIndexed { i, s ->
                val next = samples.getOrNull(i + 1)?.timeUtc
                if (next == null || next - s.timeUtc > READING_GAP_MS) interval
                else min(next - s.timeUtc, READING_GAP_MS)
            }
            val total = weights.sum().toDouble()
            val zoneMs = mutableMapOf<GlucoseZone, Long>()
            samples.forEachIndexed { i, s -> zoneMs.merge(thresholds.zoneOf(s.mgdl), weights[i], Long::plus) }

            val mean = samples.sumOf { it.mgdl.toDouble() } / samples.size
            val variance = samples.sumOf { (it.mgdl - mean) * (it.mgdl - mean) } / samples.size
            return DayGlucoseStats(
                readingCount = samples.size,
                meanMgdl = mean,
                sdMgdl = sqrt(variance),
                minMgdl = samples.minOf { it.mgdl },
                maxMgdl = samples.maxOf { it.mgdl },
                coveredMs = total.toLong(),
                zoneShare = zoneMs.mapValues { it.value / total },
                episodes = episodes(samples, thresholds, interval)
            )
        }

        private fun episodes(samples: List<GlucoseSample>, t: GlucoseThresholds, interval: Long): List<GlucoseEpisode> {
            val result = mutableListOf<GlucoseEpisode>()
            var run = mutableListOf<GlucoseSample>()
            var runIsLow = false

            fun close() {
                if (run.isEmpty()) return
                val start = run.first().timeUtc
                // The episode lasts until the last out-of-range reading's own time slot ends.
                val end = run.last().timeUtc + interval
                if (end - start >= MIN_EPISODE_MS) {
                    val extreme = if (runIsLow) run.minOf { it.mgdl } else run.maxOf { it.mgdl }
                    result += GlucoseEpisode(
                        isLow = runIsLow,
                        startUtc = start,
                        endUtc = end,
                        extremeMgdl = extreme,
                        isSevere = if (runIsLow) extreme < t.veryLow else extreme > t.veryHigh
                    )
                }
                run = mutableListOf()
            }

            samples.forEach { s ->
                val isLow = s.mgdl < t.low
                val isHigh = s.mgdl > t.high
                val gap = run.isNotEmpty() && s.timeUtc - run.last().timeUtc > READING_GAP_MS
                when {
                    !isLow && !isHigh -> close()
                    run.isEmpty() -> { runIsLow = isLow; run += s }
                    gap || runIsLow != isLow -> { close(); runIsLow = isLow; run += s }
                    else -> run += s
                }
            }
            close()
            return result
        }
    }
}
