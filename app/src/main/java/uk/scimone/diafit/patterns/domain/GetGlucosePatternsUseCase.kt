package uk.scimone.diafit.patterns.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.scimone.diafit.core.domain.repository.CgmRepository
import java.time.Instant
import java.time.ZoneId

/** The pattern AGP of a period and what was found in it. [agp] is null when there wasn't enough data. */
data class GlucosePatterns(
    val agp: PatternAgp?,
    val patterns: List<AgpPattern>,
    val fromUtc: Long,
    val toUtc: Long,
    val dayCount: Int
)

/** Folds the last [PATTERN_DAYS] days of CGM readings onto one local day and runs the detectors on it. */
class GetGlucosePatternsUseCase(private val cgmRepository: CgmRepository) {
    suspend operator fun invoke(userId: Int, nowUtc: Long = System.currentTimeMillis()): GlucosePatterns {
        val from = nowUtc - PATTERN_DAYS * 24 * 3_600_000L
        // The DAO query isn't main-safe (blocking Room call behind a suspend repository method).
        val readings = withContext(Dispatchers.IO) { cgmRepository.getEntriesBetween(from, nowUtc, userId) }
        return withContext(Dispatchers.Default) {
            val zone = ZoneId.systemDefault()
            val minutes = IntArray(readings.size)
            val values = IntArray(readings.size)
            val days = HashSet<Long>()
            readings.forEachIndexed { i, r ->
                val local = Instant.ofEpochMilli(r.timestamp).atZone(zone)
                minutes[i] = local.hour * 60 + local.minute
                values[i] = r.valueMgdl
                days += local.toLocalDate().toEpochDay()
            }
            val agp = PatternAgp.build(minutes, values, days.size)
            GlucosePatterns(agp, agp?.let(::detectAgpPatterns).orEmpty(), from, nowUtc, days.size)
        }
    }

    companion object {
        const val PATTERN_DAYS = 14
    }
}
