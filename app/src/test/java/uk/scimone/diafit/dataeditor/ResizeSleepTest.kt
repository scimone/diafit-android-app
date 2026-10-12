package uk.scimone.diafit.dataeditor

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import uk.scimone.diafit.dataeditor.data.DataEditorRepository

class ResizeSleepTest {
    private fun stage(start: Long, end: Long) = SleepStageEntity(
        userId = 1, sessionId = "s", startUtc = start, endUtc = end, stage = 4, sessionStartUtc = 0, sessionEndUtc = 300
    )
    private val stages = listOf(stage(0, 100), stage(100, 200), stage(200, 300))

    @Test fun stretchesTheEnds() {
        val r = DataEditorRepository.resizeSleep(stages, -50, 400)
        assertEquals(listOf(-50L, 100L, 200L), r.map { it.startUtc })
        assertEquals(listOf(100L, 200L, 400L), r.map { it.endUtc })
        assertEquals(setOf(-50L), r.map { it.sessionStartUtc }.toSet())
        assertEquals(setOf(400L), r.map { it.sessionEndUtc }.toSet())
    }

    @Test fun trimmingDropsStagesOutsideTheRange() {
        val r = DataEditorRepository.resizeSleep(stages, 120, 250)
        assertEquals(listOf(120L, 200L), r.map { it.startUtc })
        assertEquals(listOf(200L, 250L), r.map { it.endUtc })
    }
}
