package uk.scimone.diafit.backendsync.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import java.time.Instant

/** Request bodies of the Diafit backend API v2 (`diafit_backend_api.json`), and the mapping from local rows. */

/** Source name the backend stores for rows that originate in this app. */
const val APP_SOURCE = "Diafit"
private const val HEALTH_CONNECT_SOURCE = "HealthConnect"

private val CGM_DIRECTIONS = setOf(
    "NONE", "DoubleUp", "SingleUp", "FortyFiveUp", "Flat", "FortyFiveDown", "SingleDown", "DoubleDown",
    "NotComputable", "RateOutOfRange"
)

/** ISO-8601 in UTC with a `Z` offset (the API's time fields need one). */
fun isoUtc(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

@Serializable
data class CgmIn(
    val timestamp: String,
    @SerialName("value_mgdl") val valueMgdl: Int,
    @SerialName("five_minute_rate_mgdl") val fiveMinuteRateMgdl: Double,
    val direction: String,
    val device: String,
    val source: String,
    @SerialName("source_id") val sourceId: String?
)

@Serializable
data class BolusIn(
    @SerialName("timestamp_utc") val timestampUtc: String,
    @SerialName("created_at_utc") val createdAtUtc: String,
    @SerialName("updated_at_utc") val updatedAtUtc: String,
    val value: Double,
    @SerialName("event_type") val eventType: String,
    @SerialName("is_smb") val isSmb: Boolean,
    @SerialName("pump_type") val pumpType: String?,
    @SerialName("pump_serial") val pumpSerial: String?,
    @SerialName("pump_id") val pumpId: Long?,
    val source: String,
    @SerialName("source_id") val sourceId: String?
)

@Serializable
data class MealIn(
    @SerialName("created_at_utc") val createdAtUtc: String,
    @SerialName("meal_time_utc") val mealTimeUtc: String,
    val description: String?,
    val carbohydrates: Int,
    val calories: Int?,
    val proteins: Int?,
    val fats: Int?,
    @SerialName("impact_type") val impactType: String,
    @SerialName("meal_type") val mealType: String,
    @SerialName("is_valid") val isValid: Boolean,
    @SerialName("image_id") val imageId: String?,
    val recommendation: String?,
    val reasoning: String?,
    val source: String,
    @SerialName("source_id") val sourceId: String
)

/** `MealPatch`: everything of [MealIn] the API lets us change. */
@Serializable
data class MealPatch(
    @SerialName("meal_time_utc") val mealTimeUtc: String,
    val description: String?,
    val carbohydrates: Int,
    val calories: Int?,
    val proteins: Int?,
    val fats: Int?,
    @SerialName("impact_type") val impactType: String,
    @SerialName("meal_type") val mealType: String,
    @SerialName("is_valid") val isValid: Boolean,
    @SerialName("image_id") val imageId: String?,
    val recommendation: String?,
    val reasoning: String?
)

@Serializable
data class HeartRateIn(
    val timestamp: String,
    val value: Int,
    val device: String,
    val source: String,
    @SerialName("source_id") val sourceId: String?
)

@Serializable
data class SleepStageIn(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val stage: Int
)

@Serializable
data class SleepSessionIn(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val source: String,
    @SerialName("source_id") val sourceId: String,
    val device: String,
    val stages: List<SleepStageIn>
)

/** Null for values the API would reject (it validates 1..1000 mg/dL). */
fun CgmEntity.toBackend(): CgmIn? {
    if (valueMgdl !in 1..1000 || !fiveMinuteRateMgdl.isFinite()) return null
    return CgmIn(
        timestamp = isoUtc(timestamp),
        valueMgdl = valueMgdl,
        fiveMinuteRateMgdl = fiveMinuteRateMgdl.toDouble(),
        direction = direction.takeIf { it in CGM_DIRECTIONS } ?: "NONE",
        device = device.take(100),
        source = source.take(100),
        sourceId = sourceId?.take(100)
    )
}

fun BolusEntity.toBackend(): BolusIn? {
    if (value < 0f || !value.isFinite()) return null
    return BolusIn(
        timestampUtc = isoUtc(timestampUtc),
        createdAtUtc = isoUtc(createdAtUtc),
        updatedAtUtc = isoUtc(updatedAtUtc),
        value = value.toDouble(),
        eventType = eventType.ifBlank { "Bolus" }.take(100),
        isSmb = isSmb,
        pumpType = pumpType.ifBlank { null }?.take(100),
        pumpSerial = pumpSerial.ifBlank { null }?.take(100),
        pumpId = pumpId,
        source = APP_SOURCE,
        sourceId = (sourceId ?: "diafit-bolus-$timestampUtc").take(100)
    )
}

/**
 * The backend's id for a meal in the source system. Meals logged in the app have no source id, and local row ids
 * restart after a reinstall, so the creation time is part of the key.
 */
fun MealEntity.backendSourceId(): String = (sourceId ?: "diafit-meal-$createdAtUtc-$id").take(100)

fun MealEntity.toBackend(): MealIn = MealIn(
    createdAtUtc = isoUtc(createdAtUtc),
    mealTimeUtc = isoUtc(mealTimeUtc),
    description = sittingName?.takeIf { it.isNotBlank() && it != description }?.let { name ->
        if (description.isNullOrBlank()) name else "$name: $description"
    } ?: description,
    carbohydrates = carbohydrates.coerceAtLeast(0),
    calories = calories?.takeIf { it >= 0 },
    proteins = proteins?.takeIf { it >= 0 },
    fats = fats?.takeIf { it >= 0 },
    impactType = impactType.name,
    mealType = mealType.name,
    // A carb entry merged into a logged meal is that meal's dose, not a second meal.
    isValid = isValid && mergedIntoId == null,
    imageId = imageId.ifEmpty { null }?.take(255),
    recommendation = recommendation,
    reasoning = reasoning,
    source = if (sourceId == null) APP_SOURCE else "Imported",
    sourceId = backendSourceId()
)

fun MealIn.toPatch(): MealPatch = MealPatch(
    mealTimeUtc, description, carbohydrates, calories, proteins, fats, impactType, mealType, isValid, imageId,
    recommendation, reasoning
)

fun HeartRateEntity.toBackend(): HeartRateIn? {
    if (bpm !in 1..300) return null
    return HeartRateIn(isoUtc(timestamp), bpm, device = "Unknown", source = HEALTH_CONNECT_SOURCE, sourceId = null)
}

/** One session from its stage rows (all with the same `sessionId`). */
fun List<SleepStageEntity>.toBackendSession(): SleepSessionIn? {
    val first = firstOrNull() ?: return null
    if (first.sessionEndUtc <= first.sessionStartUtc) return null
    return SleepSessionIn(
        startTime = isoUtc(first.sessionStartUtc),
        endTime = isoUtc(first.sessionEndUtc),
        source = HEALTH_CONNECT_SOURCE,
        sourceId = first.sessionId.take(255),
        device = "Unknown",
        stages = filter { it.endUtc > it.startUtc }.sortedBy { it.startUtc }.take(2000)
            .map { SleepStageIn(isoUtc(it.startUtc), isoUtc(it.endUtc), it.stage) }
    )
}
