package uk.scimone.diafit.onboarding.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.koin.androidx.compose.koinViewModel
import uk.scimone.diafit.R
import uk.scimone.diafit.settings.presentation.ConnectedAppsPage
import uk.scimone.diafit.settings.presentation.SettingsViewModel

private const val STEPS = 4

/**
 * First-run setup, shown once: welcome + privacy, connect data, notifications, ready. Every step can be skipped;
 * nothing is required, and everything stays changeable in the account menu afterwards.
 */
@Composable
fun OnboardingScreen(
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onFinished: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val finish = {
        viewModel.completeOnboarding()
        onFinished()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // Progress + skip.
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp).height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    repeat(STEPS) { i ->
                        Box(
                            Modifier
                                .height(6.dp)
                                .width(if (i == step) 28.dp else 12.dp)
                                .background(
                                    if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                        )
                    }
                }
                if (step < STEPS - 1) TextButton(onClick = finish) { Text(stringResource(R.string.onboarding_skip)) }
            }

            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                },
                label = "onboarding-step"
            ) { current ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (current) {
                        0 -> WelcomeStep()
                        1 -> ConnectStep(state, viewModel)
                        2 -> AlertsStep(state.enabledConnectors.isNotEmpty(), onRequestNotifications, onOpenBatterySettings)
                        else -> DoneStep()
                    }
                }
            }

            // Navigation.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }) { Text(stringResource(R.string.onboarding_back)) }
                }
                Spacer(Modifier.weight(1f))
                val connectNothing = step == 1 && state.enabledConnectors.isEmpty()
                Button(onClick = { if (step < STEPS - 1) step++ else finish() }) {
                    Text(
                        stringResource(
                            when {
                                step == 0 -> R.string.onboarding_get_started
                                step == STEPS - 1 -> R.string.onboarding_finish
                                connectNothing -> R.string.onboarding_skip_step
                                else -> R.string.onboarding_next
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Spacer(Modifier.height(8.dp))
    IconBadge(Icons.Filled.WaterDrop, size = 72)
    Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineMedium)
    Text(
        stringResource(R.string.onboarding_welcome_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))
    Point(Icons.Filled.Timeline, R.string.onboarding_point_timeline_title, R.string.onboarding_point_timeline_body)
    Point(Icons.Filled.Restaurant, R.string.onboarding_point_meals_title, R.string.onboarding_point_meals_body)
    Point(Icons.Filled.Insights, R.string.onboarding_point_patterns_title, R.string.onboarding_point_patterns_body)
    Callout(Icons.Filled.Lock, R.string.onboarding_privacy_title, R.string.onboarding_privacy_body)
    Text(
        stringResource(R.string.onboarding_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ConnectStep(state: uk.scimone.diafit.settings.presentation.SettingsState, viewModel: SettingsViewModel) {
    Text(stringResource(R.string.onboarding_connect_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.onboarding_connect_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // The same connector cards as Account menu → Connected apps, so there is one place that knows how to set each up.
    ConnectedAppsPage(state, viewModel)
    Text(
        stringResource(R.string.onboarding_connect_footer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AlertsStep(syncing: Boolean, onRequestNotifications: () -> Unit, onOpenBatterySettings: () -> Unit) {
    val context = LocalContext.current
    var requested by rememberSaveable { mutableStateOf(false) }
    val granted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    IconBadge(Icons.Filled.Notifications, size = 56)
    Text(stringResource(R.string.onboarding_alerts_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.onboarding_alerts_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (granted) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.onboarding_alerts_done), style = MaterialTheme.typography.titleSmall)
        }
    } else {
        Button(onClick = { requested = true; onRequestNotifications() }) {
            Text(stringResource(R.string.onboarding_alerts_button))
        }
    }
    if (syncing) {
        Spacer(Modifier.height(8.dp))
        Callout(Icons.Filled.BatteryChargingFull, R.string.onboarding_background_title, R.string.onboarding_background_body) {
            OutlinedButton(onClick = onOpenBatterySettings) { Text(stringResource(R.string.onboarding_background_button)) }
        }
    }
}

@Composable
private fun DoneStep() {
    Spacer(Modifier.height(8.dp))
    IconBadge(Icons.Filled.CheckCircle, size = 72)
    Text(stringResource(R.string.onboarding_done_title), style = MaterialTheme.typography.headlineMedium)
    Text(
        stringResource(R.string.onboarding_done_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Callout(Icons.Filled.AutoAwesome, R.string.onboarding_done_ai_title, R.string.onboarding_done_ai_body)
}

@Composable
private fun IconBadge(icon: ImageVector, size: Int) {
    Box(
        Modifier.size(size.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size((size / 2).dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Point(icon: ImageVector, title: Int, body: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Callout(icon: ImageVector, title: Int, body: Int, action: @Composable (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            }
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}
