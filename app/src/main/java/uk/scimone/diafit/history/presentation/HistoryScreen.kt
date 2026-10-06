package uk.scimone.diafit.history.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.history.presentation.components.DayTrackRow
import uk.scimone.diafit.history.presentation.components.HistoryTimeAxis

@Composable
fun HistoryScreen(
    userId: Int,
    viewModel: HistoryViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val state by viewModel.state.collectAsState()

    Box(Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.days.isEmpty() -> Text(
                state.errorMessage ?: "No history yet",
                Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                LazyColumn(Modifier.weight(1f)) {
                    items(state.days, key = { it.epochDay }) { day ->
                        DayTrackRow(day, state.thresholds)
                    }
                }
                HistoryTimeAxis()
            }
        }
    }
}
