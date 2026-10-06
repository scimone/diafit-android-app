package uk.scimone.diafit.core.domain.model

import kotlin.math.abs
import kotlin.math.max

/**
 * Finds imported AAPS carb entries that are really the same food as a meal logged in the app (the
 * user photographs the meal, then types the carbs into AAPS' bolus calculator, which sends them back
 * as a second entry). Pure, so the rules are unit-tested; [uk.scimone.diafit.core.domain.usecase.MergeCarbEntriesUseCase]
 * applies the result.
 *
 * An imported entry can match a logged course when it arrives from [LEAD_MS] before to [TAIL_MS] after
 * the course (the photo normally comes first, but the dose may precede the photo) and the carbs are
 * within [carbTolerance]. Matches that are close on both counts and unambiguous are [Match.confident]
 * and merged automatically; the others are only suggested. Courses of a multi-course meal can also be
 * matched as a whole (one AAPS dose for the whole sitting).
 */
object MealMatcher {
    const val LEAD_MS = 15 * 60_000L
    const val TAIL_MS = 90 * 60_000L
    private const val CONFIDENT_LEAD_MS = 10 * 60_000L
    private const val CONFIDENT_TAIL_MS = 60 * 60_000L

    /** [logged] is the meal's first course; [sittingLevel] means the entry matched the whole multi-course meal. */
    data class Match(
        val logged: MealEntity,
        val imported: MealEntity,
        val confident: Boolean,
        val sittingLevel: Boolean = false
    )

    fun carbTolerance(carbs: Int): Int = max(5, (carbs * 0.2).toInt())
    private fun confidentCarbTolerance(carbs: Int): Int = max(2, (carbs * 0.1).toInt())

    private class Candidate(
        val logged: MealEntity,
        val course: List<MealEntity>,
        val imported: MealEntity,
        val loggedCarbs: Int,
        val earliest: Long,
        val latest: Long,
        val sittingLevel: Boolean
    ) {
        val carbDiff = abs(imported.carbohydrates - loggedCarbs)
        /** Negative when the entry predates the meal, positive when it arrives after it. */
        val timeGap = when {
            imported.mealTimeUtc < earliest -> imported.mealTimeUtc - earliest
            imported.mealTimeUtc > latest -> imported.mealTimeUtc - latest
            else -> 0L
        }
        val score = carbDiff.toDouble() / max(1, max(loggedCarbs, imported.carbohydrates)) * 2 + abs(timeGap).toDouble() / TAIL_MS
        val confidentShaped = carbDiff <= confidentCarbTolerance(max(loggedCarbs, imported.carbohydrates)) &&
            timeGap in -CONFIDENT_LEAD_MS..CONFIDENT_TAIL_MS
    }

    /** Matches among [meals] (valid, not yet merged): one-to-one, best pairs first. */
    fun findMatches(meals: List<MealEntity>): List<Match> {
        val imported = meals.filter { it.sourceId != null && !it.mergeDeclined && it.mergedIntoId == null }
        val logged = meals.filter { it.sourceId == null && !it.aapsLinked && it.carbohydrates > 0 }
        if (imported.isEmpty() || logged.isEmpty()) return emptyList()

        val candidates = mutableListOf<Candidate>()
        for (i in imported) {
            for (l in logged) {
                val c = Candidate(l, listOf(l), i, l.carbohydrates, l.mealTimeUtc, l.mealTimeUtc, false)
                if (c.isViable()) candidates += c
            }
        }
        // Multi-course meals can also be matched as a whole (every course must be unlinked).
        val sittings = logged.filter { it.sittingId != null }.groupBy { it.sittingId }.values.filter { it.size > 1 }
        for (courses in sittings) {
            val ordered = courses.sortedBy { it.mealTimeUtc }
            for (i in imported) {
                val c = Candidate(ordered.first(), ordered, i, ordered.sumOf { it.carbohydrates }, ordered.first().mealTimeUtc, ordered.last().mealTimeUtc, true)
                if (c.isViable()) candidates += c
            }
        }

        val used = mutableSetOf<Int>()
        val result = mutableListOf<Match>()
        // Course-level matches win ties against sitting-level ones (stable sort keeps insertion order).
        for (c in candidates.sortedBy { it.score }) {
            if (c.imported.id in used || c.course.any { it.id in used }) continue
            // Ambiguous: another equally plausible pairing exists for the same entry (a different meal)
            // or for the same meal (a different entry). The same pair seen as course and as sitting doesn't count.
            val ambiguous = candidates.any { o ->
                o !== c && o.confidentShaped && run {
                    val shareCourse = o.course.any { oc -> c.course.any { it.id == oc.id } }
                    val sameImported = o.imported.id == c.imported.id
                    (sameImported && !shareCourse) || (shareCourse && !sameImported)
                }
            }
            used += c.imported.id
            used += c.course.map { it.id }
            result += Match(c.logged, c.imported, confident = c.confidentShaped && !ambiguous, sittingLevel = c.sittingLevel)
        }
        return result
    }

    private fun Candidate.isViable(): Boolean =
        imported.carbohydrates > 0 &&
            timeGap in -LEAD_MS..TAIL_MS &&
            carbDiff <= carbTolerance(max(loggedCarbs, imported.carbohydrates))
}
