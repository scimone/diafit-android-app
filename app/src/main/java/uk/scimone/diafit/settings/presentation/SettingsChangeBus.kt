package uk.scimone.diafit.settings.presentation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object SettingsChangeBus {
    private val _settingsChanged = MutableSharedFlow<Unit>(replay = 0)
    val settingsChanged: SharedFlow<Unit> = _settingsChanged

    suspend fun notifyChange() {
        _settingsChanged.emit(Unit)
    }
}