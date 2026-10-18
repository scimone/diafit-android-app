package uk.scimone.diafit.recipes.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.R
import uk.scimone.diafit.addmeal.presentation.AddMealViewModel
import uk.scimone.diafit.addmeal.presentation.CapturePhase
import uk.scimone.diafit.addmeal.presentation.TitleField
import uk.scimone.diafit.addmeal.presentation.components.FoodItemRow
import uk.scimone.diafit.addmeal.presentation.components.MealCaptureScreen
import uk.scimone.diafit.addmeal.presentation.components.ProductPortionCard
import uk.scimone.diafit.core.domain.model.MealComponent
import uk.scimone.diafit.core.domain.model.formatGrams
import uk.scimone.diafit.core.domain.model.kcalOf
import uk.scimone.diafit.logs.presentation.components.SectionLabel
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.FatColor
import uk.scimone.diafit.ui.theme.ProteinColor
import kotlin.math.roundToInt

/**
 * Create or edit a recipe: name, ingredients (each added by scanning, searching, a photo for the AI, or by
 * hand, then weighed), and optionally what the finished dish weighs and how many portions it makes.
 * The ingredient flow reuses the meal camera stage through its own [AddMealViewModel] instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(userId: Int, recipeId: Int?, onClose: () -> Unit) {
    val viewModel: RecipeEditorViewModel = koinViewModel(key = "recipe-editor-${recipeId ?: "new"}")
    val state by viewModel.state.collectAsState()
    val ingredientVm: AddMealViewModel = koinViewModel(key = "recipe-ingredient") { parametersOf(userId) }
    val ingredientState by ingredientVm.uiState.collectAsState()
    val savedProducts by ingredientVm.savedProductList.collectAsState()
    val recipes by ingredientVm.recipeList.collectAsState()
    val aiEnabled by ingredientVm.aiEnabled.collectAsState()
    val recentSearches by ingredientVm.recentSearches.collectAsState()

    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(recipeId) { viewModel.start(recipeId) }
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    // A rotation or process restart while adding lands back on the form, not in a half-started flow.
    LaunchedEffect(Unit) { if (adding && ingredientState.capture == null && ingredientState.components.isEmpty()) adding = false }

    val msgContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(ingredientState.snackbarMessage) {
        ingredientState.snackbarMessage?.let { snackbar.showSnackbar(it.asString(msgContext)); ingredientVm.resetSnackbar() }
    }

    val requestClose = { if (viewModel.isDirty()) confirmDiscard = true else onClose() }
    BackHandler(enabled = !adding, onBack = requestClose)
    val stopAdding = { ingredientVm.discard(); adding = false }
    BackHandler(enabled = adding) {
        if (ingredientState.capture?.phase is CapturePhase.Search) ingredientVm.closeSearch() else stopAdding()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        TitleField(
                            overline = stringResource(if (viewModel.isEditing) R.string.recipe_edit else R.string.recipe_new),
                            value = state.name,
                            placeholder = stringResource(R.string.recipe_name_placeholder),
                            onValueChange = viewModel::onName
                        )
                    },
                    navigationIcon = { IconButton(onClick = requestClose) { Icon(Icons.Filled.Close, stringResource(R.string.common_close)) } },
                    actions = {
                        if (viewModel.isEditing) IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.recipe_delete))
                        }
                    }
                )
            },
            bottomBar = { RecipeSaveBar(state, onSave = viewModel::save) }
        ) { padding ->
            if (!state.loaded) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(stringResource(R.string.recipe_ingredients_label), Modifier.weight(1f))
                    if (state.ingredients.isNotEmpty()) Text(
                        stringResource(R.string.recipe_raw_weight, formatGrams(state.rawWeightG.roundToInt().toDouble())),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.ingredients.isEmpty()) {
                    Text(stringResource(R.string.recipe_ingredients_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.ingredients.forEachIndexed { i, c ->
                    FoodItemRow(
                        component = c,
                        onWeightChange = { viewModel.onIngredientWeight(i, it) },
                        onMacrosChange = { carbs, protein, fat -> viewModel.onIngredientMacros(i, carbs, protein, fat) },
                        onRemove = { viewModel.onIngredientRemoved(i) },
                        canRemove = true
                    )
                }
                AddIngredientButton(first = state.ingredients.isEmpty()) {
                    ingredientVm.startNewMeal()
                    adding = true
                }
                if (state.ingredients.isNotEmpty()) {
                    AfterCookingCard(state, viewModel::onFinishedWeight, viewModel::onServings)
                    NutritionCard(state)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (adding) {
            val capture = ingredientState.capture
            if (capture != null) {
                MealCaptureScreen(
                    phase = capture.phase,
                    onBarcode = ingredientVm::onBarcodeDetected,
                    onPhotos = ingredientVm::onCapturedPhotos,
                    onSkipPhoto = ingredientVm::finishCapture,
                    onManual = ingredientVm::finishCapture,
                    onRescan = ingredientVm::rescan,
                    onClose = stopAdding,
                    photo = ingredientState.photos.firstOrNull()?.uri,
                    aiNotes = ingredientState.aiNotes,
                    onAiNotes = ingredientVm::onAiNotesChanged,
                    onEstimateWithAi = ingredientVm::estimateWithAi,
                    aiEnabled = aiEnabled,
                    savedProducts = savedProducts,
                    recipes = recipes,
                    onUseSaved = ingredientVm::useSavedProduct,
                    onToggleFavorite = ingredientVm::toggleSavedFavorite,
                    onForgetSaved = ingredientVm::forgetSavedProduct,
                    onRetake = ingredientVm::retakePhoto,
                    onOpenSearch = ingredientVm::openSearch,
                    onCloseSearch = ingredientVm::closeSearch,
                    onSearchQuery = ingredientVm::onSearchQuery,
                    onSubmitSearch = ingredientVm::submitSearch,
                    recentSearches = recentSearches,
                    onUseRecentSearch = ingredientVm::useRecentSearch,
                    onClearRecentSearches = ingredientVm::clearRecentSearches,
                    onUseSearchResult = ingredientVm::useSearchResult
                )
            } else {
                IngredientConfirmScreen(
                    vm = ingredientVm,
                    onCancel = stopAdding,
                    onAdd = { list ->
                        viewModel.addIngredients(list)
                        ingredientVm.discard()
                        adding = false
                    },
                    snackbar = snackbar
                )
            }
        }
    }

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.addmeal_discard_title)) },
        text = { Text(stringResource(R.string.recipe_discard_text)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.addmeal_discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.addmeal_keep_editing)) } }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.recipes_delete_title, state.name.ifBlank { stringResource(R.string.recipe_untitled) })) },
        text = { Text(stringResource(R.string.recipes_delete_text)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.recipe_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** The one primary action of the page: big while the recipe is empty, a tonal button after. */
