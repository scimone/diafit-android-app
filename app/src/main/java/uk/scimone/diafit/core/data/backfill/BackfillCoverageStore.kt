package uk.scimone.diafit.core.data.backfill

import android.content.Context
import uk.scimone.diafit.core.domain.model.TimeRange
import uk.scimone.diafit.core.domain.model.TimeRanges
import uk.scimone.diafit.settings.domain.model.DataType

/**
 * Remembers which time ranges were already fetched from a connector per data type, so a repeated backfill
 * skips them. Needed for sparse data (boluses, meals, workouts) where "no rows" can't tell a gap from a
 * stretch in which nothing happened.
 */
class BackfillCoverageStore(context: Context) {
    private val prefs = context.getSharedPreferences("backfill_coverage", Context.MODE_PRIVATE)

    fun fetched(type: DataType): List<TimeRange> =
        prefs.getString(type.name, "").orEmpty().split(';').mapNotNull {
            val p = it.split('-')
            val a = p.getOrNull(0)?.toLongOrNull(); val b = p.getOrNull(1)?.toLongOrNull()
            if (a != null && b != null) TimeRange(a, b) else null
        }

    fun record(type: DataType, range: TimeRange) {
        val merged = TimeRanges.merge(fetched(type) + range)
        prefs.edit().putString(type.name, merged.joinToString(";") { "${it.start}-${it.end}" }).apply()
    }

    fun clear(type: DataType) = prefs.edit().remove(type.name).apply()
}
