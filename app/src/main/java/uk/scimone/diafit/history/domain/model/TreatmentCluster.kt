package uk.scimone.diafit.history.domain.model

/** A single carb or insulin entry reduced to what clustering needs. */
data class TreatmentEvent(val timeUtc: Long, val value: Float)

/** Entries close in time (e.g. a meal and its correction bolus) merged into one block. */
data class TreatmentCluster(val events: List<TreatmentEvent>) {
    val startUtc: Long get() = events.first().timeUtc
    val endUtc: Long get() = events.last().timeUtc
    val centerUtc: Long get() = startUtc + (endUtc - startUtc) / 2
    val total: Float get() = events.sumOf { it.value.toDouble() }.toFloat()
}
