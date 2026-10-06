package uk.scimone.diafit.core.domain.model

/**
 * Carb absorption rate (g/h) at [time] for a meal of [carbs] grams eaten at [mealTime], spread over
 * [durationMinutes]. Uses a Beta(2,3)-shaped rate (peak after ~1/3 of the duration, longer tail),
 * which integrates to exactly [carbs] — the simple analogue of [InsulinActivity] for carbs.
 */
object CarbActivity {
    fun calculate(carbs: Double, mealTime: Long, time: Long, durationMinutes: Int): Double {
        if (carbs <= 0.0 || durationMinutes <= 0) return 0.0
        val x = (time - mealTime) / 60_000.0 / durationMinutes
        if (x <= 0.0 || x >= 1.0) return 0.0
        val perMinute = carbs * 12 * x * (1 - x) * (1 - x) / durationMinutes
        return perMinute * 60
    }
}
