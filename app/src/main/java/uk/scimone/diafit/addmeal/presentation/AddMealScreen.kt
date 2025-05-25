package uk.scimone.diafit.addmeal.presentation.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.repository.MealRepository
import java.time.Instant
import java.util.*
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore

@Composable
fun AddMealScreen(mealRepository: MealRepository, userId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Launcher to pick image from gallery
    val pickImageLauncher = rememberLauncherForActivityResult(GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
        }
    }

    // Launcher to request camera permission
    val cameraPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        // No direct action here, handled in button click
    }

    // Launcher to take photo (requires Uri)
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) {
            selectedImageUri = tempPhotoUri
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Header text instead of TopAppBar
            Text(
                text = "Add Meal",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            // Buttons for photo selection
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = {
                    val permission = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    )
                    if (permission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        tempPhotoUri = createImageUri(context)
                        tempPhotoUri?.let { takePictureLauncher.launch(it) }
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }) {
                    Text("Take Photo")
                }

                Button(onClick = {
                    pickImageLauncher.launch("image/*")
                }) {
                    Text("Pick from Gallery")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            selectedImageUri?.let { uri ->
                Text("Selected Image:")
                Spacer(modifier = Modifier.height(8.dp))

                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = "Selected Meal Image",
                    modifier = Modifier
                        .size(240.dp)
                        .padding(8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            try {
                                val mealId = UUID.randomUUID().toString()
                                val storedImageUriResult = mealRepository.storeImage(mealId, uri)
                                if (storedImageUriResult.isFailure) {
                                    snackbarHostState.showSnackbar("Failed to store image")
                                    isLoading = false
                                    return@launch
                                }

                                val meal = MealEntity(
                                    id = mealId,
                                    userId = userId,
                                    description = null,
                                    createdAtUtc = Instant.now().toEpochMilli(),
                                    mealTimeUtc = Instant.now().toEpochMilli(),
                                    calories = null,
                                    carbohydrates = 0,
                                    proteins = null,
                                    fats = null,
                                    isValid = true,
                                    imageId = mealId,
                                    recommendation = null,
                                    reasoning = null
                                )

                                val createResult = mealRepository.createMeal(meal)
                                if (createResult.isSuccess) {
                                    snackbarHostState.showSnackbar("Meal added successfully")
                                    selectedImageUri = null
                                } else {
                                    snackbarHostState.showSnackbar("Failed to add meal")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Error: ${e.message}")
                            }
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isLoading) "Saving..." else "Save Meal")
                }
            }
        }
    }
}

/**
 * Helper: Create temporary Uri for camera capture.
 */
fun createImageUri(context: Context): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "meal_photo_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        // Save to Pictures/DiaFit folder (visible to user gallery)
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DiaFit")
    }

    return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
}
