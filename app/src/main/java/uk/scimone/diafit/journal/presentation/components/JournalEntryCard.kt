package uk.scimone.diafit.journal.presentation.components

import uk.scimone.diafit.home.presentation.components.UriMosaic
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Restaurant
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
fun JournalEntryCard(entry: JournalEntryUi, target: GlucoseTargetRange, onClick: (() -> Unit)?, modifier: Modifier = Modifier, onAddPhoto: ((android.net.Uri) -> Unit)? = null) {
    when (entry) {
        is MealEntityUi -> MealCard(entry, target, onClick ?: {}, modifier, onAddPhoto)
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
fun MealCard(meal: MealEntityUi, target: GlucoseTargetRange, onClick: () -> Unit, modifier: Modifier = Modifier, onAddPhoto: ((android.net.Uri) -> Unit)? = null) {
    if (meal.photoUris.isEmpty()) {
        PhotolessMealCard(meal, onClick, modifier, onAddPhoto)
        return
    }
    EntrySurface(onClick, modifier) {
        Row(Modifier.entryRow(), verticalAlignment = Alignment.CenterVertically) {
            MealThumbnail(meal)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // The start time only for multi-course meals: the full range doesn't fit next to the badge.
                        (if (meal.courseCount > 1) meal.timeFormatted.substringBefore('–') else meal.timeFormatted) + " · " + meal.mealType.type,
                        style = MaterialTheme.typography.labelMedium,
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
                Text(meal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // The numbers to read at a glance.
            Column(Modifier.width(MEAL_VALUES_WIDTH), horizontalAlignment = Alignment.End) {
                BigValue("${meal.carbohydrates}", "g", Carbs)
                meal.insulinUnits?.takeIf { it > 0.05 }?.let { BigValue(formatUnits(it), "U", Bolus) }
            }
        }
    }
}

/** A meal without a photo: a standard-size icon row like the other entries, no glucose outcome, and a one-tap way to add a photo. */
@Composable
private fun PhotolessMealCard(meal: MealEntityUi, onClick: () -> Unit, modifier: Modifier, onAddPhoto: ((android.net.Uri) -> Unit)?) {
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onAddPhoto?.invoke(uri) }
    EntrySurface(onClick, modifier) {
        Row(Modifier.entryRow(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Carbs.copy(alpha = 0.16f)).let {
                    if (onAddPhoto != null) it.clickable(onClickLabel = "Add photo") {
                        picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else it
                },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (onAddPhoto != null) Icons.Outlined.AddAPhoto else Icons.Outlined.Restaurant, if (onAddPhoto != null) "Add photo" else null, tint = Carbs, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (meal.courseCount > 1) meal.timeFormatted.substringBefore('–') else meal.timeFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    HeadingDot()
                    AbsorptionBadge(meal.impactType)
                    if (meal.aapsLinked) {
                        HeadingDot()
                        Text("AAPS", style = MaterialTheme.typography.labelSmall, color = Bolus, maxLines = 1, softWrap = false)
                    }
                }
                Text(meal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.width(MEAL_VALUES_WIDTH), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BigValue("${meal.carbohydrates}", "g", Carbs)
                meal.insulinUnits?.takeIf { it > 0.05 }?.let { BigValue(formatUnits(it), "U", Bolus) }
            }
        }
    }
}

@Composable
private fun HeadingDot() {
    Text(" · ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
}

/** Fixed, so the numbers column is the same width on every meal card. */
private val MEAL_VALUES_WIDTH = 56.dp

/** Every journal card is this tall (icon tile 48 dp + padding), whatever its kind. */
private val ENTRY_HEIGHT = 72.dp
private fun Modifier.entryRow() = this.fillMaxWidth().height(ENTRY_HEIGHT).padding(horizontal = 12.dp)

/** The figure on the right of a card: the same type size on every kind; the unit sits beside it, or under it with [unitBelow]. */
@Composable
private fun BigValue(value: String, unit: String, color: Color, unitBelow: Boolean = false) {
    val valueText: @Composable () -> Unit = {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1, softWrap = false)
    }
    if (unitBelow) {
        Column(horizontalAlignment = Alignment.End) {
            valueText()
            Text(unit, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, softWrap = false)
        }
    } else {
        Row(verticalAlignment = Alignment.Bottom) {
            valueText()
            Text(" $unit", style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

/** The cover photo (or meal-type tile) at the size of the other entries' icons; "+N" when there are more photos. */
@Composable
private fun MealThumbnail(meal: MealEntityUi) {
    val size = 48.dp
    val photos = meal.photoUris
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp))) {
        if (photos.isEmpty()) MealTypeTile(meal.mealType, size)
        else AsyncImage(model = photos.first(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (photos.size > 1) {
            Text(
                "+${photos.size - 1}",
                Modifier.align(Alignment.BottomEnd).padding(3.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).padding(horizontal = 5.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
    }
}

fun glucoseColor(mgdl: Int, target: GlucoseTargetRange): Color = when {
    mgdl < target.lowerBound -> BelowRange
    mgdl > target.upperBound -> AboveRange
    else -> InRange
}

/** "112 → peak 186 · 1h 43m": what glucose did after a meal, or null when nothing is known. Shown beside the marker in the Journal graph. */
fun mealPeakText(meal: MealEntityUi, target: GlucoseTargetRange): androidx.compose.ui.text.AnnotatedString? {
    val start = meal.startMgdl
    val peak = meal.peakMgdl
    if (start == null && peak == null) return null
    return buildAnnotatedString {
        fun value(mgdl: Int) = withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = glucoseColor(mgdl, target))) { append("$mgdl") }
        start?.let { value(it) }
        if (peak != null && (start == null || peak > start)) {
            append(if (start != null) " → peak " else "peak ")
            value(peak)
            if (start != null) meal.peakTimeUtc?.let {
                val mins = ((it - meal.mealTimeUtc) / 60_000).toInt().coerceAtLeast(0)
                append(" · ${if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"}")
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
        Row(Modifier.entryRow(), verticalAlignment = Alignment.CenterVertically) {
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
                BigValue("${episode.extremeMgdl}", if (episode.isLow) "lowest mg/dL" else "highest mg/dL", color, unitBelow = true)
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
            Row(Modifier.entryRow(), verticalAlignment = Alignment.CenterVertically) {
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
        Row(Modifier.entryRow(), verticalAlignment = Alignment.CenterVertically) {
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
                        entry.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                        BigValue(t.valueText.substringBefore(' '), t.unit, InRange, unitBelow = true)
                    }
                    entry.percentage?.let { BigValue("$it", "%", Basal) }
                    entry.timeShiftHours?.let { BigValue("${if (it > 0) "+" else "−"}${kotlin.math.abs(it)}", "h shift", MaterialTheme.colorScheme.tertiary) }
                }
            }
        }
    }
}

internal fun SleepStage.stageColor(): Color = when (this) {
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
        Column {
            Row(Modifier.entryRow(), verticalAlignment = Alignment.CenterVertically) {
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
                BigValue(formatDuration(entry.asleepMs), "asleep", Sleep)
            }
        }
    }
}

/** A workout: type, when, how long and the heart rate it showed. */
@Composable
fun ExerciseCard(entry: ExerciseEntryUi, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    EntrySurface(onClick, modifier) {
        Row(Modifier.entryRow(), verticalAlignment = Alignment.CenterVertically) {
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
                BigValue(formatDuration(entry.durationMs), "", Activity)
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
