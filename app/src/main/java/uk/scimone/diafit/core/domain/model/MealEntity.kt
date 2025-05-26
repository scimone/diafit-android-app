package uk.scimone.diafit.core.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val description: String?,
    val createdAtUtc: Long,
    val mealTimeUtc: Long,
    val calories: Int? = 0,
    val carbohydrates: Int = 0,
    val proteins: Int? = 0,
    val fats: Int? = 0,
    val isValid: Boolean = true,
    val imageId: String,
    val recommendation: String?,       // AI recommendation text
    val reasoning: String?              // AI reasoning text
)
