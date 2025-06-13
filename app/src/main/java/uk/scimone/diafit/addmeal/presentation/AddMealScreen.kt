package uk.scimone.diafit.addmeal.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.addmeal.presentation.components.ImpactTypeSelector
import uk.scimone.diafit.addmeal.presentation.components.MealDateTimePicker
import uk.scimone.diafit.addmeal.presentation.components.MealTypeSelector
import uk.scimone.diafit.addmeal.presentation.components.NumberInputField
import java.time.Instant
import java.time.ZoneId

fun String.onlyDigits() = filter { it.isDigit() }

@Composable
fun AddMealScreen(
    userId: Int,
    viewModel: AddMealViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        viewModel.startNewMeal()
    }

    val takePictureLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) currentPhotoUri?.let(viewModel::onImageSelected)
    }

    val galleryLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let(viewModel::copyGalleryImageToPrivateStorage)
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
                Button(
                    onClick = {
                        val uri = viewModel.createCameraImageUri()
                        currentPhotoUri = uri
                        takePictureLauncher.launch(uri)
                    }
                ) {
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

                OutlinedTextField(
                    value = uiState.description.orEmpty(),
                    onValueChange = viewModel::onDescriptionChanged,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                MealDateTimePicker(
                    value = uiState.mealTime?.let { millis ->
                        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    },
                    onValueChange = viewModel::onMealTimeChanged
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberInputField(
                        value = uiState.carbohydrates?.toString().orEmpty(),
                        onValueChange = viewModel::onCarbsChanged,
                        label = "Carbs (g)",
                        modifier = Modifier.weight(1f)
                    )
                    NumberInputField(
                        value = uiState.proteins?.toString().orEmpty(),
                        onValueChange = viewModel::onProteinChanged,
                        label = "Protein (g)",
                        modifier = Modifier.weight(1f)
                    )
                    NumberInputField(
                        value = uiState.fats?.toString().orEmpty(),
                        onValueChange = viewModel::onFatChanged,
                        label = "Fat (g)",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                NumberInputField(
                    value = uiState.calories?.toString().orEmpty(),
                    onValueChange = viewModel::onCaloriesChanged,
                    label = "Calories (kcal)",
                    modifier = Modifier.fillMaxWidth()
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
                        onImpactTypeSelected = viewModel::onImpactTypeChanged
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
                        onMealTypeSelected = viewModel::onMealTypeChanged
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = viewModel::saveMeal,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save Meal")
                    }
                }
            }
        }
    }
}
