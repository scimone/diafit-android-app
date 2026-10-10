package uk.scimone.diafit.insights.domain

/**
 * The three tips every insight shows, in the roles of the concept: understand (a cautious likely reason), act (one
 * practical step) and maintain/improve. Each role has ordered variants so unrelated insights don't repeat the same
 * sentence. Wording rules (checked by InsightTipsTest): plain language, one sentence, association not cause, no
 * insulin dose or treatment changes, no asking the user to analyse data. The diabetes team is mentioned only for
 * recurring lows.
 */
object InsightTips {
    private class Set(val understand: List<String>, val act: List<String>, val maintain: List<String>)

    private val sets: Map<TipTopic, Set> = mapOf(
        TipTopic.LOW_NIGHT to Set(
            understand = listOf(
                "Insulin from the evening can still be working overnight, which may play a part in lows like these.",
                "Evening activity or a lighter dinner may be linked to glucose dipping while you sleep."
            ),
            act = listOf(
                "Keep fast-acting carbs within reach of your bed.",
                "Glance at your glucose trend arrow before you go to sleep."
            ),
            maintain = listOf(
                "Because these lows keep coming back, it's worth raising them with your diabetes team.",
                "Keeping fast-acting carbs close by means a low stays short."
            )
        ),
        TipTopic.LOW_DAY to Set(
            understand = listOf(
                "Lows at the same time of day often follow a meal, a workout or active insulin from earlier.",
                "Busy stretches with skipped or delayed meals may be linked to these lows."
            ),
            act = listOf(
                "Carry fast-acting carbs on days when this time slot is likely to be busy.",
                "If you feel shaky or hungry around this time, treat it as your hypo plan describes."
            ),
            maintain = listOf(
                "Because these lows keep coming back, it's worth raising them with your diabetes team.",
                "Having a snack ready for this time slot keeps a dip from turning into a low."
            )
        ),
        TipTopic.HIGH_DAY to Set(
            understand = listOf(
                "Meals, snacks or stress can all push glucose up around this time of day.",
                "Higher glucose here may be linked to what and when you eat just before it."
            ),
            act = listOf(
                "A short walk after eating often helps flatten a rise.",
                "Moving a larger meal or snack away from this window may be worth trying."
            ),
            maintain = listOf(
                "Notice which meals keep you steadier here and repeat them.",
                "On days this window stays calmer, keep the routine that got you there."
            )
        ),
        TipTopic.HIGH_NIGHT to Set(
            understand = listOf(
                "Late meals, evening snacks or hormones released overnight may keep glucose elevated.",
                "A heavy or late dinner may be linked to glucose staying high into the night."
            ),
            act = listOf(
                "An earlier or lighter dinner may help glucose settle before bed.",
                "A short walk after dinner is an easy way to ease an evening rise."
            ),
            maintain = listOf(
                "Pick the evenings that went well and make that your usual routine.",
                "A steady bedtime routine can help overnight glucose stay calmer."
            )
        ),
        TipTopic.VARIABILITY to Set(
            understand = listOf(
                "Swings like these often come from differences in meal size, timing or activity between days.",
                "When glucose changes this much, mixed meals and varied routines may play a part."
            ),
            act = listOf(
                "Keeping meal times and portions similar at this time of day can make glucose more predictable.",
                "Plan snacks and activity for this stretch so it looks alike from day to day."
            ),
            maintain = listOf(
                "Once things settle, a steady routine here is worth keeping.",
                "Reuse the meals that gave you smooth results in this window."
            )
        ),
        TipTopic.DAWN to Set(
            understand = listOf(
                "Early-morning hormones can make glucose climb before you wake up.",
                "A late or large evening meal may be linked to a rise in the early hours."
            ),
            act = listOf(
                "An earlier, lighter dinner may smooth the morning rise.",
                "A short walk soon after waking can take the edge off a morning rise."
            ),
            maintain = listOf(
                "Mornings are easier when evenings stay steady, so keep your calm evenings going.",
                "Keep a regular wake-up and breakfast time to make mornings more predictable."
            )
        ),
        TipTopic.STEADY_NIGHT to Set(
            understand = listOf(
                "Your glucose stays steady in this window, which suggests your evening routine is working.",
                "Steady nights usually mean dinner and evening habits are a good match for you."
            ),
            act = listOf(
                "Keep dinner timing and portions close to what they are on these nights.",
                "Stick with the evening habits that work, even on busy days."
            ),
            maintain = listOf(
                "A calm night sets up a good morning, so build on this.",
                "Reuse this routine on days that feel less predictable."
            )
        ),
        TipTopic.STEADY_DAY to Set(
            understand = listOf(
                "Your glucose stays steady in this window, which suggests your meals and activity fit well here.",
                "Steady stretches like this often come from consistent meals and routines."
            ),
            act = listOf(
                "Keep the meals and activity you have in this window.",
                "Stick with what you do here when your schedule changes."
            ),
            maintain = listOf(
                "Borrow this routine for the times of day that are harder.",
                "Build on this one step at a time."
            )
        ),
        TipTopic.WEEKEND_BETTER to Set(
            understand = listOf(
                "Different sleep times, meals or activity may play a part in steadier weekends.",
                "More relaxed days with more regular meals may be linked to steadier glucose."
            ),
            act = listOf(
                "Try keeping one of your helpful weekend routines on weekdays, too.",
                "Bring the weekend meal rhythm into a weekday or two."
            ),
            maintain = listOf(
                "Stick with the habits that help keep your glucose steady.",
                "Keep what works on weekends going."
            )
        ),
        TipTopic.WEEKEND_WORSE to Set(
            understand = listOf(
                "Later meals, different sleep times or less structure may play a part on weekends.",
                "Weekend food and activity often differ from weekdays, which may be linked to this."
            ),
            act = listOf(
                "Try keeping your weekday meal and sleep times on weekends, too.",
                "Plan an activity for the weekend the way you do on weekdays."
            ),
            maintain = listOf(
                "Your weekdays work well, so borrow their rhythm where you can.",
                "Weekday habits are a good template for weekends."
            )
        )
    )

    /**
     * Three tips for [topic]. For each role the first variant not in [used] is taken (all used: the first one, so a
     * topic never runs dry). [escalate] puts the care-team line first in the lows topics.
     */
    fun select(topic: TipTopic, escalate: Boolean, used: kotlin.collections.Set<String> = emptySet()): List<String> {
        val set = sets.getValue(topic)
        val maintain = if (topic.isLow && !escalate) set.maintain.drop(1) + set.maintain.take(1) else set.maintain
        return listOf(set.understand, set.act, maintain).map { variants -> variants.firstOrNull { it !in used } ?: variants.first() }
    }

    /** Every tip sentence, for tests. */
    internal fun all(): Map<TipTopic, List<String>> = sets.mapValues { (_, s) -> s.understand + s.act + s.maintain }
}
