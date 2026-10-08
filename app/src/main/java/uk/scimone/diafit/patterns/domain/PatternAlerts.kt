package uk.scimone.diafit.patterns.domain

/** Don't alert again for a pattern that comes back within this long of its last alert (it flickers at a threshold). */
const val PATTERN_REALERT_MS = 7L * 24 * 3_600_000

/**
 * Which of [current] to notify about: alert-worthy patterns that were not found on the previous check
 * ([previouslyActive], by text) and haven't been alerted within [PATTERN_REALERT_MS]. A pattern that persists is
 * announced once; one that disappears and returns later is announced again.
 */
fun newPatternAlerts(
    current: List<AgpPattern>,
    previouslyActive: Set<String>,
    lastAlerted: Map<String, Long>,
    nowUtc: Long
): List<AgpPattern> = current.filter { p ->
    p.isAlert && p.text !in previouslyActive && (lastAlerted[p.text]?.let { nowUtc - it >= PATTERN_REALERT_MS } ?: true)
}

/** Notification title and text for a batch of new patterns. */
fun patternAlertMessage(patterns: List<AgpPattern>): Pair<String, String> =
    if (patterns.size == 1) "New glucose pattern" to "${patterns[0].text}. Tap to see it on your AGP."
    else "${patterns.size} new glucose patterns" to patterns.joinToString("\n") { "• ${it.text}" }
