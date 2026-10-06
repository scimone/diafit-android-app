package uk.scimone.diafit.journal.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import uk.scimone.diafit.core.domain.model.GlucoseEpisode
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.journal.presentation.model.GlucoseEpisodeUi
import uk.scimone.diafit.journal.presentation.model.GlucoseStatus
import uk.scimone.diafit.journal.presentation.model.JournalEntryUi
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import uk.scimone.diafit.journal.presentation.model.accent
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One row of the journal (and of a History day's journal), whatever the entry kind. */
@Composable
fun JournalEntryCard(entry: JournalEntryUi, target: GlucoseTargetRange, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    when (entry) {
        is MealEntityUi -> MealCard(entry, target, onClick ?: {}, modifier)
        is GlucoseEpisodeUi -> GlucoseEpisodeCard(entry.episode, onClick, modifier)
    }
}

@Composable
private fun EntrySurface(onClick: (() -> Unit)?, modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val color = MaterialTheme.colorScheme.surface
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, border = border, modifier = modifier.fillMaxWidth(), content = content)
    } else {
        Surface(shape = shape, color = color, border = border, modifier = modifier.fillMaxWidth(), content = content)
    }
}

/** A meal: its photo, carbs, the insulin for it, absorption speed, and what glucose did afterwards. */
@Composable
fun MealCard(meal: MealEntityUi, target: GlucoseTargetRange, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    EntrySurface(onClick, modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                MealThumbnail(meal)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildString {
                                append(meal.timeFormatted)
                                append(" · ")
                                append(meal.mealType.type)
                                if (meal.courseCount > 1) append(" · ${meal.courseCount} courses")
                                if (meal.isImported) append(" · AAPS")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = meal.mealType.accent,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Filled.ChevronRight, "Open meal", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    Text(meal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // The chip colours say carbs / insulin, so the values need no words; that leaves room for absorption.
                        ValueChip("${meal.carbohydrates} g", Carbs)
                        meal.insulinUnits?.takeIf { it > 0.05 }?.let { ValueChip("${formatUnits(it)} U", Bolus) }
                        Spacer(Modifier.width(2.dp))
                        AbsorptionBadge(meal.impactType)
                    }
                }
            }
            MealOutcomeRow(meal, target, clock)
        }
    }
}

/** The cover photo (or the meal-type tile), with a "+N" badge when there are more photos. */
@Composable
private fun MealThumbnail(meal: MealEntityUi) {
    val size = 96.dp
    val photos = meal.photoUris
    if (photos.isEmpty()) {
        MealTypeTile(meal.mealType, size)
        return
    }
    Box(Modifier.size(size)) {
        AsyncImage(
            model = photos.first(),
            contentDescription = meal.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
        )
        if (photos.size > 1) {
            Text(
                "+${photos.size - 1}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ValueChip(value: String, accent: Color) {
    Text(
        value,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .background(accent.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

fun glucoseColor(mgdl: Int, target: GlucoseTargetRange): Color = when {
    mgdl < target.lowerBound -> BelowRange
    mgdl > target.upperBound -> AboveRange
    else -> InRange
}

/** "Glucose 112 → peak 186 (+74) after 1h 43min", then the 4 h range split, or why it isn't known yet. */
@Composable
private fun MealOutcomeRow(meal: MealEntityUi, target: GlucoseTargetRange, clock: SimpleDateFormat) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val start = meal.startMgdl
        val peak = meal.peakMgdl
        if (start != null || peak != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Glucose ", style = MaterialTheme.typography.labelMedium, color = muted)
                start?.let { GlucoseValue(it, target) }
                if (peak != null && (start == null || peak > start)) {
                    Text(if (start != null) "  →  peak " else "peak ", style = MaterialTheme.typography.labelMedium, color = muted)
                    GlucoseValue(peak, target)
                    if (start != null) Text(" (+${peak - start})", style = MaterialTheme.typography.labelMedium, color = muted)
                    meal.peakTimeUtc?.let {
                        val mins = ((it - meal.mealTimeUtc) / 60_000).toInt().coerceAtLeast(0)
                        val after = if (mins >= 60) "${mins / 60}h ${mins % 60}min" else "$mins min"
                        Text(" after $after", style = MaterialTheme.typography.labelMedium, color = muted)
                    }
                }
            }
        }
        when {
            meal.glucoseStatus == GlucoseStatus.TOO_EARLY ->
                Text("Outcome is shown 4 h after the meal", style = MaterialTheme.typography.labelMedium, color = muted)
            meal.glucoseStatus == GlucoseStatus.NOT_ENOUGH_DATA || !meal.hasGlucoseData ->
                Text("Not enough sensor data after this meal", style = MaterialTheme.typography.labelMedium, color = muted)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                RangeBar(
                    below = meal.timeBelowRange.toFloat(),
                    inRange = meal.timeInRange.toFloat(),
                    above = meal.timeAboveRange.toFloat(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text("${meal.timeInRange.toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = InRange)
            }
        }
    }
}

@Composable
private fun GlucoseValue(mgdl: Int, target: GlucoseTargetRange) {
    Text("$mgdl", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = glucoseColor(mgdl, target))
}

/** A stretch below or above range: how long, when, and how far it went. [onClick] opens its day. */
@Composable
fun GlucoseEpisodeCard(episode: GlucoseEpisode, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val color = if (episode.isLow) BelowRange else AboveRange
    val title = when {
        episode.isLow && episode.isSevere -> "Very low"
        episode.isLow -> "Low"
        episode.isSevere -> "Very high"
        else -> "High"
    }
    EntrySurface(onClick, modifier) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(color.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (episode.isLow) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${clock.format(Date(episode.startUtc))}–${clock.format(Date(episode.endUtc))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
                Text(
                    "$title for ${formatDuration(episode.durationMs)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${episode.extremeMgdl}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
                Text(
                    if (episode.isLow) "lowest mg/dL" else "highest mg/dL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "4", "4.5": one decimal, without a trailing ".0". */
fun formatUnits(units: Double): String {
    val r = Math.round(units * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

/** "45 min", "2 h 05", "24 h". */
fun formatDuration(ms: Long): String {
    val minutes = Math.round(ms / 60_000.0).toInt()
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h %02d".format(m)
    }
}
