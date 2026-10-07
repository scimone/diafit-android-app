package uk.scimone.diafit.addmeal.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.addmeal.presentation.components.*
import uk.scimone.diafit.core.domain.model.MealPhoto
import uk.scimone.diafit.journal.presentation.components.SectionLabel
import uk.scimone.diafit.journal.presentation.model.hint
import uk.scimone.diafit.journal.presentation.model.label
import uk.scimone.diafit.ui.theme.Carbs
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Create ([mealId] == null), add a course to the meal of [addToMealId], or edit the course [mealId].
 * Full-screen: the host decides what happens on [onClose] (nothing saved), [onSaved] and [onDeleted].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditorScreen(
    userId: Int,
    mealId: Int?,
    addToMealId: Int? = null,
    onClose: () -> Unit,
    onSaved: (EditorResult.Saved) -> Unit,
    onDeleted: (mealId: Int) -> Unit,
    viewModel: AddMealViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(mealId, addToMealId) {
        when {
            mealId != null -> viewModel.startEditing(mealId)
            addToMealId != null -> viewModel.startAddingCourse(addToMealId)
            else -> viewModel.startNewMeal()
        }
    }

    LaunchedEffect(uiState.finished) {
        when (val result = uiState.finished) {
            is EditorResult.Saved -> onSaved(result)
            is EditorResult.Deleted -> onDeleted(result.mealId)
            null -> return@LaunchedEffect
        }
        viewModel.consumeFinished()
    }

    val takePictureLauncher = rememberLauncherForActivityResult(TakePicture(), viewModel::onCameraResult)
    // The system photo picker: no storage permission needed, several photos at once.
    val galleryLauncher = rememberLauncherForActivityResult(PickMultipleVisualMedia(MAX_PHOTOS_PER_COURSE)) { uris ->
        viewModel.onGalleryImagesPicked(uris)
    }
    val takePhoto = { takePictureLauncher.launch(viewModel.createCameraImageUri()) }
    val pickPhoto = { galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
    val extendedCourse = uiState.sitting != null

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.resetSnackbar()
        }
    }

    val requestClose = {
        if (viewModel.isDirty()) confirmDiscard = true else { viewModel.discard(); onClose() }
    }
    BackHandler(onBack = requestClose)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                // The entry's name is the title, typed in place; the small line above says what is being done.
                title = {
                    TitleField(
                        overline = when {
                            uiState.isAddingCourse -> "Add course"
                            uiState.isEditing && extendedCourse -> "Edit course"
                            uiState.isEditing -> "Edit meal"
                            else -> "New meal"
                        } + if (!uiState.dishName.isNullOrBlank() && uiState.description == uiState.dishName) " · named by AI" else "",
                        value = uiState.description.orEmpty(),
                        placeholder = if (uiState.isAddingCourse) "What arrived?" else "What did you eat?",
                        onValueChange = viewModel::onDescriptionChanged
                    )
                },
                navigationIcon = {
                    IconButton(onClick = requestClose) { Icon(Icons.Filled.Close, "Close") }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.DeleteOutline, if (extendedCourse) "Delete course" else "Delete meal")
                        }
                    }
                }
            )
        },
        bottomBar = { SaveBar(uiState, onSave = viewModel::saveMeal) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            WhenRow(
                value = uiState.mealTime?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() },
                onValueChange = viewModel::onMealTimeChanged,
                // Courses inherit the meal's type; only the meal as a whole has one.
                mealType = uiState.mealType.takeIf { !extendedCourse },
                onMealTypeChange = viewModel::onMealTypeChanged
            )

            uiState.sitting?.let {
                CourseContextCard(it, uiState.isAddingCourse, uiState.mealName, viewModel::onMealNameChanged)
            }

            PhotoSection(
                photos = uiState.photos,
                canAddPhoto = uiState.canAddPhoto,
                onTakePhoto = takePhoto,
                onPickPhoto = pickPhoto,
                onRemove = viewModel::onRemovePhoto
            )

            // Asking the AI; once it answered, its foods appear under the totals instead.
            if (uiState.photos.isNotEmpty() && (uiState.components.isEmpty() || uiState.isAnalyzing)) {
                AiRequestCard(uiState, viewModel::onAiNotesChanged, viewModel::analyzeMeal)
            }

            TotalsCard(
                state = uiState,
                onCarbs = viewModel::onCarbsChanged,
                onProtein = viewModel::onProteinChanged,
                onFat = viewModel::onFatChanged,
                onUseFoodTotals = viewModel::onUseFoodTotals
            )

            if (uiState.components.isNotEmpty() && !uiState.isAnalyzing) {
                FoodsCard(
                    state = uiState,
                    onRedo = viewModel::analyzeMeal,
                    onWeightChange = viewModel::onComponentWeightChanged,
                    onMacrosChange = viewModel::onComponentMacrosChanged,
                    onRemove = viewModel::onComponentRemoved
                )
            }

            EditorCard(
                "Absorption",
                trailing = if (uiState.impactAuto) { { SuggestedTag("Suggested") } } else null
            ) {
                AbsorptionSelector(uiState.impactType, viewModel::onImpactTypeChanged)
                Text(
                    uiState.impactType.hint + if (uiState.impactAuto) " Follows the macros until you pick one." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("What you entered won't be saved.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; viewModel.discard(); onClose() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (extendedCourse) "Delete this course?" else "Delete this meal?") },
            text = { Text("It will be removed from your journal and the Home graphs. You can undo right after.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; viewModel.deleteMeal() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}


/** A titled group of fields on a soft tonal surface. */
@Composable
private fun EditorCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(title, Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun SuggestedTag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** Always visible: what is about to be saved (carbs, absorption, time) next to the save button. */
@Composable
private fun SaveBar(state: AddMealState, onSave: () -> Unit) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${state.carbohydrates ?: 0}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Carbs)
                    Text(" g carbs", style = MaterialTheme.typography.labelLarge, color = Carbs, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text(
                    listOfNotNull(
                        state.mealTime?.let { fmt.format(Date(it)) },
                        "${state.impactType.label} absorption"
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onSave,
                enabled = state.canSave && !state.isLoading,
                modifier = Modifier.height(52.dp).widthIn(min = 148.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            state.isEditing -> "Save"
                            state.isAddingCourse -> "Add course"
                            else -> "Save meal"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

/**
 * The meal this course belongs to: its name (editable), the courses and insulin so far, so the user
 * can dose for just the new course.
 */
@Composable
private fun CourseContextCard(sitting: SittingContext, isAddingCourse: Boolean, mealName: String, onMealNameChanged: (String) -> Unit) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Carbs.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Carbs.copy(alpha = 0.3f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = mealName,
                onValueChange = onMealNameChanged,
                label = { Text(if (isAddingCourse) "Adding a course to" else "Part of the meal") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                trailingIcon = { Icon(Icons.Outlined.Edit, "Rename meal", Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sitting.otherCourses.forEach { course -> CourseThumb(course) }
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${sitting.otherCarbs} g · ${formatUnits(sitting.insulinUnits)} U",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "so far, since ${fmt.format(Date(sitting.startTime))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isAddingCourse) {
                Text(
                    "Log only what just arrived. Its carbs are what to dose for now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CourseThumb(course: CourseSummary) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Carbs.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        if (course.photo != null) {
            AsyncImage(model = course.photo, contentDescription = course.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Icons.Outlined.Restaurant, contentDescription = course.title, tint = Carbs, modifier = Modifier.size(20.dp))
        }
        Text(
            "${course.carbs}",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(topEnd = 6.dp)).padding(horizontal = 4.dp)
        )
    }
}

private fun formatUnits(units: Double): String {
    val r = Math.round(units * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

/**
 * The meal's totals: the main thing to check, so it is the most prominent card. Carbs, protein and fat
 * each in their own card (carbs largest); energy is derived. They start as the sum of the AI foods and
 * can be typed over, which then wins (said so, with a way back).
 */
@Composable
private fun TotalsCard(
    state: AddMealState,
    onCarbs: (String) -> Unit,
    onProtein: (String) -> Unit,
    onFat: (String) -> Unit,
    onUseFoodTotals: () -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Carbs.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Carbs.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(if (state.sitting != null) "This course" else "Total", Modifier.weight(1f))
                Text(state.totalCalories?.let { "$it kcal" } ?: "– kcal", style = MaterialTheme.typography.labelMedium, color = muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroTile("Carbs", state.carbohydrates, onCarbs, "g", Modifier.weight(1.3f), Carbs, MaterialTheme.typography.headlineMedium)
                MacroTile("Protein", state.proteins, onProtein, "g", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                MacroTile("Fat", state.fats, onFat, "g", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            }
            val foods = state.foodTotals
            when {
                foods != null && state.totalsOverridden -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, tint = muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Your values replace the foods' sum (${foods.carbs} g carbs · ${foods.protein} P · ${foods.fat} F).",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onUseFoodTotals, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Use sum") }
                }
                foods != null -> Text("Sum of the foods below. Type here to override it.", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            state.sitting?.let {
                Text("Meal total ${it.otherCarbs + (state.carbohydrates ?: 0)} g carbs", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

/** The entry's name as the screen title: a small context line over an inline text field. */
@Composable
private fun TitleField(overline: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    Column {
        Text(overline, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    inner()
                }
            }
        )
    }
}

/** Asking the AI: an optional note (only until it is sent), the button, then progress. */
@Composable
private fun AiRequestCard(state: AddMealState, onNotesChanged: (String) -> Unit, onAnalyze: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val n = state.photos.size
    var showNotes by rememberSaveable { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = primary.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI estimate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.isAnalyzing) { if (n > 1) "Looking at $n photos…" else "Looking at your photo…" }
                        else "Finds each food in your ${if (n > 1) "$n photos" else "photo"} and estimates its nutrients",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.isAnalyzing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
            } else {
                if (showNotes || state.aiNotes.isNotBlank()) {
                    OutlinedTextField(
                        value = state.aiNotes,
                        onValueChange = onNotesChanged,
                        label = { Text("Note for the AI") },
                        placeholder = { Text("e.g. I only ate half") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                } else {
                    AssistChip(
                        onClick = { showNotes = true },
                        label = { Text("Add a note for the AI") },
                        leadingIcon = { Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp)) }
                    )
                }
                Button(onClick = { showNotes = false; onAnalyze() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Estimate nutrition")
                }
            }
        }
    }
}

/** The foods the AI found, each editable, and how it arrived at them (always shown). */
@Composable
private fun FoodsCard(
    state: AddMealState,
    onRedo: () -> Unit,
    onWeightChange: (Int, Double) -> Unit,
    onMacrosChange: (Int, Double?, Double?, Double?) -> Unit,
    onRemove: (Int) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    EditorCard(
        "Foods · AI estimate",
        trailing = {
            TextButton(onClick = onRedo, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Redo")
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.components.forEachIndexed { index, component ->
                key(component.name, index) {
                    FoodItemRow(
                        component = component,
                        onWeightChange = { onWeightChange(index, it) },
                        onMacrosChange = { c, p, f -> onMacrosChange(index, c, p, f) },
                        onRemove = { onRemove(index) }
                    )
                }
            }
        }
        state.reasoning?.takeIf { it.isNotBlank() }?.let { reasoning ->
            Surface(shape = RoundedCornerShape(16.dp), color = primary.copy(alpha = 0.06f)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("How the AI estimated this", style = MaterialTheme.typography.labelLarge, color = primary)
                    }
                    Text(reasoning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * No photo: two big buttons. With photos: one split view (1 = full, 2 = side by side, 3 = one big +
 * two stacked, 4+ = grid with "+N"), each photo removable, add buttons on top. The first is the cover.
 */
@Composable
private fun PhotoSection(
    photos: List<MealPhoto>,
    canAddPhoto: Boolean,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemove: (imageId: String) -> Unit
) {
    if (photos.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoSourceTile(Icons.Outlined.CameraAlt, "Take photo", onTakePhoto, Modifier.weight(1f))
                PhotoSourceTile(Icons.Outlined.PhotoLibrary, "Gallery", onPickPhoto, Modifier.weight(1f))
            }
            Text(
                "Optional. With photos, AI can estimate the nutrients for you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        return
    }
    val gap = 3.dp
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(24.dp))) {
        @Composable
        fun Tile(i: Int, modifier: Modifier, more: Int = 0) = PhotoTile(photos[i], modifier, more) { onRemove(photos[i].imageId) }
        when (photos.size) {
            1 -> Tile(0, Modifier.fillMaxSize())
            2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(0, Modifier.weight(1f).fillMaxHeight())
                Tile(1, Modifier.weight(1f).fillMaxHeight())
            }
            3 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(0, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Tile(1, Modifier.weight(1f).fillMaxWidth())
                    Tile(2, Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Tile(0, Modifier.weight(1f).fillMaxHeight())
                    Tile(1, Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Tile(2, Modifier.weight(1f).fillMaxHeight())
                    Tile(3, Modifier.weight(1f).fillMaxHeight(), more = photos.size - 4)
                }
            }
        }
        if (canAddPhoto) {
            Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScrimButton(Icons.Outlined.CameraAlt, "Add another photo", onTakePhoto)
                ScrimButton(Icons.Outlined.PhotoLibrary, "Add photos from gallery", onPickPhoto)
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: MealPhoto, modifier: Modifier, more: Int, onRemove: () -> Unit) {
    Box(modifier) {
        AsyncImage(model = photo.uri, contentDescription = "Meal photo", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        if (more > 0) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Text("+$more", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            ScrimButton(Icons.Filled.Close, "Remove photo", onRemove, size = 30.dp)
        }
    }
}

@Composable
private fun PhotoSourceTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(88.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ScrimButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp = 38.dp) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.Black.copy(alpha = 0.5f), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = Color.White, modifier = Modifier.size(size * 0.52f))
        }
    }
}
