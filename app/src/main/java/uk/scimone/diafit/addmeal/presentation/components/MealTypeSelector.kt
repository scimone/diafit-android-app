package uk.scimone.diafit.addmeal.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.R
import uk.scimone.diafit.core.domain.model.MealType

@Composable
fun mealTypeIcon(mealType: MealType): Painter = when (mealType) {
    MealType.BREAKFAST -> painterResource(R.drawable.ic_meal_type_breakfast)
    MealType.LUNCH -> painterResource(R.drawable.ic_meal_type_lunch)
    MealType.DINNER -> painterResource(R.drawable.ic_meal_type_dinner)
    MealType.SNACK -> painterResource(R.drawable.ic_meal_type_snack)
    MealType.UNKNOWN -> painterResource(R.drawable.ic_meal_type_unknown)
}

@Composable
fun MealTypeSelector(
    selectedMealType: MealType,
    onMealTypeSelected: (MealType) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MealType.values().forEach { meal ->
            val selected = meal == selectedMealType
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onMealTypeSelected(meal) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                tonalElevation = if (selected) 8.dp else 0.dp
            ) {
                Icon(
                    painter = mealTypeIcon(meal),
                    contentDescription = meal.name,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}