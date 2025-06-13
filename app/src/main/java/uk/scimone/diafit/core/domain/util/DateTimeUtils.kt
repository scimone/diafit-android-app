package uk.scimone.diafit.core.domain.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

fun formatTimestamp(epochMillis: Long): String {
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

fun nowMinusXMinutes(minutes: Int): Long {
    return ZonedDateTime.now().minusMinutes(minutes.toLong()).toInstant().toEpochMilli()
}

fun timestampToLocalDateTime(
    epochMillis: Long,
): ZonedDateTime {
    val localZoneId = ZoneId.systemDefault()
    return Instant.ofEpochMilli(epochMillis).atZone(localZoneId)
}

fun localDateTimeToInstant(localDateTime: LocalDateTime): Instant {
    return localDateTime.atZone(ZoneId.systemDefault()).toInstant()
}

fun instantToLocalDateTime(instant: Instant): LocalDateTime {
    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
}

fun friendlyDateString(epochMillis: Long, nowEpochMillis: Long = System.currentTimeMillis()): String {
    val zoneId = ZoneId.systemDefault()

    val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
    val yesterday = now.minusDays(1)

    val formatterCurrentYear = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    val formatterPreviousYear = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())

    return when {
        date.isEqual(now) -> "Today"
        date.isEqual(yesterday) -> "Yesterday"
        isSameWeek(date, now) -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
        date.year == now.year -> date.format(formatterCurrentYear)
        else -> date.format(formatterPreviousYear)
    }
}

private fun isSameWeek(date1: java.time.LocalDate, date2: java.time.LocalDate): Boolean {
    val weekFields = WeekFields.of(Locale.getDefault())
    val week1 = date1.get(weekFields.weekOfWeekBasedYear())
    val week2 = date2.get(weekFields.weekOfWeekBasedYear())
    return date1.year == date2.year && week1 == week2
}

fun parseIsoToEpoch(isoString: String): Long {
    return try {
        Instant.parse(isoString).toEpochMilli()
    } catch (e: Exception) {
        Instant.now().toEpochMilli()
    }
}