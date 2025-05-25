package uk.scimone.diafit.addmeal.presentation

import android.net.Uri

data class AddMealState (
    val imageUri: Uri? = null,
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null
)