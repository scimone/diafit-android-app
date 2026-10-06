package uk.scimone.diafit.core.domain.model

/** Untagged entries (imported carbs, older meals) this close to a meal are shown as part of it. */
const val SITTING_JOIN_WINDOW_MS = 15 * 60_000L

/** A meal stays "in progress" (offered for another course) this long after its latest course. */
const val SITTING_OPEN_WINDOW_MS = 90 * 60_000L

/**
 * One meal as the user thinks of it: one or more courses (see [MealEntity]), oldest first. Courses
 * keep their own time and absorption, so carb activity of a long meal is modelled as the sum of
 * staggered curves instead of one lump at the start.
 */
data class MealSitting(val courses: List<MealEntity>) {
    init { require(courses.isNotEmpty()) }

    val startTime: Long get() = courses.first().mealTimeUtc
    val endTime: Long get() = courses.last().mealTimeUtc
    val totalCarbs: Int get() = courses.sumOf { it.carbohydrates }
    val sittingId: String? get() = courses.firstNotNullOfOrNull { it.sittingId }
    val isExtended: Boolean get() = courses.size > 1

    /** When the last course should have finished absorbing. */
    val effectEndTime: Long get() = courses.maxOf { it.mealTimeUtc + it.impactType.durationMinutes * 60_000L }

    val title: String
        get() = courses.firstNotNullOfOrNull { it.description?.takeIf(String::isNotBlank) } ?: courses.first().mealType.type
}

/**
 * Groups time-ordered items into sittings. Items sharing a sitting id always belong together; two
 * different sitting ids are never merged (the user chose to start a new meal). Untagged items
 * (imported carbs, meals logged before courses existed) join whatever they are within [windowMs] of,
 * which keeps the old "close in time = one sitting" behaviour for them.
 */
fun <T> groupIntoSittings(
    items: List<T>,
    timeOf: (T) -> Long,
    sittingIdOf: (T) -> String?,
    windowMs: Long = SITTING_JOIN_WINDOW_MS
): List<List<T>> {
    val tagged = items.filter { sittingIdOf(it) != null }.groupBy { sittingIdOf(it) }.values
    val untagged = items.filter { sittingIdOf(it) == null }.map { listOf(it) }
    val clusters = (tagged + untagged).map { c -> c.sortedBy(timeOf) }.sortedBy { timeOf(it.first()) }

    val result = mutableListOf<MutableList<T>>()
    for (cluster in clusters) {
        val current = result.lastOrNull()
        val currentTagged = current?.any { sittingIdOf(it) != null } == true
        val clusterTagged = sittingIdOf(cluster.first()) != null
        val close = current != null && timeOf(cluster.first()) - current.maxOf(timeOf) <= windowMs
        if (close && !(currentTagged && clusterTagged)) {
            current!! += cluster
            current.sortBy(timeOf)
        } else {
            result += cluster.toMutableList()
        }
    }
    return result
}

fun List<MealEntity>.toSittings(): List<MealSitting> =
    groupIntoSittings(this, { it.mealTimeUtc }, { it.sittingId }).map(::MealSitting)
