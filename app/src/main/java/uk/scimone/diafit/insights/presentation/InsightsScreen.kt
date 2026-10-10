package uk.scimone.diafit.insights.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.insights.domain.InsightState
import uk.scimone.diafit.insights.domain.InsightTone
import uk.scimone.diafit.insights.domain.StoredInsight
import uk.scimone.diafit.ui.theme.InRange
import uk.scimone.diafit.ui.theme.Warning

/** The insight cards: graph, explanation and three tips each, new ones first. Tap a card for the detail page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val viewModel: InsightsViewModel = koinViewModel()
    val insights by viewModel.insights.collectAsState()
    val thresholds by viewModel.thresholds.collectAsState()
    val unread by viewModel.unreadCount.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (unread > 0) TextButton(onClick = viewModel::markAllRead) { Text("Mark all read") } }
            )
        }
    ) { padding ->
        val list = insights
        val t = thresholds
        when {
            list == null || t == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val active = list.filter { it.state != InsightState.RESOLVED && it.state != InsightState.DISMISSED }
                    .sortedWith(compareByDescending<StoredInsight> { it.isUnreviewed }.thenByDescending { it.card.priority })
                val resolved = list.filter { it.state == InsightState.RESOLVED }
                val dismissed = list.filter { it.state == InsightState.DISMISSED }
                if (active.isEmpty() && resolved.isEmpty() && dismissed.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No insights yet. They appear once there are at least a week of CGM readings and a pattern that keeps coming back.",
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (active.isEmpty()) item {
                        Text("Nothing to report right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(active, key = { it.card.id }) { InsightCardView(it, t) { onOpen(it.card.id) } }
                    if (resolved.isNotEmpty()) {
                        item { SectionTitle("No longer observed") }
                        items(resolved, key = { "r-" + it.card.id }) { CompactRow(it) { onOpen(it.card.id) } }
                    }
                    if (dismissed.isNotEmpty()) {
                        item { SectionTitle("Dismissed") }
                        items(dismissed, key = { "d-" + it.card.id }) { CompactRow(it) { onOpen(it.card.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun toneVisuals(tone: InsightTone): Pair<ImageVector, Color> = when (tone) {
    InsightTone.FAVORABLE -> Icons.Filled.CheckCircle to InRange
    InsightTone.UNFAVORABLE -> Icons.Filled.Warning to Warning
    InsightTone.MIXED -> Icons.Filled.Insights to MaterialTheme.colorScheme.primary
}

@Composable
private fun InsightCardView(item: StoredInsight, thresholds: GlucoseThresholds, onClick: () -> Unit) {
    val card = item.card
    val (icon, tint) = toneVisuals(card.tone)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (item.isUnreviewed) tint.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                Text(card.title, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (item.isUnreviewed) StateChip(item.state.label, tint)
            }
            InsightGraph(card.miniGraph, thresholds, large = false)
            Text(card.summary, style = MaterialTheme.typography.bodyMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                card.tips.forEach { tip ->
                    Row {
                        Text("•", Modifier.padding(end = 8.dp), color = tint)
                        Text(tip, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                "${card.category.label} · ${card.effectLabel} · last ${card.sampleSize} ${card.sampleUnit}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun StateChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CompactRow(item: StoredInsight, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(item.card.title, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
