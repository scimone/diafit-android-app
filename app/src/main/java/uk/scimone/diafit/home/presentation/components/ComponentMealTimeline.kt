package uk.scimone.diafit.home.presentation.components

import androidx.compose.ui.res.pluralStringResource
import uk.scimone.diafit.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import uk.scimone.diafit.home.presentation.utils.ChartGeometry
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import uk.scimone.diafit.core.domain.util.friendlyDateString
import uk.scimone.diafit.logs.presentation.components.MealMetaRow
import androidx.compose.ui.res.painterResource
import uk.scimone.diafit.logs.presentation.model.accent
import uk.scimone.diafit.logs.presentation.model.iconRes
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.groupIntoSittings
import uk.scimone.diafit.home.presentation.model.MealEntityUi
import uk.scimone.diafit.ui.theme.Carbs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One sitting: the courses of one meal (plus untagged entries close to it), sorted oldest first. */
data class MealGroup(val meals: List<MealEntityUi>) {
    val startTime: Long get() = meals.first().mealTimeUtc
    val endTime: Long get() = meals.last().mealTimeUtc
    val totalCarbs: Int get() = meals.sumOf { it.carbohydrates }
    val photos get() = meals.flatMap { it.photoUris }
    val key: Int get() = meals.first().id
    /** A course the user logged here (not imported), which "add a course" can attach to. */
    val loggedCourse: MealEntityUi? get() = meals.lastOrNull { !it.isImported }

    /** Distance from [time] to this sitting (0 if it falls inside it). */
    fun distanceTo(time: Long): Long = when {
        time < startTime -> startTime - time
        time > endTime -> time - endTime
        else -> 0L
    }
}

/** Courses of one meal form a sitting; untagged entries (imported carbs) join one if close in time. */
fun groupMeals(meals: List<MealEntityUi>): List<MealGroup> =
    groupIntoSittings(meals, { it.mealTimeUtc }, { it.sittingId }).map { MealGroup(it) }

/** The sitting closest to [time], if one is within [toleranceMs]. */
fun List<MealGroup>.nearest(time: Long?, toleranceMs: Long = 20 * 60_000L): MealGroup? =
    time?.let { t -> filter { it.distanceTo(t) <= toleranceMs }.minByOrNull { it.distanceTo(t) } }

private fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

private fun MealGroup.title(): String = meals.firstNotNullOfOrNull { it.sittingName?.takeIf(String::isNotBlank) }
    ?: meals.firstNotNullOfOrNull { it.description?.takeIf(String::isNotBlank) }
    ?: meals.first().mealType.displayName

/**
 * Every sitting a bubble under the cursor stands for: those near [time], plus any chained to them
 * within [chainMs] (the charts merge events into one bubble the same way).
 */
fun List<MealGroup>.caughtBy(time: Long?, toleranceMs: Long = 20 * 60_000L, chainMs: Long = 15 * 60_000L): List<MealGroup> {
    if (time == null) return emptyList()
    val caught = filter { it.distanceTo(time) <= toleranceMs }.toMutableList()
    if (caught.isEmpty()) return emptyList()
    var changed = true
    while (changed) {
        changed = false
        for (g in this) {
            if (g in caught) continue
            if (caught.any { c -> g.distanceTo(c.startTime) <= chainMs || g.distanceTo(c.endTime) <= chainMs }) {
                caught += g; changed = true
            }
        }
    }
    return caught
}

/** The sittings that overlap the charts' visible time window [start]..[end]. */
fun List<MealGroup>.inView(start: Long, end: Long): List<MealGroup> =
    filter { it.endTime >= start && it.startTime <= end }

/**
 * A compact "film strip" under the carb panel with the sittings currently visible on the charts
 * (it follows panning and zooming). It reads in the same direction as the charts, newest on the
 * right. Each card is just the photo with its carbs and time on top; details open on tap. The sitting
 * under the inspection cursor is highlighted and the rest dims. Fixed height, so the page doesn't jump.
 */
