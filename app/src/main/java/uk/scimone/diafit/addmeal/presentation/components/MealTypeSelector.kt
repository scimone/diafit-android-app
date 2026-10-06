package uk.scimone.diafit.addmeal.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.journal.presentation.components.MealTypeTile
import uk.scimone.diafit.journal.presentation.model.accent

/** Breakfast / Lunch / Dinner / Snack as four equal tiles, each in its own accent colour. */
@Composable
fun MealTypeSelector(
    selectedMealType: MealType,
    onMealTypeSelected: (MealType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealType.entries.forEach { type ->
            val selected = type == selectedMealType
            Surface(
                selected = selected,
                onClick = { onMealTypeSelected(type) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = if (selected) type.accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) type.accent else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    Modifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MealTypeTile(type, size = 40.dp, shape = androidx.compose.foundation.shape.CircleShape)
                    Text(
                        type.type,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
