package uk.scimone.diafit.menu.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.BuildConfig
import uk.scimone.diafit.R
import uk.scimone.diafit.backendsync.BackendSyncFeature
import uk.scimone.diafit.backendsync.data.BackendSyncStore
import uk.scimone.diafit.core.domain.model.DeviceAge
import uk.scimone.diafit.core.domain.model.DeviceKind
import uk.scimone.diafit.devices.presentation.DevicesViewModel
import uk.scimone.diafit.patterns.presentation.PatternsViewModel
import uk.scimone.diafit.settings.presentation.SettingsPage
import uk.scimone.diafit.settings.presentation.SettingsViewModel
import uk.scimone.diafit.ui.theme.Basal
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Device
import uk.scimone.diafit.ui.theme.Warning
import kotlin.math.abs

/**
 * The page behind the person icon: who you are and the status of your devices on top, then everything about
 * your therapy, your data, meal logging and the app, each as one row that opens its own page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    userId: Int,
    onBack: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenTreatmentPlan: () -> Unit,
    onOpenPatterns: () -> Unit,
    onOpenSettings: (SettingsPage) -> Unit
) {
    val settings: SettingsViewModel = koinViewModel()
    val state by settings.state.collectAsState()
    val devices: DevicesViewModel = koinViewModel { parametersOf(userId) }
    val deviceState by devices.state.collectAsState()
    val patterns: PatternsViewModel = koinViewModel()
    val newPatterns by patterns.unreadCount.collectAsState()
    val backendConfigured = if (BackendSyncFeature.ENABLED) {
        val store: BackendSyncStore = koinInject()
        val config by store.config.collectAsState()
        config.isConfigured
    } else false

    var editingName by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menu") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ProfileHeader(
                name = state.userName,
                ages = deviceState.ages,
                loaded = deviceState.loaded,
                onEditName = { editingName = true },
                onOpenDevices = onOpenDevices
            )

            MenuGroup("My therapy") {
                MenuRow(Icons.AutoMirrored.Filled.ReceiptLong, Basal, "Treatment plan", "Basal, sensitivity, carb ratio and targets", onOpenTreatmentPlan)
                MenuRow(
                    Icons.Filled.Insights, MaterialTheme.colorScheme.tertiary, "Patterns",
                    if (newPatterns > 0) "$newPatterns new to look at" else "Recurring trends in your glucose",
                    onOpenPatterns, badge = newPatterns > 0
                )
            }

            MenuGroup("Data") {
                MenuRow(
                    Icons.Filled.Hub, Bolus, SettingsPage.CONNECTED_APPS.title,
                    connectedSummary(state.enabledConnectors.map { it.displayName }),
                    { onOpenSettings(SettingsPage.CONNECTED_APPS) }
                )
                MenuRow(
                    Icons.Filled.Tune, Basal, SettingsPage.DATA_SOURCES.title,
                    "Which app feeds glucose, insulin, food, activity",
                    { onOpenSettings(SettingsPage.DATA_SOURCES) }
                )
                MenuRow(
                    Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.primary, SettingsPage.AI.title,
                    if (!state.aiEnabled) "Off" else if (state.aiConnections.isEmpty()) "On · no connection yet"
                    else "On · ${state.aiConnections.size} connection${if (state.aiConnections.size > 1) "s" else ""}",
                    { onOpenSettings(SettingsPage.AI) }
                )
                if (BackendSyncFeature.ENABLED) {
                    MenuRow(
                        Icons.Filled.CloudUpload, Device, SettingsPage.BACKEND.title,
                        if (backendConfigured) "Uploading to your server" else "Not set up",
                        { onOpenSettings(SettingsPage.BACKEND) }
                    )
                }
            }

            MenuGroup("Meal logging") {
                if (state.aiEnabled) {
                    MenuRow(
                        Icons.Filled.Psychology, MaterialTheme.colorScheme.primary, SettingsPage.MEAL_ESTIMATION.title,
                        state.mealConnection?.let { "Photo estimates · ${it.name} · ${state.mealEstimation.model}" } ?: "Photo estimates · set a connection",
                        { onOpenSettings(SettingsPage.MEAL_ESTIMATION) }
                    )
                }
                MenuRow(
                    Icons.Filled.Restaurant, uk.scimone.diafit.ui.theme.Activity, SettingsPage.FOOD_DB.title,
                    if (state.usdaKey.isNotBlank()) "Open Food Facts · USDA" else "Open Food Facts",
                    { onOpenSettings(SettingsPage.FOOD_DB) }
                )
            }

            MenuGroup("App") {
                MenuRow(
                    Icons.Filled.Palette, MaterialTheme.colorScheme.secondary, SettingsPage.DISPLAY.title,
                    "${state.glucoseTargetRange.lowerBound}–${state.glucoseTargetRange.upperBound} mg/dL · ${state.basalStyle.label} basal",
                    { onOpenSettings(SettingsPage.DISPLAY) }
                )
                MenuRow(
                    Icons.Filled.Notifications, Warning, SettingsPage.NOTIFICATIONS.title,
                    "Expiry alerts, pattern alerts, sync",
                    { onOpenSettings(SettingsPage.NOTIFICATIONS) }
                )
                MenuRow(
                    Icons.Filled.Lock, MaterialTheme.colorScheme.error, SettingsPage.PERMISSIONS.title,
                    if (state.isBatteryOptimizationIgnored) "Background reliability, camera, Health Connect"
                    else "Background sync is being restricted",
                    { onOpenSettings(SettingsPage.PERMISSIONS) },
                    subtitleColor = if (state.isBatteryOptimizationIgnored) null else Warning
                )
            }

            Text(
                "Diafit ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (editingName) {
        var text by remember { mutableStateOf(state.userName) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Your name") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = { TextButton(onClick = { settings.onUserNameChanged(text); editingName = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Cancel") } }
        )
    }
}

private fun connectedSummary(names: List<String>) = when {
    names.isEmpty() -> "Nothing connected yet"
    names.size <= 3 -> names.joinToString(", ")
    else -> names.take(2).joinToString(", ") + " and ${names.size - 2} more"
}

/** Avatar + name, and below it one tile per device with its state; tapping a tile opens the Devices page. */
@Composable
private fun ProfileHeader(
    name: String,
    ages: List<DeviceAge>,
    loaded: Boolean,
    onEditName: () -> Unit,
    onOpenDevices: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onEditName),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (name.isBlank()) {
                        Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
                    } else {
                        Text(
                            name.first().uppercase(), style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name.ifBlank { "Add your name" },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("Tap to edit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.Edit, "Edit name", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenDevices), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Devices", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("Details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                if (ages.isEmpty()) {
                    Text(
                        if (loaded) "No devices tracked yet" else "…",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ages.forEach { DeviceTile(it, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private enum class DeviceHealth { OK, SOON, OVERDUE, UNKNOWN }

private fun DeviceAge.health(): DeviceHealth {
    val left = remainingMs ?: return DeviceHealth.UNKNOWN
    return when {
        left < 0 -> DeviceHealth.OVERDUE
        left <= 12 * 3_600_000L -> DeviceHealth.SOON
        else -> DeviceHealth.OK
    }
}

@Composable
private fun DeviceTile(age: DeviceAge, modifier: Modifier) {
    val health = age.health()
    val color = when (health) {
        DeviceHealth.OK -> MaterialTheme.colorScheme.primary
        DeviceHealth.SOON -> Warning
        DeviceHealth.OVERDUE -> MaterialTheme.colorScheme.error
        DeviceHealth.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val remaining = age.remainingMs
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f)).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painterResource(
                when (age.kind) {
                    DeviceKind.SENSOR -> R.drawable.ic_sensor
                    else -> R.drawable.ic_insulin_pump
                }
            ),
            null, tint = color, modifier = Modifier.size(26.dp)
        )
        Text(shortName(age.kind), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text(
            when {
                remaining == null -> "No change logged"
                remaining < 0 -> "Overdue ${shortDuration(-remaining)}"
                else -> "${shortDuration(remaining)} left"
            },
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

private fun shortName(kind: DeviceKind) = when (kind) {
    DeviceKind.SENSOR -> "Sensor"
    DeviceKind.SITE -> "Site"
    DeviceKind.INSULIN -> "Insulin"
    DeviceKind.BATTERY -> "Battery"
    DeviceKind.PATCH -> "Pod"
}

private fun shortDuration(ms: Long): String {
    val minutes = abs(ms) / 60_000
    val days = minutes / 1440
    val hours = (minutes % 1440) / 60
    return when {
        days >= 2 -> "$days d"
        days == 1L -> "1 d ${hours} h"
        hours >= 1 -> "$hours h"
        else -> "${minutes} m"
    }
}

/** A captioned card holding rows separated by hairlines. */
@Composable
private fun MenuGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            // The 1 dp gaps between the rows show this colour: hairline dividers.
            color = MaterialTheme.colorScheme.outlineVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) { content() }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: Boolean = false,
    subtitleColor: Color? = null
) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (badge) {
            Badge(containerColor = Color(0xFFE53935))
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
