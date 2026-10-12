package uk.scimone.diafit.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.data.local.LlmLogDao
import uk.scimone.diafit.core.domain.model.LlmLogTotals
import uk.scimone.diafit.core.domain.model.LlmRequestLogEntity

/** The log of requests sent to LLM endpoints, newest first, with token totals. */
class AiLogViewModel(private val dao: LlmLogDao) : ViewModel() {
    val entries: StateFlow<List<LlmRequestLogEntity>> =
        dao.observeRecent(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totals: StateFlow<LlmLogTotals> =
        dao.observeTotals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LlmLogTotals(0, 0, 0))

    fun clear() { viewModelScope.launch { dao.clear() } }
}
