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
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.layout.ContentScale
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import uk.scimone.diafit.ui.theme.Carbs


@Composable
fun JournalItem(mealUi: MealEntityUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time on the left, centered vertically
        Box(
            modifier = Modifier
                .width(60.dp)
                .padding(end = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mealUi.timeFormatted.substringAfter(" "),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Image + green bar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            mealUi.imageUri?.let {
                Image(
                    painter = rememberAsyncImagePainter(it),
                    contentDescription = "Meal Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(70.dp)
                        .height(60.dp)
                        .padding(end = 5.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = mealUi.description, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column() {
            TimeInRangeCard(
                mealUi.timeBelowRange.toFloat(),
                mealUi.timeInRange.toFloat(),
                mealUi.timeAboveRange.toFloat(),
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Column() {
            AmountInfoCard(
                amount=mealUi.carbohydrates.toString(),
                unit="g",
                color=Carbs
            )
        }
    }
}
