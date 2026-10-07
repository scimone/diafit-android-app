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
import androidx.compose.material.icons.filled.MonitorHeart
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
import androidx.compose.ui.graphics.Color
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import org.koin.androidx.compose.koinViewModel
import uk.scimone.diafit.core.data.healthconnect.HealthConnectAvailability
import uk.scimone.diafit.core.data.healthconnect.HealthConnectPermissions
import uk.scimone.diafit.core.data.healthconnect.HealthConnectSyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
                // Permissions can be changed in Health Connect itself.
                viewModel.refreshHealthConnect()
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

    // The permission dialog is Health Connect's own; the CGM one is only asked for when that source is picked.
    val activityPermissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.onActivityPermissionsResult()
    }
    val glucosePermissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.onGlucosePermissionResult()
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
                val healthConnect = source == CgmSource.HEALTH_CONNECT
                SelectableRow(
                    label = if (healthConnect) source.displayName else source.name,
                    caption = if (healthConnect) "Blood glucose another app wrote to Health Connect" else null,
                    selected = source == state.selectedCgmSource,
                    enabled = !healthConnect || state.healthConnect.availability == HealthConnectAvailability.AVAILABLE,
                    onClick = {
                        // Health Connect needs its own read permission for blood glucose first.
                        if (healthConnect && !state.healthConnect.glucoseGranted) {
                            glucosePermissions.launch(HealthConnectPermissions.glucose + HealthConnectPermissions.BACKGROUND)
                        } else {
                            viewModel.onCgmSourceSelected(source)
                        }
                    }
                )
            }
        }

        SettingsSection(title = "Health Connect", icon = Icons.Filled.MonitorHeart) {
            HealthConnectCard(
                state = state.healthConnect,
                onConnect = { activityPermissions.launch(HealthConnectPermissions.activity + HealthConnectPermissions.BACKGROUND) },
                onSyncNow = { viewModel.syncHealthConnectNow() },
                onBackfill = { viewModel.syncHealthConnectNow(backfill = true) },
                onDisconnect = viewModel::disconnectHealthConnect,
                onInstall = { runCatching { context.startActivity(viewModel.healthConnectStoreIntent()) } },
                onOpenHealthConnect = { runCatching { context.startActivity(viewModel.healthConnectSettingsIntent()) } }
            )
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
            Spacer(modifier = Modifier.height(12.dp))
            ModelPicker(
                model = state.aiConfig.model,
                models = state.aiModels,
                isLoading = state.isLoadingAiModels,
                error = state.aiModelsError,
                onModelChanged = viewModel::onAiModelChanged,
                onLoadModels = viewModel::loadAiModels
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
private fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit, caption: String? = null, enabled: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(label, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Connect / status / sync controls for Health Connect (heart rate, steps, sleep, exercise). */
@Composable
private fun HealthConnectCard(
    state: HealthConnectUiState,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onBackfill: () -> Unit,
    onDisconnect: () -> Unit,
    onInstall: () -> Unit,
    onOpenHealthConnect: () -> Unit
) {
    Text(
        "Imports heart rate, steps, sleep and exercise from your watch or phone and shows them under Activity on Home and History.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    when (state.availability) {
        HealthConnectAvailability.UNAVAILABLE, HealthConnectAvailability.UPDATE_REQUIRED -> {
            val update = state.availability == HealthConnectAvailability.UPDATE_REQUIRED
            Text(
                if (update) "Health Connect needs to be updated before Diafit can use it." else "Health Connect isn't available on this device.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onInstall) { Text(if (update) "Update Health Connect" else "Get Health Connect") }
        }
        HealthConnectAvailability.AVAILABLE -> {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Heart rate", "Steps", "Sleep", "Exercise").forEach {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(it, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = if (state.connected) {
                            { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                        } else null
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!state.connected) {
                if (state.enabled && !state.activityGranted) {
                    Text(
                        "Permission was withdrawn in Health Connect. Allow it again to keep importing.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick = onConnect) { Text("Connect Health Connect") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "The last 14 days are imported when you connect, then new data every 15 minutes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val sync = state.sync
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (sync) {
                        is HealthConnectSyncStatus.Syncing -> {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Importing… ${(sync.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                        }
                        is HealthConnectSyncStatus.Failed -> {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import failed: ${sync.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                        HealthConnectSyncStatus.Idle -> {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                state.lastSync?.let { "Connected · last import ${formatSyncTime(it)}" } ?: "Connected · waiting for the first import",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                state.summary?.let { sum ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Found in Health Connect (last ${sum.days} days): ${"%,d".format(sum.heartRate)} heart-rate readings · ${"%,d".format(sum.steps)} step periods · ${sum.sleep} sleep sessions · ${sum.exercise} workouts",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val missing = listOfNotNull(
                        "heart rate".takeIf { sum.heartRate == 0 }, "steps".takeIf { sum.steps == 0 },
                        "sleep".takeIf { sum.sleep == 0 }, "workouts".takeIf { sum.exercise == 0 }
                    )
                    if (missing.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        // Red only when nothing at all came through; one missing type is often just "no such data" (no workouts logged).
                        Text(
                            "No ${missing.joinToString(", ")} data found. Diafit shows what other apps (your watch or fitness app) write to Health Connect; see Manage in Health Connect → Data and access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (missing.size == 4) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!state.backgroundGranted) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Background access isn't allowed, so data is only imported while Diafit is open. You can allow it in Health Connect.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSyncNow, enabled = sync !is HealthConnectSyncStatus.Syncing) { Text("Sync now") }
                    OutlinedButton(onClick = onBackfill, enabled = sync !is HealthConnectSyncStatus.Syncing) { Text("Re-import 2 weeks") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onOpenHealthConnect) { Text("Manage in Health Connect") }
                    TextButton(onClick = onDisconnect) { Text("Disconnect") }
                }
            }
        }
    }
}

private fun formatSyncTime(time: Long): String {
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).let { it.format(Date(time)) == it.format(Date()) }
    return SimpleDateFormat(if (sameDay) "HH:mm" else "d MMM HH:mm", Locale.getDefault()).format(Date(time))
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

/** Editable model field with a dropdown of the models the endpoint reports (`GET /models`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPicker(
    model: String,
    models: List<String>,
    isLoading: Boolean,
    error: String?,
    onModelChanged: (String) -> Unit,
    onLoadModels: () -> Unit
) {
    var text by remember(model) { mutableStateOf(model) }
    var expanded by remember { mutableStateOf(false) }
    // Show everything when the field holds an already-chosen model, otherwise filter as you type.
    val filtered = remember(models, text) {
        if (text in models) models else models.filter { it.contains(text, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onModelChanged(it)
                expanded = true
            },
            label = { Text("Model") },
            singleLine = true,
            isError = error != null,
            supportingText = {
                Text(error ?: if (models.isEmpty()) "Open the dropdown to load available models, or type one." else "${models.size} models available")
            },
            trailingIcon = {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(expanded = expanded && filtered.isNotEmpty(), onDismissRequest = { expanded = false }) {
            filtered.forEach { id ->
                DropdownMenuItem(
                    text = { Text(id) },
                    onClick = {
                        text = id
                        onModelChanged(id)
                        expanded = false
                    }
                )
            }
        }
    }
    // Fetch lazily the first time the user opens the dropdown.
    LaunchedEffect(expanded) {
        if (expanded && models.isEmpty() && !isLoading) onLoadModels()
    }
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
