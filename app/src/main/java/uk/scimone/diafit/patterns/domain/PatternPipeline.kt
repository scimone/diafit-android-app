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
        return candidates
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
}
