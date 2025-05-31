package uk.scimone.diafit

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import uk.scimone.diafit.addmeal.presentation.screens.AddMealScreen
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.ui.theme.DiafitTheme
import uk.scimone.diafit.core.presentation.BottomNavigationBar
import org.koin.android.ext.android.inject
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import uk.scimone.diafit.journal.presentation.JournalScreen
import uk.scimone.diafit.home.presentation.HomeScreen
import uk.scimone.diafit.core.data.service.CgmSyncService
import uk.scimone.diafit.settings.presentation.SettingsScreen


class MainActivity : ComponentActivity() {
    private val mealRepository: MealRepository by inject()
    private val userId = 1 // replace with real user ID from your auth system
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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


        ContextCompat.startForegroundService(this, Intent(this, CgmSyncService::class.java))


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
                            3 -> SettingsScreen()
                            4 -> AddMealScreen(userId = userId)
                        }
                    }
                }
            }
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
