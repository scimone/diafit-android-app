package uk.scimone.diafit.journal.presentation.components

import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import uk.scimone.diafit.core.domain.model.MealEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.FileProvider

@Composable
fun JournalItem(meal: MealEntity) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val mealTimeStr = formatter.format(Date(meal.mealTimeUtc))

    val context = LocalContext.current
    val imageFile: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        ?.resolve("DiaFit/meal_${meal.imageId}.jpg")

    val imageUri = if (imageFile?.exists() == true)
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        imageUri?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = "Meal Image",
                modifier = Modifier
                    .size(80.dp)
                    .padding(end = 16.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Time: $mealTimeStr", style = MaterialTheme.typography.bodyMedium)
            Text(text = meal.description ?: "No description", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Carbs: ${meal.carbohydrates}, Proteins: ${meal.proteins ?: 0}, Fats: ${meal.fats ?: 0}, Calories: ${meal.calories ?: 0}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
