package uk.scimone.diafit.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.core.domain.model.AapsProfile
import uk.scimone.diafit.core.domain.model.ProfileStep
import uk.scimone.diafit.core.domain.model.ProfileSwitch
import uk.scimone.diafit.core.domain.model.ScheduleChange
import uk.scimone.diafit.core.domain.model.diffSchedules
import uk.scimone.diafit.core.domain.model.effectiveProfileAt
import uk.scimone.diafit.core.domain.model.formatDurationMinutes
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.Device
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * A Profile Switch as a diff: what AAPS was running just before (previous switch, at its percentage, or at 100%
 * once a temporary one had ended) against what it runs after this switch. Charts show the old schedule as a dashed
 * line behind the new one; below each, the stretches of the day that changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitchDetailScreen(
    userId: Int,
    eventId: Int,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: ProfileSwitchDetailViewModel = koinViewModel(key = "profile-switch-$eventId", parameters = { parametersOf(userId, eventId) })
) {
    val state by viewModel.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile switch") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (state.current != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val current = state.current
            when {
                !state.loaded -> Unit
                current == null -> Text("This event is no longer available.", Modifier.padding(24.dp))
                else -> DiffContent(current, state.previous)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this profile switch?") },
            text = { Text("Imported from AAPS. Deleting only removes it from Diafit.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onDeleted) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DiffContent(current: ProfileSwitch, previous: ProfileSwitch?) {
    val after = current.profile?.atPercentage(current.percentage)
    val before = previous?.effectiveProfileAt(current.startUtc)
    val beforePercentage = previous?.let { if (it.endUtc?.let { end -> end <= current.startUtc } == true) 100 else it.percentage }
    val nowSecond = remember { LocalTime.now().toSecondOfDay() }
    val day = remember { SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Device.copy(alpha = 0.12f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(day.format(Date(current.startUtc)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(current.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (current.durationMinutes > 0) {
                        Text("For ${formatDurationMinutes(current.durationMinutes)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (previous != null) "Compared with ${previous.baseName}${if (beforePercentage != 100) " at $beforePercentage%" else ""}, which was running before"
                        else "No earlier profile switch to compare with",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (after == null) {
            item { Text("This switch carried no profile values.", style = MaterialTheme.typography.bodyMedium) }
            return@LazyColumn
        }

        item { SummaryCard(previous, beforePercentage, current, before, after) }

        item {
            val changes = before?.let { diffSchedules(it.basal, after.basal) }.orEmpty()
            ScheduleDiffCard(
                title = "Basal", color = Bolus, unit = "U/h", decimals = 2,
                after = after.basal, before = before?.basal, changes = changes, nowSecond = nowSecond,
                subtitle = if (before != null && changes.isNotEmpty())
                    "Per day: ${fmt(before.totalDailyBasal, 2)} → ${fmt(after.totalDailyBasal, 2)} U"
                else "${fmt(after.totalDailyBasal, 2)} U per day"
            )
        }
        item {
            val mmol = after.isMmol
            ScheduleDiffCard(
                title = "Insulin sensitivity", color = IsfColor, unit = if (mmol) "mmol/L per U" else "mg/dL per U", decimals = if (mmol) 1 else 0,
                after = after.isf, before = before?.isf, changes = before?.let { diffSchedules(it.isf, after.isf) }.orEmpty(), nowSecond = nowSecond,
                subtitle = "1 U lowers glucose by this many ${if (mmol) "mmol/L" else "mg/dL"}"
            )
        }
        item {
            ScheduleDiffCard(
                title = "Carb ratio", color = Carbs, unit = "g per U", decimals = 1,
                after = after.carbRatio, before = before?.carbRatio, changes = before?.let { diffSchedules(it.carbRatio, after.carbRatio) }.orEmpty(), nowSecond = nowSecond,
                subtitle = "1 U covers this many grams of carbs"
            )
        }
        item { TargetDiffCard(before, after) }
    }
}

/** The headline of the diff: profile, percentage and insulin action time, before → after. */
@Composable
private fun SummaryCard(previous: ProfileSwitch?, beforePercentage: Int?, current: ProfileSwitch, before: AapsProfile?, after: AapsProfile) {
    val rows = buildList {
        if (previous != null && previous.baseName != current.baseName) add("Profile" to "${previous.baseName} → ${current.baseName}")
        if (beforePercentage != null && beforePercentage != current.percentage) add("Percentage" to "$beforePercentage% → ${current.percentage}%")
        val dBefore = before?.diaHours
        val dAfter = after.diaHours
        if (dBefore != null && dAfter != null && abs(dBefore - dAfter) > 1e-6) add("Insulin action time" to "${fmt(dBefore, 1)} h → ${fmt(dAfter, 1)} h")
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What changed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (previous == null) {
                Text("This is the first switch Diafit has seen, so the values below are just what it sets.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (rows.isEmpty()) {
                Text("Same profile and percentage as before; see the schedules below.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rows.forEach { (label, change) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(change, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (previous != null) {
                Text(
                    "Values compare what AAPS actually runs, with the percentage applied.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScheduleDiffCard(
    title: String,
    subtitle: String,
    color: Color,
    unit: String,
    decimals: Int,
    after: List<ProfileStep>,
    before: List<ProfileStep>?,
    changes: List<ScheduleChange>,
    nowSecond: Int
) {
    val unchanged = before != null && changes.isEmpty()
    ScheduleCard(title + if (unchanged) " · no change" else "", subtitle, color) {
        StepChart(after, color, nowSecond, { fmt(it, decimals) }, unit, previous = before.takeIf { !unchanged })
        if (changes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Dashed line = before", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                changes.forEach { c -> ChangeRow("${clock(c.startSeconds)}–${clock(c.endSeconds)}", c.before, c.after, decimals, unit, color) }
            }
        }
    }
}

@Composable
private fun TargetDiffCard(before: AapsProfile?, after: AapsProfile) {
    if (after.targetLow.isEmpty()) return
    val low = before?.let { diffSchedules(it.targetLow, after.targetLow) }.orEmpty()
    val high = before?.let { diffSchedules(it.targetHigh, after.targetHigh) }.orEmpty()
    val unit = if (after.isMmol) "mmol/L" else "mg/dL"
    val unchanged = before != null && low.isEmpty() && high.isEmpty()
    ScheduleCard("Glucose target" + if (unchanged) " · no change" else "", "AAPS aims for this $unit", TargetColor) {
        if (unchanged || before == null) {
            val sorted = after.targetLow.sortedBy { it.startSeconds }
            sorted.forEachIndexed { i, s ->
                val end = sorted.getOrNull(i + 1)?.startSeconds ?: AapsProfile.SECONDS_PER_DAY
                Row {
                    Text("${clock(s.startSeconds)}–${clock(end)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmt(s.value, 0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TargetColor)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val same = low == high
                low.forEach { c -> ChangeRow("${clock(c.startSeconds)}–${clock(c.endSeconds)}", c.before, c.after, 0, unit, TargetColor, if (same) null else "Low") }
                if (!same) high.forEach { c -> ChangeRow("${clock(c.startSeconds)}–${clock(c.endSeconds)}", c.before, c.after, 0, unit, TargetColor, "High") }
            }
        }
    }
}

/** Exactly [decimals] places, so the rows of one card line up ("0.40", not "0.4"). */
private fun fixed(v: Double, decimals: Int): String = "%.${decimals}f".format(Locale.US, v)

/** "06:00–07:00   0.40 → 0.45 U/h   +0.05". */
@Composable
private fun ChangeRow(range: String, before: Double, after: Double, decimals: Int, unit: String, color: Color, tag: String? = null) {
    val delta = after - before
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (tag != null) "$range · $tag" else range,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${fixed(before, decimals)} → ${fixed(after, decimals)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            "  ${if (delta > 0) "+" else "−"}${fixed(abs(delta), decimals)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
