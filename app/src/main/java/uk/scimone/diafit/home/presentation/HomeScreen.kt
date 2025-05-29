package uk.scimone.diafit.home.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.home.presentation.components.ComponentRotatingArrowIcon
import uk.scimone.diafit.home.presentation.model.CgmEntityUi
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.White

@Composable
fun HomeScreen(
    userId: Int,
    viewModel: HomeViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator()
            }
            state.error != null -> {
                Text(
                    text = "Error: ${state.error}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            state.cgmUi != null -> {
                CgmDisplay(cgm = state.cgmUi!!)
            }
            else -> {
                Text("No CGM data available")
            }
        }
    }
}

@Composable
fun CgmDisplay(cgm: CgmEntityUi) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        )
        {
            Text(
                text = "${cgm.value}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    (cgm.value ?: 0) <= 70 -> BelowRange
                    (cgm.value ?: 0) >= 180 -> AboveRange
                    else -> White
                },
                textDecoration = if (cgm.isStale) TextDecoration.LineThrough else TextDecoration.None

            )
            ComponentRotatingArrowIcon(inputValue = cgm.rate)
        }
        Text(text = "${cgm.timeSince} ago")
    }
}