@Composable
private fun AddIngredientButton(first: Boolean, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Icon(Icons.Filled.Add, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (first) R.string.recipe_add_first else R.string.recipe_add_ingredient), style = MaterialTheme.typography.titleSmall)
    }
    if (first) Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) { content() }
    else FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) { content() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.recipe_add_ways), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Finished weight (cooking changes it) and an optional number of portions. */
@Composable
private fun AfterCookingCard(state: RecipeEditorState, onWeight: (String) -> Unit, onServings: (Int?) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(stringResource(R.string.recipe_after_cooking))
            OutlinedTextField(
                value = state.finishedWeightText,
                onValueChange = onWeight,
                label = { Text(stringResource(R.string.recipe_finished_weight)) },
                suffix = { Text("g") },
                placeholder = { Text(state.rawWeightG.roundToInt().toString()) },
                supportingText = { Text(stringResource(R.string.recipe_finished_help, formatGrams(state.rawWeightG.roundToInt().toDouble()))) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.recipe_portions_label), style = MaterialTheme.typography.titleSmall)
                    Text(
                        state.servingG?.let { stringResource(R.string.recipe_portion_each, formatGrams(it.roundToInt().toDouble())) } ?: stringResource(R.string.recipe_portions_help),
                        style = MaterialTheme.typography.bodySmall, color = muted
                    )
                }
                FilledTonalIconButton(onClick = { onServings(state.servings?.minus(1)) }, enabled = state.servings != null) {
                    Icon(Icons.Filled.Remove, stringResource(R.string.recipe_fewer_portions))
                }
                Text(
                    state.servings?.toString() ?: "–",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                FilledTonalIconButton(onClick = { onServings((state.servings ?: 0) + 1) }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.recipe_more_portions))
                }
            }
        }
    }
}

