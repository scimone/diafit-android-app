package uk.scimone.diafit.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import uk.scimone.diafit.core.data.local.MealDao
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.repository.MealRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class MealRepositoryImpl(
    private val mealDao: MealDao,
    private val context: Context
) : MealRepository {

    override suspend fun createMeal(meal: MealEntity): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            mealDao.insertMeal(meal)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun storeImage(mealId: String, sourceUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val destUri = saveImageToLocalFolder(mealId, sourceUri)
            Result.success(destUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveImageToLocalFolder(mealId: String, sourceUri: Uri): Uri {
        val resolver: ContentResolver = context.contentResolver

        // Prepare destination folder in app's files dir
        val imagesDir = File(context.filesDir, "meal_images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        // Create destination file with mealId as name + extension from source (or default)
        val destFile = File(imagesDir, "$mealId.jpg")

        resolver.openInputStream(sourceUri).use { inputStream ->
            destFile.outputStream().use { outputStream ->
                copyStream(inputStream, outputStream)
            }
        }

        // Return Uri to the saved image file
        return Uri.fromFile(destFile)
    }

    private fun copyStream(input: InputStream?, output: OutputStream) {
        if (input == null) throw IllegalArgumentException("InputStream cannot be null")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } >= 0) {
            output.write(buffer, 0, bytesRead)
        }
        output.flush()
    }
}
