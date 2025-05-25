package uk.scimone.diafit.addmeal.presentation.screens

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.repository.MealRepository
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.*

@Composable
fun AddMealScreen(mealRepository: MealRepository, userId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Generate mealId early to keep consistent across image and meal
    val mealId = remember { UUID.randomUUID().toString() }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        // No direct action needed here
    }

    // Temp Uri for photo capture
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) {
            selectedImageUri = tempPhotoUri
        }
    }

    // Launcher to pick image from gallery
    val pickImageLauncher = rememberLauncherForActivityResult(GetContent()) { uri: Uri? ->
        uri?.let { galleryUri ->
            // Copy selected gallery image to app private folder under mealId filename
            scope.launch {
                val copiedUri = copyGalleryImageToPrivateStorage(context, galleryUri, mealId)
                if (copiedUri != null) {
                    selectedImageUri = copiedUri
                } else {
                    snackbarHostState.showSnackbar("Failed to copy image")
                }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Add Meal",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = {
                    val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    if (permission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        // Create file Uri with FileProvider for camera
                        val photoUri = createImageUri(context, mealId)
                        tempPhotoUri = photoUri
                        photoUri?.let { takePictureLauncher.launch(it) }
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
 * Create a private Uri for camera capture using FileProvider and app-specific storage.
 */
fun createImageUri(context: Context, mealId: String): Uri? {
    val picturesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DiaFit")
    if (!picturesDir.exists()) picturesDir.mkdirs()

    val imageFile = File(picturesDir, "meal_$mealId.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider", // Make sure to setup FileProvider in manifest
        imageFile
    )
}

/**
 * Copy an image selected from gallery to the app-specific private storage.
 */
suspend fun copyGalleryImageToPrivateStorage(context: Context, sourceUri: Uri, mealId: String): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            val picturesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DiaFit")
            if (!picturesDir.exists()) picturesDir.mkdirs()

            val destFile = File(picturesDir, "meal_$mealId.jpg")

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
