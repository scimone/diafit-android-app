package uk.scimone.diafit.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uk.scimone.diafit.settings.domain.model.CgmSource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import uk.scimone.diafit.settings.domain.model.BolusSource


@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onRequestIgnoreBatteryOptimizations: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()


    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkBatteryOptimization()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Battery warning dialog
    if (!state.isBatteryOptimizationIgnored) {
        BatteryOptimizationWarningDialog(
            onDismiss = { /* optional: set flag in ViewModel if you want to suppress it */ },
            onRequestIgnore = onRequestIgnoreBatteryOptimizations
        )
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Text("CGM Data Source", style = MaterialTheme.typography.titleMedium)

        CgmSource.values().forEach { source ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onCgmSourceSelected(source) }
                    .padding(vertical = 8.dp)
            ) {
                RadioButton(
                    selected = source == state.selectedCgmSource,
                    onClick = { viewModel.onCgmSourceSelected(source) }
                )
                Text(
                    text = source.name,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Bolus Data Source", style = MaterialTheme.typography.titleMedium)
        BolusSource.values().forEach { source ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onBolusSourceSelected(source) }
                    .padding(vertical = 8.dp)
            ) {
                RadioButton(
                    selected = source == state.selectedBolusSource,
                    onClick = { viewModel.onBolusSourceSelected(source) }
                )
                Text(
                    text = source.name,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Text("Battery Optimization ignored: ${state.isBatteryOptimizationIgnored}", modifier = Modifier.padding(vertical = 8.dp))

        Spacer(modifier = Modifier.height(24.dp))

        Text("Glucose Target Range (mg/dL)", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(8.dp))

        GlucoseTargetRangeInput(
            lower = state.glucoseTargetRange.lowerBound,
            upper = state.glucoseTargetRange.upperBound,
            onRangeChanged = { lower, upper -> viewModel.onGlucoseTargetRangeChanged(lower, upper) }
        )
    }
}

@Composable
fun BatteryOptimizationWarningDialog(
    onDismiss: () -> Unit,
    onRequestIgnore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Battery Optimization") },
        text = { Text("Battery optimizations may prevent background CGM syncing. Please exclude the app from battery optimization to ensure proper functionality.") },
        confirmButton = {
            TextButton(onClick = {
                onRequestIgnore()
                onDismiss()
            }) {
                Text("Go to Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
fun GlucoseTargetRangeInput(
    lower: Int,
    upper: Int,
    onRangeChanged: (Int, Int) -> Unit
) {
    var lowerText by remember { mutableStateOf(lower.toString()) }
    var upperText by remember { mutableStateOf(upper.toString()) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("Lower:", modifier = Modifier.width(60.dp))
            TextField(
                value = lowerText,
                onValueChange = {
                    lowerText = it
                    val lowerInt = it.toIntOrNull()
                    val upperInt = upperText.toIntOrNull()
                    if (lowerInt != null && upperInt != null) {
                        onRangeChanged(lowerInt, upperInt)
                    }
                },
                modifier = Modifier.width(100.dp),
                singleLine = true
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("Upper:", modifier = Modifier.width(60.dp))
            TextField(
                value = upperText,
                onValueChange = {
                    upperText = it
                    val lowerInt = lowerText.toIntOrNull()
                    val upperInt = it.toIntOrNull()
                    if (lowerInt != null && upperInt != null) {
                        onRangeChanged(lowerInt, upperInt)
                    }
                },
                modifier = Modifier.width(100.dp),
                singleLine = true
            )
        }
    }
}
