package uk.scimone.diafit.recipes.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.ComponentTotals
import uk.scimone.diafit.core.domain.model.MealComponent
import uk.scimone.diafit.core.domain.model.NutrientsPer100
import uk.scimone.diafit.core.domain.model.RecipeEntity
import uk.scimone.diafit.core.domain.model.effectiveRecipeWeight
import uk.scimone.diafit.core.domain.model.recipePer100
import uk.scimone.diafit.core.domain.model.totals
import uk.scimone.diafit.core.domain.model.withMacros
import uk.scimone.diafit.core.domain.repository.RecipeRepository

/** The recipe list; null until the first read. */
class RecipesViewModel(private val repository: RecipeRepository) : ViewModel() {
    val recipes: StateFlow<List<RecipeEntity>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(id: Int) {
        viewModelScope.launch { repository.delete(id) }
    }
}

data class RecipeEditorState(
    val loaded: Boolean = false,
    val name: String = "",
    val ingredients: List<MealComponent> = emptyList(),
    /** Raw text of the optional finished weight, so typing "3" on the way to "320" works. */
    val finishedWeightText: String = "",
    val servings: Int? = null,
    val saving: Boolean = false,
    val finished: Boolean = false
) {
    val finishedWeightG: Double? get() = finishedWeightText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val rawWeightG: Double get() = ingredients.sumOf { it.weightG }
    val effectiveWeightG: Double get() = effectiveRecipeWeight(ingredients, finishedWeightG)
    val totals: ComponentTotals get() = ingredients.totals()
    val per100: NutrientsPer100? get() = recipePer100(ingredients, finishedWeightG)
    val servingG: Double? get() = servings?.takeIf { it > 0 }?.let { effectiveWeightG / it }
    val canSave: Boolean get() = name.isNotBlank() && ingredients.isNotEmpty() && effectiveWeightG > 0.0 && !saving
}

class RecipeEditorViewModel(private val repository: RecipeRepository) : ViewModel() {
    private val _state = MutableStateFlow(RecipeEditorState())
    val state: StateFlow<RecipeEditorState> = _state

    private var recipeId: Int? = null
    private var createdUtc: Long = 0L
    private var started = false
    private var baseline = RecipeEditorState()

    /** Loads [id] (or starts an empty recipe). Safe to call again with the same id. */
    fun start(id: Int?) {
        if (started) return
        started = true
        recipeId = id
        if (id == null) {
            _state.value = RecipeEditorState(loaded = true)
            baseline = _state.value
            return
        }
        viewModelScope.launch {
            val r = repository.get(id)
            _state.value = if (r == null) RecipeEditorState(loaded = true) else RecipeEditorState(
                loaded = true, name = r.name, ingredients = r.ingredients,
                finishedWeightText = r.finishedWeightG?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty(),
                servings = r.servings
            )
            createdUtc = r?.createdUtc ?: 0L
            baseline = _state.value
        }
    }

    val isEditing: Boolean get() = recipeId != null
    val recipeIdOrNull: Int? get() = recipeId

    fun isDirty() = _state.value.copy(saving = false, finished = false) != baseline

    fun onName(name: String) = _state.update { it.copy(name = name) }

    fun addIngredients(list: List<MealComponent>) {
        val usable = list.filter { it.weightG > 0.0 }
        if (usable.isEmpty()) return
        _state.update { it.copy(ingredients = it.ingredients + usable) }
    }

    fun onIngredientWeight(index: Int, weightG: Double) = changeIngredients { list ->
        list.mapIndexed { i, c -> if (i == index) c.withWeight(weightG) else c }
    }

    fun onIngredientMacros(index: Int, carbs: Double?, protein: Double?, fat: Double?) = changeIngredients { list ->
        list.mapIndexed { i, c -> if (i == index) c.withMacros(carbs ?: c.carbsG, protein ?: c.proteinG, fat ?: c.fatG) else c }
    }

    fun onIngredientRemoved(index: Int) = changeIngredients { list -> list.filterIndexed { i, _ -> i != index } }

    private fun changeIngredients(change: (List<MealComponent>) -> List<MealComponent>) =
        _state.update { it.copy(ingredients = change(it.ingredients)) }

    fun onFinishedWeight(text: String) = _state.update { s ->
        s.copy(finishedWeightText = text.filter { it.isDigit() || it == '.' || it == ',' }.take(7))
    }

    fun onServings(servings: Int?) = _state.update { it.copy(servings = servings?.takeIf { n -> n > 0 }?.coerceAtMost(99)) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                repository.save(
                    RecipeEntity(
                        id = recipeId ?: 0, name = s.name.trim(), ingredients = s.ingredients,
                        finishedWeightG = s.finishedWeightG, servings = s.servings,
                        createdUtc = createdUtc.takeIf { it > 0 } ?: now, updatedUtc = now,
                        // Keep usage statistics of an existing recipe.
                        lastUsedUtc = recipeId?.let { repository.get(it)?.lastUsedUtc },
                        useCount = recipeId?.let { repository.get(it)?.useCount } ?: 0
                    )
                )
            }.onSuccess { _state.update { it.copy(saving = false, finished = true) } }
                .onFailure { _state.update { it.copy(saving = false) } }
        }
    }

    fun delete() {
        val id = recipeId ?: return
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(finished = true) }
        }
    }
}
