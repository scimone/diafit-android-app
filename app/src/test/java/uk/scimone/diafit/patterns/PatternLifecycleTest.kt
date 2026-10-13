package uk.scimone.diafit.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.patterns.domain.HourSpan
import uk.scimone.diafit.patterns.domain.PatternCard
import uk.scimone.diafit.patterns.domain.Msg
import uk.scimone.diafit.patterns.domain.PatternCategory
import uk.scimone.diafit.patterns.domain.PatternLifecycle
import uk.scimone.diafit.patterns.domain.PatternState
import uk.scimone.diafit.patterns.domain.PatternTone
import uk.scimone.diafit.patterns.domain.MiniGraph
import uk.scimone.diafit.patterns.domain.StoredPattern

class PatternLifecycleTest {
    private val day = 24L * 3_600_000

    private fun card(id: String = "a", effect: Double = 0.4, tone: PatternTone = PatternTone.UNFAVORABLE) = PatternCard(
        id = id, category = PatternCategory.TIME_OF_DAY, detector = "low", title = Msg(id), summary = Msg("s"),
        miniGraph = MiniGraph.AgpWindow(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), listOf(HourSpan(0, 4))),
        observationFromUtc = 0, observationToUtc = 1, sampleSize = 14, sampleUnit = "days",
        effectSize = effect, effectLabel = Msg("e"), uncertainty = Msg("u"), limitations = listOf(Msg("l")),
        tips = listOf("1", "2", "3"), tone = tone, priority = 50.0
    )

    private fun reconcile(stored: List<StoredPattern>, cards: List<PatternCard>, now: Long) = PatternLifecycle.reconcile(stored, cards, now)

    @Test fun firstDetectionIsNewAndNotified() {
        val r = reconcile(emptyList(), listOf(card()), 1000)
        assertEquals(PatternState.NEW, r.patterns.single().state)
        assertEquals(1, r.toNotify.size)
    }

    @Test fun repeatedScansDoNotNotifyAgain() {
        val first = reconcile(emptyList(), listOf(card()), 1000).patterns.map { it.copy(notifiedAtUtc = 1000) }
        val second = reconcile(first, listOf(card()), 2000)
        assertEquals(PatternState.NEW, second.patterns.single().state) // still unreviewed
        assertTrue(second.toNotify.isEmpty())
        assertEquals(1, second.patterns.size)
    }

    @Test fun reviewedAndUnchangedBecomesOngoing() {
        val stored = reconcile(emptyList(), listOf(card()), 1000).patterns.map { it.copy(isRead = true, notifiedAtUtc = 1000) }
        val r = reconcile(stored, listOf(card(effect = 0.42)), 2000)
        assertEquals(PatternState.ONGOING, r.patterns.single().state)
        assertTrue(r.toNotify.isEmpty())
    }

    @Test fun materialChangeIsUpdatedAndNotifiesOnce() {
        val stored = reconcile(emptyList(), listOf(card(effect = 0.3)), 1000).patterns.map { it.copy(isRead = true, notifiedAtUtc = 1000) }
        val r = reconcile(stored, listOf(card(effect = 0.5)), 2000)
        assertEquals(PatternState.UPDATED, r.patterns.single().state)
        assertEquals(1, r.toNotify.size)
        val again = reconcile(r.patterns.map { it.copy(notifiedAtUtc = 2000) }, listOf(card(effect = 0.5)), 3000)
        assertTrue(again.toNotify.isEmpty())
    }

    @Test fun missingForTwoScansResolvesButOneMissDoesNot() {
        val stored = reconcile(emptyList(), listOf(card()), 1000).patterns.map { it.copy(isRead = true) }
        val one = reconcile(stored, emptyList(), 2000)
        assertEquals(stored.single().state, one.patterns.single().state)
        val two = reconcile(one.patterns, emptyList(), 3000)
        assertEquals(PatternState.RESOLVED, two.patterns.single().state)
        assertTrue(two.patterns.single().isRead) // resolved never counts toward the badge
    }

    @Test fun resolvedPatternThatReturnsIsNewAgain() {
        val resolved = reconcile(emptyList(), listOf(card()), 1000).patterns.single().copy(state = PatternState.RESOLVED, isRead = true, notifiedAtUtc = 1000)
        val r = reconcile(listOf(resolved), listOf(card()), 5000)
        assertEquals(PatternState.NEW, r.patterns.single().state)
        assertEquals(1, r.toNotify.size)
    }

    @Test fun dismissedStaysDismissedUnlessMuchWorse() {
        val dismissed = reconcile(emptyList(), listOf(card(effect = 0.4)), 1000).patterns.single().copy(state = PatternState.DISMISSED, isRead = true)
        val same = reconcile(listOf(dismissed), listOf(card(effect = 0.5)), 2000)
        assertEquals(PatternState.DISMISSED, same.patterns.single().state)
        assertTrue(same.toNotify.isEmpty())
        val worse = reconcile(listOf(dismissed), listOf(card(effect = 0.7)), 3000)
        assertEquals(PatternState.UPDATED, worse.patterns.single().state)
    }

    @Test fun favourablePatternsAreBadgedButNotPushed() {
        val r = reconcile(emptyList(), listOf(card(tone = PatternTone.FAVORABLE)), 1000)
        assertTrue(r.patterns.single().isUnreviewed)
        assertTrue(r.toNotify.isEmpty())
    }

    @Test fun oldResolvedPatternsAreDropped() {
        val old = reconcile(emptyList(), listOf(card()), 1000).patterns.single().copy(state = PatternState.RESOLVED, isRead = true)
        val r = reconcile(listOf(old), emptyList(), 1000 + 31 * day)
        assertTrue(r.patterns.isEmpty())
    }
}
