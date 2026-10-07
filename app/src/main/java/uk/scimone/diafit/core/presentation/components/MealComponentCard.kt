package uk.scimone.diafit.core.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.core.domain.model.ComponentConfidence
import uk.scimone.diafit.core.domain.model.MealComponent
import uk.scimone.diafit.ui.theme.Carbs
import kotlin.math.roundToInt

/**
 * One food of a meal with its portion and nutrients. Read-only unless [onWeightChange]/[onRemove] are given;
 * changing the weight scales the nutrients proportionally (see [MealComponent.withWeight]).
 */
@Composable
fun MealComponentCard(
    component: MealComponent,
    modifier: Modifier = Modifier,
    onWeightChange: ((Double) -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(component.emoji, fontSize = 24.sp) }
                }
                Column(Modifier.weight(1f)) {
                    Text(component.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    component.basis?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (onWeightChange != null) {
                    var text by remember(component.weightG) { mutableStateOf(component.weightG.roundToInt().toString()) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v ->
                            val digits = v.filter { it.isDigit() }.take(5)
                            text = digits
                            digits.toDoubleOrNull()?.takeIf { it > 0 }?.let(onWeightChange)
                        },
                        suffix = { Text("g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(96.dp)
                    )
                } else {
                    Text("${component.weightG.roundToInt()} g", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                onRemove?.let {
                    IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, "Remove ${component.name}", Modifier.size(18.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                MacroChip("${component.carbsG.roundToInt()} g carbs", Carbs.copy(alpha = 0.3f))
                MacroChip("${component.proteinG.roundToInt()} g protein", MaterialTheme.colorScheme.secondaryContainer)
                MacroChip("${component.fatG.roundToInt()} g fat", MaterialTheme.colorScheme.secondaryContainer)
                MacroChip("${component.calories.roundToInt()} kcal", MaterialTheme.colorScheme.surfaceContainerHighest)
            }
            if (component.confidence == ComponentConfidence.LOW) {
                Text(
                    "Low confidence: hidden or unclear content, check this one",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (component.confidence == ComponentConfidence.MEDIUM && component.carbsG >= 10) {
                Text(
                    "Portion or recipe uncertain",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MacroChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
