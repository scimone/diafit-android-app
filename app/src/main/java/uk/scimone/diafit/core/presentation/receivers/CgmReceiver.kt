package uk.scimone.diafit.core.presentation.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import uk.scimone.diafit.core.domain.repository.syncsource.IntentCgmSyncSource
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase

class CgmReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        private const val TAG = "CgmReceiver"
    }

    // Inject dependencies lazily via Koin
    private val getCgmSourceUseCase: GetCgmSourceUseCase by inject()
    private val jugglucoSource: IntentCgmSyncSource by inject(named("JUGGLUCO"))
    private val xdripSource: IntentCgmSyncSource by inject(named("XDRIP"))

    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentSource = getCgmSourceUseCase()
            val handled = when (currentSource) {
                CgmSource.JUGGLUCO -> jugglucoSource.handleIntent(intent)
                CgmSource.XDRIP -> xdripSource.handleIntent(intent)
                else -> {
                    Log.w(TAG, "Received intent for inactive or unsupported CGM source: $currentSource")
                    false
                }
            }

            if (!handled) {
                Log.w(TAG, "Intent was not handled: action=${intent.action}")
            }
        }
    }
}
