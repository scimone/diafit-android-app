package uk.scimone.diafit.journal.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.core.domain.util.friendlyDateString
import uk.scimone.diafit.journal.presentation.components.*
import uk.scimone.diafit.journal.presentation.model.*
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange

/**
 * Full-page view of one meal: what it was, what it did to glucose, and the controls to edit or delete it.
 * [onDeleted] is called after the soft delete so the host can offer an Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    userId: Int,
    mealId: Int,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
    onDeleted: (Int) -> Unit,
    viewModel: MealDetailViewModel = koinViewModel(key = "meal-detail-$mealId", parameters = { parametersOf(userId, mealId) })
) {
    val state by viewModel.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    // Deleted from elsewhere (or just now): nothing left to show.
    LaunchedEffect(state.isGone) { if (state.isGone) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meal") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { onEdit(mealId) }, enabled = state.meal != null) {
                        Icon(Icons.Filled.Edit, "Edit meal")
                    }
                }
            )
        }
    ) { padding ->
        val meal = state.meal
        if (meal == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Hero(meal)
            TitleBlock(meal)
            GlucoseResponseCard(meal, state)
            NutritionCard(meal)
            AbsorptionCard(meal)
            meal.reasoning?.takeIf { it.isNotBlank() }?.let { AiNotesCard(it) }

            OutlinedButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.DeleteOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete meal")
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this meal?") },
            text = { Text("It will be removed from your journal and the Home graphs. You can undo right after.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete { onDeleted(mealId) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Hero(meal: MealEntityUi) {
    val shape = RoundedCornerShape(28.dp)
    if (meal.imageUri != null) {
        AsyncImage(
            model = meal.imageUri,
            contentDescription = meal.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(shape)
        )
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(shape)
                .background(meal.mealType.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(meal.mealType.iconRes), null, tint = meal.mealType.accent, modifier = Modifier.size(52.dp))
        }
    }
}


@Composable
private fun TitleBlock(meal: MealEntityUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(meal.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            InfoChip(meal.mealType.type, painterResource(meal.mealType.iconRes), meal.mealType.accent)
            Text(
                "${friendlyDateString(meal.mealTimeUtc)}, ${meal.timeFormatted}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (meal.isImported) {
                Text("· Imported", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoChip(text: String, icon: androidx.compose.ui.graphics.painter.Painter, accent: Color) {
    Row(
        Modifier.background(accent.copy(alpha = 0.16f), CircleShape).padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CardSection(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun GlucoseResponseCard(meal: MealEntityUi, state: MealDetailState) {
    val response = state.response
    val effectEnd = meal.mealTimeUtc + meal.impactType.durationMinutes * 60_000L
    val stillAbsorbing = System.currentTimeMillis() < effectEnd
    CardSection(
        title = "Glucose response",
        subtitle = "mg/dL · ${meal.impactType.label.lowercase()} absorption, about ${meal.impactType.durationMinutes / 60} h"
    ) {
        if (response == null) {
            Text("Couldn't load glucose data.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@CardSection
        }
        GlucoseResponseChart(
            response = response,
            eventTimeUtc = meal.mealTimeUtc,
            effectEndUtc = effectEnd,
            target = state.target,
            eventColor = Carbs
        )
        Legend()
        if (response.readings.isNotEmpty()) {
            StatRow(meal, response)
            Insight(meal, response, state.target, stillAbsorbing)
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(InRange, "In range")
        LegendDot(AboveRange, "High")
        LegendDot(BelowRange, "Low")
        LegendDot(Bolus, "Insulin")
        LegendDot(Carbs, "Meal")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatRow(meal: MealEntityUi, response: uk.scimone.diafit.core.domain.usecase.GlucoseResponse) {
    val atMeal = response.atEvent?.valueMgdl
    val peak = response.peak
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("At meal", atMeal?.toString() ?: "–", null, Modifier.weight(1f))
        StatTile(
            "Peak", peak?.valueMgdl?.toString() ?: "–",
            if (atMeal != null && peak != null && peak.valueMgdl > atMeal) "+${peak.valueMgdl - atMeal}" else null,
            Modifier.weight(1f)
        )
        StatTile("Lowest", response.low?.valueMgdl?.toString() ?: "–", null, Modifier.weight(1f))
        StatTile("In range", if (meal.hasGlucoseData) "${meal.timeInRange.toInt()}%" else "–", null, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, delta: String?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(delta ?: " ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Insight(meal: MealEntityUi, response: uk.scimone.diafit.core.domain.usecase.GlucoseResponse, target: uk.scimone.diafit.core.domain.model.GlucoseTargetRange, stillAbsorbing: Boolean) {
    val atMeal = response.atEvent?.valueMgdl
    val peak = response.peak
    val low = response.low
    fun after(t: Long) = relativeToEvent(t - meal.mealTimeUtc).removeSuffix(" after")
    val rise = when {
        atMeal == null || peak == null -> null
        peak.valueMgdl <= atMeal + 10 -> "Glucose didn't rise much after the meal."
        else -> "Glucose rose ${peak.valueMgdl - atMeal} mg/dL, peaking ${after(peak.timestamp)} after the meal."
    }
    val dip = low?.takeIf { it.valueMgdl < target.lowerBound }
        ?.let { "It then dropped to ${it.valueMgdl} mg/dL, below range, ${after(it.timestamp)} after the meal." }
    val note = if (stillAbsorbing) "Still absorbing. This updates as new readings arrive." else null
    listOfNotNull(rise, dip, note).forEach {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NutritionCard(meal: MealEntityUi) {
    CardSection("Nutrition") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MacroTile("Carbs", meal.carbohydrates.toString(), "g", Carbs, Modifier.weight(1f))
            MacroTile("Protein", meal.proteins?.toString(), "g", null, Modifier.weight(1f))
            MacroTile("Fat", meal.fats?.toString(), "g", null, Modifier.weight(1f))
            MacroTile("Energy", meal.calories?.toString(), "kcal", null, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroTile(label: String, value: String?, unit: String, accent: Color?, modifier: Modifier) {
    Column(
        modifier
            .background((accent ?: MaterialTheme.colorScheme.onSurface).copy(alpha = if (accent != null) 0.18f else 0.06f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(if (value == null) label else "$label · $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun AbsorptionCard(meal: MealEntityUi) {
    CardSection("Absorption") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(meal.impactType.iconRes), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("${meal.impactType.label} · ${meal.impactType.durationLabel}", style = MaterialTheme.typography.titleSmall)
                Text(meal.impactType.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AiNotesCard(reasoning: String) {
    CardSection("AI estimate") {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
            Spacer(Modifier.width(10.dp))
            Text(reasoning, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
