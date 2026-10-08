package uk.scimone.diafit.journal.presentation.components

import uk.scimone.diafit.home.presentation.components.UriMosaic
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Warning
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
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
import uk.scimone.diafit.core.domain.model.MEAL_OUTCOME_WINDOW_MS
import uk.scimone.diafit.R
import uk.scimone.diafit.journal.presentation.model.BolusEntryUi
import uk.scimone.diafit.journal.presentation.model.GlucoseEpisodeUi
import uk.scimone.diafit.journal.presentation.model.GlucoseStatus
import uk.scimone.diafit.journal.presentation.model.JournalEntryUi
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import uk.scimone.diafit.journal.presentation.model.PumpEventUi
import uk.scimone.diafit.journal.presentation.model.PumpEventIcon
import uk.scimone.diafit.ui.theme.Warning
import uk.scimone.diafit.journal.presentation.model.SleepEntryUi
import uk.scimone.diafit.journal.presentation.model.ExerciseEntryUi
import uk.scimone.diafit.core.domain.model.SleepStage
import uk.scimone.diafit.journal.presentation.model.accent
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.Basal
import uk.scimone.diafit.core.domain.model.formatDurationMinutes
import uk.scimone.diafit.ui.theme.Device
import uk.scimone.diafit.ui.theme.Sleep
import uk.scimone.diafit.ui.theme.Activity
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
        is PumpEventUi -> PumpEventCard(entry, onClick, modifier)
        is SleepEntryUi -> SleepCard(entry, onClick, modifier)
        is ExerciseEntryUi -> ExerciseCard(entry, onClick, modifier)
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
                // Heading line spans the full card width (above the numbers): when, what kind of meal, and how fast it absorbs. Indented by the outcome box's own padding so the text lines up with it.
                Row(Modifier.padding(start = MEAL_TEXT_INDENT), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            // The start time only for multi-course meals: the full range doesn't fit next to the badge.
                            append(if (meal.courseCount > 1) meal.timeFormatted.substringBefore('–') else meal.timeFormatted)
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
                    HeadingDot()
                    AbsorptionBadge(meal.impactType)
                    if (meal.aapsLinked) {
                        HeadingDot()
                        Text("AAPS", style = MaterialTheme.typography.labelSmall, color = Bolus, maxLines = 1, softWrap = false)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(meal.title, Modifier.padding(start = MEAL_TEXT_INDENT), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MealOutcomeRow(meal, target)
                    }
                    // The numbers to read at a glance, like the lowest value of a low.
                    Column(Modifier.width(MEAL_VALUES_WIDTH), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BigValue("${meal.carbohydrates}", "g", Carbs)
                        meal.insulinUnits?.takeIf { it > 0.05 }?.let { BigValue(formatUnits(it), "U", Bolus) }
                    }
                }
            }
        }
    }
}

/** "Result in 1h 20m": what is left of the 4 h outcome window. */
private fun resultInText(remainingMs: Long): String {
    val mins = ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    return "Result in " + if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
}

