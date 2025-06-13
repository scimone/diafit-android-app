package uk.scimone.diafit.home.presentation.model

import android.net.Uri
import androidx.core.content.FileProvider
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealType
import java.io.File
import android.content.Context


data class MealEntityUi(
    val mealTimeUtc: Long,
    val carbohydrates: Int = 0,
    val proteins: Int? = null,
    val fats: Int? = null,
    val calories: Int? = null,
    val impactType: ImpactType,
    val mealType: MealType,
    val description: String?,
    val imageUri: Uri?,
)

fun MealEntity.toMealEntityUi(context: Context
): MealEntityUi {

    val imageFile = File(context.filesDir, "meal_images/$imageId.jpg")
    val imageUri = if (imageFile.exists()) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    } else null

    return MealEntityUi(
        mealTimeUtc = this.mealTimeUtc,
        carbohydrates = this.carbohydrates,
        proteins = this.proteins,
        fats = this.fats,
        calories = this.calories,
        impactType = this.impactType,
        mealType = this.mealType,
        description = description,
        imageUri = imageUri
    )
}