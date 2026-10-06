package uk.scimone.diafit.journal.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.R
import uk.scimone.diafit.journal.presentation.model.JournalEntryKind

/** "What do you want to log?" chooser opened by the + button. Kinds that aren't built yet are shown, disabled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEntrySheet(onDismiss: () -> Unit, onPick: (JournalEntryKind) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add to journal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            JournalEntryKind.entries.forEach { kind ->
                KindRow(kind, onClick = { onPick(kind) })
            }
        }
    }
}

@Composable
private fun KindRow(kind: JournalEntryKind, onClick: () -> Unit) {
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
                Text(kind.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
