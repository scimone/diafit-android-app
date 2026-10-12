package uk.scimone.diafit.mealactivity.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import uk.scimone.diafit.mealactivity.domain.ActivitySeries
import uk.scimone.diafit.mealactivity.domain.CurveGrid

object CurveStatus { const val CURRENT = "CURRENT"; const val SUPERSEDED = "SUPERSEDED"; const val INVALID = "INVALID" }
object ModelStatus { const val ACTIVE = "ACTIVE"; const val SUPERSEDED = "SUPERSEDED"; const val REJECTED = "REJECTED" }
object Method { const val BASELINE = "baseline"; const val PERSONALIZED = "personalized" }

/**
 * A materialised meal activity curve (one per course), traceable to its inputs and model. Series are CSV text on the
 * [CurveGrid] (`carbs_active_g` is derived from the rate and [activityWindowHours], not stored). At most one row per meal
 * is CURRENT; the previous one is kept as SUPERSEDED (retention: one superseded row per meal), rows for meals that are
 * deleted or merged become INVALID. (mealId, inputFingerprint) is unique, so a retried job cannot add duplicate rows.
 */
@Entity(
    indices = [
        Index(value = ["mealId", "inputFingerprint"], unique = true),
        Index(value = ["mealId", "status"]),
        Index(value = ["userId", "status"])
    ]
)
data class MealActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Int,
    val mealId: Int,
    val mealTimeUtc: Long,
    val algorithmVersion: Int,
    /** [Method.BASELINE] or [Method.PERSONALIZED]. */
    val method: String,
    val modelVersion: Int?,
    val trainingRevision: String?,
    val inputFingerprint: String,
    val quality: String,
    val qualityNotes: String?,
    val horizonMinutes: Int,
    val stepMinutes: Int,
    val activityWindowHours: Double,
    val carbsG: Double,
    val carbsRemainingG: String,
    val carbsRateGph: String,
    val proteinRemainingG: String?,
    val proteinActivityGph: String?,
    val fatRemainingG: String?,
    val fatActivityGph: String?,
    val generatedAtUtc: Long,
    val status: String
) {
    val grid: CurveGrid get() = CurveGrid(horizonMinutes, stepMinutes)

    fun toSeries(): ActivitySeries = ActivitySeries(
        grid, csv(carbsRemainingG)!!, csv(carbsRateGph)!!, csv(proteinRemainingG), csv(proteinActivityGph),
        csv(fatRemainingG), csv(fatActivityGph), activityWindowHours
    )

    companion object {
        fun csv(a: DoubleArray?): String? = a?.joinToString(",") { "%.4f".format(java.util.Locale.ROOT, it) }
        fun csv(s: String?): DoubleArray? = s?.split(',')?.map { it.toDouble() }?.toDoubleArray()
    }
}

/** A trained (or rejected) set of personal timing parameters for one user, with the evidence for the decision. */
@Entity(indices = [Index(value = ["userId", "version"], unique = true), Index(value = ["userId", "status"])])
data class PersonalActivityModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Int,
    val version: Int,
    val status: String,
    val adjustJson: String,
    /** Revision of the training episodes the model was fitted on. */
    val datasetRevision: String,
    val algoKey: String,
    val trainedAtUtc: Long,
    val episodeCount: Int,
    val reportJson: String,
    val note: String?
)

/** Per-user pipeline bookkeeping: what was last processed and with what result (job status for operations/logging). */
@Entity
data class ActivityPipelineStateEntity(
    @PrimaryKey val userId: Int,
    val processedRevision: String?,
    val processedAlgoKey: String?,
    val lastFullRunUtc: Long,
    val lastRunUtc: Long,
    val lastResult: String,
    val lastMessage: String?,
    val eligibleEpisodes: Int,
    val curvesRecalculated: Int,
    val curvesTotal: Int
)
