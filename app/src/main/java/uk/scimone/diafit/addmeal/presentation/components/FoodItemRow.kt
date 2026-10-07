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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.core.domain.model.ComponentConfidence
import uk.scimone.diafit.core.domain.model.MealComponent
import uk.scimone.diafit.ui.theme.Carbs
import kotlin.math.max
import kotlin.math.roundToInt

/** Step of the portion stepper: 10 g, or 5 g for small portions. */
private fun stepFor(weightG: Double) = if (weightG < 50) 5.0 else 10.0

/**
 * One food the AI identified, as a compact list row: emoji, name, portion and its carbs (what the
 * dose is based on). Tap to expand: adjust the portion (nutrients scale with it), see the other
 * nutrients, what the AI assumed, and remove it.
 */
@Composable
fun FoodItemRow(
    component: MealComponent,
    onWeightChange: (Double) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val uncertain = component.confidence == ComponentConfidence.LOW

    Column(modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClickLabel = if (expanded) "Collapse" else "Adjust portion") { expanded = !expanded }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(component.emoji, fontSize = 20.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
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
                Text("${component.weightG.roundToInt()} g", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Text(
                "${component.carbsG.roundToInt()} g",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Carbs
            )
            Icon(
                Icons.Filled.ExpandMore, null,
                tint = muted,
                modifier = Modifier.padding(start = 4.dp).size(20.dp).rotate(rotation)
            )
        }

        AnimatedVisibility(expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 56.dp, end = 4.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortionStepper(component.weightG, onWeightChange)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Outlined.DeleteOutline, "Remove ${component.name}", tint = muted)
                    }
                }
                Text(
                    "${component.proteinG.roundToInt()} g protein · ${component.fatG.roundToInt()} g fat · ${component.calories.roundToInt()} kcal" +
                        if (component.sugarG >= 1) " · ${component.sugarG.roundToInt()} g sugar" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                component.basis?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                when {
                    uncertain -> Text(
                        "Low confidence: hidden or unclear content. Please check the portion.",
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

/** − [ 150 g ] + : steps the portion, or type an exact weight. */
@Composable
private fun PortionStepper(weightG: Double, onChange: (Double) -> Unit) {
    var text by remember(weightG) { mutableStateOf(weightG.roundToInt().toString()) }
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(max(1.0, weightG - stepFor(weightG))) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Remove, "Less", Modifier.size(18.dp))
            }
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
                modifier = Modifier.width(40.dp)
            )
            Text(" g", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { onChange(weightG + stepFor(weightG)) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Add, "More", Modifier.size(18.dp))
            }
        }
    }
}
