package uk.scimone.diafit.mealactivity

import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.mealactivity.domain.*
import java.util.Random

const val MIN = 60_000L
const val HOUR = 3_600_000L
const val T0 = 1_760_000_000_000L

fun meal(id: Int, timeUtc: Long, carbs: Int, protein: Int? = 10, fat: Int? = 10, user: Int = 1, impact: ImpactType = ImpactType.MEDIUM,
         sittingId: String? = "s$id", sourceId: String? = null) =
    MealEntity(id = id, userId = user, description = "m$id", createdAtUtc = timeUtc, mealTimeUtc = timeUtc, carbohydrates = carbs,
        proteins = protein, fats = fat, impactType = impact, imageId = "", recommendation = null, reasoning = null,
        sittingId = sittingId, sourceId = sourceId)

fun cgm(t: Long, v: Int, user: Int = 1) = CgmEntity(userId = user, timestamp = t, valueMgdl = v, fiveMinuteRateMgdl = 0f)

/** Synthetic world: glucose follows the documented observation model under a known "true" personal adjustment. */
class World(val trueAdjust: PersonalAdjust, val noise: Double = 3.0, seed: Long = 1) {
    val rnd = Random(seed)
    val meals = ArrayList<MealEntity>()
    val readings = ArrayList<CgmEntity>()
    private val engine = MealActivityEngine(adjust = trueAdjust)

    /** [n] meals 7 h apart, varied size and composition; CGM every 5 min around each. */
    fun generate(n: Int, startUtc: Long = T0, firstId: Int = 1, dropCgm: Boolean = false) {
        for (i in 0 until n) {
            val t = startUtc + i * 7 * HOUR
            val carbs = 25 + rnd.nextInt(70)
            val m = meal(firstId + i, t, carbs, protein = 5 + rnd.nextInt(40), fat = 3 + rnd.nextInt(50))
            meals += m
            if (dropCgm) continue
            val spec = listOf(CourseSpec(0, m.toMealInput()))
            val shape = ObservationModel.shape(spec, MealActivityEngine(adjust = trueAdjust, grid = CurveGrid(horizonMinutes = 390, stepMinutes = 5)))
            val gain = 2.5 + rnd.nextDouble() * 1.5
            for (k in -4..78) {
                val tk = t + k * 5 * MIN
                val delta = if (k <= 0) 0.0 else gain * shape[k]
                readings += cgm(tk, (110 + delta + rnd.nextGaussian() * noise).toInt())
            }
        }
    }

    fun readingsBetween(from: Long, to: Long) = readings.filter { it.timestamp in from..to }.sortedBy { it.timestamp }
    fun trainingSet(now: Long = T0 + 400 * HOUR) = TrainingExampleBuilder.buildAll(meals, now, readingsBetween = ::readingsBetween)
}
