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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Tune
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
import uk.scimone.diafit.settings.domain.model.Connector
import uk.scimone.diafit.settings.domain.model.DataType
import uk.scimone.diafit.settings.domain.model.connectorsProviding

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

    // Health Connect's own dialog; glucose and activity are asked for together.
    val healthConnectPermissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.onHealthConnectPermissionsResult()
    }
    val historyPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refreshHealthConnect()
    }
    val connectHealthConnect = {
        healthConnectPermissions.launch(
            HealthConnectPermissions.activity + HealthConnectPermissions.glucose + HealthConnectPermissions.BACKGROUND + HealthConnectPermissions.HISTORY
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

        SettingsSection(title = "Connectors", icon = Icons.Filled.Hub) {
            Text(
                "Connect the apps and services Diafit should get data from. You can connect as many as you like, then choose below which one feeds each kind of data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Connector.values().forEach { connector ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                val enabled = connector in state.enabledConnectors
                val unavailable = connector == Connector.HEALTH_CONNECT &&
                    state.healthConnect.availability != HealthConnectAvailability.AVAILABLE
                ConnectorHeader(
                    connector = connector,
                    enabled = enabled,
                    switchEnabled = !unavailable || enabled,
                    onToggle = { on ->
                        when {
                            connector == Connector.HEALTH_CONNECT && on -> connectHealthConnect()
                            connector == Connector.HEALTH_CONNECT -> viewModel.disconnectHealthConnect()
                            else -> viewModel.onConnectorToggled(connector, on)
                        }
                    }
                )
                if (connector == Connector.HEALTH_CONNECT && (enabled || unavailable)) {
                    Spacer(Modifier.height(8.dp))
                    HealthConnectCard(
                        state = state.healthConnect,
                        wanted = DataType.ACTIVITY.filter { state.selections[it] == Connector.HEALTH_CONNECT }.toSet(),
                        onConnect = connectHealthConnect,
                        onSyncNow = { viewModel.syncHealthConnectNow() },
                        onBackfill = { viewModel.syncHealthConnectNow(backfill = true) },
                        onInstall = { runCatching { context.startActivity(viewModel.healthConnectStoreIntent()) } },
                        onOpenHealthConnect = { runCatching { context.startActivity(viewModel.healthConnectSettingsIntent()) } }
                    )
                } else if (enabled) {
                    Spacer(Modifier.height(8.dp))
                    when (connector) {
                        Connector.NIGHTSCOUT -> NightscoutConnectorSetup(state, viewModel)
                        Connector.AAPS -> SetupHint("In AAPS, turn on its data broadcast so Diafit receives treatments (Config Builder → Sync → NSClient, and the Data broadcaster plugin for temp basals). Diafit doesn't need a login.")
                        Connector.XDRIP -> SetupHint("In xDrip+: Settings → Inter-app settings → Broadcast locally. Diafit then receives every reading; no login needed.")
                        Connector.JUGGLUCO -> SetupHint("In Juggluco: Settings → Data exchange → Glucodata broadcast, then switch on Diafit in the list of apps. No login needed.")
                        else -> Unit
                    }
                }
            }
        }

        SettingsSection(title = "Data sources", icon = Icons.Filled.Tune) {
            Text(
                "Choose which connector feeds each kind of data, or switch it off if you don't want it in Diafit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            DataSourcesList(
                state = state,
                onSelect = viewModel::onSelectionChanged,
                onBackfill = viewModel::startBackfill,
                onDismissBackfill = viewModel::dismissBackfill,
                missingRanges = viewModel::missingRanges,
                onAllowHistory = { historyPermission.launch(setOf(HealthConnectPermissions.HISTORY)) }
            )
        }

        SettingsSection(title = "Glucose target range (mg/dL)", icon = Icons.Filled.GpsFixed) {
            GlucoseTargetRangeInput(
                lower = state.glucoseTargetRange.lowerBound,
                upper = state.glucoseTargetRange.upperBound,
                onRangeChanged = { lower, upper -> viewModel.onGlucoseTargetRangeChanged(lower, upper) }
            )
        }

        SettingsSection(title = "Basal chart", icon = Icons.AutoMirrored.Filled.ShowChart) {
            uk.scimone.diafit.settings.domain.model.BasalStyle.values().forEach { style ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.onBasalStyleChanged(style) }
                ) {
                    RadioButton(selected = state.basalStyle == style, onClick = { viewModel.onBasalStyleChanged(style) })
                    Column(Modifier.weight(1f)) {
                        Text(style.label, style = MaterialTheme.typography.bodyLarge)
                        Text(style.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SettingsSection(title = "Device lifetimes", icon = Icons.Filled.Sensors) {
            uk.scimone.diafit.devices.presentation.DeviceLifetimeSettings()
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

        if (uk.scimone.diafit.backendsync.BackendSyncFeature.ENABLED) {
            SettingsSection(title = "Backend sync", icon = Icons.Filled.CloudUpload) {
                uk.scimone.diafit.backendsync.presentation.BackendSyncSettings()
            }
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
    wanted: Set<DataType>,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onBackfill: () -> Unit,
    onInstall: () -> Unit,
    onOpenHealthConnect: () -> Unit
) {
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
                DataType.ACTIVITY.forEach { type ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(type.label, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = if (state.connected && type in wanted) {
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
                Button(onClick = onConnect) { Text("Allow access") }
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
                        "heart rate".takeIf { DataType.HEART_RATE in wanted && sum.heartRate == 0 },
                        "steps".takeIf { DataType.STEPS in wanted && sum.steps == 0 },
                        "sleep".takeIf { DataType.SLEEP in wanted && sum.sleep == 0 },
                        "workouts".takeIf { DataType.EXERCISE in wanted && sum.exercise == 0 }
                    )
                    if (missing.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        // Red only when nothing at all came through; one missing type is often just "no such data" (no workouts logged).
                        Text(
                            "No ${missing.joinToString(", ")} data found. Diafit shows what other apps (your watch or fitness app) write to Health Connect; see Manage in Health Connect → Data and access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (missing.size == wanted.size) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
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
                TextButton(onClick = onOpenHealthConnect) { Text("Manage in Health Connect") }
            }
        }
    }
}

@Composable
private fun ConnectorHeader(connector: Connector, enabled: Boolean, switchEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(connector.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(connector.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = onToggle, enabled = switchEnabled)
    }
}

@Composable
private fun SetupHint(text: String) {
    var shown by remember { mutableStateOf(false) }
    TextButton(onClick = { shown = !shown }, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Text(if (shown) "Hide setup tips" else "Setup tips", style = MaterialTheme.typography.labelMedium)
    }
    if (shown) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NightscoutConnectorSetup(state: SettingsState, viewModel: SettingsViewModel) {
    // null = follow the saved config (collapsed once an address is set); true once the user is editing.
    var editing by remember { mutableStateOf<Boolean?>(null) }
    val open = editing ?: state.nightscoutConfig.baseUrl.isBlank()
    val check = state.nightscoutCheck
    LaunchedEffect(check) { if (check == NightscoutCheckState.Ok) editing = false }

    if (!open) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.nightscoutConfig.baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            when (check) {
                NightscoutCheckState.Ok -> Icon(Icons.Filled.CheckCircle, contentDescription = "Connected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                NightscoutCheckState.Checking -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else -> TextButton(onClick = viewModel::testNightscout) { Text("Test") }
            }
            TextButton(onClick = { editing = true }) { Text("Edit") }
        }
        if (check is NightscoutCheckState.Failed) {
            Text(check.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        return
    }
    ServerConfigInput(
        baseUrl = state.nightscoutConfig.baseUrl,
        apiKey = state.nightscoutConfig.apiKey,
        baseUrlLabel = "Address (https://…)",
        apiKeyLabel = "API secret or access token",
        onConfigChanged = { baseUrl, apiKey -> editing = true; viewModel.onNightscoutConfigChanged(baseUrl, apiKey) }
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = viewModel::testNightscout, enabled = check != NightscoutCheckState.Checking) {
            Text("Test connection")
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { editing = false }, enabled = state.nightscoutConfig.baseUrl.isNotBlank()) { Text("Done") }
        Spacer(Modifier.width(4.dp))
        when (check) {
            NightscoutCheckState.Checking -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            is NightscoutCheckState.Failed -> Text(check.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            else -> Unit
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
