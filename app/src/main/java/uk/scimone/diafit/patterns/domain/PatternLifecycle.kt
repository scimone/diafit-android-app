package uk.scimone.diafit.patterns.domain

import kotlin.math.abs

/** Result of one scan: the full new set of stored patterns, and the ones worth a push. */
class Reconciliation(val patterns: List<StoredPattern>, val toNotify: List<StoredPattern>)

/**
 * Turns a scan's detections into lifecycle states, so repeated scans don't re-announce the same pattern:
 *  - never seen, or back after being resolved: NEW (unread);
 *  - still there with a materially different size ([MATERIAL_CHANGE]): UPDATED (unread);
 *  - still there, same size: ONGOING (a NEW/UPDATED one stays so until the user opens it);
 *  - not found for [MISSES_TO_RESOLVE] scans in a row: RESOLVED (a single miss at a threshold is noise);
 *  - dismissed: stays dismissed unless it gets materially worse ([DISMISSED_REOPEN_FACTOR]).
 * A push goes out once per new/updated episode, and only for unfavourable patterns.
 */
object PatternLifecycle {
    const val MATERIAL_CHANGE = 0.25
    const val DISMISSED_REOPEN_FACTOR = 1.5
    const val MISSES_TO_RESOLVE = 2
    const val RESOLVED_KEEP_MS = 30L * 24 * 3_600_000

    fun reconcile(stored: List<StoredPattern>, detected: List<PatternCard>, nowUtc: Long): Reconciliation {
        val byId = stored.associateBy { it.card.id }
        val result = ArrayList<StoredPattern>()
        for (card in detected) {
            val s = byId[card.id]
            result += when {
                s == null -> fresh(card, PatternState.NEW, nowUtc, nowUtc)
                s.state == PatternState.RESOLVED -> fresh(card, PatternState.NEW, nowUtc, nowUtc)
                s.state == PatternState.DISMISSED ->
                    if (card.tone == PatternTone.UNFAVORABLE && card.effectSize >= s.referenceEffect * DISMISSED_REOPEN_FACTOR)
                        fresh(card, PatternState.UPDATED, s.firstSeenUtc, nowUtc)
                    else s.copy(card = card, lastSeenUtc = nowUtc, missedScans = 0)
                isMaterial(s.referenceEffect, card.effectSize) -> fresh(card, PatternState.UPDATED, s.firstSeenUtc, nowUtc)
                else -> s.copy(
                    card = card, lastSeenUtc = nowUtc, missedScans = 0,
                    state = if (s.isUnreviewed) s.state else PatternState.ONGOING
                )
            }
        }
        val detectedIds = detected.map { it.id }.toSet()
        for (s in stored) {
            if (s.card.id in detectedIds) continue
            val missed = s.missedScans + 1
            result += when {
                s.state == PatternState.RESOLVED && nowUtc - s.lastSeenUtc > RESOLVED_KEEP_MS -> continue
                s.state == PatternState.RESOLVED -> s
                s.state == PatternState.DISMISSED -> if (nowUtc - s.lastSeenUtc > RESOLVED_KEEP_MS) continue else s.copy(missedScans = missed)
                missed >= MISSES_TO_RESOLVE -> s.copy(state = PatternState.RESOLVED, isRead = true, missedScans = missed)
                else -> s.copy(missedScans = missed)
            }
        }
        val notify = result.filter {
            it.isUnreviewed && it.notifiedAtUtc == null && it.card.tone == PatternTone.UNFAVORABLE
        }
        return Reconciliation(result, notify)
    }

    private fun fresh(card: PatternCard, state: PatternState, firstSeen: Long, now: Long) = StoredPattern(
        card = card, state = state, isRead = false, firstSeenUtc = firstSeen, lastSeenUtc = now,
        referenceEffect = card.effectSize
    )

    private fun isMaterial(reference: Double, now: Double): Boolean =
        abs(now - reference) / maxOf(abs(reference), 1e-9) >= MATERIAL_CHANGE
}
