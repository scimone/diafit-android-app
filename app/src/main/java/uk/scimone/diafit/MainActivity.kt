package uk.scimone.diafit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import uk.scimone.diafit.addmeal.presentation.AddMealScreen
import uk.scimone.diafit.ui.theme.DiafitTheme
import uk.scimone.diafit.core.presentation.BottomNavigationBar
import org.koin.android.ext.android.inject
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.data.service.CgmServiceManager
import uk.scimone.diafit.journal.presentation.JournalScreen
import uk.scimone.diafit.home.presentation.HomeScreen
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase
import uk.scimone.diafit.settings.presentation.SettingsScreen
import uk.scimone.diafit.settings.presentation.SettingsViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel



class MainActivity : ComponentActivity() {
    private val getCgmSourceUseCase: GetCgmSourceUseCase by inject()
    private val cgmServiceManager: CgmServiceManager by inject()
    private val settingsViewModel: SettingsViewModel by viewModel()
    private val userId = 1 // replace with real user ID from your auth system
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        val REQUEST_CODE_DATA_SYNC = 1001

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC),
                    REQUEST_CODE_DATA_SYNC
                )
            }
        }


        lifecycleScope.launch {
            val source = getCgmSourceUseCase()
            cgmServiceManager.start(source)  // start service on app launch
            Log.d("MainActivity", "Starting CGM Service with source: $source")
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.restartCgmServiceEvent.collect { source ->
                    cgmServiceManager.start(source)
                }
            }
        }


        enableEdgeToEdge()
        setContent {
            DiafitTheme {
                var selectedTab by remember { mutableStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomNavigationBar(
                            selectedItem = selectedTab,
                            onItemSelected = { selectedTab = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        when (selectedTab) {
                            0 -> HomeScreen(userId = userId)
                            1 -> Greeting("Summary")
                            2 -> JournalScreen(userId = userId)
                            3 -> SettingsScreen(
                                onRequestIgnoreBatteryOptimizations = {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    startActivity(intent)
                                }
                            )
                            4 -> AddMealScreen(userId = userId)
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "GLUCOSE_SYNC_CHANNEL",
                "CGM Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for CGM sync service notifications"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "You selected: $name",
        modifier = modifier.padding(16.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DiafitTheme {
        Greeting("Home")
    }
}
