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
    val mealTimeUtc: Long,
    val carbohydrates: Int = 0,
    val proteins: Int? = null,
    val fats: Int? = null,
    val calories: Int? = null,
    val impactType: ImpactType,
    val mealType: MealType,
    val timeFormatted: String,
    val description: String,
    val imageUri: Uri?,
    val macrosSummary: String,
    val timeInRange: Double,
    val timeAboveRange: Double,
    val timeBelowRange: Double,
    val impactDuration: String
) {
    val mealTypeDisplayName: String
        get() = mealType.type.lowercase().replaceFirstChar { it.titlecase() }
}


data class GlucoseImpact(
    val timeInRange: Double,
    val timeAboveRange: Double,
    val timeBelowRange: Double
)

fun MealEntity.toUi(context: Context, impact: GlucoseImpact): MealEntityUi {
    val timeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault())
        .format(Date(mealTimeUtc))

    val descriptionText = description ?: "No description"

    val imageFile = File(context.filesDir, "meal_images/$imageId.jpg")
    val imageUri = if (imageFile.exists()) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    } else null

    val macrosSummary =
        "Carbs: $carbohydrates g, Proteins: ${proteins ?: 0} g, Fats: ${fats ?: 0} g"

    val hours = impactType.durationMinutes / 60
    val minutes = impactType.durationMinutes % 60
    val impactDuration = "$hours:${minutes.toString().padStart(2, '0')}"

    return MealEntityUi(
        mealTimeUtc = mealTimeUtc,
        carbohydrates = carbohydrates,
        proteins = proteins,
        fats = fats,
        calories = calories,
        impactType = impactType,
        timeFormatted = timeFormatted,
        description = descriptionText,
        imageUri = imageUri,
        macrosSummary = macrosSummary,
        timeInRange = impact.timeInRange,
        timeAboveRange = impact.timeAboveRange,
        timeBelowRange = impact.timeBelowRange,
        impactDuration = impactDuration,
        mealType = mealType
    )
}
