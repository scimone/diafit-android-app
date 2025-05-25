package uk.scimone.diafit.journal.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.journal.presentation.model.toUi
import androidx.compose.ui.layout.ContentScale


@Composable
fun JournalItem(meal: MealEntity) {
    val context = LocalContext.current
    val mealUi = meal.toUi(context)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        mealUi.imageUri?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = "Meal Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(1f)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(12.dp))  // Add rounded corners here
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Time: ${mealUi.timeFormatted}", style = MaterialTheme.typography.bodyMedium)
            Text(text = mealUi.description, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = mealUi.macrosSummary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
