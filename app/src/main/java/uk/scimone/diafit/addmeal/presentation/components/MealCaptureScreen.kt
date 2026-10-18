package uk.scimone.diafit.addmeal.presentation.components

import uk.scimone.diafit.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import uk.scimone.diafit.addmeal.presentation.CapturePhase
import uk.scimone.diafit.addmeal.presentation.MAX_PHOTOS_PER_COURSE
import uk.scimone.diafit.core.domain.model.FoodProduct
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import uk.scimone.diafit.core.domain.model.SavedProductEntity
import uk.scimone.diafit.core.domain.model.RecipeEntity
import uk.scimone.diafit.core.domain.model.formatGrams
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private val Ink = Color(0xFF0B0D10)
private val OnInk = Color.White
private val OnInkMuted = Color(0xFFC9CED6)
private val Good = Color(0xFF4CD08A)
private val Scrim = Color(0x99000000)

/**
 * The first thing a new meal shows: a live camera that is *always* looking for a barcode, with the
 * other two ways in laid out as labelled buttons under it.
 *
 *  - point at a barcode: recognised by itself (no button), the product appears, then "photograph your meal"
 *  - shutter without a barcode: a meal photo the AI estimates
 *  - "Type it": straight to the form
 */
@Composable
fun MealCaptureScreen(
    phase: CapturePhase,
    onBarcode: (String) -> Unit,
    onPhotos: (List<Uri>) -> Unit,
    onSkipPhoto: () -> Unit,
    onManual: () -> Unit,
    onRescan: () -> Unit,
    onClose: () -> Unit,
    /** The meal photo taken so far and what to do with it ([CapturePhase.PhotoTaken]). */
    photo: Uri?,
    aiNotes: String,
    onAiNotes: (String) -> Unit,
    onEstimateWithAi: () -> Unit,
    aiEnabled: Boolean,
    savedProducts: List<SavedProductEntity>,
    recipes: List<RecipeEntity>,
    onUseSaved: (SavedProductEntity) -> Unit,
    onToggleFavorite: (SavedProductEntity) -> Unit,
    onForgetSaved: (String) -> Unit,
    onRetake: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQuery: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    recentSearches: List<String>,
    onUseRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onUseSearchResult: (FoodProduct) -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS_PER_COURSE)) { uris ->
        if (uris.isNotEmpty()) onPhotos(uris)
    }
    val openGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Box(Modifier.fillMaxSize().background(Ink)) {
        if (granted) {
            CameraLayer(phase, onBarcode, onPhotos, onSkipPhoto, onManual, onRescan, onClose, openGallery, onOpenSearch)
        } else {
            NoCameraPanel(
                askedBefore = asked,
                onAllow = { permission.launch(Manifest.permission.CAMERA) },
                onGallery = openGallery,
                onManual = onManual,
                onClose = onClose
            )
        }
        if (phase is CapturePhase.PhotoTaken) {
            PhotoChoiceScreen(
                photo = photo,
                aiNotes = aiNotes,
                onAiNotes = onAiNotes,
                onEstimateWithAi = onEstimateWithAi,
                aiEnabled = aiEnabled,
                savedProducts = savedProducts,
                onUseSaved = onUseSaved,
                onToggleFavorite = onToggleFavorite,
                onForgetSaved = onForgetSaved,
                onTypeIt = onManual,
                onSearch = onOpenSearch,
                onRetake = onRetake,
                onClose = onClose
            )
        }
        if (phase is CapturePhase.Search) FoodSearchScreen(phase, savedProducts, recipes, recentSearches, onSearchQuery, onSubmitSearch, onUseRecentSearch, onClearRecentSearches, onToggleFavorite, onUseSearchResult, onCloseSearch)
    }
}

/**
 * After a meal photo: what should fill in the nutrients? The photo stays visible behind, the three
 * ways are stacked as cards (AI with its note, a saved product, or by hand).
 */
