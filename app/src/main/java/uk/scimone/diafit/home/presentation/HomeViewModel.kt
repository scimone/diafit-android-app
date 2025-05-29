package uk.scimone.diafit.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.home.presentation.model.toCgmEntityUi
import uk.scimone.diafit.core.domain.model.CgmEntity

class HomeViewModel(
    private val cgmRepository: CgmRepository,
    private val userId: Int,
) : ViewModel() {

    private val latestCgm = MutableStateFlow<CgmEntity?>(null)

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        observeLatestCgm()
        startTicker()
    }

    private fun observeLatestCgm() {
        viewModelScope.launch {
            cgmRepository.getLatestCgm()
                .catch { e ->
                    _state.value = HomeState(
                        error = e.message,
                        isLoading = false
                    )
                }
                .collect { cgm ->
                    latestCgm.value = cgm
                }
        }
    }

    private fun startTicker() {
        viewModelScope.launch {
            while (true) {
                latestCgm.value?.let { cgm ->
                    _state.value = HomeState(
                        cgmUi = cgm.toCgmEntityUi(),
                        isLoading = false
                    )
                }
                delay(1000L) // Tick every second
            }
        }
    }
}
