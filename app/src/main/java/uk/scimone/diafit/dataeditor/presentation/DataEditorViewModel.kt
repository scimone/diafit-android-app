package uk.scimone.diafit.dataeditor.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.dataeditor.data.DataEditorRepository
import uk.scimone.diafit.dataeditor.data.EditResult
import uk.scimone.diafit.dataeditor.data.EditableRecord
import uk.scimone.diafit.dataeditor.data.RecordKind
import java.time.LocalDate
import java.time.ZoneId

data class DataEditorState(
    val kind: RecordKind = RecordKind.GLUCOSE,
    val day: LocalDate = LocalDate.now(),
    val records: List<EditableRecord> = emptyList(),
    val loading: Boolean = true,
    /** A record that was just deleted, offered for undo. */
    val undo: EditableRecord? = null,
    /** One-off text for a snackbar (errors); cleared by [DataEditorViewModel.messageShown]. */
    val message: String? = null
)

class DataEditorViewModel(private val userId: Int, private val repository: DataEditorRepository) : ViewModel() {
    private val _state = MutableStateFlow(DataEditorState())
    val state: StateFlow<DataEditorState> = _state.asStateFlow()

    init { reload() }

    fun selectKind(kind: RecordKind) { _state.update { it.copy(kind = kind, records = emptyList(), loading = true) }; reload() }

    fun selectDay(day: LocalDate) {
        _state.update { it.copy(day = minOf(day, LocalDate.now()), records = emptyList(), loading = true) }
        reload()
    }

    fun shiftDay(days: Long) = selectDay(_state.value.day.plusDays(days))

    private fun reload() {
        val s = _state.value
        val zone = ZoneId.systemDefault()
        val start = s.day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = s.day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        viewModelScope.launch {
            val rows = repository.load(s.kind, start, end, userId)
            // Ignore a slow answer for a kind or day the user has already left.
            _state.update { if (it.kind == s.kind && it.day == s.day) it.copy(records = rows, loading = false) else it }
        }
    }

    /** [onDone] gets true when saved. */
    fun save(record: EditableRecord, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (val r = repository.save(record)) {
                EditResult.Saved -> { reload(); onDone(true) }
                is EditResult.Failed -> { _state.update { it.copy(message = r.message) }; onDone(false) }
            }
        }
    }

    fun delete(record: EditableRecord) {
        viewModelScope.launch {
            repository.delete(record)
            _state.update { it.copy(undo = record) }
            reload()
        }
    }

    fun undoDelete() {
        val record = _state.value.undo ?: return
        viewModelScope.launch {
            repository.restore(record)
            _state.update { it.copy(undo = null) }
            reload()
        }
    }

    fun undoShown() { _state.update { it.copy(undo = null) } }
    fun messageShown() { _state.update { it.copy(message = null) } }
}
