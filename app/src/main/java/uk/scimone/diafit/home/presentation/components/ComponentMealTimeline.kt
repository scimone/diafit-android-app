package uk.scimone.diafit.home.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.home.presentation.model.MealEntityUi
import uk.scimone.diafit.ui.theme.Carbs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Meals logged within this window of each other are one sitting (e.g. main + drink), shown as one card. */
const val MEAL_GROUP_WINDOW_MS = 15 * 60_000L

/** One sitting: one or more meals close together in time, sorted oldest first. */
data class MealGroup(val meals: List<MealEntityUi>) {
    val startTime: Long get() = meals.first().mealTimeUtc
    val endTime: Long get() = meals.last().mealTimeUtc
    val totalCarbs: Int get() = meals.sumOf { it.carbohydrates }
    val photos get() = meals.mapNotNull { it.imageUri }
    val key: Int get() = meals.first().id

    /** Distance from [time] to this sitting (0 if it falls inside it). */
    fun distanceTo(time: Long): Long = when {
        time < startTime -> startTime - time
        time > endTime -> time - endTime
        else -> 0L
    }
}

/** Chains meals into sittings: a meal joins the current group if it's within the window of the previous one. */
fun groupMeals(meals: List<MealEntityUi>, windowMs: Long = MEAL_GROUP_WINDOW_MS): List<MealGroup> {
    val groups = mutableListOf<MutableList<MealEntityUi>>()
    for (meal in meals.sortedBy { it.mealTimeUtc }) {
        val current = groups.lastOrNull()
        if (current != null && meal.mealTimeUtc - current.last().mealTimeUtc <= windowMs) current += meal
        else groups += mutableListOf(meal)
    }
    return groups.map { MealGroup(it) }
}

/** The sitting closest to [time], if one is within [toleranceMs]. */
fun List<MealGroup>.nearest(time: Long?, toleranceMs: Long = 20 * 60_000L): MealGroup? =
    time?.let { t -> filter { it.distanceTo(t) <= toleranceMs }.minByOrNull { it.distanceTo(t) } }

private fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

private fun MealGroup.title(): String = meals.mapNotNull { it.description?.takeIf(String::isNotBlank) }
    .distinct().joinToString(" · ").ifEmpty { meals.first().mealType.type }

/**
 * A horizontal "film strip" of the last 24h's meals under the charts. It reads in the same direction
 * as the charts (newest on the right, where "now" is) and opens on the newest meal. While the user
 * scrubs the charts, the sitting under the cursor is highlighted and scrolled into view, the rest dims.
 */
