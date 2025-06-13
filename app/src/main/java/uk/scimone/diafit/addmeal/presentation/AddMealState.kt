package uk.scimone.diafit.addmeal.presentation

import android.net.Uri
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealType

data class AddMealState (
    val imageUri: Uri? = null,
    val description: String? = null,
    val mealTime: Long? = null,
    val carbohydrates: Int? = null,
    val proteins: Int? = null,
    val fats: Int? = null,
    val calories: Int? = null,
    val impactType: ImpactType = ImpactType.MEDIUM,
    val mealType: MealType = MealType.UNKNOWN,
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null
)