@Composable
private fun PhotoChoiceScreen(
    photo: Uri?,
    aiNotes: String,
    onAiNotes: (String) -> Unit,
    onEstimateWithAi: () -> Unit,
    aiEnabled: Boolean,
    savedProducts: List<SavedProductEntity>,
    onUseSaved: (SavedProductEntity) -> Unit,
    onToggleFavorite: (SavedProductEntity) -> Unit,
    onForgetSaved: (String) -> Unit,
    onTypeIt: () -> Unit,
    onSearch: () -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val q = query.trim()
    // Favorites by default; a search looks through everything saved.
    val shown = remember(savedProducts, q, showAll) {
        when {
            q.isNotEmpty() -> savedProducts.filter { it.name.contains(q, true) || it.brand?.contains(q, true) == true }
            showAll -> savedProducts
            else -> savedProducts.filter { it.isFavorite }
        }.take(8)
    }
    Box(Modifier.fillMaxSize().background(Ink)) {
        if (photo != null) {
            AsyncImage(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color(0xB3000000)))))
        }
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            RoundIconButton(Icons.Filled.Close, stringResource(R.string.common_close), onClose, Modifier.align(Alignment.CenterStart))
            Surface(shape = CircleShape, color = Scrim, modifier = Modifier.align(Alignment.CenterEnd).clip(CircleShape).clickable(onClick = onRetake)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.CameraAlt, null, tint = OnInk, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.cap_retake), style = MaterialTheme.typography.labelLarge, color = OnInk)
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color(0xFF14181D),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.68f)
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text(stringResource(R.string.cap_choice_title), style = MaterialTheme.typography.titleLarge, color = OnInk, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.cap_choice_sub), style = MaterialTheme.typography.bodyMedium, color = OnInkMuted)
                }

                if (aiEnabled) {
                    ChoiceCard(Icons.Outlined.AutoAwesome, stringResource(R.string.cap_ai_card), stringResource(R.string.cap_ai_card_sub)) {
                        OutlinedTextField(
                            value = aiNotes,
                            onValueChange = onAiNotes,
                            placeholder = { Text(stringResource(R.string.cap_ai_note_placeholder), color = OnInkMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = OnInk, unfocusedTextColor = OnInk, cursorColor = OnInk,
                                focusedBorderColor = OnInk, unfocusedBorderColor = Color(0x55FFFFFF)
                            )
                        )
                        Button(onClick = onEstimateWithAi, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.cap_ai_card))
                        }
                    }
                }

                ChoiceCard(Icons.Outlined.Inventory2, stringResource(R.string.cap_saved_card), stringResource(R.string.cap_saved_card_sub)) {
                    if (savedProducts.isEmpty()) {
                        Text(
                            stringResource(R.string.cap_saved_none),
                            style = MaterialTheme.typography.bodySmall, color = OnInkMuted
                        )
                    } else {
                        run {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text(stringResource(R.string.cap_saved_search), color = OnInkMuted) },
                                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = OnInkMuted) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = OnInk, unfocusedTextColor = OnInk, cursorColor = OnInk,
                                    focusedBorderColor = OnInk, unfocusedBorderColor = Color(0x55FFFFFF)
                                )
                            )
                        }
                        if (q.isEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !showAll, onClick = { showAll = false }, label = { Text(stringResource(R.string.cap_favorites)) }, shape = CircleShape)
                                FilterChip(selected = showAll, onClick = { showAll = true }, label = { Text(stringResource(R.string.cap_all_count, savedProducts.size)) }, shape = CircleShape)
                            }
                        }
                        shown.forEach { p -> SavedProductRow(p, { onUseSaved(p) }, { onToggleFavorite(p) }, { onForgetSaved(p.barcode) }) }
                        if (shown.isEmpty()) {
                            Text(
                                when {
                                    q.isNotEmpty() -> stringResource(R.string.cap_no_match)
                                    showAll -> stringResource(R.string.cap_nothing_saved)
                                    else -> stringResource(R.string.cap_no_favorites)
                                },
                                style = MaterialTheme.typography.bodySmall, color = OnInkMuted
                            )
                        }
                    }
                }

                ChoiceCard(Icons.Outlined.Search, stringResource(R.string.cap_search_card), stringResource(R.string.cap_search_card_sub)) {
                    Button(onClick = onSearch, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Outlined.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cap_search))
                    }
                }

                TextButton(onClick = onTypeIt, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Outlined.EditNote, null, tint = OnInkMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cap_type_myself), color = OnInk)
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1E242B)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = OnInk, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = OnInk, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnInkMuted)
                }
            }
            content()
        }
    }
}

