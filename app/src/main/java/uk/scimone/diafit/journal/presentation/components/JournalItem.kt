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
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import uk.scimone.diafit.ui.theme.Carbs
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import uk.scimone.diafit.R
import uk.scimone.diafit.core.domain.model.ImpactType

@Composable
fun JournalItem(mealUi: MealEntityUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(IntrinsicSize.Min),  // height is determined by tallest child
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Image column
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

        // Card column on the right - fills remaining space and matches image height
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),  // match image height
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = mealUi.mealTypeDisplayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = mealUi.timeFormatted.substringAfter(" "),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TimeInRangeHorizontalBar(
                            timeInRange = mealUi.timeInRange.toFloat(),
                            timeAboveRange = mealUi.timeAboveRange.toFloat(),
                            timeBelowRange = mealUi.timeBelowRange.toFloat(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .align(Alignment.CenterVertically)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (mealUi.impactType) {
                                ImpactType.SHORT -> {
                                    Image(
                                        painter = painterResource(R.drawable.ic_impact_type_short),
                                        contentDescription = "Short Impact Icon",
                                        modifier = Modifier
                                            .size(30.dp)
                                            .padding(end = 4.dp),
                                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                                    )
                                }
                                ImpactType.LONG -> {
                                    Image(
                                        painter = painterResource(R.drawable.ic_impact_type_long),
                                        contentDescription = "Long Impact Icon",
                                        modifier = Modifier
                                            .size(30.dp)
                                            .padding(end = 4.dp),
                                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                                    )
                                }
                                else -> {
                                    // No icon shown, do nothing
                                }
                            }
                            AmountInfoCard(
                                amount = mealUi.carbohydrates.toString(),
                                unit = "g",
                                color = Carbs,
                                width = 55,
                                height = 30
                            )
                        }
                    }
                }
            }
        }
    }
}
