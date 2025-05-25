package uk.scimone.diafit

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
import uk.scimone.diafit.core.navigation.BottomNavigationBar
import org.koin.android.ext.android.inject
import androidx.compose.ui.unit.dp



class MainActivity : ComponentActivity() {
    private val mealRepository: MealRepository by inject()
    private val userId = "anna" // replace with real user ID from your auth system
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    Box(modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                    ) {
                        when (selectedTab) {
                            0 -> Greeting("Home")
                            1 -> Greeting("Favorites")
                            2 -> Greeting("Search")
                            3 -> Greeting("Profile")
                            4 -> AddMealScreen(mealRepository = mealRepository, userId = userId)
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
