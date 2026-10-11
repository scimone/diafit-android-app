package uk.scimone.diafit.patterns.domain

/**
 * Steps 4–8 of the shared pipeline after detection: rank, deduplicate, attach exactly three tips, cap. Detectors
 * live in their own objects ([TimeOfDayDetector], later meal/exercise/sleep/trend) and only emit candidates.
 */
object PatternPipeline {
    /** At most this many patterns are surfaced at once, so many weak candidates can't bury the strong ones. */
    const val MAX_PATTERNS = 8

    /**
     * @param existingTips tips already stored per pattern id: kept as they are, so a card doesn't reword between scans
     * @param tipsInUse tips currently shown by other stored patterns, avoided when choosing new ones
     */
    fun finalize(
        candidates: List<PatternCandidate>,
        existingTips: Map<String, List<String>> = emptyMap(),
        tipsInUse: Set<String> = emptySet()
    ): List<PatternCard> {
        val used = tipsInUse.toMutableSet()
        return withoutOverlaps(candidates)
            .distinctBy { it.card.id }
            .sortedByDescending { it.card.priority }
            .take(MAX_PATTERNS)
            .mapNotNull { c ->
                val tips = existingTips[c.card.id]?.takeIf { it.size == TIP_COUNT }
                    ?: PatternTips.select(c.topic, c.escalate, used)
                if (tips.size != TIP_COUNT || tips.any { it.isBlank() }) return@mapNotNull null // can't give three good tips: don't surface
                used += tips
                c.card.copy(tips = tips)
            }
    }

    const val TIP_COUNT = 3

    /**
     * Candidates that answer the same question as another one: [dropped] (an id prefix) goes when a candidate whose
     * id starts with [kept] is present with the same tone, so the more informative card stands alone.
     */
    private class Overlap(val dropped: String, val kept: String)

    private val OVERLAPS = listOf(
        Overlap("ctx.mealdelay", "meal.rich"),        // the broader "richer meals keep glucose up" card wins
        Overlap("meal.type.", "ctx.mealtime"),        // the size-matched clock-time comparison replaces the label comparison
        Overlap("ctx.night", "ex.nextday"),           // next-day time in range already says it
        Overlap("ctx.exgap", "ex.evening"),           // evening activity and "soon after eating" are mostly the same sessions
        Overlap("ctx.extype", "ex.intensity")         // harder-activity effect would explain a type difference
    )

    internal fun withoutOverlaps(candidates: List<PatternCandidate>): List<PatternCandidate> =
        candidates.filterNot { c ->
            OVERLAPS.any { o ->
                c.card.id.startsWith(o.dropped) &&
                    candidates.any { k -> k !== c && k.card.id.startsWith(o.kept) && k.card.tone == c.card.tone }
            }
        }
}
