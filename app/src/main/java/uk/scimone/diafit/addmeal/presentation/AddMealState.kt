package uk.scimone.diafit.addmeal.presentation

import android.net.Uri

data class AddMealState (
    val imageUri: Uri? = null,
    val description: String? = null,
    val mealTime: String? = null,
    val carbohydrates: Int? = null,
    val proteins: Int? = null,
    val fats: Int? = null,
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null
)
