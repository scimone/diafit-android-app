package uk.scimone.diafit.journal.presentation.model

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
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
    val hasGlucoseData: Boolean
) : JournalEntryUi {
    override val kind: JournalEntryKind get() = JournalEntryKind.MEAL
    override val timeUtc: Long get() = mealTimeUtc

    /** Headline for lists: the description, falling back to the meal type. */
    val title: String get() = description?.takeIf { it.isNotBlank() } ?: mealType.type
}

data class GlucoseImpact(
    val timeInRange: Double,
    val timeAboveRange: Double,
    val timeBelowRange: Double
) {
    val hasData: Boolean get() = timeInRange + timeAboveRange + timeBelowRange > 0.0
}

fun MealEntity.toUi(context: Context, impact: GlucoseImpact): MealEntityUi {
    val timeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(mealTimeUtc))

    val imageFile = File(context.filesDir, "meal_images/$imageId.jpg")
    val imageUri = if (imageId.isNotEmpty() && imageFile.exists()) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    } else null

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
        hasGlucoseData = impact.hasData
    )
}
