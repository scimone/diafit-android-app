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
import androidx.compose.ui.text.font.FontWeight
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import uk.scimone.diafit.ui.theme.Carbs


@Composable
fun JournalItem(mealUi: MealEntityUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .height(IntrinsicSize.Min), // Let height be min needed to fit content
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time box aligned center vertically with image height (60dp)
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .height(60.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mealUi.timeFormatted.substringAfter(" "),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Image centered vertically by Row
        mealUi.imageUri?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = "Meal Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 70.dp, height = 70.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Column fills remaining width, no fixed height here
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            // Heading aligned at top
            Text(
                text = mealUi.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.weight(1f)) // Push cards to bottom

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TimeInRangeCard(
                    mealUi.timeBelowRange.toFloat(),
                    mealUi.timeInRange.toFloat(),
                    mealUi.timeAboveRange.toFloat(),
                )

                Spacer(modifier = Modifier.width(4.dp))

                AmountInfoCard(
                    amount = mealUi.carbohydrates.toString(),
                    unit = "g",
                    color = Carbs
                )
            }
        }
    }
}