@Composable
fun MealTimeline(
    groups: List<MealGroup>,
    highlighted: MealGroup?,
    onGroupClick: (MealGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 20.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text("Meals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (groups.isNotEmpty()) {
                Text(
                    text = "${groups.sumOf { it.totalCarbs }} g carbs · last 24 h",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (groups.isEmpty()) {
            EmptyMealTimeline()
            return@Column
        }

        // reverseLayout + newest-first: starts at the right edge (newest) and scrolls back in time.
        val newestFirst = remember(groups) { groups.reversed() }
        val listState = rememberLazyListState()
        val highlightIndex = highlighted?.let { h -> newestFirst.indexOfFirst { it.key == h.key } } ?: -1
        LaunchedEffect(highlightIndex) {
            if (highlightIndex >= 0) listState.animateScrollToItem(highlightIndex)
        }
        LazyRow(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            itemsIndexed(newestFirst, key = { _, g -> g.key }) { index, group ->
                MealCard(
                    group = group,
                    highlighted = index == highlightIndex,
                    dimmed = highlightIndex >= 0 && index != highlightIndex,
                    onClick = { onGroupClick(group) }
                )
            }
        }
    }
}

private val CardWidth = 132.dp
private val CardShape = RoundedCornerShape(20.dp)

@Composable
private fun MealCard(group: MealGroup, highlighted: Boolean, dimmed: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (highlighted) 1.04f else 1f, label = "cardScale")
    val alpha by animateFloatAsState(if (dimmed) 0.45f else 1f, label = "cardAlpha")
    val time = formatTime(group.startTime).let { start ->
        val end = formatTime(group.endTime)
        if (end != start) "$start–$end" else start
    }

    Column(
        modifier = Modifier
            .width(CardWidth)
            .padding(vertical = 4.dp)
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
            .clip(CardShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${group.title()}, ${group.totalCarbs} grams of carbs at $time" }
    ) {
        Box(
            modifier = Modifier
                .size(CardWidth)
                .clip(CardShape)
                .then(if (highlighted) Modifier.border(3.dp, Carbs, CardShape) else Modifier)
        ) {
            PhotoMosaic(group)
            // Count badge for multi-item sittings
            if (group.meals.size > 1) {
                Text(
                    text = "${group.meals.size} items",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            CarbPill(grams = group.totalCarbs, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = group.title(),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/** 1 photo fills the tile; 2 split it; 3+ show one large and two stacked, with "+N" on the last. */
@Composable
private fun PhotoMosaic(group: MealGroup) {
    val photos = group.photos
    val gap = 2.dp
    when {
        photos.isEmpty() -> NoPhotoTile(Modifier.fillMaxSize())
        photos.size == 1 -> MealPhoto(photos[0], Modifier.fillMaxSize())
        photos.size == 2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            MealPhoto(photos[0], Modifier.weight(1f).fillMaxHeight())
            MealPhoto(photos[1], Modifier.weight(1f).fillMaxHeight())
        }
        else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            MealPhoto(photos[0], Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                MealPhoto(photos[1], Modifier.weight(1f).fillMaxWidth())
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    MealPhoto(photos[2], Modifier.fillMaxSize())
                    if (photos.size > 3) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+${photos.size - 3}", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealPhoto(uri: android.net.Uri, modifier: Modifier) {
    AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

/** Carb-only entries (e.g. from AAPS) get a calm tonal tile instead of a broken image. */
@Composable
private fun NoPhotoTile(modifier: Modifier, iconSize: Dp = 40.dp) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(Carbs.copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceVariant))
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = Carbs, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun CarbPill(grams: Int, modifier: Modifier = Modifier) {
    Text(
        text = "$grams g",
        color = Color.Black,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Carbs, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun EmptyMealTimeline() {
    Row(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = Carbs)
        Spacer(Modifier.width(12.dp))
        Text(
            "No meals in the last 24 h. Tap + to log one with a photo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Full-size view of a sitting: swipe between its items, each with its photo, time, macros and the
 * AI's notes (if the meal was analysed).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MealDetailSheet(group: MealGroup, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        val pagerState = rememberPagerState { group.meals.size }
        Column(Modifier.padding(bottom = 24.dp)) {
            if (group.meals.size > 1) {
                Text(
                    text = "${group.meals.size} items · ${group.totalCarbs} g carbs in total",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
                )
            }
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 12.dp
            ) { page ->
                val meal = group.meals[page]
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(24.dp))) {
                    if (meal.imageUri != null) MealPhoto(meal.imageUri, Modifier.fillMaxSize())
                    else NoPhotoTile(Modifier.fillMaxSize(), iconSize = 64.dp)
                }
            }
            if (group.meals.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(group.meals.size) { i ->
                        val selected = i == pagerState.currentPage
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (selected) 8.dp else 6.dp)
                                .background(
                                    if (selected) Carbs else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    CircleShape
                                )
                        )
                    }
                }
            }
            MealDetails(group.meals[pagerState.currentPage])
        }
    }
}

@Composable
private fun MealDetails(meal: MealEntityUi) {
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Schedule, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${meal.mealType.type} · ${formatTime(meal.mealTimeUtc)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            AbsorptionChip(meal.impactType)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = meal.description?.takeIf { it.isNotBlank() } ?: meal.mealType.type,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroTile("Carbs", "${meal.carbohydrates}", "g", accent = Carbs, modifier = Modifier.weight(1f))
            MacroTile("Protein", meal.proteins?.toString(), "g", modifier = Modifier.weight(1f))
            MacroTile("Fat", meal.fats?.toString(), "g", modifier = Modifier.weight(1f))
            MacroTile("Energy", meal.calories?.toString(), "kcal", modifier = Modifier.weight(1f))
        }
        meal.reasoning?.takeIf { it.isNotBlank() }?.let { notes ->
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp)
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome, contentDescription = "AI estimate",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AbsorptionChip(impact: ImpactType) {
    val label = when (impact) {
        ImpactType.SHORT -> "Fast"
        ImpactType.MEDIUM -> "Medium"
        ImpactType.LONG -> "Slow"
    }
    Text(
        text = "$label absorption · ${impact.durationMinutes / 60} h",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun MacroTile(label: String, value: String?, unit: String, modifier: Modifier = Modifier, accent: Color? = null) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent?.copy(alpha = 0.18f) ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 10.dp, horizontal = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value ?: "–",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent ?: MaterialTheme.colorScheme.onSurface
            )
            if (value != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    unit, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

/** Header preview while scrubbing: the sitting's first photo (or tile) with its carbs. */
@Composable
fun MealGroupPreview(group: MealGroup, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(84.dp).clip(RoundedCornerShape(16.dp))) {
        PhotoMosaic(group)
        Text(
            text = "${group.totalCarbs} g",
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp)
                .background(Carbs, CircleShape)
                .padding(horizontal = 7.dp, vertical = 1.dp)
        )
    }
}
