package uk.scimone.diafit.journal.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.journal.presentation.components.JournalItem
import uk.scimone.diafit.journal.presentation.JournalViewModel
import java.text.SimpleDateFormat
import java.util.*


@Composable
fun JournalScreen(
    userId: String,
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
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.meals.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No meals found.")
                }
            }

            else -> {
                val groupedMeals = uiState.meals
                    .sortedByDescending { it.mealTimeUtc }
                    .groupBy { meal ->
                        SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
                            .format(Date(meal.mealTimeUtc))
                    }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    groupedMeals.forEach { (date, meals) ->
                        item {
                            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                meals.forEach { meal ->
                                    HorizontalDivider()
                                    JournalItem(meal)
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}

fun formatHeaderDate(rawDate: String): String {
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(rawDate)
        SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(parsed ?: Date())
    } catch (e: Exception) {
        rawDate // fallback
    }
}
