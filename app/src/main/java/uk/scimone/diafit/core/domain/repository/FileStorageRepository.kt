package uk.scimone.diafit.core.domain.repository

import android.net.Uri

interface FileStorageRepository {
    fun createImageUri(mealId: String): Uri
    suspend fun copyGalleryImageToPrivateStorage(sourceUri: Uri, mealId: String): Result<Uri>
}
