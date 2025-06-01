package uk.scimone.diafit.core.domain.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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