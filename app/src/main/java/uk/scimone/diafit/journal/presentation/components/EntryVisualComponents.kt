package uk.scimone.diafit.journal.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import uk.scimone.diafit.journal.presentation.model.accent
import uk.scimone.diafit.journal.presentation.model.iconRes
import uk.scimone.diafit.journal.presentation.model.label
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange

/** Meal-type glyph on a tonal tile in the type's accent colour. */
@Composable
fun MealTypeTile(mealType: MealType, size: Dp, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(size * 0.28f)) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(mealType.accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(mealType.iconRes),
            contentDescription = mealType.type,
            tint = mealType.accent,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** The meal's photo, or a [MealTypeTile] when it has none. A small type badge marks photos. */
@Composable
fun MealAvatar(meal: MealEntityUi, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.28f)
    if (meal.imageUri == null) {
        MealTypeTile(meal.mealType, size, modifier, shape)
        return
    }
    Box(modifier.size(size)) {
        AsyncImage(
            model = meal.imageUri,
            contentDescription = meal.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().clip(shape)
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .size(size * 0.38f)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .padding(2.dp)
                .background(meal.mealType.accent.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(meal.mealType.iconRes),
                contentDescription = null,
                tint = meal.mealType.accent,
                modifier = Modifier.fillMaxSize(0.62f)
            )
        }
    }
}

/** "45 g" in the carbs colour. */
@Composable
fun CarbPill(grams: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Carbs.copy(alpha = 0.2f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text("$grams", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(2.dp))
        Text(
            "g",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 1.dp)
        )
    }
}

/** Absorption glyph with its word label, e.g. a slow curve + "Slow". */
@Composable
fun AbsorptionBadge(impact: ImpactType, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(impact.iconRes),
            contentDescription = "${impact.label} absorption",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            impact.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Thin below / in / above range split. */
@Composable
fun RangeBar(below: Float, inRange: Float, above: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Row(modifier.height(height).clip(RoundedCornerShape(50))) {
        if (below > 0f) Box(Modifier.fillMaxHeight().weight(below).background(BelowRange))
        if (inRange > 0f) Box(Modifier.fillMaxHeight().weight(inRange).background(InRange))
        if (above > 0f) Box(Modifier.fillMaxHeight().weight(above).background(AboveRange))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
