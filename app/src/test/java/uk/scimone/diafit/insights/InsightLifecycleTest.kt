package uk.scimone.diafit.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.insights.domain.HourSpan
import uk.scimone.diafit.insights.domain.InsightCard
import uk.scimone.diafit.insights.domain.InsightCategory
import uk.scimone.diafit.insights.domain.InsightLifecycle
import uk.scimone.diafit.insights.domain.InsightState
import uk.scimone.diafit.insights.domain.InsightTone
import uk.scimone.diafit.insights.domain.MiniGraph
import uk.scimone.diafit.insights.domain.StoredInsight

class InsightLifecycleTest {
    private val day = 24L * 3_600_000

    private fun card(id: String = "a", effect: Double = 0.4, tone: InsightTone = InsightTone.UNFAVORABLE) = InsightCard(
        id = id, category = InsightCategory.TIME_OF_DAY, detector = "low", title = id, summary = "s",
        miniGraph = MiniGraph.AgpWindow(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), listOf(HourSpan(0, 4))),
        observationFromUtc = 0, observationToUtc = 1, sampleSize = 14, sampleUnit = "days",
        effectSize = effect, effectLabel = "e", uncertainty = "u", limitations = listOf("l"),
        tips = listOf("1", "2", "3"), tone = tone, priority = 50.0
    )

    private fun reconcile(stored: List<StoredInsight>, cards: List<InsightCard>, now: Long) = InsightLifecycle.reconcile(stored, cards, now)

    @Test fun firstDetectionIsNewAndNotified() {
        val r = reconcile(emptyList(), listOf(card()), 1000)
        assertEquals(InsightState.NEW, r.insights.single().state)
        assertEquals(1, r.toNotify.size)
    }

    @Test fun repeatedScansDoNotNotifyAgain() {
        val first = reconcile(emptyList(), listOf(card()), 1000).insights.map { it.copy(notifiedAtUtc = 1000) }
        val second = reconcile(first, listOf(card()), 2000)
        assertEquals(InsightState.NEW, second.insights.single().state) // still unreviewed
        assertTrue(second.toNotify.isEmpty())
        assertEquals(1, second.insights.size)
    }

    @Test fun reviewedAndUnchangedBecomesOngoing() {
        val stored = reconcile(emptyList(), listOf(card()), 1000).insights.map { it.copy(isRead = true, notifiedAtUtc = 1000) }
        val r = reconcile(stored, listOf(card(effect = 0.42)), 2000)
        assertEquals(InsightState.ONGOING, r.insights.single().state)
        assertTrue(r.toNotify.isEmpty())
    }

    @Test fun materialChangeIsUpdatedAndNotifiesOnce() {
        val stored = reconcile(emptyList(), listOf(card(effect = 0.3)), 1000).insights.map { it.copy(isRead = true, notifiedAtUtc = 1000) }
        val r = reconcile(stored, listOf(card(effect = 0.5)), 2000)
        assertEquals(InsightState.UPDATED, r.insights.single().state)
        assertEquals(1, r.toNotify.size)
        val again = reconcile(r.insights.map { it.copy(notifiedAtUtc = 2000) }, listOf(card(effect = 0.5)), 3000)
        assertTrue(again.toNotify.isEmpty())
    }

    @Test fun missingForTwoScansResolvesButOneMissDoesNot() {
        val stored = reconcile(emptyList(), listOf(card()), 1000).insights.map { it.copy(isRead = true) }
        val one = reconcile(stored, emptyList(), 2000)
        assertEquals(stored.single().state, one.insights.single().state)
        val two = reconcile(one.insights, emptyList(), 3000)
        assertEquals(InsightState.RESOLVED, two.insights.single().state)
        assertTrue(two.insights.single().isRead) // resolved never counts toward the badge
    }

    @Test fun resolvedInsightThatReturnsIsNewAgain() {
        val resolved = reconcile(emptyList(), listOf(card()), 1000).insights.single().copy(state = InsightState.RESOLVED, isRead = true, notifiedAtUtc = 1000)
        val r = reconcile(listOf(resolved), listOf(card()), 5000)
        assertEquals(InsightState.NEW, r.insights.single().state)
        assertEquals(1, r.toNotify.size)
    }

    @Test fun dismissedStaysDismissedUnlessMuchWorse() {
        val dismissed = reconcile(emptyList(), listOf(card(effect = 0.4)), 1000).insights.single().copy(state = InsightState.DISMISSED, isRead = true)
        val same = reconcile(listOf(dismissed), listOf(card(effect = 0.5)), 2000)
        assertEquals(InsightState.DISMISSED, same.insights.single().state)
        assertTrue(same.toNotify.isEmpty())
        val worse = reconcile(listOf(dismissed), listOf(card(effect = 0.7)), 3000)
        assertEquals(InsightState.UPDATED, worse.insights.single().state)
    }

    @Test fun favourablePatternsAreBadgedButNotPushed() {
        val r = reconcile(emptyList(), listOf(card(tone = InsightTone.FAVORABLE)), 1000)
        assertTrue(r.insights.single().isUnreviewed)
        assertTrue(r.toNotify.isEmpty())
    }

    @Test fun oldResolvedInsightsAreDropped() {
        val old = reconcile(emptyList(), listOf(card()), 1000).insights.single().copy(state = InsightState.RESOLVED, isRead = true)
        val r = reconcile(listOf(old), emptyList(), 1000 + 31 * day)
        assertTrue(r.insights.isEmpty())
    }
}
