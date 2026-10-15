package uk.scimone.diafit.patterns.presentation

import androidx.compose.ui.unit.sp
import uk.scimone.diafit.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uk.scimone.diafit.patterns.domain.PatternCard
import uk.scimone.diafit.patterns.domain.PatternCategory
import uk.scimone.diafit.patterns.domain.PatternState
import uk.scimone.diafit.patterns.domain.PatternTone
import uk.scimone.diafit.patterns.domain.StoredPattern
import uk.scimone.diafit.ui.theme.InRange
import uk.scimone.diafit.ui.theme.Warning

/** The pattern cards: graph, explanation and three tips each, new ones first. Tap a card for the detail page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val viewModel: PatternsViewModel = koinViewModel()
    val patterns by viewModel.patterns.collectAsState()
    val unread by viewModel.unreadCount.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.patterns_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
                actions = { if (unread > 0) TextButton(onClick = viewModel::markAllRead) { Text(stringResource(R.string.pat_ui_mark_read)) } }
            )
        }
    ) { padding ->
        val list = patterns
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val active = list.filter { it.state != PatternState.RESOLVED && it.state != PatternState.DISMISSED }
                    .sortedWith(compareByDescending<StoredPattern> { it.isUnreviewed }.thenByDescending { it.card.priority })
                val resolved = list.filter { it.state == PatternState.RESOLVED }
                val dismissed = list.filter { it.state == PatternState.DISMISSED }
                if (active.isEmpty() && resolved.isEmpty() && dismissed.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.pat_ui_empty),
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (active.isEmpty()) item {
                        Text(stringResource(R.string.pat_ui_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PatternCategory.entries.forEach { category ->
                        val group = active.filter { it.card.category == category }
                        if (group.isNotEmpty()) {
                            item(key = "c-" + category.name) { SectionTitle(category.label) }
                            items(group, key = { it.card.id }) { PatternCardView(it, onClick = { onOpen(it.card.id) }) }
                        }
                    }
                    if (resolved.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.pat_state_resolved)) }
                        items(resolved, key = { "r-" + it.card.id }) { CompactRow(it) { onOpen(it.card.id) } }
                    }
                    if (dismissed.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.pat_state_dismissed)) }
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

/** Icon and colour of a pattern: low / high glucose patterns use the colour of that zone, the rest follow the tone. */
@Composable
internal fun cardVisuals(card: PatternCard): Pair<ImageVector, Color> {
    val (icon, tint) = toneVisuals(card.tone)
    return icon to (card.glucoseSide()?.color ?: tint)
}

@Composable
internal fun toneVisuals(tone: PatternTone): Pair<ImageVector, Color> = when (tone) {
    PatternTone.FAVORABLE -> Icons.Filled.CheckCircle to InRange
    PatternTone.UNFAVORABLE -> Icons.Filled.Warning to Warning
    PatternTone.MIXED -> Icons.Filled.Insights to MaterialTheme.colorScheme.primary
}

/** The compact card: a sketch of the pattern, its short name and one line of numbers. Everything else is on the detail page. */
@Composable
internal fun PatternCardView(item: StoredPattern, onClick: () -> Unit, modifier: Modifier = Modifier, showKind: Boolean = false) {
    val card = item.card
    val (icon, tint) = cardVisuals(card)
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (item.isUnreviewed) tint.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PatternGlyph(card.detector, tint)
            Column(Modifier.weight(1f)) {
                if (showKind) Text(
                    stringResource(R.string.notifications_kind_pattern).uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = tint, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
                )
                Text(card.title.text(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${card.category.label} · ${card.effectLabel.text()}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (item.isUnreviewed) StateChip(item.state.label, tint) else Icon(icon, null, tint = tint.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
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
private fun CompactRow(item: StoredPattern, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(item.card.title.text(), Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
