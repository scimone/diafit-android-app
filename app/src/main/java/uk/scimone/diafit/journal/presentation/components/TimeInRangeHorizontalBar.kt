package uk.scimone.diafit.journal.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.InRange

@Composable
fun TimeInRangeHorizontalBar(
    timeBelowRange: Float,
    timeInRange: Float,
    timeAboveRange: Float,
) {
    // Time in Range bar
    Row(
        modifier = Modifier
            .width(90.dp)
            .height(20.dp)
            .padding(start = 5.dp, end = 5.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(timeBelowRange)
                .background(BelowRange)
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(timeInRange)
                .background(InRange)
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(timeAboveRange)
                .background(AboveRange)
        )
    }
}