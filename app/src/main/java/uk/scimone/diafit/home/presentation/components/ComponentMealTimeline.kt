package uk.scimone.diafit.home.presentation.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import uk.scimone.diafit.home.presentation.utils.ChartGeometry
import kotlin.math.roundToInt
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

/** Which sittings fall inside the charts' visible time window, and the nearest ones just outside it. */
data class MealsInView(
    val groups: List<MealGroup>,
    val earlier: List<MealGroup>,
    val later: List<MealGroup>
)

fun List<MealGroup>.inView(start: Long, end: Long) = MealsInView(
    groups = filter { it.endTime >= start && it.startTime <= end },
    earlier = filter { it.endTime < start },
    later = filter { it.startTime > end }
)

/**
 * A horizontal "film strip" of the sittings that are currently visible on the charts above (like the
 * result list under a map: it follows panning and zooming). It reads in the same direction as the
 * charts, newest on the right. Sittings just outside the window are offered as "‹ N earlier" /
 * "N later ›" chips that pan the charts to them ([onReveal]). The sitting under the inspection
 * cursor is highlighted and the rest dims.
 */
@Composable
fun MealTimeline(
    allGroups: List<MealGroup>,
    inView: MealsInView,
    highlighted: MealGroup?,
    onGroupClick: (MealGroup) -> Unit,
    onReveal: (MealGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Meals in view", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            if (inView.groups.isNotEmpty()) {
                Text(
                    text = "${inView.groups.sumOf { it.totalCarbs }} g",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            inView.earlier.lastOrNull()?.let { RevealChip("‹ ${inView.earlier.size} earlier") { onReveal(it) } }
            inView.later.firstOrNull()?.let {
                Spacer(Modifier.width(6.dp))
                RevealChip("${inView.later.size} later ›") { onReveal(it) }
            }
        }

        if (allGroups.isEmpty()) {
            EmptyMealTimeline("No meals in the last 24 h. Tap + to log one with a photo.")
            return@Column
        }
        if (inView.groups.isEmpty()) {
            EmptyMealTimeline("No meals in this part of the chart.")
            return@Column
        }

        // reverseLayout + newest-first: starts at the right edge (newest) and scrolls back in time.
        val newestFirst = inView.groups.asReversed()
        val listState = rememberLazyListState()
        val highlightIndex = highlighted?.let { h -> newestFirst.indexOfFirst { it.key == h.key } } ?: -1
        LaunchedEffect(highlightIndex) {
            if (highlightIndex < 0) return@LaunchedEffect
            // Only move the strip if the card isn't already fully on screen: no needless motion.
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == highlightIndex }
            val fullyVisible = item != null && item.offset >= info.viewportStartOffset &&
                item.offset + item.size <= info.viewportEndOffset
            if (!fullyVisible) listState.animateScrollToItem(highlightIndex)
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

@Composable
private fun RevealChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

val MealPinSize = 34.dp
private val PinStem = 6.dp
val MealPinLaneHeight = PinStem + MealPinSize + 6.dp

/**
 * Meal "map pins" in a lane directly under the charts: every sitting is a small round photo at its
 * exact time on the shared x axis, so it scrolls and zooms with the charts and shows at a glance which
 * meal belongs to which part of the curve. Pins closer than a pin's width are merged (count badge).
 * Positions are read from [geometry] in the layout phase only, so panning doesn't recompose.
 */
@Composable
fun MealPinLane(
    groups: List<MealGroup>,
    geometry: State<ChartGeometry?>,
    highlighted: MealGroup?,
    onPinClick: (MealGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    val pinPx = with(LocalDensity.current) { MealPinSize.toPx() }
    // Only changes while zooming, not while panning.
    val pxPerMs by remember { derivedStateOf { geometry.value?.pxPerMs } }
    val clusters = remember(groups, pxPerMs) {
        val scale = pxPerMs ?: return@remember emptyList()
        val minGapMs = (pinPx * 1.1f / scale).toLong()
        val out = mutableListOf<MutableList<MealGroup>>()
        for (g in groups) {
            val last = out.lastOrNull()
            if (last != null && g.startTime - last.first().startTime < minGapMs) last += g else out += mutableListOf(g)
        }
        out.map { MealGroup(it.flatMap(MealGroup::meals)) }
    }
    Box(modifier.fillMaxWidth().height(MealPinLaneHeight).clipToBounds()) {
        clusters.forEach { cluster ->
            key(cluster.key) {
                val isHighlighted = highlighted != null && cluster.meals.any { it.id == highlighted.key }
                MealPin(
                    group = cluster,
                    highlighted = isHighlighted,
                    onClick = { onPinClick(cluster) },
                    modifier = Modifier.offset {
                        val x = geometry.value?.xOf(cluster.startTime) ?: -1000f
                        IntOffset((x - pinPx / 2).roundToInt(), 0)
                    }
                )
            }
        }
    }
}

@Composable
private fun MealPin(group: MealGroup, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scale by animateFloatAsState(if (highlighted) 1.15f else 1f, label = "pinScale")
    Column(modifier.width(MealPinSize), horizontalAlignment = Alignment.CenterHorizontally) {
        // Stem pointing up at the meal's moment on the carb curve.
        Box(Modifier.width(2.dp).height(PinStem).background(Carbs.copy(alpha = if (highlighted) 1f else 0.6f)))
        Box(
            Modifier
                .size(MealPinSize)
                .scale(scale)
                .clip(CircleShape)
                .border(2.dp, if (highlighted) Carbs else MaterialTheme.colorScheme.surface, CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "${group.title()}, ${group.totalCarbs} grams of carbs at ${formatTime(group.startTime)}" }
        ) {
            val photo = group.photos.firstOrNull()
            if (photo != null) MealPhoto(photo, Modifier.fillMaxSize()) else NoPhotoTile(Modifier.fillMaxSize(), iconSize = 18.dp)
            if (group.meals.size > 1) {
                Text(
                    text = "${group.meals.size}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 5.dp)
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
private fun EmptyMealTimeline(message: String) {
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
            message,
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
fun MealDetailSheet(
    group: MealGroup,
    onDismiss: () -> Unit,
    createCameraUri: (mealId: Int) -> android.net.Uri,
    onCameraResult: (success: Boolean) -> Unit,
    onPickPhoto: (mealId: Int, uri: android.net.Uri) -> Unit
) {
    var galleryMealId by remember { mutableStateOf<Int?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), onCameraResult)
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = galleryMealId
        galleryMealId = null
        if (uri != null && id != null) onPickPhoto(id, uri)
    }
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
                    else {
                        NoPhotoTile(Modifier.fillMaxSize(), iconSize = 64.dp)
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(onClick = { cameraLauncher.launch(createCameraUri(meal.id)) }) {
                                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Take photo")
                            }
                            FilledTonalButton(onClick = {
                                galleryMealId = meal.id
                                galleryLauncher.launch("image/*")
                            }) {
                                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Gallery")
                            }
                        }
                    }
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
