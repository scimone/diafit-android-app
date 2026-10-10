package uk.scimone.diafit.history.presentation.model

import androidx.compose.runtime.saveable.listSaver

/** Which context layers the History charts draw next to glucose (glucose itself is always on). */
data class HistoryLayers(
    /** Carb dots and insulin ticks. */
    val treatments: Boolean = false,
    /** Grey wash behind glucose while asleep. */
    val sleep: Boolean = false,
    /** Heart-rate / steps intensity and workouts. */
    val activity: Boolean = false
) {
    companion object {
        val Saver = listSaver<HistoryLayers, Boolean>(
            save = { listOf(it.treatments, it.sleep, it.activity) },
            restore = { HistoryLayers(it[0], it[1], it[2]) }
        )
    }
}