@Composable
private fun HeadingDot() {
    Text(" · ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
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

/** The meal-type tile, one photo, or the split view of several (same mosaic as on Home). */
@Composable
private fun MealThumbnail(meal: MealEntityUi) {
    val size = 84.dp
    val photos = meal.photoUris
    Box(Modifier.size(size).clip(RoundedCornerShape(16.dp))) {
        if (photos.isEmpty()) MealTypeTile(meal.mealType, size)
        else UriMosaic(photos)
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
                Text(resultInText(meal.mealTimeUtc + MEAL_OUTCOME_WINDOW_MS - System.currentTimeMillis()), style = labelStyle, color = muted)
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

/** Insulin that belongs to no meal: one bolus, or several close together that expand into their doses. */
@Composable
fun BolusCard(entry: BolusEntryUi, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var expanded by remember { mutableStateOf(false) }
    val grouped = entry.count > 1
    EntrySurface(if (grouped) ({ expanded = !expanded }) else onClick, modifier) {
        Column(Modifier.animateContentSize()) {
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
                        if (grouped) "${clock.format(Date(entry.startUtc))}–${clock.format(Date(entry.timeUtc))}" else clock.format(Date(entry.timeUtc)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when {
                            !grouped -> if (entry.isSmb) "Automatic · SMB" else "Insulin bolus"
                            entry.isSmb -> "Automatic · ${entry.count} SMBs"
                            else -> "${entry.count} insulin doses"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                BigValue(formatUnits(entry.units), "U", Bolus)
            }
            if (grouped && expanded) {
                Column(Modifier.padding(start = 72.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.parts.forEach { part ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${clock.format(Date(part.timeUtc))} · ${if (part.isSmb) "Automatic" else "Bolus"}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("${formatUnits(part.units)} U", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Bolus)
                        }
                    }
                }
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

/** A device or therapy milestone (pod/site change, profile switch, note...). */
@Composable
fun PumpEventCard(entry: PumpEventUi, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    EntrySurface(onClick, modifier) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(Device.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                val tint = if (entry.icon == PumpEventIcon.WARNING) Warning else Device
                when (entry.icon) {
                    PumpEventIcon.PUMP -> Icon(painterResource(R.drawable.ic_insulin_pump), null, tint = tint, modifier = Modifier.size(24.dp))
                    PumpEventIcon.SENSOR -> Icon(painterResource(R.drawable.ic_sensor), null, tint = tint, modifier = Modifier.size(24.dp))
                    PumpEventIcon.WARNING -> Icon(Icons.Filled.Warning, null, tint = tint, modifier = Modifier.size(24.dp))
                    PumpEventIcon.GENERIC -> Icon(Icons.Filled.Build, null, tint = tint, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    clock.format(Date(entry.timeUtc)) + (entry.durationMinutes?.let { " · ${formatDurationMinutes(it)}" } ?: ""),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!entry.notes.isNullOrBlank()) {
                    Text(
                        entry.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                        color = if (entry.icon == PumpEventIcon.WARNING) Warning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // The values of a profile switch / target, right-aligned like the numbers on meal cards, coloured like their charts.
            val hasValues = entry.percentage != null || entry.timeShiftHours != null || entry.target != null
            if (hasValues) {
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    entry.target?.let { t ->
                        SwitchValue(t.valueText.substringBefore(' '), t.unit, InRange)
                    }
                    entry.percentage?.let { SwitchValue("$it", "%", Basal) }
                    entry.timeShiftHours?.let { SwitchValue("${if (it > 0) "+" else "−"}${kotlin.math.abs(it)}", "h shift", MaterialTheme.colorScheme.tertiary) }
                }
            }
        }
    }
}

@Composable
private fun SwitchValue(value: String, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.width(3.dp))
        Text(unit, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(bottom = 2.dp))
    }
}

private fun SleepStage.color(): Color = when (this) {
    SleepStage.AWAKE -> uk.scimone.diafit.ui.theme.SleepAwake
    SleepStage.REM -> uk.scimone.diafit.ui.theme.SleepRem
    SleepStage.LIGHT -> uk.scimone.diafit.ui.theme.SleepLight
    SleepStage.DEEP -> uk.scimone.diafit.ui.theme.SleepDeep
    SleepStage.SLEEPING -> uk.scimone.diafit.ui.theme.Sleep
}

/** A night's sleep: when, how long, and how it split into stages. */
@Composable
fun SleepCard(entry: SleepEntryUi, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    EntrySurface(onClick, modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(Sleep.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Bedtime, null, tint = Sleep, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${clock.format(Date(entry.startUtc))} – ${clock.format(Date(entry.endUtc))}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Sleep", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatDuration(entry.asleepMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Sleep)
                    Text(" asleep", style = MaterialTheme.typography.labelSmall, color = Sleep, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            if (entry.hasStages) {
                val total = entry.stageMs.values.sum().coerceAtLeast(1L)
                Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Deep first, awake last: the shape of a good night at a glance.
                    listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM, SleepStage.AWAKE).forEach { stage ->
                        val ms = entry.stageMs[stage] ?: return@forEach
                        Box(Modifier.weight(ms / total.toFloat()).fillMaxHeight().background(stage.color()))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM, SleepStage.AWAKE).forEach { stage ->
                        val ms = entry.stageMs[stage] ?: return@forEach
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(stage.color(), CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text("${stage.label} ${formatDuration(ms)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** A workout: type, when, how long and the heart rate it showed. */
@Composable
fun ExerciseCard(entry: ExerciseEntryUi, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    EntrySurface(onClick, modifier) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(Activity.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, tint = Activity, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${clock.format(Date(entry.timeUtc))} – ${clock.format(Date(entry.endUtc))}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatDuration(entry.durationMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Activity)
                }
                if (entry.avgBpm != null) {
                    Text(
                        "avg ${entry.avgBpm}" + (entry.maxBpm?.let { " · max $it" } ?: "") + " bpm",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
