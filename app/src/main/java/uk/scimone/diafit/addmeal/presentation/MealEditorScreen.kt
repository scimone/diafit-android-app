package uk.scimone.diafit.addmeal.presentation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.addmeal.presentation.components.*
import uk.scimone.diafit.journal.presentation.components.SectionLabel
import java.time.Instant
import java.time.ZoneId

/**
 * Create ([mealId] == null) or edit a meal. Full-screen: the host decides what happens on
 * [onClose] (nothing saved), [onSaved] and [onDeleted].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditorScreen(
    userId: Int,
    mealId: Int?,
    onClose: () -> Unit,
    onSaved: (wasNew: Boolean) -> Unit,
    onDeleted: (mealId: Int) -> Unit,
    viewModel: AddMealViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var currentPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(mealId) {
        if (mealId == null) viewModel.startNewMeal() else viewModel.startEditing(mealId)
    }

    LaunchedEffect(uiState.finished) {
        when (val result = uiState.finished) {
            is EditorResult.Saved -> onSaved(result.wasNew)
            is EditorResult.Deleted -> onDeleted(result.mealId)
            null -> return@LaunchedEffect
        }
        viewModel.consumeFinished()
    }

    val takePictureLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) currentPhotoUri?.let { viewModel.onImageSelected(Uri.parse(it)) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let(viewModel::copyGalleryImageToPrivateStorage)
    }
    val takePhoto = {
        val uri = viewModel.createCameraImageUri()
        currentPhotoUri = uri.toString()
        takePictureLauncher.launch(uri)
    }
    val pickPhoto = { galleryLauncher.launch("image/*") }

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
                title = { Text(if (uiState.isEditing) "Edit meal" else "New meal") },
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
                        Text(if (uiState.isEditing) "Save changes" else "Save meal", style = MaterialTheme.typography.titleSmall)
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
            PhotoSection(
                imageUri = uiState.imageUri,
                isAnalyzing = uiState.isAnalyzing,
                reasoning = uiState.reasoning,
                onTakePhoto = takePhoto,
                onPickPhoto = pickPhoto,
                onRemove = viewModel::onRemovePhoto,
                onAnalyze = viewModel::analyzeMeal
            )

            Section("What did you eat?") {
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
                Spacer(Modifier.height(4.dp))
                MealTypeSelector(uiState.mealType, viewModel::onMealTypeChanged)
            }

            Section("Nutrition") {
                NumberInputField(
                    value = uiState.carbohydrates?.toString().orEmpty(),
                    onValueChange = viewModel::onCarbsChanged,
                    label = "Carbs",
                    suffix = "g",
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )
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
                    Text("Delete meal")
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
            title = { Text("Delete this meal?") },
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

@Composable
private fun PhotoSection(
    imageUri: Uri?,
    isAnalyzing: Boolean,
    reasoning: String?,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemove: () -> Unit,
    onAnalyze: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (imageUri == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoSourceTile(Icons.Outlined.CameraAlt, "Take photo", onTakePhoto, Modifier.weight(1f))
                PhotoSourceTile(Icons.Outlined.PhotoLibrary, "From gallery", onPickPhoto, Modifier.weight(1f))
            }
            Text(
                "A photo is optional. You can also log a meal from its carbs alone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Box {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Meal photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(28.dp))
                )
                Row(
                    Modifier.align(Alignment.TopEnd).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScrimButton(Icons.Outlined.CameraAlt, "Retake photo", onTakePhoto)
                    ScrimButton(Icons.Outlined.PhotoLibrary, "Pick another photo", onPickPhoto)
                    ScrimButton(Icons.Filled.Close, "Remove photo", onRemove)
                }
            }
            FilledTonalButton(onClick = onAnalyze, enabled = !isAnalyzing, modifier = Modifier.fillMaxWidth()) {
                if (isAnalyzing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Analyzing…")
                } else {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Estimate nutrition with AI")
                }
            }
            reasoning?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
private fun ScrimButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.Black.copy(alpha = 0.5f), modifier = Modifier.size(38.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
