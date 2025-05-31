package uk.scimone.diafit.core.domain.repository

import android.net.Uri

interface FileStorageRepository {
    suspend fun storeImage(mealId: String, sourceUri: Uri): Result<Uri>
    fun getFileProviderUri(mealId: String): Uri?
    fun createImageUri(mealId: String): Uri
    suspend fun copyGalleryImageToPrivateStorage(sourceUri: Uri, mealId: String): Result<Uri>
}
