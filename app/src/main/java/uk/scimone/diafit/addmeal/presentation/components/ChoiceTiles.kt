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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.journal.presentation.model.accent
import uk.scimone.diafit.journal.presentation.model.durationLabel
import uk.scimone.diafit.journal.presentation.model.iconRes
import uk.scimone.diafit.journal.presentation.model.label

/** Breakfast / Lunch / Dinner / Snack as one compact row, each in its own accent colour. */
@Composable
fun MealTypeSelector(selected: MealType, onSelected: (MealType) -> Unit, modifier: Modifier = Modifier) {
    ChoiceTiles(
        options = MealType.entries,
        selected = selected,
        onSelected = onSelected,
        accent = { it.accent },
        icon = { it.iconRes },
        label = { it.type },
        modifier = modifier
    )
}

/** Fast / Medium / Slow carb absorption, each with its curve glyph and how long it acts for. */
@Composable
fun AbsorptionSelector(selected: ImpactType, onSelected: (ImpactType) -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    ChoiceTiles(
        options = ImpactType.entries,
        selected = selected,
        onSelected = onSelected,
        accent = { primary },
        icon = { it.iconRes },
        label = { "${it.label} ${it.durationLabel}" },
        modifier = modifier
    )
}

/** A row of equal-width, single-choice tiles: glyph over a short label. */
@Composable
private fun <T> ChoiceTiles(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    accent: (T) -> Color,
    icon: (T) -> Int,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            val color = accent(option)
            Surface(
                selected = isSelected,
                onClick = { onSelected(option) },
                modifier = Modifier.weight(1f).height(60.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (isSelected) BorderStroke(1.5.dp, color) else null
            ) {
                Column(
                    Modifier.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painterResource(icon(option)),
                        contentDescription = null,
                        tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
