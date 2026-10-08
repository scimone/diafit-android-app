package uk.scimone.diafit.patterns.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.history.presentation.AgpBands
import uk.scimone.diafit.history.presentation.AgpPlot
import uk.scimone.diafit.patterns.domain.AgpPattern
import uk.scimone.diafit.patterns.domain.GetGlucosePatternsUseCase
import uk.scimone.diafit.patterns.domain.PatternAgp
import uk.scimone.diafit.patterns.domain.PatternTone
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.InRange
import uk.scimone.diafit.ui.theme.Warning

/**
 * The 14-day AGP the patterns are computed on, and the patterns found in it. Tapping a pattern highlights its time
 * of day on the AGP; opened from a pattern notification, that notification's patterns start highlighted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternsScreen(userId: Int, highlighted: List<String>, onBack: () -> Unit) {
    val viewModel: PatternsViewModel = koinViewModel(key = "patterns-${highlighted.joinToString("|")}") { parametersOf(userId, highlighted) }
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Glucose patterns") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        val result = state.result
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            result == null || state.thresholds == null -> Message(state.errorMessage ?: "Couldn't compute patterns", Modifier.padding(padding))
            result.agp == null -> Message(
                "Not enough data yet. Patterns need CGM readings from at least ${PatternAgp.MIN_DAYS} days, spread over the " +
                    "whole day, within the last ${GetGlucosePatternsUseCase.PATTERN_DAYS} days (found ${result.dayCount}).",
                Modifier.padding(padding)
            )
            else -> {
                val attention = result.patterns.filter { it.isAlert }.sortedBy { it.tone.ordinal }
                val good = result.patterns.filter { !it.isAlert }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "Last ${GetGlucosePatternsUseCase.PATTERN_DAYS} days · ${result.dayCount.coerceAtMost(GetGlucosePatternsUseCase.PATTERN_DAYS)} days with readings",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item { AgpChartCard(result.agp, state, onClear = viewModel::clearSelection) }
                    if (result.patterns.isEmpty()) item {
                        Text("No patterns detected for this period.", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (attention.isNotEmpty()) {
                        item { SectionTitle("Needs attention") }
                        items(attention, key = { it.text }) { PatternRow(it, it.text in state.selected) { viewModel.toggle(it.text) } }
                    }
                    if (good.isNotEmpty()) {
                        item { SectionTitle("Going well") }
                        items(good, key = { it.text }) { PatternRow(it, it.text in state.selected) { viewModel.toggle(it.text) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgpChartCard(agp: PatternAgp, state: PatternsState, onClear: () -> Unit) {
    val bands = androidx.compose.runtime.remember(agp) {
        fun DoubleArray.f() = FloatArray(size) { this[it].toFloat() }
        AgpBands(agp.p10.f(), agp.p25.f(), agp.p50.f(), agp.p75.f(), agp.p90.f())
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            AgpPlot(
                bands, state.thresholds!!, Modifier.fillMaxWidth().height(220.dp),
                highlights = state.highlights.map { it.startHour to it.endHour },
                yLabels = true,
                smooth = false
            )
            HourLabels(Modifier.fillMaxWidth().padding(horizontal = 2.dp))
            Text(
                "Median with 25–75 % and 10–90 % ranges, by time of day",
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.selected.isNotEmpty()) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.highlights.isEmpty()) "The selected patterns span the whole day"
                        else "Highlighted: " + state.highlights.joinToString(", ") { it.label },
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            } else {
                Text(
                    "Tap a pattern to highlight it on the chart",
                    Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            for (gone in state.noLongerFound) {
                Text(
                    "No longer found in the latest data: $gone",
                    Modifier.padding(horizontal = 12.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(top = 12.dp, bottom = 2.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PatternRow(pattern: AgpPattern, selected: Boolean, onClick: () -> Unit) {
    val (icon, tint) = when (pattern.tone) {
        PatternTone.DANGER -> Icons.Filled.Error to BelowRange
        PatternTone.CONCERN -> Icons.Filled.Warning to Warning
        PatternTone.GOOD -> Icons.Filled.CheckCircle to InRange
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(pattern.text, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
            Text(
                pattern.highlight?.label ?: "All day",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 00, 06, 12, 18, 24 centred under their x on the chart (clamped at the edges). */
@Composable
private fun HourLabels(modifier: Modifier) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = {
            for (h in 0..24 step 6) {
                Text("%02d".format(h), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        layout(width, placeables.maxOf { it.height }) {
            placeables.forEachIndexed { i, p ->
                val centre = width * i / (placeables.size - 1)
                p.placeRelative((centre - p.width / 2).coerceIn(0, width - p.width), 0)
            }
        }
    }
}
