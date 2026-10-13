package uk.scimone.diafit.history.presentation

import uk.scimone.diafit.core.presentation.formatStamp
import uk.scimone.diafit.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val MONTHS_LISTED = 24
private fun LocalDate.stamp(): String =
    formatStamp(atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), "dMMMy")

/**
 * Picks a custom History period, either whole finished months (one run of up to three adjacent months) or any own
 * date range of at most three months. The current, unfinished month is not offered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomPeriodDialog(current: CustomPeriod?, onDismiss: () -> Unit, onApply: (CustomPeriod) -> Unit) {
    val today = remember { LocalDate.now() }
    var tab by remember { mutableIntStateOf(0) }
    var months by remember { mutableStateOf(emptyList<YearMonth>()) } // sorted, adjacent
    val dates = rememberDateRangePickerState(
        initialSelectedStartDateMillis = current?.first?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        initialSelectedEndDateMillis = current?.last?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    )
    fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    val picked: CustomPeriod? = if (tab == 0) {
        if (months.isEmpty()) null else CustomPeriod(months.first().atDay(1), months.last().atEndOfMonth())
    } else dates.selectedStartDateMillis?.let { s ->
        val start = day(s)
        CustomPeriod(start, dates.selectedEndDateMillis?.let(::day) ?: start)
    }
    val valid = picked?.isValid(today) == true

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = valid, onClick = { onApply(picked!!) }) { Text(stringResource(R.string.common_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    ) {
        Column(Modifier.height(540.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.hist_cp_months)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.hist_cp_dates)) })
            }
            if (tab == 0) {
                val listed = remember { (1..MONTHS_LISTED).map { YearMonth.from(today).minusMonths(it.toLong()) } }
                LazyColumn(Modifier.weight(1f)) {
                    item {
                        Text(
                            stringResource(R.string.hist_cp_pick, CustomPeriod.MAX_MONTHS),
                            Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(listed) { m ->
                        val on = m in months
                        ListItem(
                            headlineContent = { Text(formatStamp(m.atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), "yMMMM")) },
                            leadingContent = { Checkbox(checked = on, onCheckedChange = null) },
                            modifier = Modifier.clickable { months = toggleMonth(months, m) }
                        )
                    }
                }
            } else {
                DateRangePicker(dates, Modifier.weight(1f), showModeToggle = false)
            }
            val note = when {
                picked == null -> stringResource(R.string.hist_cp_none)
                !valid -> stringResource(R.string.hist_cp_too_long, CustomPeriod.MAX_MONTHS)
                else -> "${picked.first.stamp()} – ${picked.last.stamp()}"
            }
            Text(
                note, Modifier.align(Alignment.CenterHorizontally).padding(8.dp), style = MaterialTheme.typography.labelLarge,
                color = if (picked != null && !valid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Tapping an end of the run shrinks it, an adjacent month extends it (up to the limit), anything else starts a new run. */
internal fun toggleMonth(selected: List<YearMonth>, m: YearMonth): List<YearMonth> = when {
    selected.isEmpty() -> listOf(m)
    m == selected.first() || m == selected.last() -> selected - m
    m in selected -> listOf(m)
    selected.size < CustomPeriod.MAX_MONTHS && m == selected.first().minusMonths(1) -> listOf(m) + selected
    selected.size < CustomPeriod.MAX_MONTHS && m == selected.last().plusMonths(1) -> selected + m
    else -> listOf(m)
}
