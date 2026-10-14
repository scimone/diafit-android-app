package uk.scimone.diafit.dailyrecap.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import uk.scimone.diafit.dailyrecap.data.RecapLlm
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import uk.scimone.diafit.R
import uk.scimone.diafit.core.presentation.UiText
import uk.scimone.diafit.dailyrecap.domain.DailyRecap
import uk.scimone.diafit.settings.presentation.ConnectionPicker
import uk.scimone.diafit.settings.presentation.ModelPicker
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.InRange
import uk.scimone.diafit.ui.theme.Warning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The recap of one morning (opened from the bell), with arrows to the earlier ones kept for two weeks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRecapScreen(epochDay: Long?, onBack: () -> Unit) {
    val viewModel: DailyRecapViewModel = koinViewModel()
    val recaps by viewModel.recaps.collectAsState()
    val state by viewModel.state.collectAsState()
    // Start on the recap that was tapped, else the newest.
    val start = remember(epochDay, recaps.size) { epochDay?.let { d -> recaps.indexOfFirst { it.epochDay == d } }?.takeIf { it >= 0 } ?: 0 }
    var picked by remember(epochDay) { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val index = (picked ?: start).coerceIn(0, (recaps.size - 1).coerceAtLeast(0))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recap_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (recaps.isEmpty()) EmptyCard() else RecapCard(
                recap = recaps[index],
                hasOlder = index < recaps.size - 1, hasNewer = index > 0,
                onOlder = { picked = index + 1 }, onNewer = { picked = index - 1 }
            )
        }
    }
}

/** Settings of the recap: on/off first, then when, what the AI is and how it is told to write. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRecapSettingsScreen(onBack: () -> Unit) {
    val viewModel: DailyRecapViewModel = koinViewModel()
    val config by viewModel.config.collectAsState()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshConnections() }
    var pickingTime by remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recap_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.recap_switch_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.recap_switch_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = config.enabled, onCheckedChange = viewModel::setEnabled)
                }
            }
            if (config.enabled) {
                SettingsCard(stringResource(R.string.recap_time_title)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pickingTime = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.recap_time_label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("%02d:%02d".format(config.hour, config.minute), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Note(stringResource(R.string.recap_time_hint))
                }
                SettingsCard(stringResource(R.string.recap_model_title)) {
                    when {
                        !state.aiEnabled -> Note(stringResource(R.string.recap_ai_off))
                        state.connections.isEmpty() -> Note(stringResource(R.string.recap_no_connection))
                        else -> {
                            ConnectionPicker(state.connections, config.connectionId ?: state.connections.first().id, viewModel::setConnection)
                            ModelPicker(
                                model = config.model, models = state.models, isLoading = state.isLoadingModels,
                                error = state.modelsError?.let { UiText.Raw(it) },
                                onModelChanged = viewModel::setModel, onLoadModels = viewModel::loadModels
                            )
                            Note(stringResource(R.string.recap_model_hint))
                        }
                    }
                }
                SettingsCard(stringResource(R.string.recap_prompt_title)) {
                    var text by remember(config.prompt) { androidx.compose.runtime.mutableStateOf(config.prompt.ifBlank { RecapLlm.DEFAULT_PROMPT.trim() }) }
                    Note(stringResource(R.string.recap_prompt_hint))
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            // Storing "" keeps following the built-in prompt, including future improvements to it.
                            viewModel.setPrompt(if (it.trim() == RecapLlm.DEFAULT_PROMPT.trim()) "" else it)
                        },
                        minLines = 8, maxLines = 18, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { text = RecapLlm.DEFAULT_PROMPT.trim(); viewModel.setPrompt("") }, enabled = config.prompt.isNotBlank()) {
                            Text(stringResource(R.string.ai_reset_prompt))
                        }
                        Spacer(Modifier.width(12.dp))
                        Note(stringResource(if (config.prompt.isBlank()) R.string.ai_prompt_builtin else R.string.ai_prompt_custom))
                    }
                }
            }
        }
    }

    if (pickingTime) {
        val picker = rememberTimePickerState(config.hour, config.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = { TextButton(onClick = { viewModel.setTime(picker.hour, picker.minute); pickingTime = false }) { Text(stringResource(R.string.common_save)) } },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.common_cancel)) } },
            text = { TimePicker(state = picker) }
        )
    }
}

@Composable
private fun EmptyCard() {
    SettingsCard(stringResource(R.string.recap_empty_title)) {
        Text(stringResource(R.string.recap_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecapCard(recap: DailyRecap, hasOlder: Boolean, hasNewer: Boolean, onOlder: () -> Unit, onNewer: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val image = remember(recap) { RecapImage.render(recap, zone).asImageBitmap() }
    val dayLabel = remember(recap) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(LocalDate.ofEpochDay(recap.epochDay)) }
    val writtenAt = remember(recap) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(recap.generatedAtUtc).atZone(zone))
    }
    val moodColor = when (recap.mood) { "good" -> InRange; "hard" -> BelowRange; else -> Warning }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOlder, enabled = hasOlder) { Icon(Icons.Filled.ChevronLeft, stringResource(R.string.recap_previous)) }
            Text(dayLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = onNewer, enabled = hasNewer) { Icon(Icons.Filled.ChevronRight, stringResource(R.string.recap_next)) }
        }
        Image(
            image, contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(RecapImage.WIDTH.toFloat() / RecapImage.HEIGHT).clip(RoundedCornerShape(22.dp))
        )
        Text(recap.headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = moodColor)

        var n = 0
        if (recap.wentWell.isNotEmpty()) Section(stringResource(R.string.recap_section_well), InRange) {
            recap.wentWell.forEach { Line(RecapImage.badge(++n), it.text) }
        }
        if (recap.wentWrong.isNotEmpty()) Section(stringResource(R.string.recap_section_wrong), Warning) {
            recap.wentWrong.forEach { Line(RecapImage.badge(++n), it.text) }
        }
        if (recap.doToday.isNotEmpty()) Section(stringResource(R.string.recap_section_today), MaterialTheme.colorScheme.primary) {
            recap.doToday.forEach { Line("→", it) }
        }
        Text(
            stringResource(R.string.recap_generated_at, writtenAt),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Section(title: String, color: Color, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Line(marker: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(marker, Modifier.width(26.dp), style = MaterialTheme.typography.bodyLarge)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsCard(title: String?, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            content()
        }
    }
}
