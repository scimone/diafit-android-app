package uk.scimone.diafit.journal.presentation.model

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import uk.scimone.diafit.core.domain.model.MealEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class MealEntityUi(
    val carbohydrates: Int = 0,
    val proteins: Int? = null,
    val fats: Int? = null,
    val calories: Int? = null,
    val timeFormatted: String,
    val description: String,
    val imageUri: Uri?,
    val macrosSummary: String,
    val timeInRange: Double = 0.7,
    val timeAboveRange: Double = 0.28,
    val timeBelowRange: Double = 0.02
)

fun MealEntity.toUi(context: Context): MealEntityUi {

    val timeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault())
        .format(Date(mealTimeUtc))

    val descriptionText = description ?: "No description"

    val imageFile: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        ?.resolve("DiaFit/meal_${imageId}.jpg")

    val imageUri = imageFile?.takeIf { it.exists() }?.let {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
    }

    val macrosSummary = "Carbs: $carbohydrates g, Proteins: ${proteins ?: 0} g, Fats: ${fats ?: 0} g, Calories: ${calories ?: 0} kcal"

    return MealEntityUi(
        carbohydrates = carbohydrates,
        proteins = proteins,
        fats = fats,
        calories = calories,
        timeFormatted = timeFormatted,
        description = descriptionText,
        imageUri = imageUri,
        macrosSummary = macrosSummary
    )
}
