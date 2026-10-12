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

    /** Adds a brand-new record (its id is ignored and assigned by the database). */
    suspend fun create(record: EditableRecord): EditResult = withContext(Dispatchers.IO) {
        try {
            when (record) {
                is Glucose -> dao.add(record.e.copy(id = 0))
                is Insulin -> dao.add(record.e.copy(id = 0))
                is Meal -> dao.add(record.e.copy(id = 0))
                is Workout -> dao.add(record.e.copy(id = 0))
                is Device -> dao.add(record.e.copy(id = 0))
                is HeartRate -> dao.add(record.e.copy(id = 0))
                is Steps -> dao.add(record.e.copy(id = 0))
                is Sleep -> dao.insertSleep(record.stages.map { it.copy(id = 0) })
            }
            EditResult.Saved
        } catch (e: SQLiteConstraintException) {
            EditResult.Failed("A record already exists at that time. Pick a different time.")
        } catch (e: Exception) {
            EditResult.Failed(e.message ?: "Could not add")
        }
    }

    suspend fun delete(records: List<EditableRecord>) = withContext(Dispatchers.IO) {
        chunked(records.filterIsInstance<Glucose>()) { dao.deleteGlucose(it.map { r -> r.e.id }) }
        chunked(records.filterIsInstance<Insulin>()) { dao.deleteBolus(it.map { r -> r.e.id }) }
        chunked(records.filterIsInstance<Meal>()) { dao.setMealsValid(it.map { r -> r.e.id }, false) }
        chunked(records.filterIsInstance<Workout>()) { dao.deleteWorkout(it.map { r -> r.e.id }) }
        chunked(records.filterIsInstance<Sleep>()) { dao.deleteSleep(it.map { r -> r.sessionId }) }
        chunked(records.filterIsInstance<Device>()) { dao.setEventsDeleted(it.map { r -> r.e.id }, true) }
        chunked(records.filterIsInstance<HeartRate>()) { dao.deleteHeartRate(it.map { r -> r.e.id }) }
        chunked(records.filterIsInstance<Steps>()) { dao.deleteSteps(it.map { r -> r.e.id }) }
    }

    /** Undo of [delete]. */
    suspend fun restore(records: List<EditableRecord>) = withContext(Dispatchers.IO) {
        chunked(records.filterIsInstance<Glucose>()) { dao.restoreGlucose(it.map { r -> r.e }) }
        chunked(records.filterIsInstance<Insulin>()) { dao.restoreBolus(it.map { r -> r.e }) }
        chunked(records.filterIsInstance<Meal>()) { dao.setMealsValid(it.map { r -> r.e.id }, true) }
        chunked(records.filterIsInstance<Workout>()) { dao.restoreWorkout(it.map { r -> r.e }) }
        chunked(records.filterIsInstance<Sleep>()) { dao.insertSleep(it.flatMap { r -> r.stages }) }
        chunked(records.filterIsInstance<Device>()) { dao.setEventsDeleted(it.map { r -> r.e.id }, false) }
        chunked(records.filterIsInstance<HeartRate>()) { dao.restoreHeartRate(it.map { r -> r.e }) }
        chunked(records.filterIsInstance<Steps>()) { dao.restoreSteps(it.map { r -> r.e }) }
    }

    /** SQLite allows 999 bound variables per statement. */
    private suspend fun <T> chunked(items: List<T>, block: suspend (List<T>) -> Unit) {
        items.chunked(400).forEach { block(it) }
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
