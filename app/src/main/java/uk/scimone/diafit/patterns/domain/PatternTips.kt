package uk.scimone.diafit.patterns.domain

/**
 * The three tips every pattern shows, in the roles of the concept: understand (a cautious likely reason), act (one
 * practical step) and maintain/improve. Each role has ordered variants so unrelated patterns don't repeat the same
 * sentence. Wording rules (checked by PatternTipsTest): plain language, one sentence, association not cause, no
 * insulin dose or treatment changes, no asking the user to analyse data. The diabetes team is mentioned only for
 * recurring lows.
 */
object PatternTips {
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
        TipTopic.MEAL_LOW to Set(
            understand = listOf(
                "Insulin taken for a meal can still be working a few hours later, which may be linked to lows after eating.",
                "A lighter meal than usual may play a part in a dip a few hours after eating."
            ),
            act = listOf(
                "Keep fast-acting carbs handy in the hours after meals.",
                "Stay alert to the signs of a low in the hours after you eat."
            ),
            maintain = listOf(
                "Because these lows keep coming back, it's worth raising them with your diabetes team.",
                "Treating a low promptly, as your plan describes, keeps it short."
            )
        ),
        TipTopic.MEAL_SPIKE to Set(
            understand = listOf(
                "Fast-digesting carbs can push glucose up quickly after a meal.",
                "Meal size and the type of carbs may both play a part in a big rise."
            ),
            act = listOf(
                "Pairing carbs with protein or fibre may slow the rise.",
                "A short walk after eating often takes the edge off a rise."
            ),
            maintain = listOf(
                "Notice which meals give you smaller rises and make them your regulars.",
                "Keep the meals that stay gentle on your glucose in rotation."
            )
        ),
        TipTopic.MEAL_SLOW to Set(
            understand = listOf(
                "Fat and protein can slow digestion, so glucose may stay up for hours after a meal.",
                "A large meal can keep glucose elevated for longer."
            ),
            act = listOf(
                "A walk a little while after eating may help glucose come down sooner.",
                "Smaller portions may be easier on the hours after eating."
            ),
            maintain = listOf(
                "Reuse the meals after which your glucose settles back sooner.",
                "Keep the habits that help glucose come back down."
            )
        ),
        TipTopic.MEAL_GOOD to Set(
            understand = listOf(
                "Your glucose rises gently and comes back down, which suggests these meals suit you.",
                "Steady responses like this often come from consistent portions and food choices."
            ),
            act = listOf(
                "Keep portions and food choices close to what they are for these meals.",
                "Reuse these meals when you want a predictable result."
            ),
            maintain = listOf(
                "Build on this one meal at a time.",
                "Save the meals that give you predictable results."
            )
        ),
        TipTopic.MEAL_RICH to Set(
            understand = listOf(
                "Fat and protein can make a meal affect your glucose for longer.",
                "Slow-digesting meals may keep glucose up for hours."
            ),
            act = listOf(
                "Pay attention to your glucose a few hours after richer meals.",
                "Plan for a longer-lasting effect when you have richer meals."
            ),
            maintain = listOf(
                "Save meals that give you more predictable results.",
                "Keep the richer meals that sit well with you."
            )
        ),
        TipTopic.MEAL_TYPE to Set(
            understand = listOf(
                "The time of day can change how your body handles food, which may play a part.",
                "Typical foods at this meal may be linked to its bigger rises."
            ),
            act = listOf(
                "Try the choices from your steadier meals at this meal, too.",
                "A short walk afterwards is an easy thing to add to this meal."
            ),
            maintain = listOf(
                "Keep what works at your steadier meals.",
                "Build on the meals that stay gentle."
            )
        ),
        TipTopic.MEAL_BOLUS to Set(
            understand = listOf(
                "Insulin that starts working before the food does may help keep a rise smaller.",
                "Timing may play a part in how big the rise after a meal is."
            ),
            act = listOf(
                "If you'd like to explore different insulin timing, your diabetes team can help you plan it.",
                "Planning meals a little ahead makes it easier to time things well."
            ),
            maintain = listOf(
                "Keep doing what works on the meals that went smoothly.",
                "Reuse the routines of your smoother meals."
            )
        ),
        TipTopic.EX_DROP to Set(
            understand = listOf(
                "Your muscles use extra glucose while you move, so glucose can drop during activity.",
                "Insulin that is still active from earlier may play a part in dips during activity."
            ),
            act = listOf(
                "Keep a quick carb source with you when you're active.",
                "Stay alert to the signs of a low while you move."
            ),
            maintain = listOf(
                "Notice which activities keep you steadier and make them a habit.",
                "Keep the routines that go well around your workouts."
            )
        ),
        TipTopic.EX_LOW_NOW to Set(
            understand = listOf(
                "Muscles keep taking up glucose right after activity, which may be linked to lows soon afterwards.",
                "Insulin that is still active may add to a low in the first hours after activity."
            ),
            act = listOf(
                "Keep fast-acting carbs handy for the first hours after activity.",
                "Stay alert to the signs of a low after you finish."
            ),
            maintain = listOf(
                "Because these lows keep coming back, it's worth raising them with your diabetes team.",
                "Treating a low promptly, as your plan describes, keeps it short."
            )
        ),
        TipTopic.EX_LOW_LATER to Set(
            understand = listOf(
                "Your muscles may keep using extra glucose for hours after exercise.",
                "Refilling the body's glucose stores can lower glucose long after a workout."
            ),
            act = listOf(
                "Keep an eye on your glucose for several hours after a workout.",
                "Keep fast-acting carbs close by in the evening after a workout."
            ),
            maintain = listOf(
                "Because these lows keep coming back, it's worth raising them with your diabetes team.",
                "Longer workouts may need extra planning under your usual exercise management plan."
            )
        ),
        TipTopic.EX_RISE to Set(
            understand = listOf(
                "Intense or short bursts of activity can make glucose rise for a while.",
                "Stress hormones during hard efforts may push glucose up."
            ),
            act = listOf(
                "A gentle cool-down or walk afterwards may help glucose settle.",
                "Be patient after hard efforts, as glucose often comes back down on its own."
            ),
            maintain = listOf(
                "Keep the workouts that leave you feeling good and steady.",
                "Reuse the types of activity that keep your glucose calmer."
            )
        ),
        TipTopic.EX_GOOD to Set(
            understand = listOf(
                "Your glucose stays steady during activity, which suggests your routine around it works.",
                "Steady responses like this often come from consistent timing and fuelling."
            ),
            act = listOf(
                "Keep the way you prepare for and recover from activity much as it is.",
                "Reuse this routine when you try new activities."
            ),
            maintain = listOf(
                "Build on this, one session at a time.",
                "Keep doing what works around your workouts."
            )
        ),
        TipTopic.EX_LONG to Set(
            understand = listOf(
                "Longer sessions use more glucose, which may be linked to lower glucose afterwards.",
                "The longer you move, the longer muscles can keep taking up glucose."
            ),
            act = listOf(
                "Plan some extra care for glucose in the hours after longer sessions.",
                "Keep fast-acting carbs close by after longer sessions."
            ),
            maintain = listOf(
                "Shorter sessions seem to go smoothly, so keep them in your routine.",
                "Longer workouts may need extra planning under your usual exercise management plan."
            )
        ),
        TipTopic.EX_EVENING to Set(
            understand = listOf(
                "Evening activity can keep glucose lower into the night, which may be linked to this.",
                "Overnight insulin and recovery after an evening workout may play a part."
            ),
            act = listOf(
                "Keep fast-acting carbs near your bed after evening activity.",
                "Be extra alert to the signs of a low after evening activity."
            ),
            maintain = listOf(
                "Earlier sessions seem to leave you steadier, so keep them in the mix.",
                "Keep the habits that work on the days you move earlier."
            )
        ),
        TipTopic.EX_NEXTDAY to Set(
            understand = listOf(
                "Regular movement can help the body use insulin more effectively the next day.",
                "Active days may be linked to steadier glucose the day after."
            ),
            act = listOf(
                "Try to keep a bit of movement in most of your days.",
                "Short walks add up, so fit them in where you can."
            ),
            maintain = listOf(
                "Keep the activity habits that work for you.",
                "Build on this one active day at a time."
            )
        ),
        TipTopic.EX_INTENSITY to Set(
            understand = listOf(
                "Harder efforts can change how much glucose the body uses and releases.",
                "Higher heart rates during activity may be linked to this bigger change."
            ),
            act = listOf(
                "Plan a little extra care around your harder sessions.",
                "A steady, easier pace may suit you better when you want glucose to stay level."
            ),
            maintain = listOf(
                "Keep the intensity that leaves your glucose steadier.",
                "Reuse the kind of session that keeps your glucose calm."
            )
        ),
        TipTopic.SLEEP_SHORT to Set(
            understand = listOf(
                "Short nights can make the body respond less well to insulin the next day, which may show up in glucose.",
                "Tired days often bring more snacking and less movement, which may be linked to this."
            ),
            act = listOf(
                "Try moving your bedtime a little earlier on the nights you can.",
                "Winding down at a regular time gives you the best chance of a full night."
            ),
            maintain = listOf(
                "Protect the nights you sleep well, as they seem to go with steadier days.",
                "Keep the evening habits that help you get a full night."
            )
        ),
        TipTopic.SLEEP_RESTLESS to Set(
            understand = listOf(
                "Restless nights may be linked to stress, late meals, caffeine or glucose swings, and they can carry into the next day.",
                "Broken sleep can leave you tired and less active the next day, which may be linked to this."
            ),
            act = listOf(
                "A dark, cool and quiet bedroom may help you sleep more soundly.",
                "Try keeping caffeine and large meals away from bedtime."
            ),
            maintain = listOf(
                "Keep whatever helps you settle on your calmer nights.",
                "Repeat the evening routine of your quieter nights."
            )
        ),
        TipTopic.SLEEP_IRREGULAR to Set(
            understand = listOf(
                "Going to bed at very different times can unsettle the body clock, which may be linked to less steady days.",
                "A shifting bedtime often shifts meals and activity too, which may play a part."
            ),
            act = listOf(
                "Aim for a bedtime within about an hour of your usual one.",
                "Pick a regular wind-down time and keep to it where you can."
            ),
            maintain = listOf(
                "Keep the regular evenings that go with your steadier days.",
                "A steady routine is a good habit to hold on to."
            )
        ),
        TipTopic.TREND_BETTER to Set(
            understand = listOf(
                "Steadier stretches often follow more regular meals, sleep and activity, which may be what is behind this.",
                "A calmer period can come from routines settling in, which may be linked to this."
            ),
            act = listOf(
                "Think about what has been different lately and try to keep it going.",
                "Stick with the routines of the past two weeks while they are working."
            ),
            maintain = listOf(
                "Keep doing what you have been doing, as it seems to be paying off.",
                "Build on this good run one day at a time."
            )
        ),
        TipTopic.TREND_WORSE to Set(
            understand = listOf(
                "Illness, stress, a new sensor or a change in routine can all move glucose for a couple of weeks.",
                "Changes in sleep, meals or activity may be linked to this shift."
            ),
            act = listOf(
                "Ease back towards the routines that worked for you before.",
                "A regular rhythm of meals, sleep and movement often helps glucose settle."
            ),
            maintain = listOf(
                "Small steady habits tend to bring the numbers back over time.",
                "Pick one routine to tighten up first and build from there."
            )
        ),
        TipTopic.TREND_LOWS_UP to Set(
            understand = listOf(
                "Lows becoming more frequent can follow changes in activity, meals or routine.",
                "More lows can come from a stretch of extra activity or lighter eating, which may be linked to this."
            ),
            act = listOf(
                "Keep fast-acting carbs with you wherever you go.",
                "Carry a quick snack for the times of day when lows tend to show up."
            ),
            maintain = listOf(
                "Because lows are becoming more frequent, it's worth raising them with your diabetes team.",
                "Having fast-acting carbs at hand keeps a low short."
            )
        ),
        TipTopic.COMBO_LATE_DINNER to Set(
            understand = listOf(
                "Food eaten late can still be digesting while you sleep, which may keep glucose higher through the night.",
                "Late dinners often overlap with the hours when the body is less sensitive to insulin, which may play a part."
            ),
            act = listOf(
                "Try finishing dinner a little earlier on the evenings you can.",
                "A lighter late meal may sit more gently overnight."
            ),
            maintain = listOf(
                "Keep the earlier dinners going, as they seem to go with calmer nights.",
                "Stick with the dinner times that give you steadier nights."
            )
        ),
        TipTopic.COMBO_EVENING_LOW to Set(
            understand = listOf(
                "Activity in the evening can keep using up glucose for many hours afterwards, which may be linked to these lows.",
                "Muscles refill their energy stores overnight after exercise, which can pull glucose down."
            ),
            act = listOf(
                "Have fast-acting carbs within reach by your bed on evenings you've been active.",
                "A small snack before bed on active evenings may help keep the night steadier."
            ),
            maintain = listOf(
                "Because these lows keep coming back, it's worth raising them with your diabetes team.",
                "Keeping fast-acting carbs close by means a low stays short."
            )
        ),
        TipTopic.COMBO_SLEEP_BREAKFAST to Set(
            understand = listOf(
                "After a short night the body can respond less well to insulin, which may make breakfast raise glucose more.",
                "Tiredness and stress hormones after poor sleep may be linked to a bigger rise."
            ),
            act = listOf(
                "After a short night, a gentler breakfast may be worth a try.",
                "A short walk after breakfast often helps flatten the rise."
            ),
            maintain = listOf(
                "Protect your sleep where you can, as longer nights seem to go with calmer mornings.",
                "Keep the evening habits that help you get a full night."
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
