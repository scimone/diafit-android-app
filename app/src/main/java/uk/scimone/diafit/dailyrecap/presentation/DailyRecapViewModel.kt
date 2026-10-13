package uk.scimone.diafit.dailyrecap.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.repository.MealAnalysisRepository
import uk.scimone.diafit.dailyrecap.data.DailyRecapScheduler
import uk.scimone.diafit.dailyrecap.data.DailyRecapService
import uk.scimone.diafit.dailyrecap.data.DailyRecapStore
import uk.scimone.diafit.settings.domain.model.AiConnection
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import java.time.LocalDate

data class DailyRecapUiState(
    val aiEnabled: Boolean = true,
    val connections: List<AiConnection> = emptyList(),
    val models: List<String> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsError: String? = null,
    /** Which stored recap is shown (index into the newest-first list). */
    val selected: Int = 0,
    val generatingFor: LocalDate? = null,
    val error: String? = null,
    /** The JSON the model would be shown, once the user asked to see it. */
    val dataPreview: String? = null,
    val isLoadingPreview: Boolean = false
)

class DailyRecapViewModel(
    private val store: DailyRecapStore,
    private val service: DailyRecapService,
    private val scheduler: DailyRecapScheduler,
    private val settings: SettingsRepository,
    private val mealAnalysis: MealAnalysisRepository
) : ViewModel() {
    val config = store.config
    val recaps = store.recaps

    private val _state = MutableStateFlow(DailyRecapUiState())
    val state: StateFlow<DailyRecapUiState> = _state

    init { refreshConnections() }

    fun refreshConnections() {
        viewModelScope.launch {
            val connections = settings.getAiConnections()
            _state.update { it.copy(aiEnabled = settings.isAiEnabled(), connections = connections) }
        }
    }

    fun select(index: Int) = _state.update { it.copy(selected = index.coerceAtLeast(0)) }

    fun setEnabled(enabled: Boolean) {
        store.update { it.copy(enabled = enabled) }
        if (enabled) scheduler.scheduleNext() else scheduler.cancel()
    }

    fun setConnection(id: String) {
        store.update { it.copy(connectionId = id) }
        _state.update { it.copy(models = emptyList(), modelsError = null) }
    }

    fun setModel(model: String) = store.update { it.copy(model = model) }

    fun loadModels() {
        val s = _state.value
        val connection = s.connections.firstOrNull { it.id == config.value.connectionId } ?: s.connections.firstOrNull() ?: return
        _state.update { it.copy(isLoadingModels = true, modelsError = null) }
        viewModelScope.launch {
            mealAnalysis.listModels(connection)
                .onSuccess { models -> _state.update { it.copy(models = models, isLoadingModels = false) } }
                .onFailure { e -> _state.update { it.copy(modelsError = e.message, isLoadingModels = false) } }
        }
    }

    /** Writes the recap of [date] now (yesterday's, or today's so far as a test) and shows it; no push. */
    fun generate(date: LocalDate, notify: Boolean = false) {
        if (_state.value.generatingFor != null) return
        _state.update { it.copy(generatingFor = date, error = null) }
        viewModelScope.launch {
            val result = service.generate(date, notify)
            _state.update { st ->
                st.copy(
                    generatingFor = null,
                    error = result.exceptionOrNull()?.message ?: result.exceptionOrNull()?.javaClass?.simpleName,
                    selected = if (result.isSuccess) 0 else st.selected
                )
            }
        }
    }

    fun showData(date: LocalDate) {
        _state.update { it.copy(isLoadingPreview = true, dataPreview = null) }
        viewModelScope.launch {
            val text = runCatching { service.inputJson(service.previewInput(date)) }.getOrElse { it.message ?: "Could not read the data." }
            _state.update { it.copy(dataPreview = text, isLoadingPreview = false) }
        }
    }

    fun hideData() = _state.update { it.copy(dataPreview = null) }
}
