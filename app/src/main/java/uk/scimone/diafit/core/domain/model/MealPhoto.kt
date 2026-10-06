package uk.scimone.diafit.core.domain.model

import android.net.Uri

/** A course photo: [imageId] names the stored file, [uri] is where its bytes currently are. */
data class MealPhoto(val imageId: String, val uri: Uri)