@Composable
fun MealTimeline(
    allGroups: List<MealGroup>,
    inView: List<MealGroup>,
    highlighted: List<MealGroup>,
    /** Sittings the user picked by tapping their photo or their bubble; tapping a selected photo opens its details. */
    selectedKeys: Set<Int> = emptySet(),
    geometry: State<ChartGeometry?>,
    scrollState: VicoScrollState,
    /** Tap on an unselected card: select it (highlights its data point). */
    onGroupClick: (MealGroup) -> Unit,
    /** Tapping an already selected card, or a long press on any card. */
    onGroupOpen: (MealGroup) -> Unit = {},
    /** Tap on the strip outside any card. */
    onBackgroundTap: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** Shown when there are no meals at all (Home's default invites logging one). */
    noMealsMessage: String = stringResource(R.string.home_no_meals)
) {
    // Dragging the strip pans the charts (and the cards follow, being tied to the data points).
        val scope = rememberCoroutineScope()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val flingJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val decay = remember(density) { splineBasedDecay<Float>(density) }
    Box(
        modifier = modifier.fillMaxWidth().height(CardSize + 8.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onBackgroundTap() }) }
            .then(
Modifier.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { dx ->
                    flingJob.value?.cancel()
                    scope.launch { scrollState.scroll(Scroll.Absolute.pixels(scrollState.value - dx)) }
                },
                onDragStarted = { flingJob.value?.cancel() },
                onDragStopped = { velocity ->
                    // Carry on after the finger lifts, like the charts do.
                    flingJob.value = scope.launch {
                        AnimationState(scrollState.value, -velocity).animateDecay(decay) {
                            val target = value
                            scope.launch { scrollState.scroll(Scroll.Absolute.pixels(target)) }
                        }
                    }
                }
            )
        )
    ) {
        if (inView.isEmpty()) {
            EmptyMealTimeline(
                if (allGroups.isEmpty()) noMealsMessage
                else stringResource(R.string.home_no_meals_here)
            )
            return@Box
        }

        val highlightKeys = highlighted.map { it.key }.toSet()
        val ordered = inView.sortedBy { it.startTime }
        // Each card sits under its meal's data point on the charts (pushed aside only to avoid overlapping
        // a neighbour, and kept on screen at the edges). Geometry is read in the layout pass, so panning
        // moves the cards without recomposing.
        Layout(
            content = {
                ordered.forEach { group ->
                    key(group.key) {
                        MealCard(
                            group = group,
                            highlighted = group.key in highlightKeys,
                            selected = group.key in selectedKeys,
                            dimmed = highlightKeys.isNotEmpty() && group.key !in highlightKeys,
                            onClick = { if (group.key in selectedKeys) onGroupOpen(group) else onGroupClick(group) },
                            onOpen = { onGroupOpen(group) }
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { measurables, constraints ->
            val size = CardSize.roundToPx()
            val gap = 8.dp.roundToPx()
            val width = constraints.maxWidth
            val placeables = measurables.map { it.measure(Constraints.fixed(size, size)) }
            val g = geometry.value
            val xs = IntArray(placeables.size) { i ->
                if (g == null) width - size
                else (g.xOf(ordered[i].startTime) - size / 2f).toInt()
            }
            val maxX = (width - size).coerceAtLeast(0)
            for (i in xs.indices) {
                xs[i] = xs[i].coerceIn(0, maxX)
                if (i > 0) xs[i] = maxOf(xs[i], xs[i - 1] + size + gap)
            }
            for (i in xs.indices.reversed()) {
                if (xs[i] > maxX) xs[i] = maxX
                if (i < xs.lastIndex) xs[i] = minOf(xs[i], xs[i + 1] - size - gap)
            }
            layout(width, constraints.maxHeight) {
                val y = (constraints.maxHeight - size) / 2
                placeables.forEachIndexed { i, p -> p.place(xs[i], y) }
            }
        }
    }
}

private val CardSize = 104.dp
private val CardShape = RoundedCornerShape(18.dp)

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MealCard(
    group: MealGroup, highlighted: Boolean, selected: Boolean, dimmed: Boolean,
    onClick: () -> Unit, onOpen: () -> Unit
) {
    val scale by animateFloatAsState(if (highlighted) 1.04f else 1f, label = "cardScale")
    val alpha by animateFloatAsState(if (dimmed) 0.45f else 1f, label = "cardAlpha")
    val time = formatTime(group.startTime)
    val mealCd = stringResource(R.string.home_meal_cd, group.title(), group.totalCarbs, time)

    Box(
        modifier = Modifier
            .size(CardSize)
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
            .clip(CardShape)
            .then(if (highlighted) Modifier.border(3.dp, Carbs, CardShape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onOpen)
            .semantics { contentDescription = mealCd }
    ) {
        PhotoMosaic(group)
        // Count badge for multi-item sittings
        if (group.meals.size > 1) {
            Text(
                text = pluralStringResource(R.plurals.home_courses, group.meals.size, group.meals.size),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        CarbPill(grams = group.totalCarbs, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        Text(
            text = time,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PhotoMosaic(group: MealGroup) = UriMosaic(group.photos)

/** 1 photo fills the tile; 2 split it; 3+ show one large and two stacked, with "+N" on the last. */
@Composable
internal fun UriMosaic(photos: List<android.net.Uri>) {
    val gap = 2.dp
    when {
        photos.isEmpty() -> NoPhotoTile(Modifier.fillMaxSize())
        photos.size == 1 -> MealPhoto(photos[0], Modifier.fillMaxSize())
        photos.size == 2 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            MealPhoto(photos[0], Modifier.weight(1f).fillMaxWidth())
            MealPhoto(photos[1], Modifier.weight(1f).fillMaxWidth())
        }
        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            MealPhoto(photos[0], Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                MealPhoto(photos[1], Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) {
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
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Carbs, CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun EmptyMealTimeline(message: String) {
    Row(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxSize()
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
    onPickPhoto: (mealId: Int, uri: android.net.Uri) -> Unit,
    onAddCourse: (mealId: Int) -> Unit,
    onOpenFull: (mealId: Int) -> Unit = {}
) {
    var galleryMealId by remember { mutableStateOf<Int?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), onCameraResult)
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = galleryMealId
        galleryMealId = null
        if (uri != null && id != null) onPickPhoto(id, uri)
    }
    // Always fits its content, so every preview has the same layout (photo, heading, macros, padding).
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState { group.meals.size }
    val openThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                // Dragging the preview upwards opens the full meal page. Watches the touches without consuming them.
                .pointerInput(group) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var dy = 0f
                        var fired = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            dy += change.positionChange().y
                            if (!fired && dy < -openThresholdPx) {
                                fired = true
                                onOpenFull(group.meals[pagerState.currentPage].id)
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                group.loggedCourse?.let { course ->
                    TextButton(onClick = { onAddCourse(course.id) }) {
                        Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.meal_add_course))
                    }
                }
            }
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 12.dp
            ) { page ->
                val meal = group.meals[page]
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(24.dp))) {
                    if (meal.photoUris.isNotEmpty()) UriMosaic(meal.photoUris)
                    else {
                        NoPhotoTile(Modifier.fillMaxSize(), iconSize = 64.dp)
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(onClick = { cameraLauncher.launch(createCameraUri(meal.id)) }) {
                                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.meal_take_photo))
                            }
                            FilledTonalButton(onClick = {
                                galleryMealId = meal.id
                                galleryLauncher.launch("image/*")
                            }) {
                                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.meal_gallery))
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
        MealMetaRow(
            "${friendlyDateString(meal.mealTimeUtc)}, ${formatTime(meal.mealTimeUtc)}",
            meal.mealType, meal.impactType
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = meal.description?.takeIf { it.isNotBlank() } ?: meal.mealType.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroTile(stringResource(R.string.macro_carbs), "${meal.carbohydrates}", "g", accent = Carbs, modifier = Modifier.weight(1f))
            MacroTile(stringResource(R.string.macro_protein), meal.proteins?.toString(), "g", modifier = Modifier.weight(1f))
            MacroTile(stringResource(R.string.macro_fat), meal.fats?.toString(), "g", modifier = Modifier.weight(1f))
            MacroTile(stringResource(R.string.macro_energy), meal.calories?.toString(), "kcal", modifier = Modifier.weight(1f))
        }
    }
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
