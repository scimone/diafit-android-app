package uk.scimone.diafit.core.domain.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

fun formatTimestamp(epochMillis: Long): String {
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}
