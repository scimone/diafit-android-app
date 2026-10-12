package uk.scimone.diafit.settings.presentation

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

    SettingsSection(title = "AI", icon = Icons.Filled.Psychology) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use AI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    if (state.aiEnabled) "Diafit may send data to the language-model connections below."
                    else "Off. Nothing is sent to any language model, and features that need one are hidden. The barcode scanner, saved products and food search still work.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = state.aiEnabled, onCheckedChange = viewModel::onAiEnabledChanged)
        }
    }
    if (!state.aiEnabled) return

    SettingsSection(title = "Connections", icon = Icons.Filled.Hub) {
        Text(
            "Any OpenAI-compatible endpoint (OpenAI, Gemini's compatible API, a proxy or your own server). Add as many as you like and pick one per feature.",
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
            Text("Add connection")
        }
    }

    SettingsSection(title = "Used for", icon = Icons.Filled.Restaurant) {
        NavRow("Meal estimation", state.mealConnection?.let { "${it.name} · ${state.mealEstimation.model}" } ?: "No connection yet") {
            onOpenPage(SettingsPage.MEAL_ESTIMATION)
        }
    }

    SettingsSection(title = "Usage", icon = Icons.Filled.Timeline) {
        Text(
            if (totals.requests == 0) "No requests yet."
            else "${"%,d".format(totals.requests)} requests · ${"%,d".format(totals.inputTokens)} tokens in · ${"%,d".format(totals.outputTokens)} tokens out",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onOpenPage(SettingsPage.AI_LOG) }) { Text("View request log") }
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
                NightscoutCheckState.Ok -> Icon(Icons.Filled.CheckCircle, "Works", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                NightscoutCheckState.Checking -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else -> TextButton(onClick = onTest) { Text("Test") }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit ${connection.name}") }
        }
        if (check is NightscoutCheckState.Failed) {
            Text(check.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
        title = { Text(if (isNew) "New connection" else "Edit connection") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                ServerConfigInput(
                    baseUrl = url, apiKey = key,
                    baseUrlLabel = "Base URL", apiKeyLabel = "API key",
                    onConfigChanged = { u, k -> url = u; key = k }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && key.isNotBlank(),
                onClick = { onSave(initial.copy(name = name.trim().ifBlank { hostOf(url.trim()) }, baseUrl = url.trim(), apiKey = key.trim())) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
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
        Text("Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** Which connection and model analyse meal photos, and the prompt they get. */
@Composable
internal fun MealEstimationPage(state: SettingsState, viewModel: SettingsViewModel, onOpenPage: (SettingsPage) -> Unit) {
    if (!state.aiEnabled) {
        Text("AI is switched off. Turn it on under AI to estimate meals from photos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onOpenPage(SettingsPage.AI) }) { Text("Open AI settings") }
        return
    }
    val meal = state.mealEstimation

    SettingsSection(title = "Connection and model", icon = Icons.Filled.Hub) {
        if (state.aiConnections.isEmpty()) {
            Text("Add a connection under AI first.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onOpenPage(SettingsPage.AI) }) { Text("Open AI settings") }
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
                "Needs a model that can read images.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    SettingsSection(title = "Prompt", icon = Icons.Filled.Edit) {
        var text by remember(meal.prompt) { mutableStateOf(meal.effectivePrompt) }
        Text(
            "The instructions the model gets with your photos. The answer format is fixed (foods with their nutrients, absorption, reasoning), so change how it should judge, not what it returns. Your note for a meal is added after this text.",
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
            ) { Text("Reset to default") }
            Spacer(Modifier.width(12.dp))
            Text(
                if (meal.prompt.isBlank()) "Using the built-in prompt" else "Customised",
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
            label = { Text("Connection") },
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

    SettingsSection(title = "Total", icon = Icons.Filled.Timeline) {
        Text(
            if (totals.requests == 0) "No requests yet. They show up here when Diafit uses an LLM."
            else "${"%,d".format(totals.requests)} requests",
            style = MaterialTheme.typography.bodyMedium
        )
        if (totals.requests > 0) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("%,d".format(totals.inputTokens), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("tokens in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Text("%,d".format(totals.outputTokens), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("tokens out", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Only the newest 300 requests are kept. Photos are not stored, only how many were sent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { confirmClear = true }) { Text("Clear log") }
        }
    }
    entries.forEach { LogCard(it) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the log?") },
            text = { Text("The request history and token totals are deleted. This doesn't change anything else.") },
            confirmButton = { TextButton(onClick = { log.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
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
                        SimpleDateFormat("d MMM HH:mm:ss", Locale.getDefault()).format(Date(entry.timestampUtc)) + " · ${entry.connectionName} · ${entry.model}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append(entry.inputTokens?.let { "%,d in".format(it) } ?: "tokens not reported")
                    if (entry.inputTokens != null || entry.outputTokens != null) append(" · ${entry.outputTokens?.let { "%,d out".format(it) } ?: "? out"}")
                    append(" · ${"%.1f".format(entry.durationMs / 1000.0)} s")
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (!entry.success) {
                Text(entry.error ?: "Failed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (open) {
                Spacer(Modifier.height(10.dp))
                LogText("Input" + if (entry.imageCount > 0) " (+ ${entry.imageCount} photo${if (entry.imageCount > 1) "s" else ""})" else "", entry.inputText)
                entry.outputText?.let {
                    Spacer(Modifier.height(10.dp))
                    LogText("Output", it)
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
