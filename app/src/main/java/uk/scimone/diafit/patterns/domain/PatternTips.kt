package uk.scimone.diafit.patterns.domain

/**
 * The three tips every pattern shows, in the roles of the concept: understand (a cautious likely reason), act (one
 * practical step) and maintain/improve. Each role has ordered variants so unrelated patterns don't repeat the same
 * sentence. Wording rules (checked by PatternTipsTest): plain language, one sentence, association not cause, no
 * insulin dose or treatment changes, no asking the user to analyse data. The diabetes team is mentioned only for
 * recurring lows. A tip is stored as the name of its string resource (`tip_<topic>_<role><n>`), so it is shown in the
 * language the app has when the pattern is opened; the wording lives in strings.xml.
 */
object PatternTips {
    private class Set(val understand: List<String>, val act: List<String>, val maintain: List<String>)

    private val sets: Map<TipTopic, Set> = mapOf(
        TipTopic.LOW_NIGHT to Set(
            understand = listOf("tip_low_night_u1", "tip_low_night_u2"),
            act = listOf("tip_low_night_a1", "tip_low_night_a2"),
            maintain = listOf("tip_low_night_m1", "tip_low_night_m2")
        ),
        TipTopic.LOW_DAY to Set(
            understand = listOf("tip_low_day_u1", "tip_low_day_u2"),
            act = listOf("tip_low_day_a1", "tip_low_day_a2"),
            maintain = listOf("tip_low_day_m1", "tip_low_day_m2")
        ),
        TipTopic.HIGH_DAY to Set(
            understand = listOf("tip_high_day_u1", "tip_high_day_u2"),
            act = listOf("tip_high_day_a1", "tip_high_day_a2"),
            maintain = listOf("tip_high_day_m1", "tip_high_day_m2")
        ),
        TipTopic.HIGH_NIGHT to Set(
            understand = listOf("tip_high_night_u1", "tip_high_night_u2"),
            act = listOf("tip_high_night_a1", "tip_high_night_a2"),
            maintain = listOf("tip_high_night_m1", "tip_high_night_m2")
        ),
        TipTopic.VARIABILITY to Set(
            understand = listOf("tip_variability_u1", "tip_variability_u2"),
            act = listOf("tip_variability_a1", "tip_variability_a2"),
            maintain = listOf("tip_variability_m1", "tip_variability_m2")
        ),
        TipTopic.DAWN to Set(
            understand = listOf("tip_dawn_u1", "tip_dawn_u2"),
            act = listOf("tip_dawn_a1", "tip_dawn_a2"),
            maintain = listOf("tip_dawn_m1", "tip_dawn_m2")
        ),
        TipTopic.STEADY_NIGHT to Set(
            understand = listOf("tip_steady_night_u1", "tip_steady_night_u2"),
            act = listOf("tip_steady_night_a1", "tip_steady_night_a2"),
            maintain = listOf("tip_steady_night_m1", "tip_steady_night_m2")
        ),
        TipTopic.STEADY_DAY to Set(
            understand = listOf("tip_steady_day_u1", "tip_steady_day_u2"),
            act = listOf("tip_steady_day_a1", "tip_steady_day_a2"),
            maintain = listOf("tip_steady_day_m1", "tip_steady_day_m2")
        ),
        TipTopic.WEEKEND_BETTER to Set(
            understand = listOf("tip_weekend_better_u1", "tip_weekend_better_u2"),
            act = listOf("tip_weekend_better_a1", "tip_weekend_better_a2"),
            maintain = listOf("tip_weekend_better_m1", "tip_weekend_better_m2")
        ),
        TipTopic.MEAL_LOW to Set(
            understand = listOf("tip_meal_low_u1", "tip_meal_low_u2"),
            act = listOf("tip_meal_low_a1", "tip_meal_low_a2"),
            maintain = listOf("tip_meal_low_m1", "tip_meal_low_m2")
        ),
        TipTopic.MEAL_SPIKE to Set(
            understand = listOf("tip_meal_spike_u1", "tip_meal_spike_u2"),
            act = listOf("tip_meal_spike_a1", "tip_meal_spike_a2"),
            maintain = listOf("tip_meal_spike_m1", "tip_meal_spike_m2")
        ),
        TipTopic.MEAL_SLOW to Set(
            understand = listOf("tip_meal_slow_u1", "tip_meal_slow_u2"),
            act = listOf("tip_meal_slow_a1", "tip_meal_slow_a2"),
            maintain = listOf("tip_meal_slow_m1", "tip_meal_slow_m2")
        ),
        TipTopic.MEAL_GOOD to Set(
            understand = listOf("tip_meal_good_u1", "tip_meal_good_u2"),
            act = listOf("tip_meal_good_a1", "tip_meal_good_a2"),
            maintain = listOf("tip_meal_good_m1", "tip_meal_good_m2")
        ),
        TipTopic.MEAL_RICH to Set(
            understand = listOf("tip_meal_rich_u1", "tip_meal_rich_u2"),
            act = listOf("tip_meal_rich_a1", "tip_meal_rich_a2"),
            maintain = listOf("tip_meal_rich_m1", "tip_meal_rich_m2")
        ),
        TipTopic.MEAL_TYPE to Set(
            understand = listOf("tip_meal_type_u1", "tip_meal_type_u2"),
            act = listOf("tip_meal_type_a1", "tip_meal_type_a2"),
            maintain = listOf("tip_meal_type_m1", "tip_meal_type_m2")
        ),
        TipTopic.MEAL_BOLUS to Set(
            understand = listOf("tip_meal_bolus_u1", "tip_meal_bolus_u2"),
            act = listOf("tip_meal_bolus_a1", "tip_meal_bolus_a2"),
            maintain = listOf("tip_meal_bolus_m1", "tip_meal_bolus_m2")
        ),
        TipTopic.EX_DROP to Set(
            understand = listOf("tip_ex_drop_u1", "tip_ex_drop_u2"),
            act = listOf("tip_ex_drop_a1", "tip_ex_drop_a2"),
            maintain = listOf("tip_ex_drop_m1", "tip_ex_drop_m2")
        ),
        TipTopic.EX_LOW_NOW to Set(
            understand = listOf("tip_ex_low_now_u1", "tip_ex_low_now_u2"),
            act = listOf("tip_ex_low_now_a1", "tip_ex_low_now_a2"),
            maintain = listOf("tip_ex_low_now_m1", "tip_ex_low_now_m2")
        ),
        TipTopic.EX_LOW_LATER to Set(
            understand = listOf("tip_ex_low_later_u1", "tip_ex_low_later_u2"),
            act = listOf("tip_ex_low_later_a1", "tip_ex_low_later_a2"),
            maintain = listOf("tip_ex_low_later_m1", "tip_ex_low_later_m2")
        ),
        TipTopic.EX_RISE to Set(
            understand = listOf("tip_ex_rise_u1", "tip_ex_rise_u2"),
            act = listOf("tip_ex_rise_a1", "tip_ex_rise_a2"),
            maintain = listOf("tip_ex_rise_m1", "tip_ex_rise_m2")
        ),
        TipTopic.EX_GOOD to Set(
            understand = listOf("tip_ex_good_u1", "tip_ex_good_u2"),
            act = listOf("tip_ex_good_a1", "tip_ex_good_a2"),
            maintain = listOf("tip_ex_good_m1", "tip_ex_good_m2")
        ),
        TipTopic.EX_LONG to Set(
            understand = listOf("tip_ex_long_u1", "tip_ex_long_u2"),
            act = listOf("tip_ex_long_a1", "tip_ex_long_a2"),
            maintain = listOf("tip_ex_long_m1", "tip_ex_long_m2")
        ),
        TipTopic.EX_EVENING to Set(
            understand = listOf("tip_ex_evening_u1", "tip_ex_evening_u2"),
            act = listOf("tip_ex_evening_a1", "tip_ex_evening_a2"),
            maintain = listOf("tip_ex_evening_m1", "tip_ex_evening_m2")
        ),
        TipTopic.EX_NEXTDAY to Set(
            understand = listOf("tip_ex_nextday_u1", "tip_ex_nextday_u2"),
            act = listOf("tip_ex_nextday_a1", "tip_ex_nextday_a2"),
            maintain = listOf("tip_ex_nextday_m1", "tip_ex_nextday_m2")
        ),
        TipTopic.EX_INTENSITY to Set(
            understand = listOf("tip_ex_intensity_u1", "tip_ex_intensity_u2"),
            act = listOf("tip_ex_intensity_a1", "tip_ex_intensity_a2"),
            maintain = listOf("tip_ex_intensity_m1", "tip_ex_intensity_m2")
        ),
        TipTopic.SLEEP_SHORT to Set(
            understand = listOf("tip_sleep_short_u1", "tip_sleep_short_u2"),
            act = listOf("tip_sleep_short_a1", "tip_sleep_short_a2"),
            maintain = listOf("tip_sleep_short_m1", "tip_sleep_short_m2")
        ),
        TipTopic.SLEEP_RESTLESS to Set(
            understand = listOf("tip_sleep_restless_u1", "tip_sleep_restless_u2"),
            act = listOf("tip_sleep_restless_a1", "tip_sleep_restless_a2"),
            maintain = listOf("tip_sleep_restless_m1", "tip_sleep_restless_m2")
        ),
        TipTopic.SLEEP_IRREGULAR to Set(
            understand = listOf("tip_sleep_irregular_u1", "tip_sleep_irregular_u2"),
            act = listOf("tip_sleep_irregular_a1", "tip_sleep_irregular_a2"),
            maintain = listOf("tip_sleep_irregular_m1", "tip_sleep_irregular_m2")
        ),
        TipTopic.TREND_BETTER to Set(
            understand = listOf("tip_trend_better_u1", "tip_trend_better_u2"),
            act = listOf("tip_trend_better_a1", "tip_trend_better_a2"),
            maintain = listOf("tip_trend_better_m1", "tip_trend_better_m2")
        ),
        TipTopic.TREND_WORSE to Set(
            understand = listOf("tip_trend_worse_u1", "tip_trend_worse_u2"),
            act = listOf("tip_trend_worse_a1", "tip_trend_worse_a2"),
            maintain = listOf("tip_trend_worse_m1", "tip_trend_worse_m2")
        ),
        TipTopic.TREND_LOWS_UP to Set(
            understand = listOf("tip_trend_lows_up_u1", "tip_trend_lows_up_u2"),
            act = listOf("tip_trend_lows_up_a1", "tip_trend_lows_up_a2"),
            maintain = listOf("tip_trend_lows_up_m1", "tip_trend_lows_up_m2")
        ),
        TipTopic.COMBO_LATE_DINNER to Set(
            understand = listOf("tip_combo_late_dinner_u1", "tip_combo_late_dinner_u2"),
            act = listOf("tip_combo_late_dinner_a1", "tip_combo_late_dinner_a2"),
            maintain = listOf("tip_combo_late_dinner_m1", "tip_combo_late_dinner_m2")
        ),
        TipTopic.COMBO_EVENING_LOW to Set(
            understand = listOf("tip_combo_evening_low_u1", "tip_combo_evening_low_u2"),
            act = listOf("tip_combo_evening_low_a1", "tip_combo_evening_low_a2"),
            maintain = listOf("tip_combo_evening_low_m1", "tip_combo_evening_low_m2")
        ),
        TipTopic.COMBO_SLEEP_BREAKFAST to Set(
            understand = listOf("tip_combo_sleep_breakfast_u1", "tip_combo_sleep_breakfast_u2"),
            act = listOf("tip_combo_sleep_breakfast_a1", "tip_combo_sleep_breakfast_a2"),
            maintain = listOf("tip_combo_sleep_breakfast_m1", "tip_combo_sleep_breakfast_m2")
        ),
        TipTopic.CTX_EX_PRE to Set(
            understand = listOf("tip_ctx_ex_pre_u1", "tip_ctx_ex_pre_u2"),
            act = listOf("tip_ctx_ex_pre_a1", "tip_ctx_ex_pre_a2"),
            maintain = listOf("tip_ctx_ex_pre_m1", "tip_ctx_ex_pre_m2")
        ),
        TipTopic.CTX_EX_GAP to Set(
            understand = listOf("tip_ctx_ex_gap_u1", "tip_ctx_ex_gap_u2"),
            act = listOf("tip_ctx_ex_gap_a1", "tip_ctx_ex_gap_a2"),
            maintain = listOf("tip_ctx_ex_gap_m1", "tip_ctx_ex_gap_m2")
        ),
        TipTopic.CTX_EX_TYPE to Set(
            understand = listOf("tip_ctx_ex_type_u1", "tip_ctx_ex_type_u2"),
            act = listOf("tip_ctx_ex_type_a1", "tip_ctx_ex_type_a2"),
            maintain = listOf("tip_ctx_ex_type_m1", "tip_ctx_ex_type_m2")
        ),
        TipTopic.CTX_MEAL_DELAY to Set(
            understand = listOf("tip_ctx_meal_delay_u1", "tip_ctx_meal_delay_u2"),
            act = listOf("tip_ctx_meal_delay_a1", "tip_ctx_meal_delay_a2"),
            maintain = listOf("tip_ctx_meal_delay_m1", "tip_ctx_meal_delay_m2")
        ),
        TipTopic.CTX_MEAL_WORSE to Set(
            understand = listOf("tip_ctx_meal_worse_u1", "tip_ctx_meal_worse_u2"),
            act = listOf("tip_ctx_meal_worse_a1", "tip_ctx_meal_worse_a2"),
            maintain = listOf("tip_ctx_meal_worse_m1", "tip_ctx_meal_worse_m2")
        ),
        TipTopic.CTX_MEAL_BETTER to Set(
            understand = listOf("tip_ctx_meal_better_u1", "tip_ctx_meal_better_u2"),
            act = listOf("tip_ctx_meal_better_a1", "tip_ctx_meal_better_a2"),
            maintain = listOf("tip_ctx_meal_better_m1", "tip_ctx_meal_better_m2")
        ),
        TipTopic.CTX_NIGHT_BETTER to Set(
            understand = listOf("tip_ctx_night_better_u1", "tip_ctx_night_better_u2"),
            act = listOf("tip_ctx_night_better_a1", "tip_ctx_night_better_a2"),
            maintain = listOf("tip_ctx_night_better_m1", "tip_ctx_night_better_m2")
        ),
        TipTopic.CTX_NIGHT_WORSE to Set(
            understand = listOf("tip_ctx_night_worse_u1", "tip_ctx_night_worse_u2"),
            act = listOf("tip_ctx_night_worse_a1", "tip_ctx_night_worse_a2"),
            maintain = listOf("tip_ctx_night_worse_m1", "tip_ctx_night_worse_m2")
        ),
        TipTopic.CTX_SLEEP_ACTIVITY to Set(
            understand = listOf("tip_ctx_sleep_activity_u1", "tip_ctx_sleep_activity_u2"),
            act = listOf("tip_ctx_sleep_activity_a1", "tip_ctx_sleep_activity_a2"),
            maintain = listOf("tip_ctx_sleep_activity_m1", "tip_ctx_sleep_activity_m2")
        ),
        TipTopic.CTX_SLEEP_GOOD to Set(
            understand = listOf("tip_ctx_sleep_good_u1", "tip_ctx_sleep_good_u2"),
            act = listOf("tip_ctx_sleep_good_a1", "tip_ctx_sleep_good_a2"),
            maintain = listOf("tip_ctx_sleep_good_m1", "tip_ctx_sleep_good_m2")
        ),
        TipTopic.CTX_LATE_RICH to Set(
            understand = listOf("tip_ctx_late_rich_u1", "tip_ctx_late_rich_u2"),
            act = listOf("tip_ctx_late_rich_a1", "tip_ctx_late_rich_a2"),
            maintain = listOf("tip_ctx_late_rich_m1", "tip_ctx_late_rich_m2")
        ),
        TipTopic.WEEKEND_WORSE to Set(
            understand = listOf("tip_weekend_worse_u1", "tip_weekend_worse_u2"),
            act = listOf("tip_weekend_worse_a1", "tip_weekend_worse_a2"),
            maintain = listOf("tip_weekend_worse_m1", "tip_weekend_worse_m2")
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

    /** Every tip key, for tests. */
    internal fun all(): Map<TipTopic, List<String>> = sets.mapValues { (_, s) -> s.understand + s.act + s.maintain }
}
