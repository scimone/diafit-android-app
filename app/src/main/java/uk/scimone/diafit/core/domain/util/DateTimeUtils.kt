package uk.scimone.diafit.core.domain.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar

private val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

fun formatTimestamp(epochMillis: Long): String {
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

fun nowMinusXMinutes(minutes: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MINUTE, -minutes)
    return calendar.timeInMillis
}