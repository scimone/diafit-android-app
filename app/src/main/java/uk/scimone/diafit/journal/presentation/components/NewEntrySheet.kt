package uk.scimone.diafit.journal.presentation.components

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
import uk.scimone.diafit.journal.presentation.model.JournalEntryKind
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
    onPick: (JournalEntryKind) -> Unit,
    openMeal: OpenMealSummary? = null,
    onAddCourse: (anchorMealId: Int) -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add to journal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            openMeal?.let {
                AddCourseRow(it, onClick = { onAddCourse(it.anchorMealId) })
                Text(
                    "Or start something new",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }
            JournalEntryKind.entries.forEach { kind ->
                KindRow(kind, onClick = { onPick(kind) }, labelOverride = if (kind == JournalEntryKind.MEAL && openMeal != null) "New meal" else null)
            }
        }
    }
}

@Composable
private fun AddCourseRow(meal: OpenMealSummary, onClick: () -> Unit) {
    val time = remember(meal.startTime) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(meal.startTime)) }
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
                Text("Add a course", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "to ${meal.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Since $time · ${meal.courseCount} ${if (meal.courseCount == 1) "course" else "courses"} · ${meal.totalCarbs} g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.AddCircle, null, tint = Carbs, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun KindRow(kind: JournalEntryKind, onClick: () -> Unit, labelOverride: String? = null) {
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
                        JournalEntryKind.MEAL -> Icon(painterResource(R.drawable.ic_meal_type_lunch), null, tint = kind.accent, modifier = Modifier.size(26.dp))
                        else -> Icon(kind.vector(), null, tint = kind.accent, modifier = Modifier.size(26.dp))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(labelOverride ?: kind.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    kind.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!kind.available) {
                Text(
                    "Soon",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun JournalEntryKind.vector(): ImageVector = when (this) {
    JournalEntryKind.SLEEP -> Icons.Filled.Bedtime
    else -> Icons.AutoMirrored.Filled.DirectionsRun
}

private fun JournalEntryKind.description(): String = when (this) {
    JournalEntryKind.MEAL -> "Photo, carbs and absorption speed"
    JournalEntryKind.SLEEP -> "Bedtime, wake-up and quality"
    JournalEntryKind.ACTIVITY -> "Workouts and how hard they were"
}
