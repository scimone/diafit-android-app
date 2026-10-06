package uk.scimone.diafit.history.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.history.domain.model.DayGlucoseStats
import uk.scimone.diafit.history.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.domain.model.GlucoseZone
import uk.scimone.diafit.history.domain.model.zoneOf
import uk.scimone.diafit.journal.presentation.components.MealTypeTile
import uk.scimone.diafit.journal.presentation.model.GlucoseStatus
import uk.scimone.diafit.journal.presentation.model.accent
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** Below this share of the (elapsed) day covered by readings, the day's numbers are flagged as based on little data. */
private const val MIN_DAY_COVERAGE = 0.7

/** How far back the day pager reaches. */
private const val MAX_DAYS_BACK = 3650

/**
 * One day in full, opened from a History row: summary, the 24 h chart with meal photos, every meal
 * and the insulin/glucose event log. Swipe sideways (or use the arrows) to move between days.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    userId: Int,
    initialEpochDay: Long,
    onBack: () -> Unit,
    onOpenMeal: (Int) -> Unit
) {
    val today = remember { LocalDate.now().toEpochDay() }
    val pageCount = MAX_DAYS_BACK + 1
    fun epochDayOf(page: Int) = today - (pageCount - 1 - page)
    val pagerState = rememberPagerState(initialPage = (pageCount - 1 - (today - initialEpochDay)).toInt().coerceIn(0, pageCount - 1)) { pageCount }
    val scope = rememberCoroutineScope()
    val shown = LocalDate.ofEpochDay(epochDayOf(pagerState.currentPage))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(relativeDayName(shown, LocalDate.ofEpochDay(today)), style = MaterialTheme.typography.titleLarge)
                        Text(
                            shown.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        enabled = pagerState.currentPage > 0
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        enabled = pagerState.currentPage < pageCount - 1
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
                }
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            key = { epochDayOf(it) }
        ) { page ->
            DayPage(userId, epochDayOf(page), onOpenMeal)
        }
    }
}

private fun relativeDayName(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

@Composable
private fun DayPage(userId: Int, epochDay: Long, onOpenMeal: (Int) -> Unit) {
    val viewModel: DayDetailViewModel = koinViewModel(key = "history-day-$epochDay", parameters = { parametersOf(userId, epochDay) })
    val state by viewModel.state.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.isEmpty) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing recorded", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    state.errorMessage ?: "No glucose readings, meals or insulin on this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "summary") { SummaryCard(state) }
        item(key = "chart") {
            Section {
                DayGlucoseChart(
                    readings = state.readings,
                    boluses = state.boluses,
                    meals = state.meals,
                    thresholds = state.thresholds,
                    dayStartUtc = state.dayStartUtc,
                    dayEndUtc = state.dayEndUtc,
                    onMealClick = onOpenMeal
                )
                ChartLegend()
            }
        }
        if (state.meals.isNotEmpty()) {
            item(key = "meals-header") {
                SectionHeader("Meals", "${state.meals.size} · ${state.totalCarbs} g carbs")
            }
            items(state.meals, key = { "meal-${it.meal.id}" }) { meal ->
                DayMealCard(meal, state.thresholds, onClick = { onOpenMeal(meal.meal.id) })
            }
        }
        if (state.events.isNotEmpty()) {
            item(key = "events-header") { SectionHeader("Insulin & glucose events", null) }
            item(key = "events") { EventLog(state.events) }
        }
    }
}

// ---------------------------------------------------------------- summary

@Composable
private fun SummaryCard(state: DayDetailState) {
    val stats = state.stats
    Section {
        if (stats == null) {
            Text("No glucose readings on this day", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Time in range", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${percent(stats.inRangeShare)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.inRangeShare >= 0.7) InRange else MaterialTheme.colorScheme.onSurface
                        )
                        Text("%", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 5.dp, start = 2.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${state.thresholds.low}–${state.thresholds.high} mg/dL",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Judge coverage against the part of the day that has happened (today isn't over yet).
                    val elapsed = (minOf(System.currentTimeMillis(), state.dayEndUtc) - state.dayStartUtc).coerceAtLeast(1)
                    val sparse = stats.coveredMs < elapsed * MIN_DAY_COVERAGE
                    Text(
                        if (sparse) "Only ${formatDuration(stats.coveredMs)} of sensor data" else "Sensor data ${formatDuration(stats.coveredMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (sparse) FontWeight.SemiBold else null,
                        color = if (sparse) Carbs else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TimeInRangeBar(stats)
            ZoneBreakdown(stats, state.thresholds)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth()) {
                Metric("Average", "${stats.meanMgdl.toInt()}", "mg/dL", Modifier.weight(1f))
                Metric("Variability", "${stats.cvPercent.toInt()}", "% CV", Modifier.weight(1f), valueColor = if (stats.cvPercent > 36) AboveRange else null)
                Metric("Range", "${stats.minMgdl}–${stats.maxMgdl}", "mg/dL", Modifier.weight(1.3f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Metric("Carbs", "${state.totalCarbs}", "g", Modifier.weight(1f), valueColor = Carbs)
            Metric("Insulin", formatUnits(state.totalInsulin), "U", Modifier.weight(1f), valueColor = Bolus)
            if (state.smbUnits > 0) {
                Metric("Bolus · SMB", "${formatUnits(state.bolusUnits)} · ${formatUnits(state.smbUnits)}", "U", Modifier.weight(1.3f))
            } else {
                Metric("Boluses", "${state.boluses.size}", "", Modifier.weight(1.3f))
            }
        }
    }
}

private val ZONE_ORDER = listOf(GlucoseZone.VERY_LOW, GlucoseZone.LOW, GlucoseZone.IN_RANGE, GlucoseZone.HIGH, GlucoseZone.VERY_HIGH)

/** Very deep zones get the full colour, the mild ones a lighter tint of it. */
private fun zoneFill(zone: GlucoseZone): Color = when (zone) {
    GlucoseZone.VERY_LOW -> BelowRange
    GlucoseZone.LOW -> BelowRange.copy(alpha = 0.6f)
    GlucoseZone.IN_RANGE -> InRange
    GlucoseZone.HIGH -> AboveRange.copy(alpha = 0.6f)
    GlucoseZone.VERY_HIGH -> AboveRange
}

