package uk.scimone.diafit.dataeditor.data

import androidx.annotation.StringRes
import uk.scimone.diafit.R
import uk.scimone.diafit.core.presentation.AppStrings

import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import uk.scimone.diafit.core.domain.model.StepsEntity

/** What the data editor can list, in the order of the chips. */
enum class RecordKind(@StringRes val labelRes: Int) {
    GLUCOSE(R.string.de_kind_glucose),
    INSULIN(R.string.de_kind_insulin),
    MEALS(R.string.logs_kind_meal_plural),
    WORKOUTS(R.string.de_kind_workouts),
    SLEEP(R.string.datatype_sleep),
    DEVICE(R.string.logs_kind_device_plural),
    HEART_RATE(R.string.datatype_heart_rate),
    STEPS(R.string.datatype_steps);

    val label: String get() = AppStrings.get(labelRes)
}

/** One stored row (or, for sleep, one session) as the editor sees it. */
sealed interface EditableRecord {
    val kind: RecordKind
    /** Unique within a kind. */
    val key: String
    /** Where it sits on the day list (epoch ms). */
    val timeUtc: Long

    data class Glucose(val e: CgmEntity) : EditableRecord {
        override val kind get() = RecordKind.GLUCOSE
        override val key get() = "g${e.id}"
        override val timeUtc get() = e.timestamp
    }
    data class Insulin(val e: BolusEntity) : EditableRecord {
        override val kind get() = RecordKind.INSULIN
        override val key get() = "b${e.id}"
        override val timeUtc get() = e.timestampUtc
    }
    data class Meal(val e: MealEntity) : EditableRecord {
        override val kind get() = RecordKind.MEALS
        override val key get() = "m${e.id}"
        override val timeUtc get() = e.mealTimeUtc
    }
    data class Workout(val e: ExerciseEntity) : EditableRecord {
        override val kind get() = RecordKind.WORKOUTS
        override val key get() = "w${e.id}"
        override val timeUtc get() = e.startUtc
    }
    /** All stage rows of one session, oldest first. */
    data class Sleep(val stages: List<SleepStageEntity>) : EditableRecord {
        override val kind get() = RecordKind.SLEEP
        override val key get() = "s${stages.first().sessionId}"
        override val timeUtc get() = stages.first().sessionEndUtc
        val sessionId get() = stages.first().sessionId
        val startUtc get() = stages.first().sessionStartUtc
        val endUtc get() = stages.first().sessionEndUtc
    }
    data class Device(val e: PumpEventEntity) : EditableRecord {
        override val kind get() = RecordKind.DEVICE
        override val key get() = "d${e.id}"
        override val timeUtc get() = e.timestampUtc
    }
    data class HeartRate(val e: HeartRateEntity) : EditableRecord {
        override val kind get() = RecordKind.HEART_RATE
        override val key get() = "h${e.id}"
        override val timeUtc get() = e.timestamp
    }
    data class Steps(val e: StepsEntity) : EditableRecord {
        override val kind get() = RecordKind.STEPS
        override val key get() = "t${e.id}"
        override val timeUtc get() = e.startUtc
    }
}
