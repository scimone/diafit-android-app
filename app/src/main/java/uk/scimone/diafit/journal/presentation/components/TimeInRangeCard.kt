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
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Black
import uk.scimone.diafit.ui.theme.InRange

@Composable
fun TimeInRangeCard(
    timeBelowRange: Float,
    timeInRange: Float,
    timeAboveRange: Float,
) {
    Box(
        modifier = Modifier
            .width(60.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (timeBelowRange > 0.0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(timeBelowRange)
                        .background(BelowRange)
                )
            }
            if (timeInRange > 0.0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(timeInRange)
                        .background(InRange)
                )
            }
            if (timeAboveRange > 0.0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(timeAboveRange)
                        .background(AboveRange)
                )
            }
        }

        // Only display the number if any value is greater than 0
        if (timeBelowRange > 0f || timeInRange > 0f || timeAboveRange > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = timeInRange.toInt().toString(),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                        color = Color.Black,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Black
                    )
                }
            }

        }
    }
}