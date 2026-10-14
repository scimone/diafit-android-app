package uk.scimone.diafit.addmeal.presentation

import uk.scimone.diafit.core.presentation.AppStrings
import uk.scimone.diafit.core.presentation.UiText
import uk.scimone.diafit.R
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.FoodProduct
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealComponent
import uk.scimone.diafit.core.domain.model.totals
import uk.scimone.diafit.core.domain.model.withMacros
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealEntity.Companion.inferImpactType
import uk.scimone.diafit.core.domain.model.MealEntity.Companion.suggestMealType
import uk.scimone.diafit.core.domain.model.MealPhoto
import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.core.domain.repository.BolusRepository
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.AnalyzeMealUseCase
import uk.scimone.diafit.core.domain.usecase.CreateMealUseCase
import uk.scimone.diafit.core.domain.usecase.GetMealSittingUseCase
import uk.scimone.diafit.core.domain.usecase.LookupBarcodeUseCase
import uk.scimone.diafit.core.domain.usecase.SearchFoodByNameUseCase
import uk.scimone.diafit.core.domain.repository.SavedProductRepository
import uk.scimone.diafit.core.domain.model.SavedProductEntity
import uk.scimone.diafit.core.domain.model.toSavedProductOrNull
import uk.scimone.diafit.core.domain.model.ProductLookup
import uk.scimone.diafit.core.domain.usecase.SetMealValidUseCase
import uk.scimone.diafit.core.domain.usecase.UpdateMealUseCase
import uk.scimone.diafit.core.domain.util.localDateTimeToInstant
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

private const val TAG = "AddMealViewModel"

/** Only a sanity bound on the stored search history. */
private const val MAX_RECENT_SEARCHES = 200

/** A course added this long after the meal's last one defaults to "now"; older meals get last + this. */
private const val COURSE_DEFAULT_GAP_MS = 10 * 60_000L
private const val RECENT_MEAL_MS = 3 * 60 * 60_000L

/**
 * Drives the meal editor: logging a new meal, adding a course to an existing meal, and editing a course.
 */
