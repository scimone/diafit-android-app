package uk.scimone.diafit.journal.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.journal.presentation.model.accent
import uk.scimone.diafit.journal.presentation.model.iconRes
import uk.scimone.diafit.journal.presentation.model.label

/** Timestamp, meal type pill and absorption pill (no duration): shared by the meal page and the Home meal preview. */
@Composable
fun MealMetaRow(timeText: String, mealType: MealType, impactType: ImpactType, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(timeText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MetaPill(mealType.type, mealType.iconRes, mealType.accent, MaterialTheme.colorScheme.onSurface)
        MetaPill(impactType.label, impactType.iconRes, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MetaPill(text: String, iconRes: Int, accent: androidx.compose.ui.graphics.Color, textColor: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.background(accent.copy(alpha = 0.16f), CircleShape).padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(iconRes), null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = textColor, maxLines = 1)
    }
}
