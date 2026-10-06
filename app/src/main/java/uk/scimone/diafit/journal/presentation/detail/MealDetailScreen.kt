package uk.scimone.diafit.journal.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.text.style.TextOverflow
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
 * Full-page view of one meal (all of its courses): what it was, the insulin given for it, what it did
 * to glucose, and the controls to edit, extend or delete it. [onDeleted] gets the soft-deleted course
 * ids so the host can offer an Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    userId: Int,
    mealId: Int,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
    onAddCourse: (Int) -> Unit,
    onDeleted: (List<Int>) -> Unit,
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
                    // A multi-course meal is edited course by course, from its timeline.
                    if (!state.isMultiCourse) {
                        IconButton(onClick = { onEdit(mealId) }, enabled = state.meal != null) {
                            Icon(Icons.Filled.Edit, "Edit meal")
                        }
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
            if (!meal.isImported) {
                FilledTonalButton(onClick = { onAddCourse(mealId) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isMultiCourse) "Add another course" else "Add a course (dessert, next plate…)")
                }
            }
            MealTimelineCard(state, onEditCourse = onEdit)
            GlucoseResponseCard(meal, state)
            NutritionCard(meal, wholeMeal = state.isMultiCourse)
            if (!state.isMultiCourse) {
                AbsorptionCard(meal)
                meal.reasoning?.takeIf { it.isNotBlank() }?.let { AiNotesCard(it) }
            } else {
                state.courses.filter { !it.reasoning.isNullOrBlank() }.takeIf { it.isNotEmpty() }?.let { CourseNotesCard(it) }
            }

            OutlinedButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.DeleteOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.isMultiCourse) "Delete whole meal" else "Delete meal")
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this meal?") },
            text = {
                Text(
                    (if (state.isMultiCourse) "All ${state.courses.size} courses will be removed" else "It will be removed") +
                        " from your journal and the Home graphs. You can undo right after."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onDeleted)
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
    val photos = meal.photoUris
    if (photos.isNotEmpty()) {
        // Every photo of every course, swipeable; one photo looks exactly like before.
        val pagerState = rememberPagerState { photos.size }
        Box {
            HorizontalPager(state = pagerState, pageSpacing = 8.dp) { page ->
                AsyncImage(
                    model = photos[page],
                    contentDescription = meal.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(shape)
                )
            }
            if (photos.size > 1) {
                Text(
                    "${pagerState.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
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

/**
 * Courses and insulin in the order they happened, with running totals. This is what keeps a long
 * meal with many small boluses readable: each dose sits next to the plate it was for.
 */
@Composable
private fun MealTimelineCard(state: MealDetailState, onEditCourse: (Int) -> Unit) {
    val fmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val items = remember(state.courses, state.boluses) {
        (state.courses.map { TimelineItem.Course(it) } + state.boluses.map { TimelineItem.Dose(it) }).sortedBy { it.time }
    }
    val carbs = state.courses.sumOf { it.carbohydrates }
    val insulin = state.totalInsulin
    CardSection(
        title = if (state.isMultiCourse) "Courses & insulin" else "Insulin for this meal",
        subtitle = buildString {
            if (state.boluses.isEmpty() && !state.isMultiCourse) append("No insulin found") else
            append("$carbs g carbs · ${formatUnits(insulin)} U insulin")
            if (insulin > 0.05 && carbs > 0) append(" · ${"%.0f".format(carbs / insulin)} g per U")
        }
    ) {
        var runningCarbs = 0
        var runningUnits = 0.0
        var courseNo = 0
        if (state.boluses.isEmpty()) {
            Text(
                "No insulin logged from 30 min before to 30 min after this meal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Without any insulin the carb row says nothing the rest of the page doesn't, so it is dropped.
        val shown = if (state.boluses.isEmpty() && !state.isMultiCourse) emptyList() else items
        Column {
            shown.forEachIndexed { index, item ->
                when (item) {
                    is TimelineItem.Course -> {
                        runningCarbs += item.meal.carbohydrates
                        courseNo++
                        CourseRow(
                            meal = item.meal,
                            label = if (state.isMultiCourse) "Course $courseNo" else null,
                            time = fmt.format(java.util.Date(item.time)),
                            running = if (state.isMultiCourse && courseNo > 1) "$runningCarbs g total" else null,
                            isLast = index == shown.lastIndex,
                            onClick = if (state.isMultiCourse && !item.meal.isImported) ({ onEditCourse(item.meal.id) }) else null
                        )
                    }
                    is TimelineItem.Dose -> {
                        runningUnits += item.bolus.value
                        DoseRow(
                            units = item.bolus.value.toDouble(),
                            isSmb = item.bolus.isSmb,
                            time = fmt.format(java.util.Date(item.time)),
                            running = "${formatUnits(runningUnits)} U total",
                            isLast = index == shown.lastIndex
                        )
                    }
                }
            }
        }
    }
}

private sealed interface TimelineItem {
    val time: Long
    data class Course(val meal: MealEntityUi) : TimelineItem { override val time get() = meal.mealTimeUtc }
    data class Dose(val bolus: uk.scimone.diafit.core.domain.model.BolusEntity) : TimelineItem { override val time get() = bolus.timestampUtc }
}

private fun formatUnits(units: Double): String {
    val r = Math.round(units * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

/** Left rail: a dot per item joined by a line, so the list reads as a timeline. */
@Composable
private fun TimelineRail(color: Color, isLast: Boolean, dotSize: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    val railColor = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.width(20.dp).height(height), contentAlignment = Alignment.TopCenter) {
        if (!isLast) Box(Modifier.padding(top = height / 2).width(2.dp).fillMaxHeight().background(railColor))
        Box(Modifier.padding(top = height / 2 - dotSize / 2).size(dotSize).background(color, CircleShape))
    }
}

@Composable
private fun CourseRow(meal: MealEntityUi, label: String?, time: String, running: String?, isLast: Boolean, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineRail(Carbs, isLast, 12.dp, 64.dp)
        Spacer(Modifier.width(6.dp))
        MealAvatar(meal, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(time, label).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(meal.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meal.photoUris.size > 1) {
                Text("${meal.photoUris.size} photos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            CarbPill(meal.carbohydrates)
            running?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onClick != null) {
            Icon(Icons.Filled.ChevronRight, "Edit course", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DoseRow(units: Double, isSmb: Boolean, time: String, running: String, isLast: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TimelineRail(Bolus, isLast, 8.dp, 34.dp)
        Spacer(Modifier.width(6.dp))
        Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
        Text(
            "${formatUnits(units)} U" + if (isSmb) " · SMB" else " bolus",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Bolus,
            modifier = Modifier.weight(1f)
        )
        Text(running, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun CourseNotesCard(courses: List<MealEntityUi>) {
    CardSection("AI estimates") {
        courses.forEach { course ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${course.timeFormatted} · ${course.title}", style = MaterialTheme.typography.titleSmall)
                Text(course.reasoning.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TitleBlock(meal: MealEntityUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(meal.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            InfoChip(
                if (meal.courseCount > 1) "${meal.mealType.type} · ${meal.courseCount} courses" else meal.mealType.type,
                painterResource(meal.mealType.iconRes), meal.mealType.accent
            )
            Text(
                "${friendlyDateString(meal.mealTimeUtc)}, ${meal.timeFormatted}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (meal.isImported) {
                Text("· AAPS", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val effectEnd = state.effectEndUtc
    val stillAbsorbing = System.currentTimeMillis() < effectEnd
    CardSection(
        title = "Glucose response",
        subtitle = if (state.isMultiCourse) "mg/dL · from the first course until the last one is absorbed"
        else "mg/dL · ${meal.impactType.label.lowercase()} absorption, about ${meal.impactType.durationMinutes / 60} h"
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
            eventColor = Carbs,
            laterEventTimesUtc = state.courses.drop(1).map { it.mealTimeUtc },
            otherCarbs = state.otherCarbs
        )
        Legend()
        if (response.readings.isNotEmpty()) {
            StatRow(meal, response, state.target)
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
private fun StatRow(meal: MealEntityUi, response: uk.scimone.diafit.core.domain.usecase.GlucoseResponse, target: uk.scimone.diafit.core.domain.model.GlucoseTargetRange) {
    fun tint(v: Int?) = v?.let { uk.scimone.diafit.journal.presentation.components.glucoseColor(it, target) }
    val atMeal = response.atEvent?.valueMgdl
    val peak = response.peak
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("At meal", atMeal?.toString() ?: "–", null, Modifier.weight(1f), tint(atMeal))
        StatTile(
            "Peak", peak?.valueMgdl?.toString() ?: "–",
            if (atMeal != null && peak != null && peak.valueMgdl > atMeal) "+${peak.valueMgdl - atMeal}" else null,
            Modifier.weight(1f), tint(peak?.valueMgdl)
        )
        StatTile("Lowest", response.low?.valueMgdl?.toString() ?: "–", null, Modifier.weight(1f), tint(response.low?.valueMgdl))
        StatTile("In range", if (meal.hasGlucoseData) "${meal.timeInRange.toInt()}%" else "–", null, Modifier.weight(1f),
            if (meal.hasGlucoseData) InRange else null)
    }
}

@Composable
private fun StatTile(label: String, value: String, delta: String?, modifier: Modifier = Modifier, valueColor: Color? = null) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, color = valueColor ?: Color.Unspecified)
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
private fun NutritionCard(meal: MealEntityUi, wholeMeal: Boolean) {
    CardSection("Nutrition", subtitle = if (wholeMeal) "All courses together" else null) {
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