@Composable
private fun SavedProductRow(product: SavedProductEntity, onClick: () -> Unit, onToggleFavorite: () -> Unit, onForget: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).clickable(onClick = onClick).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.bodyLarge, color = OnInk, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    product.brand,
                    product.servingG?.let { stringResource(R.string.cap_serving_carbs, formatGrams(it), (product.carbs100 * it / 100).roundToInt()) } ?: stringResource(R.string.addmeal_carbs_per_100, product.carbs100.roundToInt())
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = OnInkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (product.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                if (product.isFavorite) stringResource(R.string.cap_unfav_named, product.name) else stringResource(R.string.cap_fav_named, product.name),
                tint = if (product.isFavorite) Color(0xFFFFC107) else OnInkMuted, modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onForget) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.cap_forget_named, product.name), tint = OnInkMuted, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun BoxScope.CameraLayer(
    phase: CapturePhase,
    onBarcode: (String) -> Unit,
    onPhotos: (List<Uri>) -> Unit,
    onSkipPhoto: () -> Unit,
    onManual: () -> Unit,
    onRescan: () -> Unit,
    onClose: () -> Unit,
    onGallery: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build()
        )
    }
    val currentPhase by rememberUpdatedState(phase)
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    var torch by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var shutterFlash by remember { mutableStateOf(false) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            setImageAnalysisAnalyzer(
                mainExecutor,
                MlKitAnalyzer(listOf(scanner), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, mainExecutor) { result ->
                    if (currentPhase != CapturePhase.Scanning) return@MlKitAnalyzer
                    val code = result.getValue(scanner)?.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                    if (code != null) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        currentOnBarcode(code)
                    }
                }
            )
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            scanner.close()
        }
    }
    LaunchedEffect(torch) { runCatching { controller.enableTorch(torch) } }

    val shutter = {
        if (!capturing) {
            capturing = true
            shutterFlash = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
            controller.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        capturing = false
                        onPhotos(listOf(Uri.fromFile(file)))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        capturing = false
                        file.delete()
                    }
                }
            )
        }
    }
    val flashAlpha by animateFloatAsState(if (shutterFlash) 0.85f else 0f, tween(if (shutterFlash) 40 else 220), label = "flash") {
        if (shutterFlash) shutterFlash = false
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    val searching = phase is CapturePhase.Scanning || phase is CapturePhase.LookingUp || phase is CapturePhase.NotFound || phase is CapturePhase.Failed
    if (searching) ScanFrameOverlay(phase)

    // Top: close, progress, torch.
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        RoundIconButton(Icons.Filled.Close, stringResource(R.string.common_close), onClose, Modifier.align(Alignment.CenterStart))
        StepPill(phase, Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundIconButton(Icons.Outlined.Search, stringResource(R.string.cap_search_card), onOpenSearch)
        if (controller.cameraInfo?.hasFlashUnit() != false) {
            RoundIconButton(
                if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                if (torch) stringResource(R.string.cap_flash_off) else stringResource(R.string.cap_flash_on),
                { torch = !torch },
                Modifier,
                active = torch
            )
        }
        }
    }

    // Middle: what the camera is doing / what it found.
    Box(Modifier.fillMaxSize().padding(bottom = 190.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = phase,
            contentKey = { it::class },
            transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 10 }) togetherWith fadeOut(tween(120)) },
            label = "phase"
        ) { p ->
            when (p) {
                is CapturePhase.ProductFound -> ProductFoundPanel(p.product, onRescan)
                else -> Unit
            }
        }
    }
    if (searching) {
        ScanStatus(phase, onRescan, onOpenSearch)
    }

    ControlBar(
        phase = phase,
        capturing = capturing,
        onGallery = onGallery,
        onShutter = shutter,
        onSkipPhoto = onSkipPhoto,
        onManual = onManual,
        modifier = Modifier.align(Alignment.BottomCenter)
    )

    if (flashAlpha > 0f) Box(Modifier.fillMaxSize().alpha(flashAlpha).background(Color.White))
}

// ---- Overlays ---------------------------------------------------------------------------------

