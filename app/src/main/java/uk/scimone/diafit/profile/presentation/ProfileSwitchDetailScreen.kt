package uk.scimone.diafit.profile.presentation

import uk.scimone.diafit.core.presentation.AppStrings
import uk.scimone.diafit.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import uk.scimone.diafit.ui.theme.Basal
import uk.scimone.diafit.core.domain.model.DayWindow
import uk.scimone.diafit.core.domain.model.TemporaryTarget
import uk.scimone.diafit.core.domain.model.valueAt
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import uk.scimone.diafit.core.domain.model.basalInsulin
import uk.scimone.diafit.core.domain.model.clipChanges
import uk.scimone.diafit.core.domain.model.effectiveProfile
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Surface
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
                title = { Text(stringResource(R.string.logs_pump_profile_switch)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
                actions = {
                    if (state.current != null || state.target != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.common_delete)) }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val current = state.current
            when {
                !state.loaded -> Unit
                current != null -> DiffContent(current, state.previous, state.target)
                state.target != null -> TargetOnlyContent(state.target!!, state.previous)
                else -> Text(stringResource(R.string.profile_gone), Modifier.padding(24.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = { Text(stringResource(R.string.profile_delete_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onDeleted) }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun DiffContent(current: ProfileSwitch, previous: ProfileSwitch?, target: TemporaryTarget?) {
    val after = current.effectiveProfile()
    // What AAPS ran before: the previous switch's profile, or else this same saved profile unchanged.
    val before = previous?.effectiveProfileAt(current.startUtc) ?: current.profile
    val beforeEnded = previous?.endUtc?.let { it <= current.startUtc } == true
    val beforePercentage = previous?.let { if (beforeEnded) 100 else it.percentage } ?: 100
    val beforeShift = previous?.let { if (beforeEnded) 0 else it.timeShiftHours } ?: 0
    val beforeName = previous?.baseName ?: current.baseName
    val nowSecond = remember { LocalTime.now().toSecondOfDay() }
    val scrub = rememberScrubState()
    val day = remember(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]) { SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()) }

    // A switch is always temporary (max 168 h). Only the stretch it was in force is compared; a day or longer shows one day.
    val lengthSeconds = (if (current.durationMinutes > 0) current.durationMinutes * 60 else AapsProfile.SECONDS_PER_DAY)
    val startSecond = remember(current.startUtc) {
        java.time.Instant.ofEpochMilli(current.startUtc).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toSecondOfDay()
    }
    val window = DayWindow(startSecond, minOf(lengthSeconds, AapsProfile.SECONDS_PER_DAY))
    val highlight = if (window.isFullDay) emptyList() else window.intervals

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Device.copy(alpha = 0.12f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(day.format(Date(current.startUtc)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(current.baseName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        if (current.percentage != 100) TypeChip("${current.percentage}%")
                        if (current.timeShiftHours != 0) TypeChip(stringResource(R.string.profile_chip_shift, "${if (current.timeShiftHours > 0) "+" else "−"}${abs(current.timeShiftHours)}"))
                        if (target != null) TypeChip(stringResource(R.string.profile_chip_target, target.valueText))
                        if (current.percentage == 100 && current.timeShiftHours == 0 && target == null) TypeChip(stringResource(R.string.profile_chip_profile_only))
                    }
                    Text(
                        if (current.durationMinutes > 0) stringResource(R.string.profile_in_force, formatDurationMinutes(current.durationMinutes)) else stringResource(R.string.profile_no_end),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (window.isFullDay && current.durationMinutes > 24 * 60) stringResource(R.string.profile_compared_day, beforeName, beforePercentage) else stringResource(R.string.profile_compared, beforeName, beforePercentage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (after == null || before == null) {
            item { Text(stringResource(R.string.profile_no_values), style = MaterialTheme.typography.bodyMedium) }
            return@LazyColumn
        }

        val basalChanges = clipChanges(diffSchedules(before.basal, after.basal), window)
        val isfChanges = clipChanges(diffSchedules(before.isf, after.isf), window)
        val carbChanges = clipChanges(diffSchedules(before.carbRatio, after.carbRatio), window)
        val targetChanged = clipChanges(diffSchedules(before.targetLow, after.targetLow), window).isNotEmpty() ||
            clipChanges(diffSchedules(before.targetHigh, after.targetHigh), window).isNotEmpty()
        val nothingElse = basalChanges.isEmpty() && isfChanges.isEmpty() && carbChanges.isEmpty() && !targetChanged

        // Only the parts this switch actually touches are shown.
        if (!nothingElse || target == null) {
            item { SummaryCard(beforeName, beforePercentage, beforeShift, current, before, after, window, lengthSeconds, showInsulin = basalChanges.isNotEmpty()) }
        }
        if (target != null) item { TemporaryTargetCard(target, before, current.startUtc, lengthSeconds) }
        if (basalChanges.isNotEmpty()) {
            item {
                ScheduleDiffCard(
                    title = stringResource(R.string.panel_basal), color = Basal, unit = "U/h", decimals = 2,
                    after = after.basal, before = before.basal, changes = basalChanges, nowSecond = nowSecond, scrub = scrub, highlight = highlight,
                    subtitle = stringResource(R.string.profile_basal_diff_sub)
                )
            }
        }
        if (isfChanges.isNotEmpty()) {
            item {
                val mmol = after.isMmol
                ScheduleDiffCard(
                    title = stringResource(R.string.profile_isf), color = IsfColor, unit = stringResource(if (mmol) R.string.profile_unit_isf_mmol else R.string.profile_unit_isf_mgdl), decimals = if (mmol) 1 else 0,
                    after = after.isf, before = before.isf, changes = isfChanges, nowSecond = nowSecond, scrub = scrub, highlight = highlight,
                    subtitle = stringResource(if (mmol) R.string.profile_isf_sub_mmol else R.string.profile_isf_sub_mgdl)
                )
            }
        }
        if (carbChanges.isNotEmpty()) {
            item {
                ScheduleDiffCard(
                    title = stringResource(R.string.profile_carb_ratio), color = Carbs, unit = stringResource(R.string.profile_unit_carbs), decimals = 1,
                    after = after.carbRatio, before = before.carbRatio, changes = carbChanges, nowSecond = nowSecond, scrub = scrub, highlight = highlight,
                    subtitle = stringResource(R.string.profile_carb_ratio_sub)
                )
            }
        }
        if (target == null && targetChanged) item { TargetDiffCard(before, after, window) }
    }
}

/** A temporary target on its own: the same page, with only the target card (and what AAPS was running at the time for the "before" value). */
@Composable
private fun TargetOnlyContent(target: TemporaryTarget, context: ProfileSwitch?) {
    val day = remember(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]) { SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()) }
    val before = context?.effectiveProfileAt(target.startUtc)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Device.copy(alpha = 0.12f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(day.format(Date(target.startUtc)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(context?.baseName ?: stringResource(R.string.logs_pump_profile_switch), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.padding(vertical = 4.dp)) { TypeChip(stringResource(R.string.profile_chip_target, target.valueText)) }
                    Text(
                        if (target.durationMinutes > 0) stringResource(R.string.profile_in_force, formatDurationMinutes(target.durationMinutes)) else stringResource(R.string.profile_no_end),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(stringResource(R.string.profile_only_target), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { TemporaryTargetCard(target, before, target.startUtc, (if (target.durationMinutes > 0) target.durationMinutes else 24 * 60) * 60) }
    }
}

@Composable
private fun TemporaryTargetCard(target: TemporaryTarget, before: AapsProfile?, startUtc: Long, lengthSeconds: Int) {
    val zone = java.time.ZoneId.systemDefault()
    val start = java.time.Instant.ofEpochMilli(startUtc).atZone(zone)
    val startSecond = start.toLocalTime().toSecondOfDay()
    val profileLow = before?.targetLow?.valueAt(startSecond)
    val profileHigh = before?.targetHigh?.valueAt(startSecond) ?: profileLow
    val profileText = profileLow?.let { if (profileHigh != null && profileHigh != it) "${fmt(it, 0)}–${fmt(profileHigh, 0)}" else fmt(it, 0) }
    val delta = profileLow?.let { target.low - it }
    val fmtClock = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val end = start.plusSeconds((if (target.durationMinutes > 0) target.durationMinutes * 60 else lengthSeconds).toLong())
    ScheduleCard(stringResource(R.string.logs_pump_temp_target), stringResource(R.string.profile_temp_target_sub), TargetColor) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.profile_profile_target), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(profileText ?: "–", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = TargetColor.copy(alpha = 0.16f)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.profile_temporary), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(target.valueText.substringBefore(' '), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TargetColor)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${start.format(fmtClock)}–${end.format(fmtClock)}" + if (target.reason.isNotEmpty()) " · ${target.reason}" else "",
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (delta != null && abs(delta) > 1e-6) {
                Text("${if (delta > 0) "+" else "−"}${fmt(abs(delta), 0)} ${target.unit}", style = MaterialTheme.typography.labelLarge, color = TargetColor)
            }
        }
    }
}

@Composable
private fun TypeChip(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 2.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** The headline of the diff: what was set, and the effect on basal insulin over the time it was in force. */
@Composable
private fun SummaryCard(
    beforeName: String, beforePercentage: Int, beforeShift: Int,
    current: ProfileSwitch, before: AapsProfile, after: AapsProfile, window: DayWindow, lengthSeconds: Int, showInsulin: Boolean
) {
    val rows = buildList {
        if (beforeName != current.baseName) add(stringResource(R.string.profile_sum_profile) to "$beforeName → ${current.baseName}")
        if (beforePercentage != current.percentage) add(stringResource(R.string.profile_sum_percentage) to "$beforePercentage% → ${current.percentage}%")
        if (beforeShift != current.timeShiftHours) add(stringResource(R.string.profile_sum_time_shift) to "${signedHours(beforeShift)} → ${signedHours(current.timeShiftHours)}")
        val dBefore = before.diaHours
        val dAfter = after.diaHours
        if (dBefore != null && dAfter != null && abs(dBefore - dAfter) > 1e-6) add(stringResource(R.string.profile_sum_dia) to "${fmt(dBefore, 1)} h → ${fmt(dAfter, 1)} h")
    }
    val total = lengthSeconds.coerceAtMost(7 * 24 * 3600)
    val insulinBefore = basalInsulin(before.basal, window.startSeconds, total)
    val insulinAfter = basalInsulin(after.basal, window.startSeconds, total)
    val delta = insulinAfter - insulinBefore
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.profile_what_changed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (rows.isEmpty()) {
                Text(stringResource(R.string.profile_same), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rows.forEach { (label, change) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(change, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (showInsulin) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.profile_basal_over, formatDurationMinutes(total / 60)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (abs(delta) < 0.005) stringResource(R.string.profile_unchanged, fmt(insulinAfter, 2))
                    else "${fmt(insulinBefore, 2)} → ${fmt(insulinAfter, 2)} U (${if (delta > 0) "+" else "−"}${fmt(abs(delta), 2)})",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Basal
                )
            }
            if (showInsulin) Text(
                stringResource(R.string.profile_basal_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun signedHours(h: Int) = if (h == 0) AppStrings.get(R.string.profile_shift_none) else "${if (h > 0) "+" else "−"}${abs(h)} h"

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
    nowSecond: Int,
    scrub: ScrubState,
    highlight: List<Pair<Int, Int>>
) {
    val unchanged = before != null && changes.isEmpty()
    var expanded by remember { mutableStateOf(false) }
    ScheduleCard(if (unchanged) stringResource(R.string.profile_card_no_change, title) else title, subtitle, color) {
        StepChart(after, color, nowSecond, { fmt(it, decimals) }, unit, previous = before.takeIf { !unchanged }, scrubState = scrub, highlight = highlight)
        if (changes.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    pluralStringResource(R.plurals.profile_changed_stretches, changes.size, changes.size),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, stringResource(if (expanded) R.string.common_hide else R.string.hist_show), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    changes.forEach { c -> ChangeRow("${clock(c.startSeconds)}–${clock(c.endSeconds)}", c.before, c.after, decimals, unit, color) }
                }
            }
        }
    }
}

@Composable
private fun TargetDiffCard(before: AapsProfile, after: AapsProfile, window: DayWindow) {
    if (after.targetLow.isEmpty()) return
    val low = clipChanges(diffSchedules(before.targetLow, after.targetLow), window)
    val high = clipChanges(diffSchedules(before.targetHigh, after.targetHigh), window)
    val unit = if (after.isMmol) "mmol/L" else "mg/dL"
    val unchanged = low.isEmpty() && high.isEmpty()
    ScheduleCard(if (unchanged) stringResource(R.string.profile_card_no_change, stringResource(R.string.profile_target)) else stringResource(R.string.profile_target), stringResource(R.string.profile_target_sub, unit), TargetColor) {
        if (unchanged) {
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
                low.forEach { c -> ChangeRow("${clock(c.startSeconds)}–${clock(c.endSeconds)}", c.before, c.after, 0, unit, TargetColor, if (same) null else stringResource(R.string.logs_ep_low)) }
                if (!same) high.forEach { c -> ChangeRow("${clock(c.startSeconds)}–${clock(c.endSeconds)}", c.before, c.after, 0, unit, TargetColor, stringResource(R.string.logs_ep_high)) }
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
