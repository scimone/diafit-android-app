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
    val timeFormatted: String,
    val description: String,
    val imageUri: Uri?,
    val macrosSummary: String
)

fun MealEntity.toUi(context: Context): MealEntityUi {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val formattedDate = formatter.format(Date(mealTimeUtc))

    val descriptionText = description ?: "No description"

    val imageFile: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        ?.resolve("DiaFit/meal_${imageId}.jpg")

    val imageUri = imageFile?.takeIf { it.exists() }?.let {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
    }

    val macrosSummary = "Carbs: $carbohydrates, Proteins: ${proteins ?: 0}, Fats: ${fats ?: 0}, Calories: ${calories ?: 0}"

    return MealEntityUi(
        timeFormatted = formattedDate,
        description = descriptionText,
        imageUri = imageUri,
        macrosSummary = macrosSummary
    )
}