/** Dimmed camera with a clear window: the place to hold a barcode. */
@Composable
private fun ScanFrameOverlay(phase: CapturePhase) {
    val busy = phase is CapturePhase.LookingUp
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseAlpha"
    )
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "sweepY"
    )
    val bracket by animateColorAsState(if (busy) Good else OnInk, label = "bracket")
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.width * 0.8f
        val h = w * 0.58f
        val left = (size.width - w) / 2f
        val top = size.height * 0.30f - h / 2f + 40.dp.toPx()
        val radius = CornerRadius(22.dp.toPx())
        drawRect(Scrim)
        drawRoundRect(Color.Black, Offset(left, top), Size(w, h), radius, blendMode = BlendMode.Clear)

        val len = 34.dp.toPx()
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        val a = if (busy) pulse else 1f
        val c = bracket.copy(alpha = a)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y + dy * radius.y), Offset(x, y + dy * len), stroke.width, StrokeCap.Round)
            drawLine(c, Offset(x + dx * radius.x, y), Offset(x + dx * len, y), stroke.width, StrokeCap.Round)
            drawArc(
                c, startAngle = when { dx > 0 && dy > 0 -> 180f; dx < 0 && dy > 0 -> 270f; dx > 0 -> 90f; else -> 0f },
                sweepAngle = 90f, useCenter = false,
                topLeft = Offset(if (dx > 0) x else x - radius.x * 2, if (dy > 0) y else y - radius.y * 2),
                size = Size(radius.x * 2, radius.y * 2), style = stroke
            )
        }
        corner(left, top, 1f, 1f)
        corner(left + w, top, -1f, 1f)
        corner(left, top + h, 1f, -1f)
        corner(left + w, top + h, -1f, -1f)
        if (!busy) {
            val y = top + 14.dp.toPx() + (h - 28.dp.toPx()) * sweep
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, bracket.copy(alpha = 0.9f), Color.Transparent), left, left + w),
                Offset(left + 18.dp.toPx(), y), Offset(left + w - 18.dp.toPx(), y), 2.dp.toPx()
            )
        }
    }
}

/** The line of guidance under the frame. */
@Composable
private fun ScanStatus(phase: CapturePhase, onRescan: () -> Unit, onSearch: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frameBottom = maxHeight * 0.30f + (maxWidth * 0.8f * 0.58f) / 2f + 40.dp
        Column(
            Modifier.padding(top = frameBottom + 18.dp).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (phase) {
                is CapturePhase.Scanning -> {
                    Text(stringResource(R.string.cap_scan_title), style = MaterialTheme.typography.titleLarge, color = OnInk, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.cap_scan_hint),
                        style = MaterialTheme.typography.bodyMedium, color = OnInkMuted, textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(R.string.cap_scan_alt),
                        style = MaterialTheme.typography.bodySmall, color = OnInkMuted.copy(alpha = 0.8f), textAlign = TextAlign.Center
                    )
                }
                is CapturePhase.LookingUp -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Good)
                    Text(stringResource(R.string.cap_looking_up, phase.barcode), style = MaterialTheme.typography.titleMedium, color = OnInk)
                }
                is CapturePhase.NotFound -> NoticeCard(
                    stringResource(R.string.cap_not_found_title),
                    stringResource(R.string.cap_not_found_text, phase.barcode),
                    onRescan,
                    onSearch
                )
                is CapturePhase.Failed -> NoticeCard(stringResource(R.string.cap_failed_title), phase.reason + ".", onRescan, onSearch)
                else -> Unit
            }
        }
    }
}

@Composable
private fun NoticeCard(title: String, text: String, onRescan: () -> Unit, onSearch: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xE6101418)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = OnInk, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = OnInkMuted, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRescan, border = androidx.compose.foundation.BorderStroke(1.dp, OnInkMuted)) {
                    Text(stringResource(R.string.cap_scan_again), color = OnInk)
                }
                OutlinedButton(onClick = onSearch, border = androidx.compose.foundation.BorderStroke(1.dp, OnInkMuted)) {
                    Text(stringResource(R.string.cap_search_card), color = OnInk)
                }
            }
        }
    }
}

