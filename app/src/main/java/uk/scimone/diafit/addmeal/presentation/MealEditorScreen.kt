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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.CameraAlt
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.addmeal.presentation.components.*
import uk.scimone.diafit.core.domain.model.MealPhoto
import uk.scimone.diafit.journal.presentation.components.SectionLabel
import uk.scimone.diafit.ui.theme.Bolus
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
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = viewModel::saveMeal,
                    enabled = uiState.canSave && !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(
                            when {
                                uiState.isEditing -> "Save changes"
                                uiState.isAddingCourse -> "Add to meal"
                                else -> "Save meal"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            uiState.sitting?.let { SittingBanner(it, isAddingCourse = uiState.isAddingCourse) }

            PhotoSection(
                state = uiState,
                onTakePhoto = takePhoto,
                onPickPhoto = pickPhoto,
                onRemove = viewModel::onRemovePhoto,
                onMakeCover = viewModel::onMakeCover,
                onAiNotesChanged = viewModel::onAiNotesChanged,
                onAnalyze = viewModel::analyzeMeal
            )

            Section(if (uiState.isAddingCourse) "What arrived?" else "What did you eat?") {
                OutlinedTextField(
                    value = uiState.description.orEmpty(),
                    onValueChange = viewModel::onDescriptionChanged,
                    placeholder = { Text("e.g. Chicken wrap and an apple") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            Section("When") {
                MealDateTimePicker(
                    value = uiState.mealTime?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() },
                    onValueChange = viewModel::onMealTimeChanged
                )
                // Courses inherit the meal's type; only the meal as a whole has one.
                if (!extendedCourse) {
                    Spacer(Modifier.height(4.dp))
                    MealTypeSelector(uiState.mealType, viewModel::onMealTypeChanged)
                }
            }

            Section("Nutrition") {
                NumberInputField(
                    value = uiState.carbohydrates?.toString().orEmpty(),
                    onValueChange = viewModel::onCarbsChanged,
                    label = if (extendedCourse) "Carbs in this course" else "Carbs",
                    suffix = "g",
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )
                uiState.sitting?.let { sitting ->
                    val thisCourse = uiState.carbohydrates ?: 0
                    Text(
                        "Meal total ${sitting.otherCarbs + thisCourse} g " +
                            "(${sitting.otherCarbs} g earlier + $thisCourse g now)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberInputField(
                        value = uiState.proteins?.toString().orEmpty(),
                        onValueChange = viewModel::onProteinChanged,
                        label = "Protein", suffix = "g", modifier = Modifier.weight(1f)
                    )
                    NumberInputField(
                        value = uiState.fats?.toString().orEmpty(),
                        onValueChange = viewModel::onFatChanged,
                        label = "Fat", suffix = "g", modifier = Modifier.weight(1f)
                    )
                    NumberInputField(
                        value = uiState.calories?.toString().orEmpty(),
                        onValueChange = viewModel::onCaloriesChanged,
                        label = "Energy", suffix = "kcal", modifier = Modifier.weight(1.2f)
                    )
                }
            }

            Section("Absorption speed") {
                AbsorptionSelector(
                    selected = uiState.impactType,
                    isSuggested = uiState.impactAuto,
                    onSelected = viewModel::onImpactTypeChanged
                )
            }

            if (uiState.isEditing) {
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Filled.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (extendedCourse) "Delete course" else "Delete meal")
                }
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

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(title)
        content()
    }
}

/**
 * The meal this course belongs to: what has been eaten and injected so far, so the user can dose for
 * just the new course.
 */
@Composable
private fun SittingBanner(sitting: SittingContext, isAddingCourse: Boolean) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Carbs.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Carbs.copy(alpha = 0.35f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isAddingCourse) "Adding to ${sitting.title}" else sitting.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val n = sitting.otherCourses.size
                    Text(
                        "Started ${fmt.format(Date(sitting.startTime))} · $n ${if (isAddingCourse) "" else "other "}${if (n == 1) "course" else "courses"}" +
                            if (isAddingCourse) " so far" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BannerStat("${sitting.otherCarbs} g", "carbs so far", Carbs, Modifier.weight(1f))
                BannerStat(formatUnits(sitting.insulinUnits) + " U", "insulin so far", Bolus, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                sitting.otherCourses.forEach { course ->
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Carbs.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
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
private fun BannerStat(value: String, label: String, accent: Color, modifier: Modifier) {
    Column(modifier.background(accent.copy(alpha = 0.16f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatUnits(units: Double): String {
    val r = Math.round(units * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

@Composable
private fun PhotoSection(
    state: AddMealState,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemove: (imageId: String) -> Unit,
    onMakeCover: (imageId: String) -> Unit,
    onAiNotesChanged: (String) -> Unit,
    onAnalyze: () -> Unit
) {
    val photos = state.photos
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (photos.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoSourceTile(Icons.Outlined.CameraAlt, "Take photo", onTakePhoto, Modifier.weight(1f))
                PhotoSourceTile(Icons.Outlined.PhotoLibrary, "From gallery", onPickPhoto, Modifier.weight(1f))
            }
            Text(
                "Add one or more photos, e.g. main dish and dessert, or several small plates. " +
                    "The AI estimates them together. Photos are optional.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (photos.size == 1) {
                // One photo: show it large, like before.
                Box {
                    AsyncImage(
                        model = photos[0].uri,
                        contentDescription = "Meal photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(28.dp))
                    )
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScrimButton(Icons.Outlined.CameraAlt, "Add another photo", onTakePhoto)
                        ScrimButton(Icons.Outlined.PhotoLibrary, "Add photos from gallery", onPickPhoto)
                        ScrimButton(Icons.Filled.Close, "Remove photo", onClick = { onRemove(photos[0].imageId) })
                    }
                }
            } else {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = state.aiNotes,
                onValueChange = onAiNotesChanged,
                label = { Text("Notes for the AI (optional)") },
                placeholder = { Text("e.g. I only drank half of the bottle") },
                enabled = !state.isAnalyzing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            FilledTonalButton(onClick = onAnalyze, enabled = !state.isAnalyzing, modifier = Modifier.fillMaxWidth()) {
                if (state.isAnalyzing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (photos.size > 1) "Analyzing ${photos.size} photos…" else "Analyzing…")
                } else {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            state.analysisOutdated -> "Photos changed · estimate again"
                            photos.size > 1 -> "Estimate nutrition of all ${photos.size} photos"
                            else -> "Estimate nutrition with AI"
                        }
                    )
                }
            }
            state.reasoning?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val ThumbSize = 116.dp

@Composable
private fun PhotoThumb(photo: MealPhoto, isCover: Boolean, onRemove: () -> Unit, onMakeCover: () -> Unit) {
    Box(
        Modifier
            .size(ThumbSize)
            .clip(RoundedCornerShape(18.dp))
            .then(if (isCover) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier)
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
        modifier = Modifier.size(width = 54.dp, height = (ThumbSize - 8.dp) / 2),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
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
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
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
