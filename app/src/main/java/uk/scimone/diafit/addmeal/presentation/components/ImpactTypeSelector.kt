package uk.scimone.diafit.addmeal.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.R
import uk.scimone.diafit.core.domain.model.ImpactType


// Mock icon mapping — replace with your real icons
@Composable
fun impactTypeIcon(impactType: ImpactType): Painter = when (impactType) {
    ImpactType.SHORT -> painterResource(R.drawable.ic_impact_type_short)
    ImpactType.MEDIUM -> painterResource(R.drawable.ic_impact_type_medium)
    ImpactType.LONG -> painterResource(R.drawable.ic_impact_type_long)
}

@Composable
fun ImpactTypeSelector(
    selectedImpactType: ImpactType,
    onImpactTypeSelected: (ImpactType) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ImpactType.values().forEach { impact ->
            val selected = impact == selectedImpactType
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onImpactTypeSelected(impact) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                tonalElevation = if (selected) 8.dp else 0.dp
            ) {
                Icon(
                    painter = impactTypeIcon(impact),
                    contentDescription = impact.name,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}