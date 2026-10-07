package uk.scimone.diafit.core.domain.model

/**
 * Ambulatory glucose profile: every reading of a period folded onto one 24 h clock, summarised per
 * time-of-day bin as the 5/25/50/75/95th percentiles. Arrays hold [BINS] values; a bin with too few
 * readings is `NaN` and breaks the line.
 */
class AgpProfile(
    val p5: FloatArray,
    val p25: FloatArray,
    val median: FloatArray,
    val p75: FloatArray,
    val p95: FloatArray,
    val readingCount: Int
) {
    companion object {
        const val BIN_MINUTES = 15
        const val BINS = 24 * 60 / BIN_MINUTES
        private const val MIN_MGDL = 20
        private const val MAX_MGDL = 500
        private const val LEVELS = MAX_MGDL - MIN_MGDL + 1
        private const val MIN_PER_BIN = 5
        private const val MINUTE_MS = 60_000L

        /**
         * One pass, no sorting and no per-reading allocation: each reading increments a counter in a
         * per-bin histogram of mg/dL values, and the percentiles are read off the cumulative counts.
         * Cost is O(readings + BINS * levels), independent of how many months are folded in.
         */
        fun builder() = Builder()
    }

    class Builder {
        private val counts = IntArray(BINS * LEVELS)
        private val perBin = IntArray(BINS)
        private var total = 0

        /** Adds one day of readings; minutes are taken from [dayStartUtc], so a DST day just clamps at the ends. */
        fun addDay(dayStartUtc: Long, times: LongArray, values: IntArray, size: Int = times.size) {
            for (i in 0 until size) {
                val minute = ((times[i] - dayStartUtc) / MINUTE_MS).toInt().coerceIn(0, 24 * 60 - 1)
                add(minute, values[i])
            }
        }

        fun add(minuteOfDay: Int, mgdl: Int) {
            if (mgdl <= 0) return
            val bin = (minuteOfDay / BIN_MINUTES).coerceIn(0, BINS - 1)
            val level = mgdl.coerceIn(MIN_MGDL, MAX_MGDL) - MIN_MGDL
            counts[bin * LEVELS + level]++
            perBin[bin]++
            total++
        }

        fun build(): AgpProfile? {
            if (total == 0) return null
            val raw = Array(5) { FloatArray(BINS) { Float.NaN } }
            val fractions = floatArrayOf(0.05f, 0.25f, 0.5f, 0.75f, 0.95f)
            for (bin in 0 until BINS) {
                val n = perBin[bin]
                if (n < MIN_PER_BIN) continue
                var level = 0
                var cumulative = 0
                val base = bin * LEVELS
                for (k in fractions.indices) {
                    // Smallest value whose cumulative count reaches the target rank.
                    val rank = kotlin.math.ceil(fractions[k] * n).toInt().coerceAtLeast(1)
                    while (cumulative < rank && level < LEVELS) cumulative += counts[base + level++]
                    raw[k][bin] = (MIN_MGDL + level - 1).toFloat()
                }
            }
            // Circular 3-bin smoothing, as AGP reports do, so the bands read as curves not stairs.
            val smooth = raw.map { smooth(it) }
            return AgpProfile(smooth[0], smooth[1], smooth[2], smooth[3], smooth[4], total)
        }

        private fun smooth(src: FloatArray): FloatArray = FloatArray(BINS) { i ->
            val a = src[(i + BINS - 1) % BINS]
            val b = src[i]
            val c = src[(i + 1) % BINS]
            if (b.isNaN()) Float.NaN
            else {
                var sum = b * 2
                var w = 2f
                if (!a.isNaN()) { sum += a; w += 1f }
                if (!c.isNaN()) { sum += c; w += 1f }
                sum / w
            }
        }
    }
}
