package uk.scimone.diafit.patterns.presentation

import uk.scimone.diafit.patterns.domain.EVENT_START
import uk.scimone.diafit.core.presentation.formatStamp
import uk.scimone.diafit.core.presentation.AppStrings
import uk.scimone.diafit.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.patterns.domain.MiniGraph
import uk.scimone.diafit.patterns.domain.PatternCard
import uk.scimone.diafit.patterns.domain.PatternState
import uk.scimone.diafit.patterns.domain.StoredPattern
import java.time.Instant
import java.time.ZoneId

/**
 * One pattern in full, in the order a person needs it: what was found and how often/how big (header and key figures),
 * the typical shape across all occurrences (the aggregate graph), the evidence on their own days (example days with
 * glucose, meals, insulin, activity and sleep, the pattern's window shaded; for comparisons typical days from both
 * groups), what they could try, and finally how sure the finding is and its caveats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternDetailScreen(id: String, onBack: () -> Unit, onOpenDay: (Long) -> Unit = {}) {
    val viewModel: PatternDetailViewModel = koinViewModel(key = "pattern-$id") { parametersOf(id) }
    val item by viewModel.pattern.collectAsState()
    val thresholds by viewModel.thresholds.collectAsState()
    val examples by viewModel.examples.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.card?.category?.label?.let { stringResource(R.string.pat_ui_detail_title, it) } ?: stringResource(R.string.pat_ui_title_fallback)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } }
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
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(pattern, tint)
            KeyFigures(card, tint)
            Panel(graphTitle(card.miniGraph), stringResource(graphCaption(card.miniGraph))) { PatternGraph(card.miniGraph, t, large = true, highlightColor = tint) }
            Examples(card, examples, t, tint, onOpenDay)
            if (card.tips.isNotEmpty()) Panel(stringResource(R.string.pat_ui_try)) {
                card.tips.forEachIndexed { i, tip -> Numbered(i + 1, AppStrings.byName(tip), tint) }
            }
            Confidence(card)
            when (pattern.state) {
                PatternState.DISMISSED -> OutlinedButton(onClick = viewModel::restore, Modifier.fillMaxWidth()) { Text(stringResource(R.string.pat_ui_restore)) }
                PatternState.RESOLVED -> Text(
                    stringResource(R.string.pat_ui_resolved_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> OutlinedButton(onClick = { viewModel.dismiss(); onBack() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.pat_ui_dismiss)) }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header(pattern: StoredPattern, tint: Color) {
    val card = pattern.card
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PatternGlyph(card.detector, tint, Modifier.padding(end = 14.dp), size = 56.dp)
            Column(Modifier.weight(1f)) {
                Text(card.title.text(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        period(card.observationFromUtc, card.observationToUtc),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (pattern.state != PatternState.ONGOING) StateChip(pattern.state.label, tint)
                }
            }
        }
        Text(card.summary.text(), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Two figures up front: how often (or, for a comparison, how many in each group) and how big. A proportion bar makes
 * "24 of 40" readable at a glance.
 */
@Composable
private fun KeyFigures(card: PatternCard, tint: Color) {
    val comparison = card.baselineLabel != null
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val positive = card.positiveCount
        val unitText = AppStrings.byName("pat_unit_" + card.sampleUnit)
        Figure(
            Modifier.weight(1f),
            label = stringResource(if (comparison) R.string.pat_ui_fig_compared else if (positive != null) R.string.pat_ui_fig_often else R.string.pat_ui_fig_looked),
            value = when {
                comparison && positive != null -> stringResource(R.string.pat_ui_vs, positive, card.sampleSize - positive)
                positive != null -> stringResource(R.string.pat_ui_x_of_y, positive, card.sampleSize)
                else -> "${card.sampleSize}"
            },
            unit = unitText,
            share = if (!comparison && positive != null && card.sampleSize > 0) positive.toFloat() / card.sampleSize else null,
            tint = tint
        )
        // For count findings the effect label repeats "how often": show when it happens instead.
        val window = (card.miniGraph as? MiniGraph.AgpWindow)?.highlights?.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.label }
        if (positive != null && card.effectIsCount && window != null) {
            Figure(Modifier.weight(1f), label = stringResource(R.string.pat_ui_fig_when), value = window, unit = stringResource(R.string.pat_ui_time_of_day), share = null, tint = tint)
        } else if (positive != null && card.effectIsCount) {
            Figure(Modifier.weight(1f), label = stringResource(R.string.pat_ui_fig_share), value = "${Math.round(100.0 * positive / card.sampleSize)} %", unit = unitText, share = null, tint = tint)
        } else {
            Figure(Modifier.weight(1f), label = stringResource(R.string.pat_ui_fig_big), value = card.effectLabel.text(), unit = null, share = null, tint = tint)
        }
    }
}

