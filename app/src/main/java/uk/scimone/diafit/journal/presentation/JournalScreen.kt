package uk.scimone.diafit.journal.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.journal.presentation.components.JournalItem
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import uk.scimone.diafit.core.domain.util.friendlyDateString


@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun JournalScreen(
    userId: Int,
    viewModel: JournalViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        val groupedMeals = uiState.meals
            .sortedByDescending { it.mealTimeUtc }
            .groupBy { meal ->
                friendlyDateString(meal.mealTimeUtc)
            }


        val isRefreshing = uiState.isLoading
        val refreshState = rememberPullToRefreshState()

        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refreshMeals,
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                isRefreshing && groupedMeals.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                groupedMeals.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No meals found.")
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        groupedMeals.forEach { (date, meals) ->
                            item {
                                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                    meals.forEach { meal ->
                                        JournalItem(meal)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Refresh indicator (optional but recommended)
            PullToRefreshDefaults.Indicator(
                isRefreshing = isRefreshing,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}