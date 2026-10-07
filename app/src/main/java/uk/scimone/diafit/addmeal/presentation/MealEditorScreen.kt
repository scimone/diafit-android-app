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
                title = {
                    Text(
                        when {
                            uiState.isAddingCourse -> "Add course"
                            uiState.isEditing && extendedCourse -> "Edit course"
                            uiState.isEditing -> "Edit meal"
                            else -> "New meal"
                        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.sitting?.let {
                CourseContextCard(it, uiState.isAddingCourse, uiState.mealName, viewModel::onMealNameChanged)
            }

            PhotoSection(
                state = uiState,
                onTakePhoto = takePhoto,
                onPickPhoto = pickPhoto,
                onRemove = viewModel::onRemovePhoto,
                onMakeCover = viewModel::onMakeCover
            )

            if (uiState.photos.isNotEmpty()) {
                AiEstimateCard(
                    state = uiState,
                    onNotesChanged = viewModel::onAiNotesChanged,
                    onAnalyze = viewModel::analyzeMeal,
                    onComponentWeightChanged = viewModel::onComponentWeightChanged,
                    onComponentRemoved = viewModel::onComponentRemoved
                )
            }

            EditorCard(if (uiState.isAddingCourse) "What arrived?" else "What did you eat?") {
                OutlinedTextField(
                    value = uiState.description.orEmpty(),
                    onValueChange = viewModel::onDescriptionChanged,
                    placeholder = { Text("e.g. Chicken wrap and an apple") },
                    supportingText = if (!uiState.dishName.isNullOrBlank() && uiState.description == uiState.dishName) {
                        { Text("Named by the AI. Tap to change.") }
                    } else null,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            NutritionCard(
                state = uiState,
                onCarbs = viewModel::onCarbsChanged,
                onProtein = viewModel::onProteinChanged,
                onFat = viewModel::onFatChanged,
                onCalories = viewModel::onCaloriesChanged,
                onUseFoodTotals = viewModel::onUseComponentTotals
            )

            EditorCard("When") {
                MealDateTimePicker(
                    value = uiState.mealTime?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() },
                    onValueChange = viewModel::onMealTimeChanged
                )
                // Courses inherit the meal's type; only the meal as a whole has one.
                if (!extendedCourse) MealTypeSelector(uiState.mealType, viewModel::onMealTypeChanged)
            }

            EditorCard(
                "Absorption",
                trailing = if (uiState.impactAuto) { { SuggestedTag("Suggested") } } else null
            ) {
                AbsorptionSelector(uiState.impactType, viewModel::onImpactTypeChanged)
                Text(uiState.impactType.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Carbs first and large (they drive the dose), then the other macros as small tiles. */
@Composable
private fun NutritionCard(
    state: AddMealState,
    onCarbs: (String) -> Unit,
    onProtein: (String) -> Unit,
    onFat: (String) -> Unit,
    onCalories: (String) -> Unit,
    onUseFoodTotals: () -> Unit
) {
    EditorCard("Nutrition") {
        Surface(shape = RoundedCornerShape(18.dp), color = Carbs.copy(alpha = 0.12f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.sitting != null) "Carbs in this course" else "Carbs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val sub = state.sitting?.let { "Meal total ${it.otherCarbs + (state.carbohydrates ?: 0)} g" }
                        ?: if (state.components.isNotEmpty() && state.componentCarbsHint == null) "Sum of the foods above" else "What you dose for"
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                InlineNumberField(
                    value = state.carbohydrates,
                    onValueChange = onCarbs,
                    unit = "g",
                    style = MaterialTheme.typography.displaySmall,
                    color = Carbs,
                    align = TextAlign.End,
                    modifier = Modifier.width(150.dp)
                )
            }
        }
        state.componentCarbsHint?.let { sum ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "The foods add up to $sum g. Your value is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onUseFoodTotals) { Text("Use $sum g") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroTile("Protein", state.proteins, onProtein, "g", Modifier.weight(1f))
            MacroTile("Fat", state.fats, onFat, "g", Modifier.weight(1f))
            MacroTile("Energy", state.calories, onCalories, "kcal", Modifier.weight(1.2f))
        }
    }
}

/**
 * Everything AI in one place: an optional note, the estimate button, then the foods it found as an
 * editable list (portion changes re-sum the totals) and, folded away, why it chose these numbers.
 */
@Composable
private fun AiEstimateCard(
    state: AddMealState,
    onNotesChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onComponentWeightChanged: (Int, Double) -> Unit,
    onComponentRemoved: (Int) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val hasResult = state.components.isNotEmpty()
    val n = state.photos.size
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var showReasoning by rememberSaveable { mutableStateOf(false) }

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
                        when {
                            state.isAnalyzing -> if (n > 1) "Looking at $n photos…" else "Looking at your photo…"
                            hasResult -> "${state.components.size} ${if (state.components.size == 1) "food" else "foods"} · tap one to adjust"
                            else -> "Finds each food in your ${if (n > 1) "$n photos" else "photo"} and estimates its nutrients"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
                if (hasResult && !state.isAnalyzing) {
                    TextButton(onClick = onAnalyze) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Redo")
                    }
                }
            }

            if (state.isAnalyzing) LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))

            if (hasResult && state.analysisOutdated && !state.isAnalyzing) {
                Text(
                    "The photos changed since this estimate. Tap Redo to update it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (hasResult) {
                Column {
                    state.components.forEachIndexed { index, component ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        key(component.name, index) {
                            FoodItemRow(
                                component = component,
                                onWeightChange = { onComponentWeightChanged(index, it) },
                                onRemove = { onComponentRemoved(index) }
                            )
                        }
                    }
                }
            }

            // The note is sent with the next estimate, so it stays available after a result (for Redo).
            if (!state.isAnalyzing) {
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
            }

            if (!hasResult && !state.isAnalyzing) {
                Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Estimate nutrition")
                }
            }

            state.reasoning?.takeIf { hasResult && it.isNotBlank() }?.let { reasoning ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showReasoning = !showReasoning }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("How the AI estimated this", style = MaterialTheme.typography.labelLarge, color = primary, modifier = Modifier.weight(1f))
                    Icon(if (showReasoning) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = primary)
                }
                if (showReasoning) {
                    Text(reasoning, style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
        }
    }
}

@Composable
private fun PhotoSection(
    state: AddMealState,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemove: (imageId: String) -> Unit,
    onMakeCover: (imageId: String) -> Unit
) {
    val photos = state.photos
    when {
        photos.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        photos.size == 1 -> Box {
            AsyncImage(
                model = photos[0].uri,
                contentDescription = "Meal photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(24.dp))
            )
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScrimButton(Icons.Outlined.CameraAlt, "Add another photo", onTakePhoto)
                ScrimButton(Icons.Outlined.PhotoLibrary, "Add photos from gallery", onPickPhoto)
                ScrimButton(Icons.Filled.Close, "Remove photo", onClick = { onRemove(photos[0].imageId) })
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(photos, key = { _, p -> p.imageId }) { index, photo ->
                    PhotoThumb(
                        photo = photo,
                        isCover = index == 0,
                        onRemove = { onRemove(photo.imageId) },
                        onMakeCover = { onMakeCover(photo.imageId) }
                    )
                }
                if (state.canAddPhoto) {
                    item(key = "add") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallAddTile(Icons.Outlined.CameraAlt, "Take another photo", onTakePhoto)
                            SmallAddTile(Icons.Outlined.PhotoLibrary, "Add photos from gallery", onPickPhoto)
                        }
                    }
                }
            }
            Text(
                "${photos.size} photos · tap one to make it the cover",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

private val ThumbSize = 140.dp

@Composable
private fun PhotoThumb(photo: MealPhoto, isCover: Boolean, onRemove: () -> Unit, onMakeCover: () -> Unit) {
    Box(
        Modifier
            .size(ThumbSize)
            .clip(RoundedCornerShape(20.dp))
            .then(if (isCover) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(enabled = !isCover, onClickLabel = "Make cover photo", onClick = onMakeCover)
    ) {
        AsyncImage(model = photo.uri, contentDescription = "Meal photo", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            ScrimButton(Icons.Filled.Close, "Remove photo", onRemove, size = 30.dp)
        }
        if (isCover) {
            Text(
                "Cover",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun SmallAddTile(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 56.dp, height = (ThumbSize - 8.dp) / 2),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
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
