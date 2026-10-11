package uk.scimone.diafit.insights.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.presentation.model.toThresholds
import uk.scimone.diafit.insights.data.InsightStore
import uk.scimone.diafit.insights.domain.StoredInsight
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase

/** The insight list and the unread badge (one instance for the whole activity, so the menu badge and the page agree). */
class InsightsViewModel(private val store: InsightStore) : ViewModel() {
    val insights: StateFlow<List<StoredInsight>?> =
        store.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val unreadCount: StateFlow<Int> =
        store.observeUnreadCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markAllRead() { viewModelScope.launch(NonCancellable) { store.markAllRead() } }
}

/** One insight's page: opening it counts as reviewing it. */
class InsightDetailViewModel(
    private val store: InsightStore,
    private val getTargetRange: GetTargetRangeUseCase,
    private val id: String
) : ViewModel() {
    val insight: StateFlow<StoredInsight?> = store.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _thresholds = MutableStateFlow<GlucoseThresholds?>(null)
    val thresholds: StateFlow<GlucoseThresholds?> = _thresholds

    init {
        viewModelScope.launch {
            store.markRead(id)
            try { _thresholds.value = getTargetRange().toCore().toThresholds() } catch (e: Exception) { Log.e("Insights", "No target range", e) }
        }
    }

    fun dismiss() { viewModelScope.launch(NonCancellable) { store.dismiss(id) } }
    fun restore() { viewModelScope.launch(NonCancellable) { store.restore(id) } }
}