/** After a successful scan: confirms the product and points to the next (optional) step. */
@Composable
private fun ProductFoundPanel(product: FoodProduct, onRescan: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = Color(0xE6101418), modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Good), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Ink, modifier = Modifier.size(30.dp))
            }
            Text(
                product.name, style = MaterialTheme.typography.titleLarge, color = OnInk, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(product.brand, stringResource(R.string.addmeal_carbs_per_100, product.per100.carbsG.roundToInt())).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = OnInkMuted, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.cap_next_photo), style = MaterialTheme.typography.titleMedium, color = Good, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.cap_next_photo_sub),
                style = MaterialTheme.typography.bodySmall, color = OnInkMuted, textAlign = TextAlign.Center
            )
            TextButton(onClick = onRescan) { Text(stringResource(R.string.cap_wrong_product), color = OnInkMuted) }
        }
    }
}

/** A two-step progress that only appears once there is a step to speak of: barcode, then photo. */
@Composable
private fun StepPill(phase: CapturePhase, modifier: Modifier = Modifier) {
    val found = phase is CapturePhase.ProductFound
    androidx.compose.animation.AnimatedVisibility(found, modifier, enter = fadeIn(), exit = fadeOut()) {
        Surface(shape = CircleShape, color = Scrim) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Check, null, tint = Good, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.cap_step_barcode), style = MaterialTheme.typography.labelLarge, color = OnInkMuted)
                Box(Modifier.width(14.dp).height(1.dp).background(OnInkMuted))
                Box(Modifier.size(18.dp).clip(CircleShape).background(OnInk), contentAlignment = Alignment.Center) {
                    Text("2", style = MaterialTheme.typography.labelSmall, color = Ink, fontWeight = FontWeight.Bold)
                }
                Text(stringResource(R.string.cap_step_photo), style = MaterialTheme.typography.labelLarge, color = OnInk, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---- Controls ---------------------------------------------------------------------------------

@Composable
private fun ControlBar(
    phase: CapturePhase,
    capturing: Boolean,
    onGallery: () -> Unit,
    onShutter: () -> Unit,
    onSkipPhoto: () -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    val found = phase is CapturePhase.ProductFound
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .navigationBarsPadding()
            .padding(top = 40.dp, bottom = 18.dp, start = 16.dp, end = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            ControlButton(Icons.Outlined.PhotoLibrary, if (found) stringResource(R.string.cap_pick_photo) else stringResource(R.string.meal_gallery), onGallery, Modifier.weight(1f))
            Column(Modifier.weight(1.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                ShutterButton(capturing, onShutter)
                Spacer(Modifier.height(8.dp))
                AnimatedContent(found, label = "shutterLabel") { isFound ->
                    Text(
                        if (isFound) stringResource(R.string.cap_photo_of_meal) else stringResource(R.string.cap_photo_ai),
                        style = MaterialTheme.typography.labelLarge, color = OnInk, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center
                    )
                }
            }
            if (found) ControlButton(Icons.Filled.Check, stringResource(R.string.cap_skip_photo), onSkipPhoto, Modifier.weight(1f))
            else ControlButton(Icons.Outlined.EditNote, stringResource(R.string.cap_type_it), onManual, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x40FFFFFF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = OnInk, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = OnInk, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun ShutterButton(busy: Boolean, onClick: () -> Unit) {
    val takePhotoLabel = stringResource(R.string.meal_take_photo)
    Box(
        Modifier
            .size(76.dp)
            .border(4.dp, OnInk, CircleShape)
            .padding(7.dp)
            .clip(CircleShape)
            .background(OnInk)
            .clickable(enabled = !busy, onClickLabel = stringResource(R.string.meal_take_photo), onClick = onClick)
            .semantics { contentDescription = takePhotoLabel },
        contentAlignment = Alignment.Center
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = Ink)
        else Icon(Icons.Outlined.CameraAlt, null, tint = Ink, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(44.dp).clip(CircleShape).background(if (active) OnInk else Scrim)
    ) {
        Icon(icon, description, tint = if (active) Ink else OnInk)
    }
}

/** Shown when the camera permission was refused: the other two ways in still work. */
@Composable
private fun NoCameraPanel(askedBefore: Boolean, onAllow: () -> Unit, onGallery: () -> Unit, onManual: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        RoundIconButton(Icons.Filled.Close, "Close", onClose, Modifier.padding(12.dp))
        Column(
            Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.NoPhotography, null, tint = OnInkMuted, modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.cap_no_camera_title), style = MaterialTheme.typography.titleLarge, color = OnInk, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.cap_no_camera_text),
                style = MaterialTheme.typography.bodyMedium, color = OnInkMuted, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (askedBefore) stringResource(R.string.cap_allow_camera) else stringResource(R.string.cap_continue))
            }
            OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(52.dp), border = androidx.compose.foundation.BorderStroke(1.dp, OnInkMuted)) {
                Text(stringResource(R.string.cap_pick_a_photo), color = OnInk)
            }
            TextButton(onClick = onManual) { Text(stringResource(R.string.cap_type_instead), color = OnInk) }
        }
    }
}

