package uk.scimone.diafit.journal.presentation.components

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.ui.theme.Black

@Composable
fun AmountInfoCard(
    amount: String,
    unit: String? = null,
    color: Color? = null,
) {
    Box(
        modifier = Modifier
            .width(50.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color ?: MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = amount,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                color = Black
            )
            if (unit != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Black
                )
            }
        }
    }
}
