package uk.scimone.diafit.home.presentation.model

import android.net.Uri
import androidx.core.content.FileProvider
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealType
import java.io.File
import android.content.Context


data class MealEntityUi(
    val id: Int,
    val mealTimeUtc: Long,
    val carbohydrates: Int = 0,
    val proteins: Int? = null,
    val fats: Int? = null,
    val calories: Int? = null,
    val impactType: ImpactType,
    val mealType: MealType,
    val description: String?,
    val imageUri: Uri?,
    val reasoning: String? = null,
    /** Every photo of this course, cover ([imageUri]) first. */
    val photoUris: List<Uri> = listOfNotNull(imageUri),
    val sittingId: String? = null,
    val sittingName: String? = null,
    val isImported: Boolean = false,
)

fun MealEntity.toMealEntityUi(context: Context
): MealEntityUi {

    val photos = photoIds.mapNotNull { id ->
        val imageFile = File(context.filesDir, "meal_images/$id.jpg")
        if (imageFile.exists()) FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile) else null
    }
    val imageUri = photos.firstOrNull()

    return MealEntityUi(
        id = this.id,
        mealTimeUtc = this.mealTimeUtc,
        carbohydrates = this.carbohydrates,
        proteins = this.proteins,
        fats = this.fats,
        calories = this.calories,
        impactType = this.impactType,
        mealType = this.mealType,
        description = description,
        imageUri = imageUri,
        reasoning = reasoning,
        photoUris = photos,
        sittingId = sittingId,
        sittingName = sittingName,
        isImported = sourceId != null
    )
}