package uk.scimone.diafit.addmeal.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.journal.presentation.model.durationLabel
import uk.scimone.diafit.journal.presentation.model.hint
import uk.scimone.diafit.journal.presentation.model.iconRes
import uk.scimone.diafit.journal.presentation.model.label

/**
 * Fast / Medium / Slow carb absorption. Each option shows its curve glyph and how long it acts for,
 * with a one-line explanation of the selected one underneath.
 */
@Composable
fun AbsorptionSelector(
    selected: ImpactType,
    isSuggested: Boolean,
    onSelected: (ImpactType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImpactType.entries.forEach { impact ->
                val isSelected = impact == selected
                val primary = MaterialTheme.colorScheme.primary
                Surface(
                    selected = isSelected,
                    onClick = { onSelected(impact) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(impact.iconRes),
                            contentDescription = null,
                            tint = if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            impact.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            impact.durationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Text(
            buildString {
                append(selected.hint)
                if (isSuggested) append(" Suggested from your macros.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
