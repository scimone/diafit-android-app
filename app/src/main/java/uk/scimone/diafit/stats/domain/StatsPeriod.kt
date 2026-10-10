package uk.scimone.diafit.stats.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** The time frames of the Stats tab: a day, and a week / month / year drawn as bars. */
enum class StatsPeriod(val label: String, val title: String) {
    DAY("D", "Day"),
    WEEK("W", "Week"),
    MONTH("M", "Month"),
    YEAR("Y", "Year");

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
            StatsPeriod.DAY -> listOf(StatsBucket(first, lastExclusive, "", dayFormat.format(first)))
            StatsPeriod.WEEK -> days.map {
                StatsBucket(it, it.plusDays(1), it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), dayFormat.format(it))
            }
            StatsPeriod.MONTH -> days.map { StatsBucket(it, it.plusDays(1), it.dayOfMonth.toString(), dayFormat.format(it)) }
            StatsPeriod.YEAR -> generateSequence(first) { it.plusMonths(1) }.takeWhile { it < lastExclusive }.map {
                StatsBucket(
                    it, it.plusMonths(1),
                    it.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    it.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + it.year
                )
            }.toList()
        }
    }

    fun label(today: LocalDate): String = when (period) {
        StatsPeriod.DAY -> when (first) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> dayFormat.format(first)
        }
        StatsPeriod.WEEK -> {
            val last = lastExclusive.minusDays(1)
            val sameMonth = first.month == last.month
            if (sameMonth) "${first.dayOfMonth} – ${last.dayOfMonth} ${monthFormat.format(last)}"
            else "${first.dayOfMonth} ${shortMonthFormat.format(first)} – ${last.dayOfMonth} ${shortMonthFormat.format(last)} ${last.year}"
        }
        StatsPeriod.MONTH -> monthFormat.format(first)
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

        private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
        private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
        private val shortMonthFormat = DateTimeFormatter.ofPattern("MMM")
    }
}
