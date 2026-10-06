package uk.scimone.diafit.history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            days = state.days,
            canGoNewer = state.page > 0,
            onOlder = viewModel::showOlder,
            onNewer = viewModel::showNewer
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.days.isEmpty() -> Text(
                state.errorMessage ?: "No history yet",
                Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Column(Modifier.fillMaxSize()) {
                // Sticky chart placeholder (~30% of the page), full width.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.3f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Placeholder Chart",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    HistoryTimeAxis()
                    LazyColumn(Modifier.weight(1f)) {
                        items(state.days, key = { it.epochDay }) { day ->
                            DayTrackRow(day, state.thresholds)
                        }
                    }
                }
            }
        }
    }
    }
}

private val RANGE_FORMAT = DateTimeFormatter.ofPattern("d MMM")

/** Two-week window selector: back/forward arrows around the date range. */
@Composable
private fun PageHeader(days: List<DayHistoryUi>, canGoNewer: Boolean, onOlder: () -> Unit, onNewer: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOlder) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous two weeks") }
        val label = if (days.isEmpty()) "" else {
            val first = LocalDate.ofEpochDay(days.last().epochDay)
            val last = LocalDate.ofEpochDay(days.first().epochDay)
            "${first.format(RANGE_FORMAT)} – ${last.format(RANGE_FORMAT)}"
        }
        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onNewer, enabled = canGoNewer) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next two weeks") }
    }
}
