package uk.scimone.diafit.core.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.ActivityOverview
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import uk.scimone.diafit.core.domain.model.StepsEntity

interface ActivityRepository {
    /** Everything from [start] on, updating live while a sync writes new data. */
    fun observeSince(start: Long, userId: Int): Flow<ActivityData>

    /** Only sleep and exercise sessions (no heart rate or steps), cheap enough for long ranges. */
    fun observeSessionsSince(start: Long, userId: Int): Flow<ActivityData>

    /** Sleep, workouts and elevated-activity ranges (see [uk.scimone.diafit.core.domain.model.ElevatedActivity]) from [start] on. */
    fun observeOverviewSince(start: Long, userId: Int): Flow<ActivityOverview>

    suspend fun getSessionsBetween(start: Long, end: Long, userId: Int): ActivityData

    suspend fun getHeartRateBetween(start: Long, end: Long, userId: Int): List<HeartRateEntity>

    suspend fun getBetween(start: Long, end: Long, userId: Int): ActivityData

    suspend fun saveHeartRate(rows: List<HeartRateEntity>)
    suspend fun saveSteps(rows: List<StepsEntity>)
    suspend fun saveSleep(sessionIds: List<String>, rows: List<SleepStageEntity>)
    suspend fun saveExercise(rows: List<ExerciseEntity>)
}
