package uk.scimone.diafit.dailyrecap.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import uk.scimone.diafit.R
import uk.scimone.diafit.dailyrecap.data.DailyRecapStore

/** The Notifications settings row for the recap's push (it always lands in the bell, the push is optional). */
@Composable
fun DailyRecapPushToggle() {
    val store: DailyRecapStore = koinInject()
    val config by store.config.collectAsState()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.recap_push_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.recap_push_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = config.push, onCheckedChange = { push -> store.update { it.copy(push = push) } })
    }
}
