package uk.scimone.diafit.home.presentation.model

import uk.scimone.diafit.core.domain.model.CgmEntity
import java.time.Instant
import java.time.temporal.ChronoUnit

data class CgmEntityUi(
    val value: Int?,
    val rate: Float?,
    val direction: String?,
    val device: String?,
    val source: String?,
    val timeSince: String?,
    val isStale: Boolean = false
)

// Extension to convert domain model to UI model
fun CgmEntity.toCgmEntityUi(): CgmEntityUi {
    val now = Instant.now()
    val last = Instant.ofEpochMilli(timestamp)
    val durationMinutes = ChronoUnit.MINUTES.between(last, now)

    return CgmEntityUi(
        value = valueMgdl,
        rate = fiveMinuteRateMgdl.toNormalizedRate(),
        direction = direction,
        device = device,
        source = source,
        timeSince = timestamp.toTimeSince(),
        isStale = durationMinutes > 10
    )
}

// Clamp the rate to [-3, 3] and normalize to [0, 1]
fun Float.toNormalizedRate(): Float {
    return ((this.coerceIn(-3f, 3f) + 3) / 6)
}

// Extension to format timestamp into "xh ym zs"
fun Long.toTimeSince(): String {
    val now = Instant.now()
    val last = Instant.ofEpochMilli(this)
    val duration = ChronoUnit.SECONDS.between(last, now)

    val hours = duration / 3600
    val minutes = (duration % 3600) / 60
    val seconds = duration % 60

    return buildString {
        if (hours > 0) append("${hours}h ")
        if (minutes > 0 || hours > 0) append("${minutes}m ")
        append("${seconds}s")
    }
}
