package uk.scimone.diafit.settings.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.SetCgmSourceUseCase

class SettingsViewModel(
    private val getCgmSource: GetCgmSourceUseCase,
    private val setCgmSource: SetCgmSourceUseCase
) : ViewModel() {

    var state by mutableStateOf(SettingsState())
        private set

    init {
        loadCurrentSource()
    }

    private fun loadCurrentSource() {
        viewModelScope.launch {
            state = state.copy(isLoading = true)
            val source = getCgmSource()
            state = state.copy(selectedSource = source, isLoading = false)
        }
    }

    fun onSourceSelected(source: CgmSource) {
        viewModelScope.launch {
            setCgmSource(source)
            state = state.copy(selectedSource = source)
        }
    }
}
