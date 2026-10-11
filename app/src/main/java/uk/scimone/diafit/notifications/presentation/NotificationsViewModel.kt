package uk.scimone.diafit.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import uk.scimone.diafit.patterns.data.PatternStore
import uk.scimone.diafit.patterns.domain.PatternState
import uk.scimone.diafit.patterns.domain.StoredPattern
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.data.local.NotificationDao
import uk.scimone.diafit.core.domain.model.AppNotificationEntity
import uk.scimone.diafit.notifications.data.AppNotifier

class NotificationsViewModel(
    private val dao: NotificationDao,
    private val notifier: AppNotifier,
    patternStore: PatternStore
) : ViewModel() {
    /** Every current pattern (not resolved or dismissed) stays in the bell, opened or not; only unopened ones count toward the badge. */
    val patterns: StateFlow<List<StoredPattern>> =
        patternStore.observeAll().map { all -> all.filter { it.state != PatternState.RESOLVED && it.state != PatternState.DISMISSED }
                .sortedWith(compareByDescending<StoredPattern> { it.isUnreviewed }.thenByDescending { it.card.priority }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<AppNotificationEntity>> =
        dao.observeRecent(50).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCount: StateFlow<Int> =
        dao.observeUnreadCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Hides [n] from the list and clears its push; [restore] undoes it. */
    fun remove(n: AppNotificationEntity) {
        viewModelScope.launch(NonCancellable) { dao.setDeleted(n.id, true) }
        notifier.cancel(n.dedupeKey)
    }

    fun restore(n: AppNotificationEntity) {
        viewModelScope.launch(NonCancellable) { dao.setDeleted(n.id, false) }
    }

    fun markAllRead() {
        viewModelScope.launch(NonCancellable) { dao.markAllRead() }
    }
}
