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

    override fun createImageUri(mealId: String): Uri {
        val picturesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DiaFit")
        if (!picturesDir.exists()) picturesDir.mkdirs()

        val file = File(picturesDir, "meal_$mealId.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    override suspend fun copyGalleryImageToPrivateStorage(sourceUri: Uri, mealId: String): Result<Uri> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val picturesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DiaFit")
                if (!picturesDir.exists()) picturesDir.mkdirs()

                val destFile = File(picturesDir, "meal_$mealId.jpg")
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }

                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
            }
        }
    }
}
