package uk.scimone.diafit.home.presentation.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

@Composable
fun ComponentMealImage(
    imageUrl: Uri,
) {
    Image(
        painter = rememberAsyncImagePainter(imageUrl),
        contentDescription = "Meal Image",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .width(95.dp)
            .height(95.dp)
            .clip(RoundedCornerShape(6.dp))
    )
}