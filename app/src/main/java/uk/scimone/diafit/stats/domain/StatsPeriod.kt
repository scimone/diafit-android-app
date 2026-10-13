package uk.scimone.diafit.stats.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import androidx.annotation.StringRes
import uk.scimone.diafit.R
import uk.scimone.diafit.core.presentation.AppStrings
import uk.scimone.diafit.core.presentation.formatLocalized

/** The time frames of the Stats tab: a day, and a week / month / year drawn as bars. */
enum class StatsPeriod(@StringRes private val labelRes: Int, @StringRes private val titleRes: Int) {
    DAY(R.string.stats_p_day_short, R.string.stats_p_day),
    WEEK(R.string.stats_p_week_short, R.string.stats_p_week),
    MONTH(R.string.stats_p_month_short, R.string.stats_p_month),
    YEAR(R.string.stats_p_year_short, R.string.stats_p_year);

    val label: String get() = AppStrings.get(labelRes)
    val title: String get() = AppStrings.get(titleRes)

    /** Bars of the W/M/Y charts: per day, except a year, which is per month. */
    val barsPerMonth: Boolean get() = this == YEAR
}

/** One bar of a chart: the days it covers and how it is labelled on the axis. */
data class StatsBucket(val first: LocalDate, val lastExclusive: LocalDate, val label: String, val longLabel: String) {
    val days: List<LocalDate> get() = generateSequence(first) { it.plusDays(1) }.takeWhile { it < lastExclusive }.toList()
    operator fun contains(date: LocalDate) = date >= first && date < lastExclusive
}

/**
 * The days a Stats page shows. [offset] pages through time: 0 is the current day / week / month / year,
 * -1 the one before, and so on (the future isn't reachable).
 */
data class StatsWindow(val period: StatsPeriod, val first: LocalDate, val lastExclusive: LocalDate) {

    val days: List<LocalDate> get() = generateSequence(first) { it.plusDays(1) }.takeWhile { it < lastExclusive }.toList()

    /** The bars: one per day for a week or month, one per month for a year, one for a day. */
    val buckets: List<StatsBucket> by lazy {
        when (period) {
            StatsPeriod.DAY -> listOf(StatsBucket(first, lastExclusive, "", first.formatLocalized(DAY_SKELETON)))
            StatsPeriod.WEEK -> days.map {
                StatsBucket(it, it.plusDays(1), it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), it.formatLocalized(DAY_SKELETON))
            }
            StatsPeriod.MONTH -> days.map { StatsBucket(it, it.plusDays(1), it.dayOfMonth.toString(), it.formatLocalized(DAY_SKELETON)) }
            StatsPeriod.YEAR -> generateSequence(first) { it.plusMonths(1) }.takeWhile { it < lastExclusive }.map {
                StatsBucket(
                    it, it.plusMonths(1),
                    it.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    it.formatLocalized("yMMMM")
                )
            }.toList()
        }
    }

    fun label(today: LocalDate): String = when (period) {
        StatsPeriod.DAY -> when (first) {
            today -> AppStrings.get(R.string.date_today)
            today.minusDays(1) -> AppStrings.get(R.string.date_yesterday)
            else -> first.formatLocalized(DAY_SKELETON)
        }
        StatsPeriod.WEEK -> {
            val last = lastExclusive.minusDays(1)
            val sameMonth = first.month == last.month
            if (sameMonth) "${first.formatLocalized("d")} – ${last.formatLocalized("dMMMM")}"
            else "${first.formatLocalized("dMMM")} – ${last.formatLocalized("dMMMy")}"
        }
        StatsPeriod.MONTH -> first.formatLocalized("yMMMM")
        StatsPeriod.YEAR -> first.year.toString()
    }

    /** The window [steps] pages away (negative = older). */
    fun shifted(steps: Int): StatsWindow = of(period, first, steps)

    companion object {
        /** The window [offset] pages from the one containing [today]. */
        fun of(period: StatsPeriod, today: LocalDate, offset: Int = 0): StatsWindow = when (period) {
            StatsPeriod.DAY -> today.plusDays(offset.toLong()).let { StatsWindow(period, it, it.plusDays(1)) }
            StatsPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
                .let { StatsWindow(period, it, it.plusDays(7)) }
            StatsPeriod.MONTH -> today.withDayOfMonth(1).plusMonths(offset.toLong()).let { StatsWindow(period, it, it.plusMonths(1)) }
            StatsPeriod.YEAR -> today.withDayOfYear(1).plusYears(offset.toLong()).let { StatsWindow(period, it, it.plusYears(1)) }
        }

        /** How many pages [date] lies from the page containing [today] (0 for today and for any future date). */
        fun offsetOf(period: StatsPeriod, today: LocalDate, date: LocalDate): Int {
            val target = of(period, date).first
            val current = of(period, today).first
            return when (period) {
                StatsPeriod.DAY -> java.time.temporal.ChronoUnit.DAYS.between(current, target)
                StatsPeriod.WEEK -> java.time.temporal.ChronoUnit.WEEKS.between(current, target)
                StatsPeriod.MONTH -> java.time.temporal.ChronoUnit.MONTHS.between(current, target)
                StatsPeriod.YEAR -> java.time.temporal.ChronoUnit.YEARS.between(current, target)
            }.toInt().coerceAtMost(0)
        }

        private const val DAY_SKELETON = "EEEdMMM"
    }
}
