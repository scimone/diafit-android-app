package uk.scimone.diafit.dataeditor.data

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.scimone.diafit.dataeditor.data.EditableRecord.*

/** The outcome of a save: failures carry text that can be shown as is. */
sealed interface EditResult {
    data object Saved : EditResult
    data class Failed(val message: String) : EditResult
}

class DataEditorRepository(private val dao: DataEditorDao) {

    suspend fun load(kind: RecordKind, start: Long, end: Long, userId: Int): List<EditableRecord> = withContext(Dispatchers.IO) {
        when (kind) {
            RecordKind.GLUCOSE -> dao.glucose(start, end, userId).map(::Glucose)
            RecordKind.INSULIN -> dao.boluses(start, end, userId).map(::Insulin)
            RecordKind.MEALS -> dao.meals(start, end, userId).map(::Meal)
            RecordKind.WORKOUTS -> dao.workouts(start, end, userId).map(::Workout)
            RecordKind.SLEEP -> dao.sleepRows(start, end, userId)
                .groupBy { it.sessionId }.values
                .map { Sleep(it.sortedBy { r -> r.startUtc }) }
                .sortedByDescending { it.endUtc }
            RecordKind.DEVICE -> dao.deviceEvents(start, end, userId).map(::Device)
            RecordKind.HEART_RATE -> dao.heartRate(start, end, userId).map(::HeartRate)
            RecordKind.STEPS -> dao.steps(start, end, userId).map(::Steps)
        }
    }

    suspend fun save(record: EditableRecord): EditResult = withContext(Dispatchers.IO) {
        try {
            when (record) {
                is Glucose -> dao.update(record.e)
                is Insulin -> dao.update(record.e)
                is Meal -> dao.update(record.e)
                is Workout -> dao.update(record.e)
                is Device -> dao.update(record.e)
                is HeartRate -> dao.update(record.e)
                is Steps -> dao.update(record.e)
                is Sleep -> dao.replaceSleep(record.sessionId, record.stages)
            }
            EditResult.Saved
        } catch (e: SQLiteConstraintException) {
            EditResult.Failed("Another record already exists at that time. Pick a different time.")
        } catch (e: Exception) {
            EditResult.Failed(e.message ?: "Could not save")
        }
    }

    suspend fun delete(record: EditableRecord) = withContext(Dispatchers.IO) {
        when (record) {
            is Glucose -> dao.deleteGlucose(record.e.id)
            is Insulin -> dao.deleteBolus(record.e.id)
            is Meal -> dao.setMealValid(record.e.id, false)
            is Workout -> dao.deleteWorkout(record.e.id)
            is Sleep -> dao.deleteSleep(record.sessionId)
            is Device -> dao.setEventDeleted(record.e.id, true)
            is HeartRate -> dao.deleteHeartRate(record.e.id)
            is Steps -> dao.deleteSteps(record.e.id)
        }
    }

    /** Undo of [delete]. */
    suspend fun restore(record: EditableRecord) = withContext(Dispatchers.IO) {
        when (record) {
            is Glucose -> dao.restore(record.e)
            is Insulin -> dao.restore(record.e)
            is Meal -> dao.setMealValid(record.e.id, true)
            is Workout -> dao.restore(record.e)
            is Sleep -> dao.insertSleep(record.stages)
            is Device -> dao.setEventDeleted(record.e.id, false)
            is HeartRate -> dao.restore(record.e)
            is Steps -> dao.restore(record.e)
        }
    }

    companion object {
        /**
         * A sleep session moved to [startUtc]..[endUtc]: stages keep their order, the first starts at the new
         * start, the last ends at the new end, stages entirely outside the new range are dropped.
         */
        fun resizeSleep(stages: List<uk.scimone.diafit.core.domain.model.SleepStageEntity>, startUtc: Long, endUtc: Long) =
            stages.sortedBy { it.startUtc }
                .mapIndexed { i, s ->
                    s.copy(
                        startUtc = if (i == 0) startUtc else maxOf(s.startUtc, startUtc),
                        endUtc = if (i == stages.lastIndex) endUtc else minOf(s.endUtc, endUtc),
                        sessionStartUtc = startUtc, sessionEndUtc = endUtc
                    )
                }
                .filter { it.endUtc > it.startUtc }
    }
}
