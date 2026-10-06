package uk.scimone.diafit.settings.presentation

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.koin.androidx.compose.koinViewModel
import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.model.CgmSource

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onRequestIgnoreBatteryOptimizations: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

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

    if (!state.isBatteryOptimizationIgnored) {
        BatteryOptimizationWarningDialog(
            onDismiss = { /* optional: set flag in ViewModel if you want to suppress it */ },
            onRequestIgnore = onRequestIgnoreBatteryOptimizations
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        SettingsSection(title = "CGM data source", icon = Icons.Filled.Sensors) {
            CgmSource.values().forEach { source ->
                SelectableRow(
                    label = source.name,
                    selected = source == state.selectedCgmSource,
                    onClick = { viewModel.onCgmSourceSelected(source) }
                )
            }
        }

        SettingsSection(title = "Bolus data source", icon = Icons.Filled.Vaccines) {
            BolusSource.values().forEach { source ->
                SelectableRow(
                    label = source.name,
                    selected = source == state.selectedBolusSource,
                    onClick = { viewModel.onBolusSourceSelected(source) }
                )
            }
        }

        SettingsSection(title = "Glucose target range (mg/dL)", icon = Icons.Filled.GpsFixed) {
            GlucoseTargetRangeInput(
                lower = state.glucoseTargetRange.lowerBound,
                upper = state.glucoseTargetRange.upperBound,
                onRangeChanged = { lower, upper -> viewModel.onGlucoseTargetRangeChanged(lower, upper) }
            )
        }

        SettingsSection(title = "Nightscout", icon = Icons.Filled.CloudQueue) {
            ServerConfigInput(
                baseUrl = state.nightscoutConfig.baseUrl,
                apiKey = state.nightscoutConfig.apiKey,
                baseUrlLabel = "Base URL",
                apiKeyLabel = "API secret",
                onConfigChanged = { baseUrl, apiKey -> viewModel.onNightscoutConfigChanged(baseUrl, apiKey) }
            )
        }

        SettingsSection(title = "AI meal analysis", icon = Icons.Filled.Psychology) {
            Text(
                "OpenAI-compatible endpoint used to analyze meal photos for nutrient estimates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            ServerConfigInput(
                baseUrl = state.aiConfig.baseUrl,
                apiKey = state.aiConfig.apiKey,
                baseUrlLabel = "Base URL",
                apiKeyLabel = "API key",
                onConfigChanged = { baseUrl, apiKey -> viewModel.onAiConfigChanged(baseUrl, apiKey) }
            )
        }

        SettingsSection(title = "Background reliability", icon = Icons.Filled.BatteryChargingFull) {
            BackgroundReliabilityStatus(
                isBatteryOptimizationIgnored = state.isBatteryOptimizationIgnored,
                onRequestIgnoreBatteryOptimizations = onRequestIgnoreBatteryOptimizations
            )

            if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Xiaomi/MIUI devices can still kill background sync overnight even with battery optimization disabled. Enable Autostart for Diafit too:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { openXiaomiAutostartSettings(context) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Autostart settings")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun openXiaomiAutostartSettings(context: Context) {
    try {
        val intent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "Couldn't open Autostart settings automatically — look for \"Autostart\" under Settings > Apps > Diafit > Permissions instead.",
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun BackgroundReliabilityStatus(
    isBatteryOptimizationIgnored: Boolean,
    onRequestIgnoreBatteryOptimizations: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isBatteryOptimizationIgnored) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (isBatteryOptimizationIgnored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (isBatteryOptimizationIgnored) "Battery optimization excluded" else "Battery optimization is still restricting background sync",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    if (!isBatteryOptimizationIgnored) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRequestIgnoreBatteryOptimizations) {
            Text("Exclude from battery optimization")
        }
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
fun ServerConfigInput(
    baseUrl: String,
    apiKey: String,
    baseUrlLabel: String,
    apiKeyLabel: String,
    onConfigChanged: (String, String) -> Unit
) {
    var baseUrlText by remember(baseUrl) { mutableStateOf(baseUrl) }
    var apiKeyText by remember(apiKey) { mutableStateOf(apiKey) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = baseUrlText,
        onValueChange = {
            baseUrlText = it
            onConfigChanged(it, apiKeyText)
        },
        label = { Text(baseUrlLabel) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = apiKeyText,
        onValueChange = {
            apiKeyText = it
            onConfigChanged(baseUrlText, it)
        },
        label = { Text(apiKeyLabel) },
        singleLine = true,
        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                Icon(
                    imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (apiKeyVisible) "Hide $apiKeyLabel" else "Show $apiKeyLabel"
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = lowerText,
            onValueChange = {
                lowerText = it
                val lowerInt = it.toIntOrNull()
                val upperInt = upperText.toIntOrNull()
                if (lowerInt != null && upperInt != null) onRangeChanged(lowerInt, upperInt)
            },
            label = { Text("Lower") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = upperText,
            onValueChange = {
                upperText = it
                val lowerInt = lowerText.toIntOrNull()
                val upperInt = it.toIntOrNull()
                if (lowerInt != null && upperInt != null) onRangeChanged(lowerInt, upperInt)
            },
            label = { Text("Upper") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
}
