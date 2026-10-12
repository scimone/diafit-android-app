package uk.scimone.diafit.dataeditor.presentation

import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.model.PumpEventNormalizer
import uk.scimone.diafit.core.domain.model.SleepStage
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import uk.scimone.diafit.core.domain.model.StepsEntity
import uk.scimone.diafit.dataeditor.data.EditableRecord
import uk.scimone.diafit.dataeditor.data.RecordKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Source label stored on records the user adds by hand. */
const val MANUAL_SOURCE = "Manual"

/**
 * An empty record of [kind] for the form to fill in, placed on [day]: now if that is today, else midday
 * (sleep: 23:00 the evening before to 07:00). Ids are placeholders; the repository assigns real ones.
 */
fun blankRecord(kind: RecordKind, day: LocalDate, userId: Int): EditableRecord {
    val zone = ZoneId.systemDefault()
    val at = (if (day == LocalDate.now()) java.time.LocalDateTime.now() else day.atTime(LocalTime.NOON))
        .withSecond(0).withNano(0).atZone(zone).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val tag = "manual-${UUID.randomUUID()}"
    return when (kind) {
        RecordKind.GLUCOSE -> EditableRecord.Glucose(
            CgmEntity(userId = userId, timestamp = at, valueMgdl = 0, fiveMinuteRateMgdl = 0f, direction = "Flat", device = MANUAL_SOURCE, source = MANUAL_SOURCE, sourceId = tag)
        )
        RecordKind.INSULIN -> EditableRecord.Insulin(
            BolusEntity(
                userId = userId, timestampUtc = at, createdAtUtc = now, updatedAtUtc = now, value = 0f, eventType = "Manual bolus",
                isSmb = false, pumpType = "", pumpSerial = "", pumpId = 0, sourceId = null
            )
        )
        RecordKind.MEALS -> EditableRecord.Meal(
            MealEntity(
                userId = userId, description = null, createdAtUtc = now, mealTimeUtc = at, imageId = "",
                recommendation = null, reasoning = null, sittingId = UUID.randomUUID().toString()
            )
        )
        RecordKind.WORKOUTS -> EditableRecord.Workout(
            ExerciseEntity(userId = userId, sourceId = tag, startUtc = at, endUtc = at, exerciseType = 0, title = null)
        )
        RecordKind.SLEEP -> {
            val end = day.atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
            val start = day.minusDays(1).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
            EditableRecord.Sleep(
                listOf(
                    SleepStageEntity(
                        userId = userId, sessionId = tag, startUtc = start, endUtc = end, stage = SleepStage.SLEEPING.code,
                        sessionStartUtc = start, sessionEndUtc = end
                    )
                )
            )
        }
        RecordKind.DEVICE -> EditableRecord.Device(
            PumpEventEntity(
                userId = userId, timestampUtc = at, createdAtUtc = now, eventType = "Site Change", sourceId = tag,
                rawJson = """{"eventType":"Site Change","${PumpEventNormalizer.SOURCE_KEY}":"$MANUAL_SOURCE"}"""
            )
        )
        RecordKind.HEART_RATE -> EditableRecord.HeartRate(HeartRateEntity(userId = userId, timestamp = at, bpm = 0))
        RecordKind.STEPS -> EditableRecord.Steps(StepsEntity(userId = userId, startUtc = at, count = 0))
    }
}
