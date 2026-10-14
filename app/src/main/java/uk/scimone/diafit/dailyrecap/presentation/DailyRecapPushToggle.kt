package uk.scimone.diafit.dailyrecap.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.koin.compose.koinInject
import uk.scimone.diafit.R
import uk.scimone.diafit.dailyrecap.data.DailyRecapStore
import uk.scimone.diafit.dailyrecap.data.RecapNotifier
import uk.scimone.diafit.settings.presentation.ToggleChannelRow

/** The Notifications settings row for the recap's push (it always lands in the bell); tapping the text opens the sound settings. */
@Composable
fun DailyRecapPushToggle() {
    val store: DailyRecapStore = koinInject()
    val notifier: RecapNotifier = koinInject()
    val config by store.config.collectAsState()
    val context = LocalContext.current
    // The Android channel must exist before its settings can be opened.
    LaunchedEffect(Unit) { notifier.ensureChannel() }
    ToggleChannelRow(
        context, RecapNotifier.CHANNEL_ID, stringResource(R.string.recap_push_title), stringResource(R.string.recap_push_sub),
        config.push, onChecked = { push -> store.update { it.copy(push = push) } }
    )
}