class AddMealViewModel(
    private val createMealUseCase: CreateMealUseCase,
    private val updateMealUseCase: UpdateMealUseCase,
    private val setMealValidUseCase: SetMealValidUseCase,
    private val analyzeMealUseCase: AnalyzeMealUseCase,
    private val lookupBarcode: LookupBarcodeUseCase,
    private val searchFoodByName: SearchFoodByNameUseCase,
    private val savedProducts: SavedProductRepository,
    private val getMealSitting: GetMealSittingUseCase,
    private val mealRepository: MealRepository,  // TODO: Use usecase instead of repository
    private val bolusRepository: BolusRepository,
    private val fileStorageRepository: FileStorageRepository,  // TODO: Use usecase instead of repository
    private val settingsRepository: uk.scimone.diafit.settings.domain.repository.SettingsRepository,
    private val userId: Int,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AddMealState())
    val uiState = _uiState.asStateFlow()

    /** Products saved in earlier meals, latest first, offered after a meal photo. */
    val savedProductList = savedProducts.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SavedProductEntity>())

    /** Products looked up or picked in this session, so saving remembers the database's values, not edited ones. */
    private val scannedProducts = mutableMapOf<String, FoodProduct>()

    /** Photo files created in this session and not saved yet; deleted if the editor is left without saving. */
    private val unsavedPhotoIds = mutableSetOf<String>()
    private var pendingCamera: MealPhoto? = null
    private var editing: MealEntity? = null
    /** The meal a new course joins (its courses), or null for a brand-new meal. */
    private var targetSitting: MealSitting? = null
    private var baseline: AddMealState = AddMealState()
    /** The meal's title when the editor opened, to tell a rename from an untouched default. */
    private var sittingTitleAtStart: String? = null

    private val _aiEnabled = MutableStateFlow(true)
    /** False when the user switched AI or meal estimation off in the menu: no photo estimates are offered. */
    val aiEnabled = _aiEnabled.asStateFlow()

    private fun reset() {
        viewModelScope.launch { _aiEnabled.value = settingsRepository.isAiEnabled() && settingsRepository.isMealEstimationEnabled() }
        editing = null
        targetSitting = null
        sittingTitleAtStart = null
        pendingCamera = null
        unsavedPhotoIds.clear()
    }

    fun startNewMeal() {
        reset()
        val now = LocalDateTime.now()
        _uiState.value = AddMealState(
            mealTime = localDateTimeToInstant(now).toEpochMilli(),
            mealType = suggestMealType(now.hour, null),
            description = "",
            capture = CaptureState()
        )
        baseline = _uiState.value.formFields()
    }

    /** A new course for the meal that [mealId] belongs to. */
    fun startAddingCourse(mealId: Int) {
        reset()
        viewModelScope.launch {
            val sitting = getMealSitting(mealId)
            if (sitting == null) {
                startNewMeal()
                return@launch
            }
            targetSitting = sitting
            sittingTitleAtStart = sitting.title
            val now = System.currentTimeMillis()
            val time = if (now - sitting.endTime < RECENT_MEAL_MS) now else sitting.endTime + COURSE_DEFAULT_GAP_MS
            _uiState.value = AddMealState(
                mealTime = time,
                mealType = sitting.courses.first().mealType,
                mealTypeAuto = false,
                description = "",
                sitting = sittingContext(sitting, excludeId = null),
                mealName = sitting.title,
                capture = CaptureState()
            )
            baseline = _uiState.value.formFields()
        }
    }

    fun startEditing(mealId: Int) {
        reset()
        viewModelScope.launch {
            val meal = mealRepository.getMealById(mealId)
            if (meal == null) {
                _uiState.update { it.copy(snackbarMessage = UiText.of(R.string.addmeal_msg_gone), finished = EditorResult.Deleted(mealId)) }
                return@launch
            }
            editing = meal
            val sitting = getMealSitting(mealId)?.takeIf { it.isExtended }
            sittingTitleAtStart = sitting?.title
            _uiState.value = AddMealState(
                editingMealId = meal.id,
                photos = meal.photoIds.mapNotNull { id -> fileStorageRepository.getFileProviderUri(id)?.let { MealPhoto(id, it) } },
                description = meal.description.orEmpty(),
                mealTime = meal.mealTimeUtc,
                carbohydrates = meal.carbohydrates,
                proteins = meal.proteins,
                fats = meal.fats,
                calories = meal.calories,
                impactType = meal.impactType,
                impactAuto = false,
                mealType = meal.mealType,
                mealTypeAuto = false,
                reasoning = meal.reasoning,
                components = meal.components,
                sitting = sitting?.let { sittingContext(it, excludeId = meal.id) },
                mealName = sitting?.title.orEmpty()
            )
            baseline = _uiState.value.formFields()
        }
    }

    private suspend fun sittingContext(sitting: MealSitting, excludeId: Int?): SittingContext {
        val insulin = runCatching {
            bolusRepository.getBolusBetween(sitting.startTime - 30 * 60_000L, System.currentTimeMillis(), userId).sumOf { it.value.toDouble() }
        }.onFailure { Log.e(TAG, "Couldn't load insulin for the meal", it) }.getOrDefault(0.0)
        return SittingContext(
            title = sitting.title,
            startTime = sitting.startTime,
            otherCourses = sitting.courses.filter { it.id != excludeId }.mapIndexed { i, c ->
                CourseSummary(
                    id = c.id,
                    time = c.mealTimeUtc,
                    carbs = c.carbohydrates,
                    title = c.description?.takeIf { it.isNotBlank() } ?: AppStrings.get(R.string.logs_tl_course, i + 1),
                    photo = c.photoIds.firstOrNull()?.let { fileStorageRepository.getFileProviderUri(it) }
                )
            },
            insulinUnits = insulin
        )
    }

    fun isDirty(): Boolean = _uiState.value.formFields() != baseline

    /** Called when the editor is left without saving. */
    fun discard() {
        val orphans = unsavedPhotoIds.toList()
        unsavedPhotoIds.clear()
        if (orphans.isNotEmpty()) viewModelScope.launch { orphans.forEach { fileStorageRepository.deleteImage(it) } }
    }

    fun resetSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /** Uri for the camera to write the next photo into; report back with [onCameraResult]. */
    fun createCameraImageUri(): Uri {
        val id = UUID.randomUUID().toString()
        val uri = fileStorageRepository.createImageUri(id)
        pendingCamera = MealPhoto(id, uri)
        unsavedPhotoIds += id
        return uri
    }

    fun onCameraResult(success: Boolean) {
        val photo = pendingCamera ?: return
        pendingCamera = null
        if (success) addPhotos(listOf(photo))
        else {
            unsavedPhotoIds -= photo.imageId
            viewModelScope.launch { fileStorageRepository.deleteImage(photo.imageId) }
        }
    }

    /** Copies picked gallery images into private storage and adds them, up to [MAX_PHOTOS_PER_COURSE]. */
    fun onGalleryImagesPicked(sourceUris: List<Uri>) {
        val room = MAX_PHOTOS_PER_COURSE - _uiState.value.photos.size
        if (sourceUris.isEmpty() || room <= 0) return
        viewModelScope.launch {
            val copied = sourceUris.take(room).mapNotNull { source ->
                val id = UUID.randomUUID().toString()
                fileStorageRepository.copyGalleryImageToPrivateStorage(source, id)
                    .onFailure { Log.e(TAG, "Failed to copy $source", it) }
                    .getOrNull()
                    ?.let { MealPhoto(id, it).also { unsavedPhotoIds += id } }
            }
            addPhotos(copied)
            when {
                copied.size < sourceUris.take(room).size -> _uiState.update { it.copy(snackbarMessage = UiText.of(R.string.addmeal_msg_photos_failed)) }
                sourceUris.size > room -> _uiState.update { it.copy(snackbarMessage = UiText.of(R.string.addmeal_msg_photos_limit, MAX_PHOTOS_PER_COURSE)) }
            }
        }
    }

    private fun addPhotos(photos: List<MealPhoto>) {
        if (photos.isEmpty()) return
        _uiState.update { it.withoutEstimate().copy(photos = (it.photos + photos).take(MAX_PHOTOS_PER_COURSE)) }
    }

    /**
     * Changed photos invalidate the AI estimate: its foods, totals, reasoning and suggested name are
     * dropped so nothing from the old photos lingers. Values the user typed without AI are kept.
     */
    private fun AddMealState.withoutEstimate(): AddMealState {
        // A scanned product doesn't come from the photos, so changing them leaves it alone.
        if (reasoning == null && components.all { it.barcode != null }) return this
        val hadFoods = components.isNotEmpty()
        return copy(
            components = emptyList(),
            reasoning = null,
            description = if (description == dishName) "" else description,
            dishName = null,
            carbohydrates = if (hadFoods) null else carbohydrates,
            proteins = if (hadFoods) null else proteins,
            fats = if (hadFoods) null else fats,
            calories = if (hadFoods) null else calories,
            impactType = if (impactAuto || hadFoods) ImpactType.MEDIUM else impactType,
            impactAuto = impactAuto || hadFoods
        )
    }

    // ---- Camera stage -------------------------------------------------------------------------

    private fun setPhase(phase: CapturePhase) = _uiState.update { it.copy(capture = CaptureState(phase)) }

    private val capturePhase get() = _uiState.value.capture?.phase

    /** The camera saw a barcode. Only acted on while scanning, so a code that stays in view fires once. */
    fun onBarcodeDetected(barcode: String) {
        if (capturePhase != CapturePhase.Scanning) return
        setPhase(CapturePhase.LookingUp(barcode))
        viewModelScope.launch {
            val phase = when (val result = lookupBarcode(barcode)) {
                is ProductLookup.Found -> CapturePhase.ProductFound(result.product)
                // Offline (or unknown to the database): a product saved before still works.
                is ProductLookup.NotFound -> savedProducts.get(barcode)?.let { CapturePhase.ProductFound(it) } ?: CapturePhase.NotFound(result.barcode)
                is ProductLookup.Failed -> savedProducts.get(barcode)?.let { CapturePhase.ProductFound(it) } ?: CapturePhase.Failed(result.barcode, result.reason)
            }
            // The user may have left the camera (typed manually) while the lookup ran.
            if (capturePhase is CapturePhase.LookingUp) setPhase(phase)
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    /**
     * Cancelling an in-flight request makes Ktor's HttpURLConnection engine drain and close the response
     * stream on the cancelling thread; on the main thread that throws ("Unbalanced enter/exit") and crashes.
     */
    private fun cancelSearch() {
        val job = searchJob ?: return
        searchJob = null
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { job.cancel() }
    }

    fun openSearch() {
        cancelSearch()
        setPhase(CapturePhase.Search(fromPhoto = _uiState.value.photos.isNotEmpty()))
    }

    fun closeSearch() {
        cancelSearch()
        val search = capturePhase as? CapturePhase.Search ?: return
        setPhase(if (search.fromPhoto) CapturePhase.PhotoTaken else CapturePhase.Scanning)
    }

    private val searchPrefs = application.getSharedPreferences("food_search", android.content.Context.MODE_PRIVATE)
    private val _recentSearches = MutableStateFlow(searchPrefs.getString("recent", "").orEmpty().split('\n').filter { it.isNotBlank() })
    /** Earlier searches, latest first. */
    val recentSearches = _recentSearches.asStateFlow()

    private fun rememberSearch(q: String) {
        val list = (listOf(q) + _recentSearches.value.filterNot { it.equals(q, true) }).take(MAX_RECENT_SEARCHES)
        _recentSearches.value = list
        searchPrefs.edit().putString("recent", list.joinToString("\n")).apply()
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        searchPrefs.edit().remove("recent").apply()
    }

    /** Typing only edits the text: nothing goes to the network until [submitSearch]. */
    fun onSearchQuery(query: String) {
        val current = capturePhase as? CapturePhase.Search ?: return
        cancelSearch()
        setPhase(current.copy(query = query, loading = false, results = emptyList(), error = null, searched = false))
    }

    /** Search button / keyboard action: one request for the finished query. */
    fun submitSearch() {
        val current = capturePhase as? CapturePhase.Search ?: return
        val q = current.query.trim()
        if (q.length < 2 || current.loading) return
        cancelSearch()
        rememberSearch(q)
        setPhase(current.copy(loading = true, error = null, searched = false))
        searchJob = viewModelScope.launch {
            val result = searchFoodByName(q)
            val now = capturePhase as? CapturePhase.Search ?: return@launch
            if (now.query.trim() != q) return@launch
            setPhase(
                result.fold(
                    onSuccess = { now.copy(loading = false, results = it, error = null, searched = true) },
                    onFailure = { now.copy(loading = false, results = emptyList(), error = UiText.of(R.string.addmeal_msg_search_failed), searched = true) }
                )
            )
        }
    }

    fun useRecentSearch(query: String) {
        val current = capturePhase as? CapturePhase.Search ?: return
        setPhase(current.copy(query = query, results = emptyList(), error = null, searched = false))
        submitSearch()
    }

    /** A search result was chosen: its default portion goes into the form (with the photo, if one was taken). */
    fun useSearchResult(product: FoodProduct) {
        cancelSearch()
        _uiState.update { it.copy(capture = null) }
        // Counts as used right away (even if the meal is discarded); saving the meal doesn't count it a second time.
        rememberedOnPick += product.barcode
        viewModelScope.launch { runCatching { savedProducts.remember(product) } }
        applyProduct(product)
    }

    /** Products already recorded as used when they were picked from a search. */
    private val rememberedOnPick = mutableSetOf<String>()

    /** Back to looking for a barcode (wrong product, or not found). */
    fun rescan() = setPhase(CapturePhase.Scanning)

    /** Leaves the camera for the form, keeping a scanned product if there is one (the "skip photo" path). */
    fun finishCapture() {
        val product = (capturePhase as? CapturePhase.ProductFound)?.product
        _uiState.update { it.copy(capture = null) }
        if (product != null) applyProduct(product)
    }

    /** A photo taken in the camera stage (or picked from the gallery there). */
    fun onCapturedPhotos(sources: List<Uri>) {
        val product = (capturePhase as? CapturePhase.ProductFound)?.product
        if (sources.isEmpty()) return
        viewModelScope.launch {
            val copied = sources.take(MAX_PHOTOS_PER_COURSE).mapNotNull { source ->
                val id = UUID.randomUUID().toString()
                fileStorageRepository.copyGalleryImageToPrivateStorage(source, id)
                    .onFailure { Log.e(TAG, "Failed to copy $source", it) }
                    .getOrNull()
                    ?.let { MealPhoto(id, it).also { unsavedPhotoIds += id } }
                    .also { if (source.scheme == "file") source.path?.let { path -> java.io.File(path).delete() } }
            }
            if (copied.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = UiText.of(R.string.addmeal_msg_photo_save_failed)) }
                return@launch
            }
            addPhotos(copied)
            if (product != null) {
                _uiState.update { it.copy(capture = null) }
                applyProduct(product)
            } else {
                // A photo without a barcode: next the user decides how the nutrients are found.
                setPhase(CapturePhase.PhotoTaken)
            }
        }
    }

    /** From the choice after a photo: let the AI estimate it (with the note typed there). */
    fun estimateWithAi() {
        _uiState.update { it.copy(capture = null) }
        analyzeMeal()
    }

    /** From the choice after a photo: use a product saved earlier (its serving is the default portion). */
    fun useSavedProduct(product: SavedProductEntity) {
        _uiState.update { it.copy(capture = null) }
        applyProduct(product.toProduct())
    }

    /** Star on a saved product in the picker. */
    fun toggleSavedFavorite(product: SavedProductEntity) {
        viewModelScope.launch { savedProducts.setFavorite(product.barcode, !product.isFavorite) }
    }

    /** Star on the scanned product's card: applied when the meal is saved. */
    fun toggleProductFavorite(barcode: String) {
        _uiState.update { it.copy(favoriteBarcodes = if (barcode in it.favoriteBarcodes) it.favoriteBarcodes - barcode else it.favoriteBarcodes + barcode) }
    }

    fun forgetSavedProduct(barcode: String) {
        viewModelScope.launch { savedProducts.forget(barcode) }
    }

    /** From the choice after a photo: throw the photo(s) away and look at the camera again. */
    fun retakePhoto() {
        _uiState.value.photos.forEach { onRemovePhoto(it.imageId) }
        setPhase(CapturePhase.Scanning)
    }

    /** Fills the form from a scanned product: one portion (its serving) whose weight the user can change. */
    private fun applyProduct(product: FoodProduct) {
        scannedProducts[product.barcode] = product
        viewModelScope.launch {
            if (savedProducts.isFavorite(product.barcode)) _uiState.update { it.copy(favoriteBarcodes = it.favoriteBarcodes + product.barcode) }
        }
        updateComponents { listOf(product.toComponent()) }
        _uiState.update {
            it.copy(
                description = if (it.description.isNullOrBlank()) product.name else it.description,
                dishName = null,
                reasoning = null
            )
        }
    }

    /** From the form: drop the scanned product and look for another. */
    fun rescanProduct() {
        _uiState.update {
            it.copy(
                components = emptyList(), carbohydrates = null, proteins = null, fats = null, calories = null,
                impactType = ImpactType.MEDIUM, impactAuto = true,
                description = if (it.components.firstOrNull()?.name == it.description) "" else it.description,
                capture = CaptureState()
            )
        }
    }

    fun onRemovePhoto(imageId: String) {
        // Files of saved photos are only deleted when the edit is saved, so cancelling keeps them.
        if (imageId in unsavedPhotoIds) {
            unsavedPhotoIds -= imageId
            viewModelScope.launch { fileStorageRepository.deleteImage(imageId) }
        }
        _uiState.update { s ->
            s.withoutEstimate().copy(photos = s.photos.filterNot { it.imageId == imageId })
        }
    }

    /** Estimates nutrition for all of this course's photos in one request. */
    fun analyzeMeal() {
        val photos = uiState.value.photos
        if (photos.isEmpty() || uiState.value.isAnalyzing || !_aiEnabled.value) return
        viewModelScope.launch {
            val notes = uiState.value.aiNotes
            // The note stays, so a failed request can be edited and resent (or a redo can reuse it).
            _uiState.update { it.copy(isAnalyzing = true) }

            analyzeMealUseCase(photos.map { it.uri }, notes)
                .onSuccess { analysis ->
                    if (analysis.components.isEmpty()) {
                        // Nothing usable: leave the form as it was instead of filling in "No food detected".
                        _uiState.update { it.copy(isAnalyzing = false, aiRequestOpen = true, snackbarMessage = UiText.of(R.string.addmeal_msg_ai_no_food)) }
                        return@onSuccess
                    }
                    val totals = analysis.totals
                    _uiState.update {
                        it.copy(
                            dishName = analysis.mealName,
                            // Re-analysing replaces a name the AI suggested before, but never one the user typed.
                            description = if (it.description.isNullOrBlank() || it.description == it.dishName) analysis.mealName else it.description,
                            components = analysis.components,
                            carbohydrates = totals.carbs,
                            proteins = totals.protein,
                            fats = totals.fat,
                            calories = totals.calories,
                            // Still "suggested": it follows later edits of the values until the user picks one.
                            impactType = analysis.impactType,
                            impactAuto = true,
                            reasoning = analysis.reasoning,
                            isAnalyzing = false,
                            aiRequestOpen = false,
                            snackbarMessage = UiText.of(R.string.addmeal_msg_ai_ready)
                        ).withSuggestedType()
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "Meal analysis failed", error)
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            aiRequestOpen = true,
                            snackbarMessage = UiText.of(R.string.addmeal_msg_ai_failed, error.message ?: error.javaClass.simpleName)
                        )
                    }
                }
        }
    }

    /** Changes a component's weight (its nutrients scale) and refreshes the totals from the components. */
    fun onComponentWeightChanged(index: Int, weightG: Double) = updateComponents { list ->
        list.mapIndexed { i, c -> if (i == index) c.withWeight(weightG) else c }
    }

    /** Drops typed totals and uses the sum of the foods again. */
    fun onUseFoodTotals() = updateComponents { it }

    /** Edits one food's macros (null = unchanged); its energy and the meal totals follow. */
    fun onComponentMacrosChanged(index: Int, carbs: Double? = null, protein: Double? = null, fat: Double? = null) = updateComponents { list ->
        list.mapIndexed { i, c ->
            if (i == index) c.withMacros(carbs ?: c.carbsG, protein ?: c.proteinG, fat ?: c.fatG) else c
        }
    }

    fun onComponentRemoved(index: Int) = updateComponents { list -> if (list.size <= 1) list else list.filterIndexed { i, _ -> i != index } }
    /** Edits to the components re-sum the nutrition totals (overwriting manually typed ones). */
    private fun updateComponents(change: (List<MealComponent>) -> List<MealComponent>) {
        _uiState.update {
            val components = change(it.components)
            val totals = components.totals()
            val next = it.copy(
                components = components,
                carbohydrates = if (components.isEmpty()) it.carbohydrates else totals.carbs,
                proteins = if (components.isEmpty()) it.proteins else totals.protein,
                fats = if (components.isEmpty()) it.fats else totals.fat,
                calories = if (components.isEmpty()) it.calories else totals.calories
            )
            (if (next.impactAuto) next.copy(impactType = inferImpactType(next.carbohydrates, next.proteins, next.fats)) else next).withSuggestedType()
        }
    }

    fun saveMeal() {
        val state = uiState.value
        if (!state.canSave || state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val original = editing
            val sittingId = if (original == null) targetSitting?.let { ensureSittingId(it) } else original.sittingId
            val result: Result<*> = if (original == null) {
                createMealUseCase(
                    photos = state.photos,
                    description = state.description?.trim(),
                    userId = userId,
                    sittingId = sittingId,
                    mealTimeUtc = state.mealTime ?: Instant.now().toEpochMilli(),
                    carbohydrates = state.carbohydrates ?: 0,
                    proteins = state.proteins,
                    fats = state.fats,
                    calories = state.totalCalories,
                    impactType = state.impactType,
                    mealType = state.mealType,
                    reasoning = state.reasoning,
                    components = state.components
                )
            } else {
                val edited = original.copy(
                    description = state.description?.trim(),
                    mealTimeUtc = state.mealTime ?: original.mealTimeUtc,
                    carbohydrates = state.carbohydrates ?: 0,
                    proteins = state.proteins,
                    fats = state.fats,
                    calories = state.totalCalories,
                    impactType = state.impactType,
                    mealType = state.mealType,
                    reasoning = state.reasoning,
                    components = state.components
                )
                updateMealUseCase(meal = edited, photos = state.photos)
            }

            if (result.isSuccess) {
                // A renamed meal carries its name on every course; an untouched default stays derived.
                val name = state.mealName.trim()
                val sitting = state.sitting
                if (sitting != null && sittingId != null && name.isNotEmpty() && name != targetSitting?.title && name != sittingTitleAtStart) {
                    mealRepository.setSittingName(sittingId, name)
                }
                unsavedPhotoIds.clear()
                // Scanned products are remembered so the next meal can pick them without scanning.
                state.components.mapNotNull { c ->
                    val code = c.barcode ?: return@mapNotNull null
                    scannedProducts[code] ?: c.toSavedProductOrNull(System.currentTimeMillis())?.toProduct()
                }.forEach { product ->
                    runCatching {
                        if (product.barcode !in rememberedOnPick) savedProducts.remember(product)
                        savedProducts.setFavorite(product.barcode, product.barcode in state.favoriteBarcodes)
                    }
                }
                _uiState.update {
                    it.copy(isLoading = false, finished = EditorResult.Saved(wasNew = original == null, addedCourse = targetSitting != null))
                }
            } else {
                Log.e(TAG, "Saving meal failed", result.exceptionOrNull())
                _uiState.update { it.copy(isLoading = false, snackbarMessage = UiText.of(R.string.addmeal_msg_save_failed)) }
            }
        }
    }

    /** Meals logged before courses existed have no sitting id yet: give their courses one now. */
    private suspend fun ensureSittingId(sitting: MealSitting): String {
        sitting.sittingId?.let { return it }
        val id = UUID.randomUUID().toString()
        sitting.courses.forEach { mealRepository.setSittingId(it.id, id) }
        return id
    }

    /** Soft-deletes the course being edited. */
    fun deleteMeal() {
        val id = editing?.id ?: return
        viewModelScope.launch {
            setMealValidUseCase(id, false)
                .onSuccess { _uiState.update { it.copy(finished = EditorResult.Deleted(id)) } }
                .onFailure { _uiState.update { s -> s.copy(snackbarMessage = UiText.of(R.string.addmeal_msg_delete_failed)) } }
        }
    }

    /** Shows the request card again (with the previous note) so the user can edit it and resend. */
    fun openAiRequest() {
        _uiState.update { it.copy(aiRequestOpen = true) }
    }

    fun onAiNotesChanged(notes: String) {
        _uiState.update { it.copy(aiNotes = notes) }
    }

    /** While the type is still auto-suggested: a snack for under 20 g of carbs, else by time of day. */
    private fun AddMealState.withSuggestedType(): AddMealState {
        if (!mealTypeAuto) return this
        val hour = java.time.Instant.ofEpochMilli(mealTime ?: System.currentTimeMillis()).atZone(java.time.ZoneId.systemDefault()).hour
        return copy(mealType = suggestMealType(hour, carbohydrates))
    }

    fun onMealNameChanged(name: String) {
        _uiState.update { it.copy(mealName = name) }
    }

    fun onDescriptionChanged(newDescription: String) {
        _uiState.update { it.copy(description = newDescription) }
    }

    fun onMealTimeChanged(newMealTime: LocalDateTime) {
        _uiState.update {
            it.copy(
                mealTime = localDateTimeToInstant(newMealTime).toEpochMilli(),
                mealType = if (it.mealTypeAuto) suggestMealType(newMealTime.hour, it.carbohydrates) else it.mealType
            )
        }
    }

    fun onCarbsChanged(newCarbs: String) = onMacroChanged { it.copy(carbohydrates = newCarbs.toIntOrNull()) }

    fun onProteinChanged(newProtein: String) = onMacroChanged { it.copy(proteins = newProtein.toIntOrNull()) }

    fun onFatChanged(newFat: String) = onMacroChanged { it.copy(fats = newFat.toIntOrNull()) }

    /** Applies a macro edit and, until the user picks an absorption speed themselves, re-suggests one. */
    private fun onMacroChanged(change: (AddMealState) -> AddMealState) {
        _uiState.update {
            val next = change(it)
            (if (next.impactAuto) next.copy(impactType = inferImpactType(next.carbohydrates, next.proteins, next.fats)) else next).withSuggestedType()
        }
    }

    fun onImpactTypeChanged(impactType: ImpactType) {
        _uiState.update { it.copy(impactType = impactType, impactAuto = false) }
    }

    fun onMealTypeChanged(mealType: MealType) {
        _uiState.update { it.copy(mealType = mealType, mealTypeAuto = false) }
    }

    fun consumeFinished() {
        _uiState.update { it.copy(finished = null) }
    }
}
