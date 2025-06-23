package uk.scimone.diafit.core.domain.model

import kotlin.math.exp
import kotlin.math.pow

data class InsulinActivity(
    val activity: Double
) {
    companion object {
        fun calculate(
            bolusAmount: Double,
            bolusTime: Long = 0L,
            time: Long = 0L,
            dia: Double = 4.5, // Default DIA in hours
            peak: Double = 45.0 // Default peak time in minutes
        ): InsulinActivity {
            require(dia != 0.0)
            require(peak != 0.0)

            if (bolusAmount == 0.0) return InsulinActivity(0.0)

            val t = (time - bolusTime) / 1000.0 / 60.0
            val td = dia * 60
            val tp = peak

            if (t >= td) return InsulinActivity(0.0)

            val tau = tp * (1 - tp / td) / (1 - 2 * tp / td)
            val a = 2 * tau / td
            val s = 1 / (1 - a + (1 + a) * exp(-td / tau))

            val activity = bolusAmount * (s / tau.pow(2.0)) * t * (1 - t / td) * exp(-t / tau)

            return InsulinActivity(activity)
        }
    }
}
