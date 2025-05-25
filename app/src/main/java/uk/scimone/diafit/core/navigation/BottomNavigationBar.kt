package uk.scimone.diafit.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BottomNavigationBar(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    NavigationBar(containerColor = colors.surface) {
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = if (selectedItem == 0) colors.primary else colors.onSurfaceVariant
                )
            },
            selected = selectedItem == 0,
            onClick = { onItemSelected(0) }
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Summary",
                    tint = if (selectedItem == 1) colors.primary else colors.onSurfaceVariant
                )
            },
            selected = selectedItem == 1,
            onClick = { onItemSelected(1) }
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Create,
                    contentDescription = "Journal",
                    tint = if (selectedItem == 2) colors.primary else colors.onSurfaceVariant
                )
            },
            selected = selectedItem == 2,
            onClick = { onItemSelected(2) }
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "History",
                    tint = if (selectedItem == 3) colors.primary else colors.onSurfaceVariant
                )
            },
            selected = selectedItem == 3,
            onClick = { onItemSelected(3) }
        )

        // Custom Add button without NavigationBarItem - no selection rectangle
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(
                    indication = null,   // disables ripple effect
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { onItemSelected(4) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