/** Search the food databases by name; picking a result fills the form with its default portion. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FoodSearchScreen(
    phase: CapturePhase.Search,
    saved: List<SavedProductEntity>,
    recipes: List<RecipeEntity>,
    recents: List<String>,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onToggleFavorite: (SavedProductEntity) -> Unit,
    onPick: (FoodProduct) -> Unit,
    onBack: () -> Unit
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = phase.query.trim()
    val canSearch = q.length >= 2 && !phase.loading
    val submit = { if (canSearch) { keyboard?.hide(); onSubmit() } }
    val savedByCode = remember(saved) { saved.associateBy { it.barcode } }
    // "Recipes" lists the user's own dishes (local, filtered as you type); "Foods" searches the databases.
    var recipesTab by remember { mutableStateOf(false) }
    val recipeProducts = remember(recipes, q) {
        recipes.filter { q.isEmpty() || it.name.contains(q, true) }.mapNotNull { it.toProduct()?.let { p -> it to p } }
    }
    // Saved products that fit the text: free, shown as you type. Bookmarks first.
    val savedMatches = remember(saved, q) {
        saved.filter { q.isEmpty() || it.name.contains(q, true) || it.brand?.contains(q, true) == true }
            .sortedWith(compareByDescending<SavedProductEntity> { it.isFavorite }.thenByDescending { it.lastUsedUtc })
    }
    // After a search: what you saved before comes first, then the rest of the results.
    val results = remember(phase.results, savedMatches) {
        val fromDb = phase.results.sortedByDescending { r -> savedByCode[r.barcode]?.let { if (it.isFavorite) 2 else 1 } ?: 0 }
        val codes = fromDb.map { it.barcode }.toSet()
        val extra = savedMatches.filter { it.barcode !in codes }.map { it.toProduct() }
        (extra + fromDb).sortedByDescending { r -> savedByCode[r.barcode]?.let { if (it.isFavorite) 2 else 1 } ?: 0 }
    }
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), onBack)
            OutlinedTextField(
                value = phase.query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.cap_search_placeholder), color = OnInkMuted) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = OnInkMuted) },
                trailingIcon = {
                    if (phase.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, stringResource(R.string.common_clear), tint = OnInkMuted, modifier = Modifier.size(18.dp)) }
                },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OnInk, unfocusedTextColor = OnInk, cursorColor = OnInk,
                    focusedBorderColor = OnInk, unfocusedBorderColor = Color(0x55FFFFFF)
                )
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !recipesTab, onClick = { recipesTab = false }, label = { Text(stringResource(R.string.recipe_tab_foods)) }, shape = CircleShape)
            FilterChip(
                selected = recipesTab, onClick = { recipesTab = true }, shape = CircleShape,
                label = { Text(stringResource(R.string.recipe_tab_recipes) + if (recipes.isNotEmpty()) " (${recipes.size})" else "") }
            )
        }
        if (!recipesTab) Button(
            onClick = submit, enabled = canSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp)
        ) {
            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cap_search))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                recipesTab -> {
                    recipeProducts.forEach { (r, p) -> RecipeResultRow(r, p, onClick = { onPick(p) }) }
                    if (recipeProducts.isEmpty()) Text(
                        stringResource(if (recipes.isEmpty()) R.string.recipe_none_hint else R.string.cap_no_match),
                        style = MaterialTheme.typography.bodyMedium, color = OnInkMuted
                    )
                }
                phase.loading -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Good)
                }
                phase.searched -> {
                    if (phase.error != null) Text(phase.error.asString(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFF8A80))
                    if (results.isEmpty() && phase.error == null) Text(stringResource(R.string.cap_nothing_found), style = MaterialTheme.typography.bodyMedium, color = OnInkMuted)
                    results.forEach { r -> SearchResultRow(r, savedByCode[r.barcode], { onPick(r) }, { savedByCode[r.barcode]?.let(onToggleFavorite) }) }
                }
                else -> {
                    val recentShown = recents.filter { q.isEmpty() || it.contains(q, true) }
                    if (recentShown.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel(stringResource(R.string.cap_recent), Modifier.weight(1f))
                            if (q.isEmpty()) TextButton(onClick = onClearRecents) { Text(stringResource(R.string.common_clear), color = OnInkMuted) }
                        }
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            recentShown.forEach { r ->
                                Surface(shape = CircleShape, color = Color(0xFF1E242B), modifier = Modifier.clip(CircleShape).clickable { onRecent(r) }) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Outlined.History, null, tint = OnInkMuted, modifier = Modifier.size(16.dp))
                                        Text(r, style = MaterialTheme.typography.bodyMedium, color = OnInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    val bookmarks = savedMatches.filter { it.isFavorite }
                    val used = savedMatches.filter { !it.isFavorite }
                    if (bookmarks.isNotEmpty()) {
                        SectionLabel(stringResource(R.string.cap_bookmarks), tint = BookmarkGold)
                        bookmarks.forEach { p -> SearchResultRow(p.toProduct(), p, { onPick(p.toProduct()) }, { onToggleFavorite(p) }) }
                    }
                    if (used.isNotEmpty()) {
                        SectionLabel(stringResource(R.string.cap_used_before))
                        used.forEach { p -> SearchResultRow(p.toProduct(), p, { onPick(p.toProduct()) }, { onToggleFavorite(p) }) }
                    }
                    if (recentShown.isEmpty() && savedMatches.isEmpty()) Text(
                        if (q.isEmpty()) stringResource(R.string.cap_search_empty_hint)
                        else stringResource(R.string.cap_search_no_saved),
                        style = MaterialTheme.typography.bodyMedium, color = OnInkMuted
                    )
                }
            }
            if (!phase.loading && !recipesTab) Text(
                stringResource(R.string.cap_search_footer),
                style = MaterialTheme.typography.bodySmall, color = OnInkMuted.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private val BookmarkGold = Color(0xFFFFC107)

/** A recipe in the picker: name, how many ingredients and the portion it suggests, carbs per 100 g. */
@Composable
private fun RecipeResultRow(recipe: RecipeEntity, product: FoodProduct, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF1E242B)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🍲", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f)) {
            Text(recipe.name, style = MaterialTheme.typography.bodyLarge, color = OnInk, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    pluralStringResource(R.plurals.recipe_ingredients, recipe.ingredients.size, recipe.ingredients.size),
                    product.servingG?.let { stringResource(R.string.recipe_portion_of, formatGrams(it)) },
                    stringResource(R.string.addmeal_carbs_per_100, product.per100.carbsG.roundToInt())
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = OnInkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier, tint: Color = OnInkMuted) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(top = 6.dp))
}

