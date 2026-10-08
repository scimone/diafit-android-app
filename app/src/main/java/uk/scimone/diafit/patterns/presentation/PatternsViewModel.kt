package uk.scimone.diafit.patterns.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.presentation.model.toThresholds
import uk.scimone.diafit.patterns.domain.AgpPattern
import uk.scimone.diafit.patterns.domain.GlucosePatterns
import uk.scimone.diafit.patterns.domain.GetGlucosePatternsUseCase
import uk.scimone.diafit.patterns.domain.HourSpan
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase

data class PatternsState(
    val isLoading: Boolean = true,
    val result: GlucosePatterns? = null,
    val thresholds: GlucoseThresholds? = null,
    /** Pattern texts highlighted on the AGP: the notification's patterns at first, then whatever the user taps. */
    val selected: Set<String> = emptySet(),
    val errorMessage: String? = null
) {
    val highlights: List<HourSpan> get() = selected.mapNotNull { AgpPattern(it).highlight }.distinct()

    /** Patterns the notification named that the current data no longer shows. */
    val noLongerFound: List<String> get() = result?.let { r -> selected.filter { s -> r.patterns.none { it.text == s } } }.orEmpty()
}

class PatternsViewModel(
    private val getPatterns: GetGlucosePatternsUseCase,
    private val getTargetRange: GetTargetRangeUseCase,
    private val userId: Int,
    initialSelection: List<String>
) : ViewModel() {
    private val _state = MutableStateFlow(PatternsState(selected = initialSelection.toSet()))
    val state: StateFlow<PatternsState> = _state

    init {
        viewModelScope.launch {
            try {
                val thresholds = getTargetRange().toCore().toThresholds()
                val result = getPatterns(userId)
                _state.update { it.copy(isLoading = false, result = result, thresholds = thresholds) }
            } catch (e: Exception) {
                Log.e("Patterns", "Failed to compute patterns", e)
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to compute patterns") }
            }
        }
    }

    fun toggle(text: String) = _state.update { s ->
        s.copy(selected = if (text in s.selected) s.selected - text else s.selected + text)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }
}
