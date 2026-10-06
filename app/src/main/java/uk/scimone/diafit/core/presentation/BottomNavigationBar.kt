package uk.scimone.diafit.core.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class NavTab(
    val index: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navTabs = listOf(
    NavTab(0, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavTab(1, "Summary", Icons.Filled.Insights, Icons.Outlined.Insights),
    NavTab(2, "Journal", Icons.Filled.Book, Icons.Outlined.Book),
    NavTab(3, "History", Icons.Filled.History, Icons.Outlined.History),
)

/** Settings moved behind the top-bar overflow menu (see MainActivity) and is no longer a bottom tab. */
const val SETTINGS_TAB_INDEX = 4

/** Index used for the central "Add entry" action, kept out of [navTabs] since it renders as a FAB, not a NavigationBarItem. */
const val ADD_MEAL_TAB_INDEX = 5

@Composable
fun BottomNavigationBar(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit
) {
    NavigationBar {
        navTabs.forEach { tab ->
            val selected = selectedItem == tab.index
            NavigationBarItem(
                selected = selected,
                onClick = { onItemSelected(tab.index) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) }
            )
        }

        // Custom Add button, rendered inside the bar but visually raised and elevated
        // so it reads as a primary action rather than a sixth equal-weight nav item.
        // NOTE: do not use fillMaxHeight() here — NavigationBar's row only sets a
        // *minimum* height, and Scaffold gives the bottomBar slot very loose height
        // constraints, so a fillMaxHeight() child makes the whole bar balloon to
        // fill the screen (pushing all page content into zero remaining height).
        Box(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset(y = (-6).dp)
                    .size(52.dp)
                    .shadow(elevation = 4.dp, shape = CircleShape)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onItemSelected(ADD_MEAL_TAB_INDEX) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add entry",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
