package uk.scimone.diafit.settings.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.domain.usecase.SetCgmSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.SetTargetRangeUseCase

class SettingsViewModel(
    private val getCgmSource: GetCgmSourceUseCase,
    private val setCgmSource: SetCgmSourceUseCase,
    private val getGlucoseTargetRange: GetTargetRangeUseCase,
    private val setGlucoseTargetRange: SetTargetRangeUseCase
) : ViewModel() {

    var state by mutableStateOf(SettingsState())
        private set

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            state = state.copy(isLoading = true)
            val source = getCgmSource()
            val range = getGlucoseTargetRange()
            state = state.copy(selectedCgmSource = source, glucoseTargetRange = range, isLoading = false)
        }
    }

    fun onSourceSelected(source: CgmSource) {
        viewModelScope.launch {
            setCgmSource(source)
            state = state.copy(selectedCgmSource = source)
        }
    }

    fun onGlucoseTargetRangeChanged(lower: Int, upper: Int) {
        viewModelScope.launch {
            val newRange = SettingsGlucoseTargetRange(lower, upper)
            setGlucoseTargetRange(newRange)
            state = state.copy(glucoseTargetRange = newRange)
            SettingsChangeBus.notifyChange()
        }
    }
}
