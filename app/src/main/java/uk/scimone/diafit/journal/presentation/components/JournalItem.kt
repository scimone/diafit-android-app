package uk.scimone.diafit.journal.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.journal.presentation.model.toUi
import androidx.compose.ui.layout.ContentScale
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange


@Composable
fun JournalItem(meal: MealEntity) {
    val context = LocalContext.current
    val mealUi = meal.toUi(context)

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
                .padding(end = 8.dp),
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
                        .width(90.dp)
                        .height(65.dp)
//                        .aspectRatio(1f)
                        .padding(top = 2.dp, start = 5.dp, end = 5.dp, bottom = 5.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                )
            }

            TimeInRangeHorizontalBar(
                mealUi.timeBelowRange.toFloat(),
                mealUi.timeInRange.toFloat(),
                mealUi.timeAboveRange.toFloat(),
            )

        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = mealUi.description, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = mealUi.macrosSummary, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column() {
            AmountInfoCard(
                amount=mealUi.carbohydrates.toString(),
                unit="g",
                color=Carbs
            )
        }
    }
}