/** [saved] non-null = used before (subtle highlight); a bookmark gets a gold outline and star. */
@Composable
private fun SearchResultRow(product: FoodProduct, saved: SavedProductEntity?, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val fav = saved?.isFavorite == true
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (fav) Color(0x26FFC107) else if (saved != null) Color(0xFF26303A) else Color(0xFF1E242B))
            .then(if (fav) Modifier.border(1.5.dp, BookmarkGold, shape) else if (saved != null) Modifier.border(1.dp, Good.copy(alpha = 0.6f), shape) else Modifier)
            .clickable(onClick = onClick).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = if (saved != null) 2.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.bodyLarge, color = OnInk, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(product.brand, stringResource(R.string.addmeal_carbs_per_100, product.per100.carbsG.roundToInt()), product.source).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = OnInkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (saved != null && !fav) Text(stringResource(R.string.cap_used_before), style = MaterialTheme.typography.labelSmall, color = Good, fontWeight = FontWeight.SemiBold)
        }
        if (saved != null) IconButton(onClick = onToggleFavorite) {
            Icon(
                if (fav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                if (fav) stringResource(R.string.cap_unbookmark_named, product.name) else stringResource(R.string.cap_bookmark_named, product.name),
                tint = if (fav) BookmarkGold else OnInkMuted, modifier = Modifier.size(22.dp)
            )
        }
    }
}
