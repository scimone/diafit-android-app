package uk.scimone.diafit.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.home.presentation.model.toCgmEntityUi

class HomeViewModel(
    private val cgmRepository: CgmRepository,
    private val userId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        observeLatestCgm()
    }

    private fun observeLatestCgm() {
        viewModelScope.launch {
            cgmRepository.getLatestCgm()
                .map { cgm -> cgm.toCgmEntityUi() }
                .onEach { uiModel ->
                    _state.value = HomeState(
                        cgmUi = uiModel,
                        isLoading = false
                    )
                }
                .catch { e ->
                    _state.value = HomeState(
                        error = e.message,
                        isLoading = false
                    )
                }
                .collect()
        }
    }
}
