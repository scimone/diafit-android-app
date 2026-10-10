package uk.scimone.diafit.core.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One value of a Health Connect metric that has no table of its own: resting heart rate, heart rate
 * variability (RMSSD) and calories burned. [timeUtc] is the sample time, or the start of the hour for
 * calories (each row is the energy of that hour), so a re-import replaces the same row.
 */
@Entity(indices = [Index(value = ["type", "timeUtc"], unique = true)])
data class HealthMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    /** [HealthMetricType.code] */
    val type: Int,
    val timeUtc: Long,
    val value: Double
)

enum class HealthMetricType(val code: Int) {
    /** bpm */
    RESTING_HEART_RATE(1),
    /** RMSSD, ms */
    HEART_RATE_VARIABILITY(2),
    /** kcal burned in the hour, everything (resting metabolism included) */
    TOTAL_CALORIES(3),
    /** kcal burned in the hour by activity only */
    ACTIVE_CALORIES(4)
}