/** Stacked bar of the five zones, low on the left. */
@Composable
private fun TimeInRangeBar(stats: DayGlucoseStats) {
    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ZONE_ORDER.forEach { zone ->
            val share = stats.share(zone)
            if (share > 0.0) Box(Modifier.weight(share.toFloat()).fillMaxHeight().background(zoneFill(zone)))
        }
    }
}

@Composable
private fun ZoneBreakdown(stats: DayGlucoseStats, t: GlucoseThresholds) {
    val rows = listOf(
        Triple(GlucoseZone.VERY_HIGH, "Very high", ">${t.veryHigh}"),
        Triple(GlucoseZone.HIGH, "High", "${t.high + 1}–${t.veryHigh}"),
        Triple(GlucoseZone.IN_RANGE, "In range", "${t.low}–${t.high}"),
        Triple(GlucoseZone.LOW, "Low", "${t.veryLow}–${t.low - 1}"),
        Triple(GlucoseZone.VERY_LOW, "Very low", "<${t.veryLow}")
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (zone, label, range) ->
            val share = stats.share(zone)
            val faded = share == 0.0
            val textColor = if (faded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(zoneFill(zone).copy(alpha = if (faded) 0.3f else 1f), RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(10.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.width(84.dp))
                Text(range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (faded) 0.5f else 1f), modifier = Modifier.weight(1f))
                Text(
                    formatDuration((share * stats.coveredMs).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (faded) 0.5f else 1f)
                )
                Text(
                    "${percent(share)}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    modifier = Modifier.width(48.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(" $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun ChartLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(InRange, "In range")
        LegendDot(AboveRange, "High")
        LegendDot(BelowRange, "Low")
        LegendDot(Carbs, "Carbs")
        LegendDot(Bolus, "Insulin")
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

// ---------------------------------------------------------------- meals

/** A meal of the day: its photo, what was in it, the insulin for it and what glucose did afterwards. */
@Composable
private fun DayMealCard(item: DayMealUi, thresholds: GlucoseThresholds, onClick: () -> Unit) {
    val meal = item.meal
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                MealThumbnail(meal.photoUris, meal)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildString {
                                append(meal.timeFormatted)
                                append(" · ")
                                append(meal.mealType.type)
                                if (meal.courseCount > 1) append(" · ${meal.courseCount} courses")
                                if (meal.isImported) append(" · Imported")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = meal.mealType.accent,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Filled.ChevronRight, "Open meal", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    Text(meal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ValueChip("${meal.carbohydrates} g", "carbs", Carbs)
                        if (item.insulinUnits > 0.05) ValueChip("${formatUnits(item.insulinUnits)} U", "insulin", Bolus)
                        meal.proteins?.takeIf { it > 0 }?.let { ValueChip("$it g", "protein", null) }
                        meal.fats?.takeIf { it > 0 }?.let { ValueChip("$it g", "fat", null) }
                        meal.calories?.takeIf { it > 0 }?.let { ValueChip("$it", "kcal", null) }
                    }
                }
            }
            MealOutcome(item, thresholds, clock)
        }
    }
}

/** The cover photo (or the meal-type tile), with a "+N" badge when there are more photos. */
@Composable
private fun MealThumbnail(photos: List<android.net.Uri>, meal: uk.scimone.diafit.journal.presentation.model.MealEntityUi) {
    val size = 96.dp
    if (photos.isEmpty()) {
        MealTypeTile(meal.mealType, size)
        return
    }
    Box(Modifier.size(size)) {
        AsyncImage(
            model = photos.first(),
            contentDescription = meal.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
        )
        if (photos.size > 1) {
            Text(
                "+${photos.size - 1}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ValueChip(value: String, label: String, accent: Color?) {
    Row(
        Modifier
            .background((accent ?: MaterialTheme.colorScheme.onSurface).copy(alpha = if (accent != null) 0.16f else 0.06f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(" $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Glucose 112 → peak 186 (+74) after 1 h 05 · 64% in range over 4 h", or why it isn't known yet. */
@Composable
private fun MealOutcome(item: DayMealUi, thresholds: GlucoseThresholds, clock: SimpleDateFormat) {
    val meal = item.meal
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val start = item.startMgdl
        val peak = item.peakMgdl
        if (start != null || peak != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Glucose ", style = MaterialTheme.typography.labelMedium, color = muted)
                start?.let { GlucoseValue(it, thresholds) }
                if (peak != null && start != null && peak > start) {
                    Text("  →  peak ", style = MaterialTheme.typography.labelMedium, color = muted)
                    GlucoseValue(peak, thresholds)
                    Text(" (+${peak - start})", style = MaterialTheme.typography.labelMedium, color = muted)
                    item.peakTimeUtc?.let {
                        Text(" at ${clock.format(Date(it))}", style = MaterialTheme.typography.labelMedium, color = muted)
                    }
                } else if (start == null && peak != null) {
                    Text("peak ", style = MaterialTheme.typography.labelMedium, color = muted)
                    GlucoseValue(peak, thresholds)
                }
            }
        }
        when (meal.glucoseStatus) {
            GlucoseStatus.READY -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniRangeBar(meal.timeBelowRange, meal.timeInRange, meal.timeAboveRange, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text("${meal.timeInRange.toInt()}% in range · 4 h", style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
            GlucoseStatus.TOO_EARLY -> Text("Outcome is shown 4 h after the meal", style = MaterialTheme.typography.labelMedium, color = muted)
            GlucoseStatus.NOT_ENOUGH_DATA -> Text("Not enough sensor data after this meal", style = MaterialTheme.typography.labelMedium, color = muted)
        }
    }
}

@Composable
private fun GlucoseValue(mgdl: Int, thresholds: GlucoseThresholds) {
    Text(
        "$mgdl",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = zoneColor(thresholds.zoneOf(mgdl))
    )
}

@Composable
private fun MiniRangeBar(below: Double, inRange: Double, above: Double, modifier: Modifier = Modifier) {
    Row(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        listOf(below to BelowRange, inRange to InRange, above to AboveRange).forEach { (share, color) ->
            if (share > 0.0) Box(Modifier.weight(share.toFloat()).fillMaxHeight().background(color))
        }
    }
}

// ---------------------------------------------------------------- event log

@Composable
private fun EventLog(events: List<DayEventUi>) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Section(contentSpacing = 0.dp) {
        events.forEachIndexed { index, event ->
            when (event) {
                is DayEventUi.Bolus -> EventRow(
                    time = clock.format(Date(event.timeUtc)),
                    dot = Bolus,
                    title = "${formatUnits(event.units)} U bolus",
                    detail = null,
                    titleColor = Bolus
                )
                is DayEventUi.Smbs -> EventRow(
                    time = clock.format(Date(event.timeUtc)),
                    dot = Bolus.copy(alpha = 0.5f),
                    title = "${formatUnits(event.units)} U in ${event.count} SMB${if (event.count > 1) "s" else ""}",
                    detail = if (event.count > 1) "until ${clock.format(Date(event.endUtc))}" else null,
                    titleColor = MaterialTheme.colorScheme.onSurface
                )
                is DayEventUi.Episode -> {
                    val e = event.episode
                    val color = if (e.isLow) BelowRange else AboveRange
                    EventRow(
                        time = clock.format(Date(e.startUtc)),
                        dot = color,
                        title = when {
                            e.isLow && e.isSevere -> "Very low"
                            e.isLow -> "Low"
                            e.isSevere -> "Very high"
                            else -> "High"
                        } + " for ${formatDuration(e.durationMs)}",
                        detail = (if (e.isLow) "lowest " else "highest ") + "${e.extremeMgdl} mg/dL",
                        titleColor = color,
                        icon = if (e.isLow) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward
                    )
                }
            }
            if (index < events.lastIndex) {
                HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
private fun EventRow(
    time: String,
    dot: Color,
    title: String,
    detail: String?,
    titleColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = dot, modifier = Modifier.size(16.dp))
            else Box(Modifier.size(8.dp).background(dot, CircleShape))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = titleColor, modifier = Modifier.weight(1f))
        detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
private fun Section(contentSpacing: androidx.compose.ui.unit.Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = if (contentSpacing == 0.dp) 4.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(contentSpacing), content = content)
    }
}

@Composable
private fun SectionHeader(title: String, detail: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun percent(share: Double): Int = Math.round(share * 100).toInt()

/** "45 min", "2 h 05", "24 h". */
fun formatDuration(ms: Long): String {
    val minutes = Math.round(ms / 60_000.0).toInt()
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h %02d".format(m)
    }
}
