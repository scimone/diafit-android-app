package uk.scimone.diafit.journal.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Vaccines
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.GlucoseEpisode
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.journal.presentation.model.BolusEntryUi
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
        is BolusEntryUi -> BolusCard(entry, onClick, modifier)
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
    EntrySurface(onClick, modifier) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MealThumbnail(meal)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Heading line: when, what kind of meal, and how fast it absorbs. Indented by the outcome box's own padding so the text lines up with it.
                Row(Modifier.padding(start = MEAL_TEXT_INDENT), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            append(meal.timeFormatted)
                            append(" · ")
                            append(meal.mealType.type)
                            if (meal.courseCount > 1) append(" · ${meal.courseCount} courses")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    AbsorptionBadge(meal.impactType)
                    if (meal.aapsLinked) {
                        Spacer(Modifier.width(4.dp))
                        Text("AAPS ✓", style = MaterialTheme.typography.labelSmall, color = Bolus, maxLines = 1, softWrap = false)
                    }
                }
                Text(meal.title, Modifier.padding(start = MEAL_TEXT_INDENT), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MealOutcomeRow(meal, target)
            }
            Spacer(Modifier.width(10.dp))
            // The numbers to read at a glance, like the lowest value of a low.
            Column(Modifier.width(MEAL_VALUES_WIDTH), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BigValue("${meal.carbohydrates}", "g", Carbs)
                meal.insulinUnits?.takeIf { it > 0.05 }?.let { BigValue(formatUnits(it), "U", Bolus) }
            }
        }
    }
}

/** Horizontal padding inside the outcome box; the heading and title are indented by the same amount. */
private val MEAL_TEXT_INDENT = 8.dp

/** Fixed, so the glucose outcome box (and its range bar) is the same width on every meal card. */
private val MEAL_VALUES_WIDTH = 52.dp

@Composable
private fun BigValue(value: String, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color, maxLines = 1, softWrap = false)
        Text(" $unit", style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 3.dp))
    }
}

/** The cover photo (or the meal-type tile), with a "+N" badge when there are more photos. */
@Composable
private fun MealThumbnail(meal: MealEntityUi) {
    val size = 84.dp
    val photos = meal.photoUris
    Box(Modifier.size(size)) {
        if (photos.isEmpty()) {
            MealTypeTile(meal.mealType, size)
        } else {
            AsyncImage(
                model = photos.first(),
                contentDescription = meal.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
            )
        }
        if (photos.size > 1) {
            Text(
                "+${photos.size - 1}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

fun glucoseColor(mgdl: Int, target: GlucoseTargetRange): Color = when {
    mgdl < target.lowerBound -> BelowRange
    mgdl > target.upperBound -> AboveRange
    else -> InRange
}

/** "112 → peak 186 after 1h 43min", then the 4 h range split, or why it isn't known yet. Sits beside the photo, so it stays narrow. */
@Composable
private fun MealOutcomeRow(meal: MealEntityUi, target: GlucoseTargetRange) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelMedium
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
            .padding(horizontal = MEAL_TEXT_INDENT, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val start = meal.startMgdl
        val peak = meal.peakMgdl
        if (start != null || peak != null) {
            val text = buildAnnotatedString {
                fun value(mgdl: Int) = withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = glucoseColor(mgdl, target))) { append("$mgdl") }
                start?.let { value(it) }
                if (peak != null && (start == null || peak > start)) {
                    append(if (start != null) " → peak " else "peak ")
                    value(peak)
                    if (start != null) 
                    meal.peakTimeUtc?.let {
                        val mins = ((it - meal.mealTimeUtc) / 60_000).toInt().coerceAtLeast(0)
                        append(" · ${if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"}")
                    }
                }
            }
            Text(text, style = labelStyle, color = muted)
        }
        when {
            meal.glucoseStatus == GlucoseStatus.TOO_EARLY ->
                Text("Outcome is shown 4 h after the meal", style = labelStyle, color = muted)
            meal.glucoseStatus == GlucoseStatus.NOT_ENOUGH_DATA || !meal.hasGlucoseData ->
                Text("Not enough sensor data after this meal", style = labelStyle, color = muted)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                RangeBar(
                    below = meal.timeBelowRange.toFloat(),
                    inRange = meal.timeInRange.toFloat(),
                    above = meal.timeAboveRange.toFloat(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("${meal.timeInRange.toInt()}%", Modifier.width(34.dp), style = labelStyle, fontWeight = FontWeight.Bold, color = InRange, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
            }
        }
    }
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
                    color = color
                )
            }
        }
    }
}

/** Insulin that belongs to no meal: a manual bolus, or the SMBs of one hour. */
@Composable
fun BolusCard(entry: BolusEntryUi, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    EntrySurface(onClick, modifier) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(Bolus.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Vaccines, null, tint = Bolus, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (entry.isSmb) "${clock.format(Date(entry.hourStartUtc))}–${clock.format(Date(entry.hourStartUtc + 3_600_000L))}"
                    else clock.format(Date(entry.timeUtc)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (entry.isSmb) "Automatic · ${entry.count} ${if (entry.count == 1) "SMB" else "SMBs"}" else "Insulin bolus",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            BigValue(formatUnits(entry.units), "U", Bolus)
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