@Composable
private fun Figure(modifier: Modifier, label: String, value: String, unit: String?, share: Float?, tint: Color) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = tint.copy(alpha = 0.08f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = if (value.length > 14) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = tint)
            unit?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            share?.let {
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(tint.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(it.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(tint))
                }
            }
        }
    }
}

@Composable
private fun graphTitle(graph: MiniGraph): String = when (graph) {
    is MiniGraph.AgpWindow -> stringResource(R.string.pat_ui_g_day)
    is MiniGraph.EventResponse ->
        if (graph.eventLabel.k == EVENT_START) stringResource(R.string.pat_ui_g_activity) else stringResource(R.string.pat_ui_g_event, graph.eventLabel.text())
    is MiniGraph.ProfileComparison -> stringResource(R.string.pat_ui_g_cmp, graph.aLabel.text(), graph.bLabel.text())
}

private fun graphCaption(graph: MiniGraph): Int = when (graph) {
    is MiniGraph.AgpWindow -> R.string.pat_ui_c_day
    is MiniGraph.EventResponse -> R.string.pat_ui_c_event
    is MiniGraph.ProfileComparison -> R.string.pat_ui_c_cmp
}

/**
 * The evidence on the person's own days. For a comparison the examples come as typical members of both groups, each
 * group under its own heading in the colour of the graph's line; otherwise the clearest occurrences.
 */
@Composable
private fun Examples(card: PatternCard, data: List<ExampleData>?, t: GlucoseThresholds, tint: Color, onOpenDay: (Long) -> Unit) {
    if (card.cases.isEmpty()) return
    val groups = card.cases.withIndex().groupBy { it.value.group }
    val comparison = groups.keys.any { it != null }
    val graph = card.miniGraph
    val aLabel = (graph as? MiniGraph.EventResponse)?.aLabel ?: (graph as? MiniGraph.ProfileComparison)?.aLabel
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val yMax = exampleYMax(data)
    val zone = ZoneId.systemDefault()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.pat_ui_ex_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(if (comparison) R.string.pat_ui_ex_cmp else R.string.pat_ui_ex_single),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ExampleLegend(tint)
        }
        for ((group, items) in groups) {
            val groupColor = group?.let { if (it == aLabel) primary else secondary }
            if (group != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(groupColor!!))
                    Text(group.text(), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
            for ((index, example) in items) {
                ExampleCard(example, data?.getOrNull(index), t, yMax, tint, groupColor) {
                    onOpenDay(Instant.ofEpochMilli(example.eventUtc ?: example.focusFromUtc).atZone(zone).toLocalDate().toEpochDay())
                }
            }
        }
        if (data == null) Text(stringResource(R.string.pat_ui_ex_loading), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How sure the finding is (always visible), then the period, comparison and caveats (folded away). */
@Composable
private fun Confidence(card: PatternCard) {
    var open by rememberSaveable { mutableStateOf(false) }
    Panel(stringResource(R.string.pat_ui_sure)) {
        Text(card.uncertainty.text(), style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(if (open) R.string.pat_ui_less else R.string.pat_ui_data_limits), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.primary)
        }
        if (open) {
            Fact(stringResource(R.string.pat_ui_fact_period), period(card.observationFromUtc, card.observationToUtc))
            card.baselineLabel?.let { Fact(stringResource(R.string.pat_ui_fact_compared), it.text()) }
            Fact(stringResource(R.string.pat_ui_fact_based), stringResource(R.string.pat_ui_based_value, card.sampleSize, AppStrings.byName("pat_unit_" + card.sampleUnit)))
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            card.limitations.forEach { Bullet(it.text()) }
        }
        Bullet(stringResource(R.string.pat_ui_disclaimer))
    }
}

@Composable
private fun Panel(title: String? = null, caption: String? = null, content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun Numbered(n: Int, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tint)
        }
        Text(text, Modifier.padding(start = 10.dp, top = 1.dp), style = MaterialTheme.typography.bodyMedium)
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
private fun Bullet(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row {
        Text("•", Modifier.padding(end = 8.dp), color = color)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun period(from: Long, to: Long): String =
    stringResource(R.string.pat_ui_period_range, formatStamp(from, "dMMM"), formatStamp(to, "dMMM"))
