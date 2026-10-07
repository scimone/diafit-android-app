package uk.scimone.diafit.profile.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import uk.scimone.diafit.core.domain.model.formatDurationMinutes
import uk.scimone.diafit.core.domain.model.valueAt
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale

internal val IsfColor = Bolus
internal val TargetColor = InRange

/**
 * The profile AAPS is running (read-only, imported from its latest Profile Switch): who/what percentage
 * and until when, the values in force right now, and the 24 h schedules for basal, sensitivity and carb ratio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(userId: Int, onBack: () -> Unit, viewModel: ProfileViewModel = koinViewModel(parameters = { parametersOf(userId) })) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        val current = state.latest
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.loaded -> Unit
                current == null -> EmptyProfile()
                else -> ProfileContent(current)
            }
        }
    }
}

@Composable
private fun ProfileContent(current: ProfileSwitch) {
    // Always the profile as saved in AAPS (100 %), whatever temporary percentage switch was done recently.
    val shown = current.profile ?: return
    val nowSecond = remember { LocalTime.now().toSecondOfDay() }
    val mmol = shown.isMmol
    val scrub = rememberScrubState()

    Column(Modifier.fillMaxSize()) {
        // Sticky: the profile name and the values in force right now.
        Surface(tonalElevation = 2.dp, shadowElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TitleRow(current.baseName)
                NowRow(shown, nowSecond)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScheduleCard(
                    title = "Basal",
                    subtitle = "Background insulin, ${fmt(shown.totalDailyBasal, 2)} U per day",
                    color = Bolus
                ) { StepChart(shown.basal, Bolus, nowSecond, { fmt(it, 2) }, "U/h", scrubState = scrub) }
            }
            item {
                ScheduleCard(
                    title = "Insulin sensitivity",
                    subtitle = "1 U lowers glucose by this many ${if (mmol) "mmol/L" else "mg/dL"}",
                    color = IsfColor
                ) { StepChart(shown.isf, IsfColor, nowSecond, { fmt(it, if (mmol) 1 else 0) }, if (mmol) "mmol/L per U" else "mg/dL per U", scrubState = scrub) }
            }
            item {
                ScheduleCard(
                    title = "Carb ratio",
                    subtitle = "1 U covers this many grams of carbs",
                    color = Carbs
                ) { StepChart(shown.carbRatio, Carbs, nowSecond, { fmt(it, 1) }, "g per U", scrubState = scrub) }
            }
            item { TargetCard(shown, nowSecond) }

            shown.diaHours?.let { dia ->
                item {
                    Text(
                        "Insulin action time (DIA): ${fmt(dia, 1)} h",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            item {
                Text(
                    "Read-only. This is imported from AAPS whenever you do a profile switch there; change it in AAPS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TitleRow(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** The four numbers in force at this moment, as tiles. */
@Composable
private fun NowRow(profile: AapsProfile, nowSecond: Int) {
    val mmol = profile.isMmol
    val target = profile.targetLow.valueAt(nowSecond)?.let { low ->
        val high = profile.targetHigh.valueAt(nowSecond) ?: low
        if (high != low) "${fmt(low, 0)}–${fmt(high, 0)}" else fmt(low, 0)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Right now", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NowTile("Basal", profile.basal.valueAt(nowSecond)?.let { fmt(it, 2) }, "U/h", Bolus, Modifier.weight(1f))
            NowTile("Sensitivity", profile.isf.valueAt(nowSecond)?.let { fmt(it, if (mmol) 1 else 0) }, if (mmol) "mmol/L per U" else "mg/dL per U", IsfColor, Modifier.weight(1f))
            NowTile("Carb ratio", profile.carbRatio.valueAt(nowSecond)?.let { fmt(it, 1) }, "g per U", Carbs, Modifier.weight(1f))
            NowTile("Target", target, if (mmol) "mmol/L" else "mg/dL", TargetColor, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NowTile(label: String, value: String?, unit: String, color: Color, modifier: Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
internal fun ScheduleCard(title: String, subtitle: String, color: Color, chart: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            chart()
        }
    }
}

/** Targets change rarely, so they read better as a short list of time ranges than as a chart. */
@Composable
private fun TargetCard(profile: AapsProfile, nowSecond: Int) {
    val lows = profile.targetLow.sortedBy { it.startSeconds }
    if (lows.isEmpty()) return
    val unit = if (profile.isMmol) "mmol/L" else "mg/dL"
    ScheduleCard("Glucose target", "AAPS aims for this $unit", TargetColor) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lows.forEachIndexed { i, step ->
                val end = lows.getOrNull(i + 1)?.startSeconds ?: AapsProfile.SECONDS_PER_DAY
                val high = profile.targetHigh.valueAt(step.startSeconds) ?: step.value
                val isNow = nowSecond in step.startSeconds until end
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${clock(step.startSeconds)}–${clock(end)}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isNow) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isNow) Text("now  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (high != step.value) "${fmt(step.value, 0)}–${fmt(high, 0)}" else fmt(step.value, 0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TargetColor
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProfile() {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Person, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Text("No profile received yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(4.dp))
        Text(
            "Diafit gets your profile from AAPS when you do a profile switch there. Do one (for example the same profile at 100%) and it appears here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun Card(color: Color = MaterialTheme.colorScheme.surface, content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        content = content
    )
}

internal fun clock(seconds: Int): String = "%02d:%02d".format((seconds / 3600) % 24, (seconds / 60) % 60)

/** [decimals] places, trailing zeros trimmed ("0.5", "84", "13"). */
internal fun fmt(v: Double, decimals: Int): String =
    "%.${decimals}f".format(Locale.US, v).let { if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it }
