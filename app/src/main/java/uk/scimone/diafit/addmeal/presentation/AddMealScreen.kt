package uk.scimone.diafit.addmeal.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import org.koin.core.parameter.parametersOf
import org.koin.androidx.compose.koinViewModel
import uk.scimone.diafit.addmeal.presentation.components.MealDateTimePicker
import java.time.LocalDateTime
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import uk.scimone.diafit.addmeal.presentation.components.ImpactTypeSelector
import uk.scimone.diafit.addmeal.presentation.components.MealTypeSelector
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealType
import java.time.Instant
import java.time.ZoneId

@Composable
fun AddMealScreen(
    userId: Int,
    viewModel: AddMealViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    var description by remember { mutableStateOf(uiState.description ?: "") }
    var carbs by remember { mutableStateOf(uiState.carbohydrates?.toString() ?: "") }
    var protein by remember { mutableStateOf(uiState.proteins?.toString() ?: "") }
    var fat by remember { mutableStateOf(uiState.fats?.toString() ?: "") }

    // New states for calories, impactType and mealType
    var calories by remember { mutableStateOf(uiState.calories?.toString() ?: "") }

    LaunchedEffect(Unit) {
        viewModel.startNewMeal()
    }

    val takePictureLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) {
            currentPhotoUri?.let { uri -> viewModel.onImageSelected(uri) }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let { viewModel.copyGalleryImageToPrivateStorage(it) }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.resetSnackbar()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Add Meal", style = MaterialTheme.typography.headlineMedium)

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = {
                    val uri = viewModel.createCameraImageUri()
                    currentPhotoUri = uri
                    takePictureLauncher.launch(uri)
                }) {
                    Text("Take Photo")
                }

                Button(onClick = { galleryLauncher.launch("image/*") }) {
                    Text("Pick from Gallery")
                }
            }

            Spacer(Modifier.height(24.dp))

            uiState.imageUri?.let { imageUri ->
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(300.dp)
                        .aspectRatio(1f)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(30.dp))
                )

                Spacer(Modifier.height(16.dp))

                // Description input
                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        viewModel.onDescriptionChanged(it)
                    },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                MealDateTimePicker(
                    value = uiState.mealTime?.let {
                        try {
                            val instant = Instant.parse(it)
                            LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                        } catch (e: Exception) {
                            null
                        }
                    },
                    onValueChange = { viewModel.onMealTimeChanged(it) }
                )

                Spacer(Modifier.height(12.dp))

                // Macros inputs row
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = {
                            carbs = it.filter { ch -> ch.isDigit() }
                            viewModel.onCarbsChanged(carbs)
                        },
                        label = { Text("Carbs (g)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = protein,
                        onValueChange = {
                            protein = it.filter { ch -> ch.isDigit() }
                            viewModel.onProteinChanged(protein)
                        },
                        label = { Text("Protein (g)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = {
                            fat = it.filter { ch -> ch.isDigit() }
                            viewModel.onFatChanged(fat)
                        },
                        label = { Text("Fat (g)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Calories input
                OutlinedTextField(
                    value = calories,
                    onValueChange = {
                        calories = it.filter { ch -> ch.isDigit() }
                        viewModel.onCaloriesChanged(calories)
                    },
                    label = { Text("Calories (kcal)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Impact Type:", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    ImpactTypeSelector(
                        selectedImpactType = uiState.impactType,
                        onImpactTypeSelected = { viewModel.onImpactTypeChanged(it) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Meal Type:", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    MealTypeSelector(
                        selectedMealType = uiState.mealType,
                        onMealTypeSelected = { viewModel.onMealTypeChanged(it) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = viewModel::saveMeal,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isLoading) "Saving..." else "Save Meal")
                }
            }
        }
    }
}
