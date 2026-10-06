package uk.scimone.diafit.journal.presentation.model

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.model.MealType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class MealEntityUi(
    override val id: Int,
    val mealTimeUtc: Long,
    val carbohydrates: Int = 0,
    val proteins: Int? = null,
    val fats: Int? = null,
    val calories: Int? = null,
    val impactType: ImpactType,
    val mealType: MealType,
    val timeFormatted: String,
    /** User/AI description, or null when none was entered. */
    val description: String?,
    val imageUri: Uri?,
    val reasoning: String?,
    /** Imported from another app (e.g. AAPS) rather than logged here. */
    val isImported: Boolean,
    val timeInRange: Double,
    val timeAboveRange: Double,
    val timeBelowRange: Double,
    /** False while there are no CGM readings in the meal's window (e.g. a just-logged meal). */
    val hasGlucoseData: Boolean,
    val glucoseStatus: GlucoseStatus = GlucoseStatus.READY,
    /** Every photo, cover first (all courses' photos for a multi-course meal). */
    val photoUris: List<Uri> = listOfNotNull(imageUri),
    /** The courses this entry stands for (just [id] for a single-course meal). */
    val courseIds: List<Int> = listOf(id),
    /** When the last course was eaten (== [mealTimeUtc] for a single course). */
    val endTimeUtc: Long = mealTimeUtc
) : JournalEntryUi {
    override val kind: JournalEntryKind get() = JournalEntryKind.MEAL
    override val timeUtc: Long get() = mealTimeUtc

    /** Headline for lists: the description, falling back to the meal type. */
    val title: String get() = description?.takeIf { it.isNotBlank() } ?: mealType.type

    val courseCount: Int get() = courseIds.size
}

/** Whether a meal's glucose outcome can be shown yet. */
enum class GlucoseStatus { READY, TOO_EARLY, NOT_ENOUGH_DATA }

data class GlucoseImpact(
    val timeInRange: Double,
    val timeAboveRange: Double,
    val timeBelowRange: Double,
    val status: GlucoseStatus = GlucoseStatus.READY
) {
    val hasData: Boolean get() = timeInRange + timeAboveRange + timeBelowRange > 0.0
}

private fun formatTime(millis: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

/** Content uri of a stored meal photo, or null if the file is gone. */
fun mealPhotoUri(context: Context, imageId: String): Uri? {
    if (imageId.isEmpty()) return null
    val imageFile = File(context.filesDir, "meal_images/$imageId.jpg")
    return if (imageFile.exists()) FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile) else null
}

/** A whole meal as one journal entry: summed nutrition, all photos, the slowest absorption. */
fun MealSitting.toUi(context: Context, impact: GlucoseImpact): MealEntityUi {
    if (!isExtended) return courses.first().toUi(context, impact)
    val first = courses.first()
    fun sumOrNull(f: (MealEntity) -> Int?) = courses.mapNotNull(f).takeIf { it.isNotEmpty() }?.sum()
    val photos = courses.flatMap { c -> c.photoIds.mapNotNull { mealPhotoUri(context, it) } }
    return MealEntityUi(
        id = first.id,
        mealTimeUtc = startTime,
        carbohydrates = totalCarbs,
        proteins = sumOrNull { it.proteins },
        fats = sumOrNull { it.fats },
        calories = sumOrNull { it.calories },
        impactType = courses.maxBy { it.impactType.durationMinutes }.impactType,
        mealType = first.mealType,
        timeFormatted = "${formatTime(startTime)}–${formatTime(endTime)}",
        description = title,
        imageUri = photos.firstOrNull(),
        reasoning = null,
        isImported = courses.all { it.sourceId != null },
        timeInRange = impact.timeInRange,
        timeAboveRange = impact.timeAboveRange,
        timeBelowRange = impact.timeBelowRange,
        hasGlucoseData = impact.hasData,
        glucoseStatus = impact.status,
        photoUris = photos,
        courseIds = courses.map { it.id },
        endTimeUtc = endTime
    )
}

fun MealEntity.toUi(context: Context, impact: GlucoseImpact): MealEntityUi {
    val timeFormatted = formatTime(mealTimeUtc)
    val photos = photoIds.mapNotNull { mealPhotoUri(context, it) }
    val imageUri = photos.firstOrNull()

    return MealEntityUi(
        id = id,
        mealTimeUtc = mealTimeUtc,
        carbohydrates = carbohydrates,
        proteins = proteins,
        fats = fats,
        calories = calories,
        impactType = impactType,
        mealType = mealType,
        timeFormatted = timeFormatted,
        description = description,
        imageUri = imageUri,
        reasoning = reasoning,
        isImported = sourceId != null,
        timeInRange = impact.timeInRange,
        timeAboveRange = impact.timeAboveRange,
        timeBelowRange = impact.timeBelowRange,
        hasGlucoseData = impact.hasData,
        glucoseStatus = impact.status,
        photoUris = photos
    )
}
