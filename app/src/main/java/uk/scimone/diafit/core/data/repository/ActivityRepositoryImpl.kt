package uk.scimone.diafit.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import uk.scimone.diafit.core.data.local.ActivityDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.ActivityOverview
import uk.scimone.diafit.core.domain.model.ElevatedActivity
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import uk.scimone.diafit.core.domain.model.StepsEntity
import uk.scimone.diafit.core.domain.repository.ActivityRepository

class ActivityRepositoryImpl(private val dao: ActivityDao) : ActivityRepository {

    override fun observeSince(start: Long, userId: Int): Flow<ActivityData> = combine(
        dao.observeHeartRateSince(start, userId),
        dao.observeStepsSince(start, userId),
        dao.observeSleepSince(start, userId),
        dao.observeExerciseSince(start, userId)
    ) { heartRate, steps, sleep, exercise -> ActivityData(heartRate, steps, sleep, exercise) }

    override fun observeSessionsSince(start: Long, userId: Int): Flow<ActivityData> = combine(
        dao.observeSleepSince(start, userId),
        dao.observeExerciseSince(start, userId)
    ) { sleep, exercise -> ActivityData(sleep = sleep, exercise = exercise) }

    override fun observeOverviewSince(start: Long, userId: Int): Flow<ActivityOverview> = combine(
        dao.observeSleepSince(start, userId),
        dao.observeExerciseSince(start, userId),
        dao.observeHeartRateBucketsSince(start, userId),
        dao.observeStepsSince(start, userId)
    ) { sleep, exercise, hr, steps ->
        val sessions = ActivityData(sleep = sleep, exercise = exercise)
        ActivityOverview(sessions, ElevatedActivity.detect(hr, steps, sessions.sleepSessions), steps)
    }.flowOn(Dispatchers.Default)

    override suspend fun getSessionsBetween(start: Long, end: Long, userId: Int) = ActivityData(
        sleep = dao.getSleepBetween(start, end, userId),
        exercise = dao.getExerciseBetween(start, end, userId)
    )

    override suspend fun getHeartRateBetween(start: Long, end: Long, userId: Int) =
        dao.getHeartRateBetween(start, end, userId)

    override suspend fun getBetween(start: Long, end: Long, userId: Int) = ActivityData(
        heartRate = dao.getHeartRateBetween(start, end, userId),
        steps = dao.getStepsBetween(start, end, userId),
        sleep = dao.getSleepBetween(start, end, userId),
        exercise = dao.getExerciseBetween(start, end, userId)
    )

    override suspend fun saveHeartRate(rows: List<HeartRateEntity>) = dao.insertHeartRate(rows)
    override suspend fun saveSteps(rows: List<StepsEntity>) = dao.insertSteps(rows)
    override suspend fun saveSleep(sessionIds: List<String>, rows: List<SleepStageEntity>) =
        dao.replaceSleepSessions(sessionIds, rows)
    override suspend fun saveExercise(rows: List<ExerciseEntity>) = dao.insertExercise(rows)
}
