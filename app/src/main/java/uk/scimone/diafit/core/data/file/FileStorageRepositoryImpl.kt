package uk.scimone.diafit.core.data.file

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import java.io.File

class FileStorageRepositoryImpl(
    private val context: Context
) : FileStorageRepository {

    private fun getImagesDir(): File {
        val dir = File(context.filesDir, "meal_images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun createImageUri(mealId: String): Uri {
        val file = File(getImagesDir(), "$mealId.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    override suspend fun copyGalleryImageToPrivateStorage(sourceUri: Uri, mealId: String): Result<Uri> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val destFile = File(getImagesDir(), "$mealId.jpg")
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
            }
        }
    }

    private fun saveImageToLocalFolder(mealId: String, sourceUri: Uri): Uri {
        val destFile = File(getImagesDir(), "$mealId.jpg")
        context.contentResolver.openInputStream(sourceUri).use { inputStream ->
            destFile.outputStream().use { outputStream ->
                inputStream?.copyTo(outputStream) ?: throw IllegalStateException("Failed to open input stream")
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
    }

    override fun getFileProviderUri(mealId: String): Uri? {
        val file = File(getImagesDir(), "$mealId.jpg")
        return if (file.exists()) FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) else null
    }


    override suspend fun storeImage(mealId: String, sourceUri: Uri): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val destUri = saveImageToLocalFolder(mealId, sourceUri)
                Result.success(destUri)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

}


