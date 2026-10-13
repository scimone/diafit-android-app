package uk.scimone.diafit.logs.presentation.components

import uk.scimone.diafit.core.presentation.formatStamp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.R
import coil3.compose.AsyncImage
import uk.scimone.diafit.logs.presentation.model.LogsEntryKind
import uk.scimone.diafit.ui.theme.Carbs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A meal still in progress, offered at the top of the + sheet so further courses join it. */
data class OpenMealSummary(
    /** Any course of the meal; the editor resolves the whole meal from it. */
    val anchorMealId: Int,
    val title: String,
    val courseCount: Int,
    val totalCarbs: Int,
    val startTime: Long,
    val coverPhoto: Uri?
)

/**
 * "What do you want to log?" chooser opened by the + button. Kinds that aren't built yet are shown,
 * disabled. While a meal is in progress (e.g. plates still arriving at a sushi bar) the first and
 * largest choice is adding a course to it, one tap from the + button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEntrySheet(
    onDismiss: () -> Unit,
    onPick: (LogsEntryKind) -> Unit,
    openMeal: OpenMealSummary? = null,
    onAddCourse: (anchorMealId: Int) -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.logs_new_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            openMeal?.let {
                AddCourseRow(it, onClick = { onAddCourse(it.anchorMealId) })
                Text(
                    stringResource(R.string.logs_new_other),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }
            LogsEntryKind.creatableKinds.forEach { kind ->
                KindRow(kind, onClick = { onPick(kind) }, labelOverride = if (kind == LogsEntryKind.MEAL && openMeal != null) stringResource(R.string.logs_new_meal) else null)
            }
        }
    }
}

@Composable
private fun AddCourseRow(meal: OpenMealSummary, onClick: () -> Unit) {
    val time = remember(meal.startTime) { formatStamp(meal.startTime, "Hm") }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Carbs.copy(alpha = 0.14f),
        border = BorderStroke(1.5.dp, Carbs.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Carbs.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                if (meal.coverPhoto != null) {
                    AsyncImage(meal.coverPhoto, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                } else {
                    Icon(painterResource(R.drawable.ic_meal_type_lunch), null, tint = Carbs, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.logs_new_add_course), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.logs_new_to_meal, meal.title),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pluralStringResource(R.plurals.logs_new_since, meal.courseCount, time, meal.courseCount, meal.totalCarbs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.AddCircle, null, tint = Carbs, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun KindRow(kind: LogsEntryKind, onClick: () -> Unit, labelOverride: String? = null) {
    Surface(
        onClick = onClick,
        enabled = kind.available,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().alpha(if (kind.available) 1f else 0.55f)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = kind.accent.copy(alpha = 0.18f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    when (kind) {
                        LogsEntryKind.MEAL -> Icon(painterResource(R.drawable.ic_meal_type_lunch), null, tint = kind.accent, modifier = Modifier.size(26.dp))
                        else -> Icon(kind.vector(), null, tint = kind.accent, modifier = Modifier.size(26.dp))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(labelOverride ?: stringResource(kind.labelResId), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(kind.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!kind.available) {
                Text(
                    stringResource(R.string.logs_new_soon),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun LogsEntryKind.vector(): ImageVector = when (this) {
    LogsEntryKind.SLEEP -> Icons.Filled.Bedtime
    else -> Icons.AutoMirrored.Filled.DirectionsRun
}


