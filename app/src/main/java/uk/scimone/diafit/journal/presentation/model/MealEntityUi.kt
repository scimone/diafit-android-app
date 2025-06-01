package uk.scimone.diafit.journal.presentation.model

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
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
    val timeFormatted: String,
    val description: String,
    val imageUri: Uri?,
    val macrosSummary: String,
    val timeInRange: Double,
    val timeAboveRange: Double,
    val timeBelowRange: Double
)

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
        timeBelowRange = impact.timeBelowRange
    )
}
