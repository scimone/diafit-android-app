package uk.scimone.diafit.home.presentation

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import java.util.UUID
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.usecase.GetAllCgmSinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetLatestCgmUseCase
import uk.scimone.diafit.core.domain.util.nowMinusXMinutes
import uk.scimone.diafit.home.presentation.model.toCgmEntityUi
import uk.scimone.diafit.home.presentation.model.toChartData
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.presentation.SettingsChangeBus

import kotlinx.coroutines.delay
import uk.scimone.diafit.core.domain.usecase.ObserveActivitySinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetAllBolusSinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetAllMealsSinceUseCase
import uk.scimone.diafit.core.domain.model.toSmbMarks
import uk.scimone.diafit.home.presentation.model.BolusChartData
import uk.scimone.diafit.home.presentation.model.CarbsChartData
import uk.scimone.diafit.home.presentation.model.toInsulinActivityChartData
import uk.scimone.diafit.home.presentation.model.toMealEntityUi

class HomeViewModel(
    private val getLatestCgmUseCase: GetLatestCgmUseCase,
    private val getAllCgmSinceUseCase: GetAllCgmSinceUseCase,
    private val getAllBolusSinceUseCase: GetAllBolusSinceUseCase,
    private val getTargetRangeUseCase: GetTargetRangeUseCase,
    private val observeActivitySinceUseCase: ObserveActivitySinceUseCase,
    private val getBasalTimelineUseCase: uk.scimone.diafit.core.domain.usecase.GetBasalTimelineUseCase,
    private val pumpEventRepository: uk.scimone.diafit.core.domain.repository.PumpEventRepository,
    private val settingsRepository: uk.scimone.diafit.settings.domain.repository.SettingsRepository,
    private val getAllMealsSinceUseCase: GetAllMealsSinceUseCase,
    private val application: Application,
    private val mealRepository: MealRepository,
    private val fileStorageRepository: FileStorageRepository,
    private val userId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState(isLoading = true))
    val state: StateFlow<HomeState> = _state.asStateFlow()

    // Store latest CgmEntity separately to update timeSince
    private var latestCgmEntity: CgmEntity? = null

    init {
        loadAllData()

        viewModelScope.launch {
            SettingsChangeBus.settingsChanged.collect {
                loadAllData()
            }
        }

        startCountdownUpdater()
    }

    private fun loadAllData() {
        observeLatestCgm()
        observeCgmHistory()
        observeBolusHistory()
        observeMealData()
        observeActivity()
        observeBasal()
        loadTargetRange()
        viewModelScope.launch { _state.update { it.copy(basalStyle = settingsRepository.getBasalStyle()) } }
    }

    private fun observeLatestCgm() {
        viewModelScope.launch {
            getLatestCgmUseCase(userId)
                .catch { e ->
                    _state.update {
                        it.copy(
                            error = e.message,
                            isLoading = false
                        )
                    }
                }
                .collect { cgm ->
                    latestCgmEntity = cgm
                    _state.update {
                        it.copy(
                            cgmUi = cgm?.toCgmEntityUi(),
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun observeCgmHistory(nowMinus24h: Long = nowMinusXMinutes(24 * 60)) {
        viewModelScope.launch {
            getAllCgmSinceUseCase(nowMinus24h, userId)
                .catch { e ->
                    _state.update {
                        it.copy(
                            error = e.message,
                            isLoading = false
                        )
                    }
                }
                .collect { history ->
                    val cgmChartDataList = history.map { it.toChartData() }
                    _state.update {
                        it.copy(
                            cgmHistory = cgmChartDataList,
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun observeBolusHistory(nowMinus24h: Long = nowMinusXMinutes(24 * 60)) {
        viewModelScope.launch {
            getAllBolusSinceUseCase(nowMinus24h, userId)
                .catch { e ->
                    _state.update {
                        it.copy(
                            error = e.message,
                            isLoading = false
                        )
                    }
                }
                .collect { bolusList ->
                    val bolusUiList = bolusList.map { it.toChartData() }
                    val insulinActivityList = bolusList.map { it.toInsulinActivityChartData() }

                    _state.update {
                        it.copy(
                            bolusHistory = bolusUiList,
                            smbs = bolusList.toSmbMarks(),
                            insulinActivityHistory = insulinActivityList,
                            isLoading = false
                        )
                    }
                }
        }
    }



    // Time the user is scrubbing on the charts; lives here so the app bar title can show that reading.
    private val _selectedTime = MutableStateFlow<Long?>(null)
    val selectedTime: StateFlow<Long?> = _selectedTime.asStateFlow()
    fun onSelectedTimeChange(time: Long?) { _selectedTime.value = time }

    // Image id of a camera capture in flight (the camera writes into the file behind the Uri we hand out).
    private var pendingCameraMealId: Int? = null
    private var pendingCameraImageId: String? = null

    /** Uri for the camera to write a photo for [mealId] into; call [onCameraPhotoResult] afterwards. */
    fun createCameraUriForMeal(mealId: Int): Uri {
        val imageId = UUID.randomUUID().toString()
        pendingCameraMealId = mealId
        pendingCameraImageId = imageId
        return fileStorageRepository.createImageUri(imageId)
    }

    fun onCameraPhotoResult(success: Boolean) {
        val mealId = pendingCameraMealId ?: return
        val imageId = pendingCameraImageId ?: return
        pendingCameraMealId = null
        pendingCameraImageId = null
        if (success) viewModelScope.launch { mealRepository.updateMealImage(mealId, imageId) }
    }

    fun attachGalleryPhoto(mealId: Int, sourceUri: Uri) {
        viewModelScope.launch {
            val imageId = UUID.randomUUID().toString()
            fileStorageRepository.copyGalleryImageToPrivateStorage(sourceUri, imageId)
                .onSuccess { mealRepository.updateMealImage(mealId, imageId) }
                .onFailure { Log.e("HomeViewModel", "Failed to copy photo for meal $mealId", it) }
        }
    }

    private fun observeMealData(nowMinus24h: Long = nowMinusXMinutes(24 * 60)) {
        viewModelScope.launch {
            getAllMealsSinceUseCase(nowMinus24h, userId)
                .catch { e ->
                    _state.update {
                        it.copy(
                            error = e.message,
                            isLoading = false
                        )
                    }
                }
                .collect { meals ->
                    val mealUiList = meals.map { it.toMealEntityUi(application.applicationContext) }
                    val carbUiList = mealUiList.map { it.toChartData() }
                    _state.update {
                        it.copy(
                            mealHistory = mealUiList,
                            carbHistory = carbUiList,
                            isLoading = false
                        )
                    }
                }
        }
    }

    /** Basal over the last 24 h; reloaded when a pump event arrives (profile switch, temp basal) and every minute. */
    private fun observeBasal() {
        basalJob?.cancel()
        basalJob = viewModelScope.launch {
            val ticks = flow { while (true) { emit(Unit); delay(60_000L) } }
            combine(pumpEventRepository.observeCount(userId), ticks) { _, _ -> }
                .catch { e -> Log.e("HomeViewModel", "Failed to observe basal", e) }
                .collect {
                    val now = System.currentTimeMillis()
                    val basal = runCatching { getBasalTimelineUseCase(now - 25 * 3_600_000L, now, userId) }
                        .onFailure { e -> Log.e("HomeViewModel", "Failed to load basal", e) }
                        .getOrDefault(emptyList())
                    _state.update { it.copy(basal = basal) }
                }
        }
    }

    private var basalJob: kotlinx.coroutines.Job? = null

    private fun observeActivity(nowMinus24h: Long = nowMinusXMinutes(24 * 60)) {
        viewModelScope.launch {
            val connected = settingsRepository.isActivityEnabled()
            _state.update { it.copy(activityConnected = connected) }
            observeActivitySinceUseCase(nowMinus24h, userId)
                .catch { e -> Log.e("HomeViewModel", "Failed to load activity data", e) }
                .collect { activity -> _state.update { it.copy(activity = activity) } }
        }
    }

    private fun loadTargetRange() {
        viewModelScope.launch {
            try {
                val targetRange = getTargetRangeUseCase().toCore()
                _state.update {
                    it.copy(
                        targetRangeLower = targetRange.lowerBound,
                        targetRangeUpper = targetRange.upperBound
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        targetRangeLower = 70,
                        targetRangeUpper = 180
                    )
                }
            }
        }
    }

    private fun startCountdownUpdater() {
        viewModelScope.launch {
            while (true) {
                latestCgmEntity?.let { cgm ->
                    _state.update {
                        it.copy(
                            cgmUi = cgm.toCgmEntityUi(), // this recalculates "timeSince"
                            isLoading = false
                        )
                    }
                }
                delay(1000L)  // update every second (adjust if needed)
            }
        }
    }
}