/** What 100 g of the finished dish contains, the numbers a meal made from it will use. */
@Composable
private fun NutritionCard(state: RecipeEditorState) {
    val per = state.per100 ?: return
    Surface(
        shape = RoundedCornerShape(24.dp), color = Carbs.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Carbs.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.recipe_per_100_label), Modifier.weight(1f))
                Text("${per.kcal.roundToInt()} kcal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NutrientTile(stringResource(R.string.macro_carbs), per.carbsG, Carbs, Modifier.weight(1.3f))
                NutrientTile(stringResource(R.string.macro_protein), per.proteinG, ProteinColor, Modifier.weight(1f))
                NutrientTile(stringResource(R.string.macro_fat), per.fatG, FatColor, Modifier.weight(1f))
            }
            val t = state.totals
            Text(
                stringResource(R.string.recipe_whole_dish, t.carbs, t.protein, t.fat, formatGrams(state.effectiveWeightG.roundToInt().toDouble())),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NutrientTile(label: String, value: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${value.roundToInt()}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
                Text(" g", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun RecipeSaveBar(state: RecipeEditorState, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${state.totals.carbs}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Carbs)
                    Text(" " + stringResource(R.string.addmeal_g_carbs), style = MaterialTheme.typography.labelLarge, color = Carbs, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text(
                    pluralStringResource(R.plurals.recipe_ingredients, state.ingredients.size, state.ingredients.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.height(52.dp).widthIn(min = 148.dp)) {
                if (state.saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else {
                    Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recipe_save), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

/**
 * Step two of adding an ingredient: what the scan, search or photo found, to be weighed and confirmed
 * (or typed by hand when nothing was found or the user chose to).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientConfirmScreen(vm: AddMealViewModel, onCancel: () -> Unit, onAdd: (List<MealComponent>) -> Unit, snackbar: SnackbarHostState) {
    val state by vm.uiState.collectAsState()
    val components = state.components
    var manualName by rememberSaveable { mutableStateOf("") }
    var manualWeight by rememberSaveable { mutableStateOf("") }
    var manualCarbs by rememberSaveable { mutableStateOf("") }
    var manualProtein by rememberSaveable { mutableStateOf("") }
    var manualFat by rememberSaveable { mutableStateOf("") }
    fun num(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0
    val manual = MealComponent(
        name = manualName.trim(), emoji = "🥣", weightG = num(manualWeight), carbsG = num(manualCarbs), proteinG = num(manualProtein), fatG = num(manualFat),
        calories = kcalOf(num(manualCarbs), num(manualProtein), num(manualFat))
    )
    val manualValid = manual.name.isNotEmpty() && manual.weightG > 0.0
    val canAdd = !state.isAnalyzing && if (components.isNotEmpty()) components.all { it.weightG > 0.0 } else manualValid

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipe_ingredient_title)) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, stringResource(R.string.common_close)) } }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { onAdd(if (components.isNotEmpty()) components else listOf(manual)) },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp)
                ) {
                    Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recipe_add_to_recipe), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                state.isAnalyzing -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.recipe_estimating))
                }
                components.size == 1 && components[0].barcode != null -> ProductPortionCard(
                    product = components[0],
                    onWeightChange = { vm.onComponentWeightChanged(0, it) },
                    onRescan = vm::rescanProduct,
                    isFavorite = components[0].barcode in state.favoriteBarcodes,
                    onToggleFavorite = { components[0].barcode?.let(vm::toggleProductFavorite) }
                )
                components.isNotEmpty() -> {
                    components.forEachIndexed { i, c ->
                        FoodItemRow(
                            component = c,
                            onWeightChange = { vm.onComponentWeightChanged(i, it) },
                            onMacrosChange = { carbs, protein, fat -> vm.onComponentMacrosChanged(i, carbs, protein, fat) },
                            onRemove = { vm.onComponentRemoved(i) },
                            canRemove = components.size > 1
                        )
                    }
                    state.reasoning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = vm::rescanProduct) { Text(stringResource(R.string.recipe_try_another_way)) }
                }
                else -> {
                    Text(stringResource(R.string.recipe_manual_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = manualName, onValueChange = { manualName = it }, label = { Text(stringResource(R.string.recipe_ingredient_name)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences)
                    )
                    DecimalField(manualWeight, { manualWeight = it }, stringResource(R.string.addmeal_weight), "g", Modifier.fillMaxWidth())
                    Text(stringResource(R.string.recipe_manual_nutrients), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField(manualCarbs, { manualCarbs = it }, stringResource(R.string.macro_carbs), "g", Modifier.weight(1f))
                        DecimalField(manualProtein, { manualProtein = it }, stringResource(R.string.macro_protein), "g", Modifier.weight(1f))
                        DecimalField(manualFat, { manualFat = it }, stringResource(R.string.macro_fat), "g", Modifier.weight(1f))
                    }
                    TextButton(onClick = vm::rescanProduct) { Text(stringResource(R.string.recipe_try_another_way)) }
                }
            }
        }
    }
}

@Composable
private fun DecimalField(value: String, onChange: (String) -> Unit, label: String, suffix: String, modifier: Modifier) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7)) },
        label = { Text(label, maxLines = 1) }, suffix = { Text(suffix) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier
    )
}
