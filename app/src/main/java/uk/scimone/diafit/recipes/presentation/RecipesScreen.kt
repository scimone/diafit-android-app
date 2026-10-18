package uk.scimone.diafit.recipes.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import uk.scimone.diafit.R
import uk.scimone.diafit.core.domain.model.RecipeEntity
import uk.scimone.diafit.core.domain.model.formatGrams
import uk.scimone.diafit.ui.theme.Carbs
import kotlin.math.roundToInt

/** Menu -> Recipes: the user's dishes, newest/most used first. Tap one to edit it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(onBack: () -> Unit, onOpenRecipe: (Int?) -> Unit) {
    val viewModel: RecipesViewModel = koinViewModel()
    val recipes by viewModel.recipes.collectAsState()
    var toDelete by remember { mutableStateOf<RecipeEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } }
            )
        },
        floatingActionButton = {
            if (!recipes.isNullOrEmpty()) ExtendedFloatingActionButton(
                onClick = { onOpenRecipe(null) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.recipes_new)) }
            )
        }
    ) { padding ->
        val list = recipes
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyRecipes(Modifier.padding(padding), onCreate = { onOpenRecipe(null) })
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.id }) { r -> RecipeCard(r, onClick = { onOpenRecipe(r.id) }, onDelete = { toDelete = r }) }
            }
        }
    }

    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.recipes_delete_title, r.name)) },
            text = { Text(stringResource(R.string.recipes_delete_text)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(r.id); toDelete = null }) { Text(stringResource(R.string.recipe_delete)) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun EmptyRecipes(modifier: Modifier, onCreate: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🍲", fontSize = 56.sp)
        Text(stringResource(R.string.recipes_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.recipes_empty_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Button(onClick = onCreate) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.recipes_new))
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val per100 = recipe.per100
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("🍲", fontSize = 22.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(recipe.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        pluralStringResource(R.plurals.recipe_ingredients, recipe.ingredients.size, recipe.ingredients.size),
                        recipe.effectiveWeightG.takeIf { it > 0 }?.let { formatGrams(it.roundToInt().toDouble()) },
                        recipe.servings?.let { pluralStringResource(R.plurals.recipe_portions, it, it) }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = muted
                )
                if (per100 != null) Text(
                    stringResource(R.string.recipe_per_100_line, per100.carbsG.roundToInt(), per100.proteinG.roundToInt(), per100.fatG.roundToInt(), per100.kcal.roundToInt()),
                    style = MaterialTheme.typography.bodySmall, color = Carbs, fontWeight = FontWeight.Medium
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.recipe_delete), tint = muted) }
        }
    }
}
