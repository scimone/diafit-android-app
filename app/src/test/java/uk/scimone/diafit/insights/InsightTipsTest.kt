package uk.scimone.diafit.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.insights.domain.InsightTips
import uk.scimone.diafit.insights.domain.TipTopic

class InsightTipsTest {
    private val all = InsightTips.all()

    @Test fun everyTopicYieldsExactlyThreeDistinctTips() {
        for (topic in TipTopic.entries) for (escalate in listOf(false, true)) {
            val tips = InsightTips.select(topic, escalate)
            assertEquals("$topic", 3, tips.size)
            assertEquals("$topic", 3, tips.toSet().size)
        }
    }

    @Test fun tipsAreShortSingleSentences() {
        for ((topic, tips) in all) for (t in tips) {
            assertTrue("$topic: '$t' too long", t.length <= 120)
            assertEquals("$topic: '$t' should be one sentence", 1, t.count { it == '.' })
            assertTrue("$t", t.endsWith("."))
        }
    }

    @Test fun noInsulinOrTreatmentDirectives() {
        val directive = Regex(
            "\\b(increase|decrease|reduce|raise|lower|adjust|change|skip|stop|double|halve|take|give|add|bolus|inject)\\b[^.]*\\b(insulin|dose|doses|units|basal|bolus|ratio|correction)\\b",
            RegexOption.IGNORE_CASE
        )
        val banned = Regex("\\b(you must|you should|need to|dose|units|U/h)\\b", RegexOption.IGNORE_CASE)
        for ((topic, tips) in all) for (t in tips) {
            assertFalse("$topic: '$t' reads like a dosing instruction", directive.containsMatchIn(t))
            assertFalse("$topic: '$t'", banned.containsMatchIn(t))
        }
    }

    @Test fun tipsDoNotAskTheUserToAnalyseTheirData() {
        val analyse = Regex("\\b(review|investigate|analy[sz]e|study|examine|look (back )?at your|check your (data|graph|chart|history)|go through)\\b", RegexOption.IGNORE_CASE)
        for ((topic, tips) in all) for (t in tips) assertFalse("$topic: '$t'", analyse.containsMatchIn(t))
    }

    @Test fun causesUseCautiousWording() {
        val cautious = Regex("\\b(may|can|often|could|suggests|usually)\\b", RegexOption.IGNORE_CASE)
        val causal = Regex("\\b(because of|is caused by|are caused by|due to|proves)\\b", RegexOption.IGNORE_CASE)
        for ((topic, tips) in all) {
            assertTrue("$topic understand tip should be cautious", cautious.containsMatchIn(tips.first()))
            for (t in tips) assertFalse("$topic: '$t'", causal.containsMatchIn(t))
        }
    }

    @Test fun careTeamIsOnlyMentionedForRecurringLows() {
        for ((topic, tips) in all) {
            val mentions = tips.count { "team" in it.lowercase() }
            if (topic.isLow) assertEquals(1, mentions) else assertEquals("$topic", 0, mentions)
        }
        // And first for escalated lows, last (replaced by a practical line) otherwise.
        assertTrue("team" in InsightTips.select(TipTopic.LOW_NIGHT, escalate = true)[2].lowercase())
        assertFalse("team" in InsightTips.select(TipTopic.LOW_NIGHT, escalate = false)[2].lowercase())
    }

    @Test fun variantsAvoidTipsAlreadyInUse() {
        val first = InsightTips.select(TipTopic.HIGH_DAY, false)
        val second = InsightTips.select(TipTopic.HIGH_DAY, false, used = first.toSet())
        assertTrue(first.zip(second).all { (a, b) -> a != b })
    }

    @Test fun favourableTopicsIncludeAWayToKeepWhatWorks() {
        for (topic in listOf(TipTopic.STEADY_NIGHT, TipTopic.STEADY_DAY, TipTopic.WEEKEND_BETTER)) {
            val keep = Regex("\\b(keep|stick|build|reuse|borrow)\\b", RegexOption.IGNORE_CASE)
            assertTrue("$topic", InsightTips.select(topic, false).drop(1).any { keep.containsMatchIn(it) })
        }
    }
}
