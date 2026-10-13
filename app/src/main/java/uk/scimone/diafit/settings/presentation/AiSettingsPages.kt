package uk.scimone.diafit.settings.presentation

import uk.scimone.diafit.core.presentation.formatStamp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import uk.scimone.diafit.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uk.scimone.diafit.core.domain.model.LlmRequestLogEntity
import uk.scimone.diafit.settings.domain.model.AiConnection
import uk.scimone.diafit.settings.domain.model.DEFAULT_AI_BASE_URL
import uk.scimone.diafit.settings.domain.model.DEFAULT_MEAL_ESTIMATION_PROMPT
import androidx.compose.runtime.saveable.rememberSaveable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun hostOf(url: String) = url.removePrefix("https://").removePrefix("http://").substringBefore('/').ifBlank { url }

/** The AI switch, the LLM connections (any number) and a way into the request log and the features that use AI. */
@Composable
internal fun AiPage(state: SettingsState, viewModel: SettingsViewModel, onOpenPage: (SettingsPage) -> Unit) {
    val log: AiLogViewModel = koinViewModel()
    val totals by log.totals.collectAsState()
    // null = closed; a connection = editing it; a blank one = adding.
    var editing by remember { mutableStateOf<AiConnection?>(null) }

    SettingsSection(title = stringResource(R.string.settings_page_ai), icon = Icons.Filled.Psychology) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_use), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    if (state.aiEnabled) stringResource(R.string.ai_on_desc)
                    else stringResource(R.string.ai_off_desc),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = state.aiEnabled, onCheckedChange = viewModel::onAiEnabledChanged)
        }
    }
    if (!state.aiEnabled) return

    SettingsSection(title = stringResource(R.string.ai_connections_title), icon = Icons.Filled.Hub) {
        Text(
            stringResource(R.string.ai_connections_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.aiConnections.forEach { connection ->
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            ConnectionRow(
                connection = connection,
                check = state.aiChecks[connection.id] ?: NightscoutCheckState.Idle,
                onTest = { viewModel.testAiConnection(connection.id) },
                onEdit = { editing = connection }
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { editing = AiConnection(UUID.randomUUID().toString(), "", DEFAULT_AI_BASE_URL, "") }) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ai_add_connection))
        }
    }

    SettingsSection(title = stringResource(R.string.ai_used_for), icon = Icons.Filled.Restaurant) {
        NavRow(stringResource(R.string.settings_page_meal_estimation), state.mealConnection?.let { "${it.name} · ${state.mealEstimation.model}" } ?: stringResource(R.string.ai_no_connection_yet)) {
            onOpenPage(SettingsPage.MEAL_ESTIMATION)
        }
    }

    SettingsSection(title = stringResource(R.string.ai_usage), icon = Icons.Filled.Timeline) {
        Text(
            if (totals.requests == 0) stringResource(R.string.ai_no_requests)
            else stringResource(R.string.ai_usage_line, "%,d".format(totals.requests), "%,d".format(totals.inputTokens), "%,d".format(totals.outputTokens)),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onOpenPage(SettingsPage.AI_LOG) }) { Text(stringResource(R.string.ai_view_log)) }
    }

    editing?.let { connection ->
        ConnectionDialog(
            initial = connection,
            isNew = state.aiConnections.none { it.id == connection.id },
            onSave = { viewModel.saveAiConnection(it); editing = null },
            onDelete = { viewModel.deleteAiConnection(connection.id); editing = null },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun ConnectionRow(connection: AiConnection, check: NightscoutCheckState, onTest: () -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(connection.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(hostOf(connection.baseUrl), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when (check) {
                NightscoutCheckState.Ok -> Icon(Icons.Filled.CheckCircle, stringResource(R.string.ai_works), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                NightscoutCheckState.Checking -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else -> TextButton(onClick = onTest) { Text(stringResource(R.string.common_test)) }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.ai_edit_named, connection.name)) }
        }
        if (check is NightscoutCheckState.Failed) {
            Text(check.message.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ConnectionDialog(initial: AiConnection, isNew: Boolean, onSave: (AiConnection) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.baseUrl) }
    var key by remember { mutableStateOf(initial.apiKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) stringResource(R.string.ai_new_connection) else stringResource(R.string.ai_edit_connection)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.ai_name_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                ServerConfigInput(
                    baseUrl = url, apiKey = key,
                    baseUrlLabel = stringResource(R.string.ai_base_url), apiKeyLabel = stringResource(R.string.ai_api_key),
                    onConfigChanged = { u, k -> url = u; key = k }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && key.isNotBlank(),
                onClick = { onSave(initial.copy(name = name.trim().ifBlank { hostOf(url.trim()) }, baseUrl = url.trim(), apiKey = key.trim())) }
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    )
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(stringResource(R.string.common_open), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** Which connection and model analyse meal photos, and the prompt they get. */
@Composable
internal fun MealEstimationPage(state: SettingsState, viewModel: SettingsViewModel, onOpenPage: (SettingsPage) -> Unit) {
    if (!state.aiEnabled) {
        Text(stringResource(R.string.ai_meal_off), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onOpenPage(SettingsPage.AI) }) { Text(stringResource(R.string.ai_open_settings)) }
        return
    }
    val meal = state.mealEstimation

    SettingsSection(title = stringResource(R.string.ai_meal_conn_title), icon = Icons.Filled.Hub) {
        if (state.aiConnections.isEmpty()) {
            Text(stringResource(R.string.ai_add_connection_first), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onOpenPage(SettingsPage.AI) }) { Text(stringResource(R.string.ai_open_settings)) }
        } else {
            ConnectionPicker(state.aiConnections, state.mealConnection?.id, viewModel::onMealConnectionChanged)
            Spacer(Modifier.height(12.dp))
            ModelPicker(
                model = meal.model,
                models = state.aiModels,
                isLoading = state.isLoadingAiModels,
                error = state.aiModelsError,
                onModelChanged = viewModel::onMealModelChanged,
                onLoadModels = viewModel::loadMealModels
            )
            Text(
                stringResource(R.string.ai_needs_vision),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    SettingsSection(title = stringResource(R.string.ai_prompt_title), icon = Icons.Filled.Edit) {
        var text by remember(meal.prompt) { mutableStateOf(meal.effectivePrompt) }
        Text(
            stringResource(R.string.ai_prompt_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                // Storing "" keeps following the built-in prompt, including future improvements to it.
                viewModel.onMealPromptChanged(if (it.trim() == DEFAULT_MEAL_ESTIMATION_PROMPT.trim()) "" else it)
            },
            minLines = 8, maxLines = 18,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { text = DEFAULT_MEAL_ESTIMATION_PROMPT.trim(); viewModel.onMealPromptChanged("") },
                enabled = meal.prompt.isNotBlank()
            ) { Text(stringResource(R.string.ai_reset_prompt)) }
            Spacer(Modifier.width(12.dp))
            Text(
                if (meal.prompt.isBlank()) stringResource(R.string.ai_prompt_builtin) else stringResource(R.string.ai_prompt_custom),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionPicker(connections: List<AiConnection>, selectedId: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = connections.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { if (it.name == hostOf(it.baseUrl)) it.name else "${it.name} (${hostOf(it.baseUrl)})" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.ai_connection_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            connections.forEach { c ->
                DropdownMenuItem(
                    text = { Column { Text(c.name); Text(hostOf(c.baseUrl), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    onClick = { onSelect(c.id); expanded = false }
                )
            }
        }
    }
}

/** Every request sent to an LLM endpoint: when, for what, which model, tokens in and out, and (expanded) the text both ways. */
@Composable
internal fun AiLogPage() {
    val log: AiLogViewModel = koinViewModel()
    val entries by log.entries.collectAsState()
    val totals by log.totals.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    SettingsSection(title = stringResource(R.string.ai_log_total), icon = Icons.Filled.Timeline) {
        Text(
            if (totals.requests == 0) stringResource(R.string.ai_log_empty)
            else pluralStringResource(R.plurals.ai_log_requests, totals.requests, "%,d".format(totals.requests)),
            style = MaterialTheme.typography.bodyMedium
        )
        if (totals.requests > 0) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("%,d".format(totals.inputTokens), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.ai_tokens_in), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Text("%,d".format(totals.outputTokens), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.ai_tokens_out), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ai_log_kept), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.ai_log_clear)) }
        }
    }
    entries.forEach { LogCard(it) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.ai_log_clear_title)) },
            text = { Text(stringResource(R.string.ai_log_clear_text)) },
            confirmButton = { TextButton(onClick = { log.clear(); confirmClear = false }) { Text(stringResource(R.string.common_clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun LogCard(entry: LlmRequestLogEntity) {
    var open by rememberSaveable(entry.id) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (entry.success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null,
                    tint = if (entry.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.purpose, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(
                        formatStamp(entry.timestampUtc, "dMMMHms") + " · ${entry.connectionName} · ${entry.model}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            val inText = entry.inputTokens?.let { stringResource(R.string.ai_log_in, "%,d".format(it)) } ?: stringResource(R.string.ai_log_not_reported)
            val outText = entry.outputTokens?.let { stringResource(R.string.ai_log_out, "%,d".format(it)) } ?: stringResource(R.string.ai_log_out_unknown)
            Text(
                buildString {
                    append(inText)
                    if (entry.inputTokens != null || entry.outputTokens != null) append(" · $outText")
                    append(" · ${"%.1f".format(entry.durationMs / 1000.0)} s")
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (!entry.success) {
                Text(entry.error ?: stringResource(R.string.common_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (open) {
                Spacer(Modifier.height(10.dp))
                LogText(
                    if (entry.imageCount > 0) pluralStringResource(R.plurals.ai_log_input_photos, entry.imageCount, entry.imageCount) else stringResource(R.string.ai_log_input),
                    entry.inputText
                )
                entry.outputText?.let {
                    Spacer(Modifier.height(10.dp))
                    LogText(stringResource(R.string.ai_log_output), it)
                }
            }
        }
    }
}

@Composable
private fun LogText(label: String, text: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(10.dp)
            )
        }
    }
}
