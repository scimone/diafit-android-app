package uk.scimone.diafit.core.presentation

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.scimone.diafit.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** A date/time format for the current locale from a skeleton (`"EEEdMMMHm"`), so month and weekday names follow the language. */
fun localizedFormat(skeleton: String, locale: Locale = Locale.getDefault()): SimpleDateFormat =
    SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

fun formatStamp(utc: Long, skeleton: String): String = localizedFormat(skeleton).format(Date(utc))

/** Compact: "3 d", "1 d 5 h", "5 h", "30 min". */
fun Resources.shortDuration(ms: Long): String {
    val minutes = abs(ms) / 60_000
    val days = (minutes / 1440).toInt()
    val hours = ((minutes % 1440) / 60).toInt()
    return when {
        days >= 2 -> getString(R.string.duration_d, days)
        days == 1 -> getString(R.string.duration_dh, 1, hours)
        hours >= 1 -> getString(R.string.duration_h, hours)
        else -> getString(R.string.duration_m, minutes.toInt())
    }
}

/** Two units: "3 d 4 h", "5 h 10 min", "30 min". */
fun Resources.duration(ms: Long): String {
    val minutes = abs(ms) / 60_000
    val days = (minutes / 1440).toInt()
    val hours = ((minutes % 1440) / 60).toInt()
    return when {
        days > 0 -> getString(R.string.duration_dh, days, hours)
        hours > 0 -> getString(R.string.duration_hm, hours, (minutes % 60).toInt())
        else -> getString(R.string.duration_m, minutes.toInt())
    }
}

@Composable
fun rememberResources(): Resources = LocalContext.current.resources
@Composable
fun rememberLocalizedFormat(skeleton: String) = remember(skeleton, Locale.getDefault()) { localizedFormat(skeleton) }
