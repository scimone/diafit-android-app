package uk.scimone.diafit.notifications.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.AppNotificationEntity
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uk.scimone.diafit.patterns.presentation.PatternCardView
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import uk.scimone.diafit.R
import uk.scimone.diafit.core.domain.model.DeviceKind
import uk.scimone.diafit.patterns.presentation.GlyphTile
import uk.scimone.diafit.core.presentation.formatStamp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.scimone.diafit.ui.theme.Device
import java.util.Locale

/** Recent notifications; everything counts as read once this page is left (so the bell's dot clears). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpen: (link: String?) -> Unit, onOpenPattern: (id: String) -> Unit, onOpenAllPatterns: () -> Unit) {
    val viewModel: NotificationsViewModel = koinViewModel()
    val items by viewModel.recent.collectAsState()
    val patterns by viewModel.patterns.collectAsState()
    val markRead = rememberUpdatedState(viewModel::markAllRead)
    DisposableEffect(Unit) { onDispose { markRead.value() } }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedText = stringResource(R.string.notifications_removed)
    val undoText = stringResource(R.string.action_undo)
    val remove: (AppNotificationEntity) -> Unit = { n ->
        viewModel.remove(n)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(removedText, actionLabel = undoText, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                viewModel.restore(n)
            }
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } }
            )
        }
    ) { padding ->
        if (items.isEmpty() && patterns.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.notifications_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onOpenAllPatterns) { Text(stringResource(R.string.notifications_see_all_patterns)) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (patterns.isNotEmpty()) {
                    item(key = "patterns-header") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.patterns_title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = onOpenAllPatterns) { Text(stringResource(R.string.common_see_all)) }
                        }
                    }
                    items(patterns, key = { "pattern-" + it.card.id }) { PatternCardView(it, onClick = { onOpenPattern(it.card.id) }) }
                } else item(key = "patterns-link") {
                    TextButton(onClick = onOpenAllPatterns) { Text(stringResource(R.string.notifications_see_all_patterns)) }
                }
                items(items, key = { it.id }) { n ->
                    // Swipe either way, or tap the ✕, to remove.
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) remove(n)
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.animateItem(),
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp),
                                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    ) {
                        NotificationCard(n, onClick = { onOpen(n.link) }, onRemove = { remove(n) })
                    }
                }
            }
        }
    }
}

/** The same card shape as a pattern: tinted icon tile, title, one or two lines of text, small date, ✕. */
@Composable
private fun NotificationCard(n: AppNotificationEntity, onClick: () -> Unit, onRemove: () -> Unit) {
    val (icon, tint) = notificationVisual(n)
    val context = LocalContext.current
    val (title, text) = remember(n.title, n.text, Locale.getDefault()) { renderNotification(context, n.title, n.text) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (n.isRead) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else tint.copy(alpha = 0.6f))
    ) {
        Row(Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlyphTile(tint) { icon() }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatStamp(n.timestampUtc, STAMP_SKELETON),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_remove), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Icon and colour for a notification, by what raised it (the device-expiry alerts are keyed "<KIND>-<time>-<hours>"). */
@Composable
private fun notificationVisual(n: AppNotificationEntity): Pair<@Composable () -> Unit, Color> {
    val kind = n.dedupeKey.substringBefore('-')
    // Device alerts keep the neutral device colour, like the device events in the Logs.
    val tint = if (DeviceKind.entries.any { it.name == kind }) Device else MaterialTheme.colorScheme.primary
    val icon: @Composable () -> Unit = {
        when (kind) {
            DeviceKind.SENSOR.name -> Icon(painterResource(R.drawable.ic_sensor), null, tint = tint, modifier = Modifier.size(26.dp))
            DeviceKind.SITE.name, DeviceKind.INSULIN.name, DeviceKind.PATCH.name ->
                Icon(painterResource(R.drawable.ic_insulin_pump), null, tint = tint, modifier = Modifier.size(26.dp))
            DeviceKind.BATTERY.name -> Icon(Icons.Filled.BatteryAlert, null, tint = tint, modifier = Modifier.size(26.dp))
            else -> Icon(Icons.Filled.Notifications, null, tint = tint, modifier = Modifier.size(26.dp))
        }
    }
    return icon to tint
}
