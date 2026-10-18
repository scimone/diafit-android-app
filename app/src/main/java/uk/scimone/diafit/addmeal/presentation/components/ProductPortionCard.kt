package uk.scimone.diafit.addmeal.presentation.components

import uk.scimone.diafit.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RestaurantMenu
import uk.scimone.diafit.core.domain.model.isRecipeId
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.MealComponent
import uk.scimone.diafit.core.domain.model.formatGrams
import uk.scimone.diafit.logs.presentation.components.SectionLabel
import kotlin.math.abs
import kotlin.math.roundToInt

private const val GRAM_STEP = 25.0
private const val SERVING_STEP = 0.5

/**
 * A scanned packaged food with its portion. The portion starts as one serving (as printed on the pack,
 * else 100 g); − / + step it, the chips jump to common amounts, and the grams can be typed. The
 * nutrition totals below follow the portion.
 */
@Composable
fun ProductPortionCard(
    product: MealComponent,
    onWeightChange: (Double) -> Unit,
    onRescan: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val serving = product.servingG
    val weight = product.weightG
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (product.barcode.isRecipeId()) Icons.Filled.RestaurantMenu else Icons.Filled.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                SectionLabel(stringResource(if (product.barcode.isRecipeId()) R.string.recipe_label else R.string.addmeal_product), Modifier.weight(1f))
                if (!product.barcode.isRecipeId()) IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        if (isFavorite) stringResource(R.string.addmeal_fav_remove) else stringResource(R.string.addmeal_fav_add),
                        tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onRescan, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(if (product.barcode.isRecipeId()) R.string.recipe_pick_another else R.string.addmeal_scan_another))
                }
            }

            Column {
                Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        stringResource(R.string.addmeal_carbs_per_100, (product.carbsG / weight.coerceAtLeast(1.0) * 100).roundToInt()),
                        product.basis?.substringBefore(" · ")?.takeIf { it.isNotBlank() } ?: "Open Food Facts"
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
            }

            // Portion stepper.
            val step = if (serving != null) serving * SERVING_STEP else GRAM_STEP
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                FilledTonalIconButton(onClick = { onWeightChange((weight - step).coerceAtLeast(step)) }, enabled = weight > step + 0.01) {
                    Icon(Icons.Filled.Remove, stringResource(R.string.addmeal_smaller_portion))
                }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.addmeal_portion), style = MaterialTheme.typography.labelMedium, color = muted)
                    Text(
                        if (serving != null) pluralStringResource(R.plurals.addmeal_servings, if (abs(weight / serving - 1.0) < 0.01) 1 else 2, formatServings(weight / serving))
                        else formatGrams(weight),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                FilledTonalIconButton(onClick = { onWeightChange(weight + step) }) { Icon(Icons.Filled.Add, stringResource(R.string.addmeal_larger_portion)) }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (serving != null) stringResource(R.string.addmeal_serving_eq, formatGrams(serving)) else stringResource(R.string.addmeal_no_serving),
                    style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.weight(1f)
                )
                Text(stringResource(R.string.addmeal_weight), style = MaterialTheme.typography.labelMedium, color = muted)
                WeightField(weight, onWeightChange)
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (serving != null) {
                    listOf(0.5, 1.0, 2.0).forEach { n ->
                        PortionChip(
                            label = pluralStringResource(R.plurals.addmeal_servings, if (n == 1.0) 1 else 2, formatServings(n)),
                            selected = abs(weight - serving * n) < 0.5,
                            onClick = { onWeightChange(serving * n) }
                        )
                    }
                }
                PortionChip("100 g", abs(weight - 100.0) < 0.5) { onWeightChange(100.0) }
                product.packageG?.let { pack ->
                    PortionChip(stringResource(R.string.addmeal_whole_pack, formatGrams(pack)), abs(weight - pack) < 0.5) { onWeightChange(pack) }
                }
            }
        }
    }
}

@Composable
private fun PortionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, shape = CircleShape)
}

/** 1, 1.5, 2.25 … without trailing zeros. */
private fun formatServings(n: Double): String {
    val rounded = (n * 100).roundToInt() / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString().trimEnd('0')
}
