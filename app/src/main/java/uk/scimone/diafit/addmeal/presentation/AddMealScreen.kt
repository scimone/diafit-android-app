package uk.scimone.diafit.addmeal.presentation.screens

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
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.addmeal.presentation.AddMealViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun AddMealScreen(
    userId: String,
    viewModel: AddMealViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Start a new meal process when screen enters composition
    LaunchedEffect(Unit) {
        viewModel.startNewMeal()
    }

    val takePictureLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) {
            currentPhotoUri?.let { uri ->
                viewModel.onImageSelected(uri)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let {
            viewModel.copyGalleryImageToPrivateStorage(context, it)
        }
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

                Button(onClick = {
                    galleryLauncher.launch("image/*")
                }) {
                    Text("Pick from Gallery")
                }
            }

            Spacer(Modifier.height(24.dp))

            uiState.imageUri?.let { imageUri ->
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = null,
                    modifier = Modifier.size(240.dp).padding(8.dp)
                )

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
