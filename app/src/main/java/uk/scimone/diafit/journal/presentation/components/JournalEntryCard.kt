package uk.scimone.diafit.journal.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import uk.scimone.diafit.journal.presentation.model.GlucoseStatus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.journal.presentation.model.JournalEntryUi
import uk.scimone.diafit.journal.presentation.model.MealEntityUi

/** One row of the journal, whatever the entry kind. */
@Composable
fun JournalEntryCard(entry: JournalEntryUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    when (entry) {
        is MealEntityUi -> MealEntryCard(entry, onClick, modifier)
    }
}

@Composable
private fun MealEntryCard(meal: MealEntityUi, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MealAvatar(meal, size = 64.dp)
            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    meal.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(meal.mealType.type)
                        append(" · ")
                        append(meal.timeFormatted)
                        if (meal.courseCount > 1) append(" · ${meal.courseCount} courses")
                        if (meal.isImported) append(" · Imported")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                GlucoseOutcome(meal)
            }

            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CarbPill(meal.carbohydrates)
                AbsorptionBadge(meal.impactType)
            }
        }
    }
}

/** Range split bar plus a one-word verdict, or a muted note while there is no CGM data. */
@Composable
private fun GlucoseOutcome(meal: MealEntityUi) {
    val note = when {
        meal.glucoseStatus == GlucoseStatus.TOO_EARLY -> "Not enough glucose data yet."
        meal.glucoseStatus == GlucoseStatus.NOT_ENOUGH_DATA || !meal.hasGlucoseData -> "Not enough glucose data."
        else -> null
    }
    if (note != null) {
        Text(
            note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RangeBar(
            below = meal.timeBelowRange.toFloat(),
            inRange = meal.timeInRange.toFloat(),
            above = meal.timeAboveRange.toFloat(),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${meal.timeInRange.toInt()}% in range",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(84.dp)
        )
    }
}
