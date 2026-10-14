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
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import uk.scimone.diafit.core.presentation.rememberResources
import uk.scimone.diafit.core.presentation.shortDuration
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
    onOpenDataEditor: () -> Unit,
    onOpenDailyRecap: () -> Unit,
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
                title = { Text(stringResource(R.string.menu_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } }
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

            MenuGroup(stringResource(R.string.menu_group_therapy)) {
                MenuRow(Icons.AutoMirrored.Filled.ReceiptLong, Basal, stringResource(R.string.menu_treatment_plan), stringResource(R.string.menu_treatment_plan_sub), onOpenTreatmentPlan)
                if (uk.scimone.diafit.dailyrecap.DailyRecapFeature.ENABLED) {
                    val recapStore: uk.scimone.diafit.dailyrecap.data.DailyRecapStore = koinInject()
                    val recapConfig by recapStore.config.collectAsState()
                    MenuRow(
                        Icons.Filled.WbSunny, Warning, stringResource(R.string.recap_title),
                        if (recapConfig.enabled) stringResource(R.string.recap_menu_on) else stringResource(R.string.recap_menu_sub),
                        onOpenDailyRecap
                    )
                }
                MenuRow(
                    Icons.Filled.Insights, MaterialTheme.colorScheme.tertiary, stringResource(R.string.patterns_title),
                    if (newPatterns > 0) stringResource(R.string.menu_patterns_new, newPatterns) else stringResource(R.string.menu_patterns_sub),
                    onOpenPatterns, badge = newPatterns > 0
                )
            }

            MenuGroup(stringResource(R.string.menu_group_data)) {
                MenuRow(
                    Icons.Filled.Hub, Bolus, stringResource(SettingsPage.CONNECTED_APPS.titleRes),
                    connectedSummary(state.enabledConnectors.map { it.displayName }),
                    { onOpenSettings(SettingsPage.CONNECTED_APPS) }
                )
                MenuRow(
                    Icons.Filled.Tune, Basal, stringResource(SettingsPage.DATA_SOURCES.titleRes),
                    stringResource(R.string.menu_data_sources_sub),
                    { onOpenSettings(SettingsPage.DATA_SOURCES) }
                )
                MenuRow(
                    Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.primary, stringResource(SettingsPage.AI.titleRes),
                    if (!state.aiEnabled) stringResource(R.string.common_off) else if (state.aiConnections.isEmpty()) stringResource(R.string.menu_ai_no_connection)
                    else pluralStringResource(R.plurals.menu_ai_connections, state.aiConnections.size, state.aiConnections.size),
                    { onOpenSettings(SettingsPage.AI) }
                )
                MenuRow(
                    Icons.Filled.EditNote, MaterialTheme.colorScheme.secondary, stringResource(R.string.menu_data_editor),
                    stringResource(R.string.menu_data_editor_sub),
                    onOpenDataEditor
                )
                if (BackendSyncFeature.ENABLED) {
                    MenuRow(
                        Icons.Filled.CloudUpload, Device, stringResource(SettingsPage.BACKEND.titleRes),
                        if (backendConfigured) stringResource(R.string.menu_backend_on) else stringResource(R.string.menu_backend_off),
                        { onOpenSettings(SettingsPage.BACKEND) }
                    )
                }
            }

            MenuGroup(stringResource(R.string.menu_group_meal_logging)) {
                if (state.aiEnabled) {
                    MenuRow(
                        Icons.Filled.Psychology, MaterialTheme.colorScheme.primary, stringResource(SettingsPage.MEAL_ESTIMATION.titleRes),
                        if (!state.mealEstimationEnabled) stringResource(R.string.common_off) else state.mealConnection?.let { stringResource(R.string.menu_meal_estimation_set, it.name, state.mealEstimation.model) } ?: stringResource(R.string.menu_meal_estimation_none),
                        { onOpenSettings(SettingsPage.MEAL_ESTIMATION) }
                    )
                }
                MenuRow(
                    Icons.Filled.Restaurant, uk.scimone.diafit.ui.theme.Activity, stringResource(SettingsPage.FOOD_DB.titleRes),
                    if (state.usdaKey.isNotBlank()) "Open Food Facts · USDA" else "Open Food Facts",
                    { onOpenSettings(SettingsPage.FOOD_DB) }
                )
            }

            MenuGroup(stringResource(R.string.menu_group_app)) {
                MenuRow(
                    Icons.Filled.Palette, MaterialTheme.colorScheme.secondary, stringResource(SettingsPage.DISPLAY.titleRes),
                    stringResource(R.string.menu_display_sub, state.glucoseTargetRange.lowerBound, state.glucoseTargetRange.upperBound, stringResource(state.basalStyle.labelRes)),
                    { onOpenSettings(SettingsPage.DISPLAY) }
                )
                MenuRow(
                    Icons.Filled.Notifications, Warning, stringResource(SettingsPage.NOTIFICATIONS.titleRes),
                    stringResource(R.string.menu_notifications_sub),
                    { onOpenSettings(SettingsPage.NOTIFICATIONS) }
                )
                MenuRow(
                    Icons.Filled.Lock, MaterialTheme.colorScheme.error, stringResource(SettingsPage.PERMISSIONS.titleRes),
                    if (state.isBatteryOptimizationIgnored) stringResource(R.string.menu_permissions_ok)
                    else stringResource(R.string.menu_permissions_restricted),
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
            title = { Text(stringResource(R.string.menu_name_title)) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.menu_name_label)) })
            },
            confirmButton = { TextButton(onClick = { settings.onUserNameChanged(text); editingName = false }) { Text(stringResource(R.string.common_save)) } },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun connectedSummary(names: List<String>) = when {
    names.isEmpty() -> stringResource(R.string.menu_connected_none)
    names.size <= 3 -> names.joinToString(", ")
    else -> stringResource(R.string.menu_connected_more, names.take(2).joinToString(", "), names.size - 2)
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
                        name.ifBlank { stringResource(R.string.menu_add_name) },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(stringResource(R.string.menu_tap_to_edit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.Edit, stringResource(R.string.menu_edit_name), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenDevices), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.menu_devices), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.menu_details), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                if (ages.isEmpty()) {
                    Text(
                        if (loaded) stringResource(R.string.menu_no_devices) else "…",
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
        Text(stringResource(shortName(age.kind)), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        val resources = rememberResources()
        Text(
            when {
                remaining == null -> stringResource(R.string.device_no_change)
                remaining < 0 -> stringResource(R.string.device_overdue_short, resources.shortDuration(-remaining))
                else -> stringResource(R.string.device_left_short, resources.shortDuration(remaining))
            },
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

private fun shortName(kind: DeviceKind) = when (kind) {
    DeviceKind.SENSOR -> R.string.device_short_sensor
    DeviceKind.SITE -> R.string.device_short_site
    DeviceKind.INSULIN -> R.string.device_short_insulin
    DeviceKind.BATTERY -> R.string.device_short_battery
    DeviceKind.PATCH -> R.string.device_short_patch
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
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        if (badge) {
            Badge(containerColor = Color(0xFFE53935))
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
