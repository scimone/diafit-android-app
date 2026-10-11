package uk.scimone.diafit.patterns.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.patterns.domain.PatternCard
import uk.scimone.diafit.patterns.domain.PatternState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One pattern in full: bigger graph, periods, sample, size and uncertainty, examples, caveats and the three tips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternDetailScreen(id: String, onBack: () -> Unit) {
    val viewModel: PatternDetailViewModel = koinViewModel(key = "pattern-$id") { parametersOf(id) }
    val item by viewModel.pattern.collectAsState()
    val thresholds by viewModel.thresholds.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pattern") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        val pattern = item
        val t = thresholds
        if (pattern == null || t == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val card = pattern.card
        val (_, tint) = cardVisuals(card)
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PatternGlyph(card.detector, tint, Modifier.padding(end = 12.dp))
                Text(card.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (pattern.state != PatternState.ONGOING) StateChip(pattern.state.label, tint)
            }
            Text(card.summary, style = MaterialTheme.typography.bodyLarge)
            Panel { PatternGraph(card.miniGraph, t, large = true) }

            Panel("The numbers") {
                (card.miniGraph as? uk.scimone.diafit.patterns.domain.MiniGraph.AgpWindow)?.highlights?.takeIf { it.isNotEmpty() }?.let { spans ->
                    Fact("Time window", spans.joinToString(", ") { it.label })
                }
                Fact("Looked at", period(card.observationFromUtc, card.observationToUtc))
                card.baselineLabel?.let { Fact("Compared with", it) }
                Fact("Observations", "${card.sampleSize} ${card.sampleUnit}" + (card.positiveCount?.let { " ($it showed it)" } ?: ""))
                Fact("Size", card.effectLabel)
                Fact("How sure", card.uncertainty)
            }
            if (card.examples.isNotEmpty()) Panel("Examples") {
                card.examples.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            Panel("Worth knowing") {
                card.limitations.forEach { Bullet(it) }
                Bullet("This is an association in your data, not proof of a cause. It isn't a dosing instruction.")
            }
            Panel("Three tips") { card.tips.forEach { Bullet(it, tint) } }

            when (pattern.state) {
                PatternState.DISMISSED -> OutlinedButton(onClick = viewModel::restore, Modifier.fillMaxWidth()) { Text("Show this pattern again") }
                PatternState.RESOLVED -> Text(
                    "This pattern isn't showing in the latest data.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> OutlinedButton(onClick = { viewModel.dismiss(); onBack() }, Modifier.fillMaxWidth()) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun Panel(title: String? = null, content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Bullet(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row {
        Text("•", Modifier.padding(end = 8.dp), color = color)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun period(from: Long, to: Long): String {
    val f = DateTimeFormatter.ofPattern("d MMM")
    val zone = ZoneId.systemDefault()
    return "${f.format(Instant.ofEpochMilli(from).atZone(zone))} – ${f.format(Instant.ofEpochMilli(to).atZone(zone))}"
}
