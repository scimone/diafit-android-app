package uk.scimone.diafit.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.data.local.NotificationDao
import uk.scimone.diafit.core.domain.model.AppNotificationEntity

class NotificationsViewModel(private val dao: NotificationDao) : ViewModel() {
    val recent: StateFlow<List<AppNotificationEntity>> =
        dao.observeRecent(50).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCount: StateFlow<Int> =
        dao.observeUnreadCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markAllRead() {
        viewModelScope.launch(NonCancellable) { dao.markAllRead() }
    }
}
