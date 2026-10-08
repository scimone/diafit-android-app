package uk.scimone.diafit.patterns.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The AGP the pattern detectors run on (see `patterns.md`): 288 five-minute bins (index 0 = 00:00, local time),
 * 10/25/50/75/90th percentiles in mg/dL, rounded to 1 decimal like the backend does before detection.
 * Not the History card's [uk.scimone.diafit.core.domain.model.AgpProfile] (15-min bins, 5/95th percentiles).
 */
class PatternAgp(
    val p10: DoubleArray,
    val p25: DoubleArray,
    val p50: DoubleArray,
    val p75: DoubleArray,
    val p90: DoubleArray,
    val readingCount: Int = 0,
    val dayCount: Int = 0
) {
    init {
        require(listOf(p10, p25, p50, p75, p90).all { it.size == BINS }) { "AGP arrays must have $BINS points" }
    }

    /** The same profile with every value rounded to 1 decimal. */
    fun rounded() = PatternAgp(p10.round1(), p25.round1(), p50.round1(), p75.round1(), p90.round1(), readingCount, dayCount)

    companion object {
        const val BIN_MINUTES = 5
        const val BINS = 24 * 60 / BIN_MINUTES

        /** Fewer distinct days than this and no profile is built: a pattern needs repetition to be a pattern. */
        const val MIN_DAYS = 5

        private const val MIN_MGDL = 20
        private const val MAX_MGDL = 500
        private const val LEVELS = MAX_MGDL - MIN_MGDL + 1
        /** Readings a bin needs before its percentiles mean anything. */
        private const val MIN_PER_BIN = 10
        /** Each bin pools its neighbours (±2 bins = 25 min); sparse bins widen up to ±6 (65 min). */
        private const val POOL_RADIUS = 2
        private const val MAX_POOL_RADIUS = 6
        private val FRACTIONS = doubleArrayOf(0.10, 0.25, 0.50, 0.75, 0.90)

        /**
         * Builds the profile from readings given as (minute of the local day, mg/dL), plus how many distinct days
         * they came from. Null when there are too few days, or some time of day has too few readings even after
         * widening its window (the detectors need all 288 points).
         */
        fun build(minutes: IntArray, values: IntArray, dayCount: Int): PatternAgp? {
            if (dayCount < MIN_DAYS) return null
            val counts = IntArray(BINS * LEVELS)
            val perBin = IntArray(BINS)
            var total = 0
            for (i in minutes.indices) {
                if (values[i] <= 0) continue
                val bin = (minutes[i] / BIN_MINUTES).coerceIn(0, BINS - 1)
                counts[bin * LEVELS + values[i].coerceIn(MIN_MGDL, MAX_MGDL) - MIN_MGDL]++
                perBin[bin]++
                total++
            }
            val out = Array(FRACTIONS.size) { DoubleArray(BINS) }
            val pooled = IntArray(LEVELS)
            for (bin in 0 until BINS) {
                var radius = POOL_RADIUS
                fun pooledCount() = (-radius..radius).sumOf { perBin[(bin + it + BINS) % BINS] }
                while (pooledCount() < MIN_PER_BIN && radius < MAX_POOL_RADIUS) radius++
                val n = pooledCount()
                if (n < MIN_PER_BIN) return null
                pooled.fill(0)
                for (d in -radius..radius) {
                    val base = ((bin + d + BINS) % BINS) * LEVELS
                    for (l in 0 until LEVELS) pooled[l] += counts[base + l]
                }
                for (k in FRACTIONS.indices) out[k][bin] = percentile(pooled, n, FRACTIONS[k])
            }
            return PatternAgp(out[0], out[1], out[2], out[3], out[4], total, dayCount).rounded()
        }

        /** Linear-interpolated percentile (numpy's default) of a histogram of [n] values over the mg/dL levels. */
        private fun percentile(hist: IntArray, n: Int, fraction: Double): Double {
            val pos = fraction * (n - 1)
            val lo = kotlin.math.floor(pos).toInt()
            val a = valueAtRank(hist, lo)
            val b = if (lo + 1 < n) valueAtRank(hist, lo + 1) else a
            return a + (b - a) * (pos - lo)
        }

        /** The value with 0-based rank [rank] in sorted order. */
        private fun valueAtRank(hist: IntArray, rank: Int): Double {
            var cumulative = 0
            for (l in hist.indices) {
                cumulative += hist[l]
                if (cumulative > rank) return (MIN_MGDL + l).toDouble()
            }
            return MAX_MGDL.toDouble()
        }
    }
}

/** Python's `round(x, 1)`: exact binary value, ties to even. */
internal fun round1(x: Double): Double = BigDecimal(x).setScale(1, RoundingMode.HALF_EVEN).toDouble()

private fun DoubleArray.round1() = DoubleArray(size) { round1(this[it]) }
