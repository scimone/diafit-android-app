package uk.scimone.diafit.addmeal.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.core.domain.model.ComponentConfidence
import uk.scimone.diafit.core.domain.model.MealComponent
import uk.scimone.diafit.ui.theme.Carbs
import kotlin.math.roundToInt

/**
 * One food the AI identified, as a compact row: emoji, name, its carbs, and the portion (editable
 * right here; the nutrients scale with it). The chevron opens its carbs/protein/fat for editing
 * (energy follows), what the AI assumed, and removing it.
 */
@Composable
fun FoodItemRow(
    component: MealComponent,
    onWeightChange: (Double) -> Unit,
    onMacrosChange: (carbs: Double?, protein: Double?, fat: Double?) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val uncertain = component.confidence == ComponentConfidence.LOW

    Column(modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(component.emoji, fontSize = 18.sp) }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = if (expanded) "Hide nutrients" else "Edit nutrients") { expanded = !expanded }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        component.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (uncertain) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.ErrorOutline, "Uncertain estimate", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Carbs, fontWeight = FontWeight.SemiBold)) { append("${component.carbsG.roundToInt()} g carbs") }
                        append(" · ${component.proteinG.roundToInt()} P · ${component.fatG.roundToInt()} F")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            WeightField(component.weightG, onWeightChange)
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ExpandMore, if (expanded) "Hide nutrients" else "Edit nutrients", tint = muted, modifier = Modifier.rotate(rotation))
            }
        }

        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 46.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val small = MaterialTheme.typography.titleSmall
                    MacroTile("Carbs", component.carbsG.roundToInt(), { onMacrosChange(it.toDoubleOrNull() ?: 0.0, null, null) }, "g", Modifier.weight(1f), Carbs, small)
                    MacroTile("Protein", component.proteinG.roundToInt(), { onMacrosChange(null, it.toDoubleOrNull() ?: 0.0, null) }, "g", Modifier.weight(1f), style = small)
                    MacroTile("Fat", component.fatG.roundToInt(), { onMacrosChange(null, null, it.toDoubleOrNull() ?: 0.0) }, "g", Modifier.weight(1f), style = small)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull("${component.calories.roundToInt()} kcal", component.basis?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, "Remove ${component.name}", tint = muted, modifier = Modifier.size(20.dp))
                    }
                }
                when {
                    uncertain -> Text(
                        "Low confidence: hidden or unclear content. Please check.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    component.confidence == ComponentConfidence.MEDIUM && component.carbsG >= 10 -> Text(
                        "Portion or recipe uncertain.",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted
                    )
                }
            }
        }
    }
}

/** The portion as a small pill to type into; keeps its own text so it can be cleared while typing. */
@Composable
private fun WeightField(weightG: Double, onChange: (Double) -> Unit) {
    var text by remember(weightG.roundToInt()) { mutableStateOf(weightG.roundToInt().toString()) }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { v ->
                    val digits = v.filter(Char::isDigit).take(4)
                    text = digits
                    digits.toDoubleOrNull()?.takeIf { it > 0 }?.let(onChange)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(36.dp)
            )
            Text(" g", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
