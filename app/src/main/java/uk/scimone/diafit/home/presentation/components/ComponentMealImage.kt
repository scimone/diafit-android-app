package uk.scimone.diafit.home.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import coil.compose.rememberAsyncImagePainter
import uk.scimone.diafit.home.presentation.model.MealEntityUi
import uk.scimone.diafit.ui.theme.Carbs

@Composable
fun ComponentMealImage(
    meal: MealEntityUi,
) {
    Box(
        modifier = Modifier
            .width(95.dp)
            .height(95.dp)
            .clip(RoundedCornerShape(6.dp))
    ) {
        Image(
            painter = rememberAsyncImagePainter(meal.imageUri),
            contentDescription = "Meal Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
        )

        // Carbs pill in bottom right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp) // distance from image edge
                .size(width = 35.dp, height = 20.dp)
                .background(
                    color = Carbs,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${meal.carbohydrates}g",
                fontSize = 12.sp, // must be small to fit
                lineHeight = 12.sp, // match font size
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                maxLines = 1,
            )
        }

    }
}
