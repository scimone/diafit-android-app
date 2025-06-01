package uk.scimone.diafit.core.domain.usecase

import android.net.Uri
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.util.timestampToLocalDateTime
import java.time.Instant

class CreateMealUseCase(
    private val mealRepository: MealRepository,
    private val fileStorageRepository: FileStorageRepository
) {
    suspend operator fun invoke(
        imageUri: Uri,
        description: String?,
        userId: Int,
        imageId: String,
        mealTimeUtc: Long = Instant.now().toEpochMilli(),
        calories: Int? = null,
        carbohydrates: Int = 0,
        proteins: Int? = null,
        fats: Int? = null,
        impactType: ImpactType? = null,
        mealType: MealType? = null,
    ): Result<Pair<MealEntity, Uri>> {
        val storedFileUriResult = fileStorageRepository.storeImage(imageId, imageUri)
        if (storedFileUriResult.isFailure) return Result.failure(storedFileUriResult.exceptionOrNull()!!)

        val storedFileUri = storedFileUriResult.getOrThrow()

        val storedContentUri = fileStorageRepository.getFileProviderUri(imageId) ?: storedFileUri

        val resolvedImpactType = impactType ?: run {
            if (proteins != null && fats != null) {
                when {
                    carbohydrates >= 60 && proteins >= 30 && fats >= 20 -> ImpactType.LONG
                    carbohydrates >= 40 -> ImpactType.MEDIUM
                    else -> ImpactType.SHORT
                }
            } else {
                ImpactType.MEDIUM
            }
        }

        // Infer mealType based on local time if not provided
        val resolvedMealType = mealType ?: run {
            val localDateTime = timestampToLocalDateTime(mealTimeUtc)
            val hour = localDateTime.hour

            when (hour) {
                in 5 until 11 -> MealType.BREAKFAST
                in 11 until 16 -> MealType.LUNCH
                in 16 until 22 -> MealType.DINNER
                else -> MealType.SNACK
            }
        }

        val meal = MealEntity(
            userId = userId,
            description = description,
            createdAtUtc = Instant.now().toEpochMilli(),
            mealTimeUtc = mealTimeUtc,
            calories = calories,
            carbohydrates = carbohydrates,
            proteins = proteins,
            fats = fats,
            impactType = resolvedImpactType,
            mealType = resolvedMealType,
            isValid = true,
            imageId = imageId,
            recommendation = null,
            reasoning = null
        )

        val createResult = mealRepository.createMeal(meal)
        if (createResult.isFailure) return Result.failure(createResult.exceptionOrNull()!!)

        return Result.success(Pair(meal, storedContentUri))
    }
}
