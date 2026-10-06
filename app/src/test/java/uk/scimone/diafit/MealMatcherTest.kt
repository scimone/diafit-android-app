package uk.scimone.diafit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealMatcher

class MealMatcherTest {
    private val min = 60_000L

    private fun logged(id: Int, minute: Long, carbs: Int, sitting: String? = null) = MealEntity(
        id = id, userId = 1, description = null, createdAtUtc = 0, mealTimeUtc = minute * min,
        carbohydrates = carbs, imageId = "photo$id", recommendation = null, reasoning = null, sittingId = sitting
    )

    private fun aaps(id: Int, minute: Long, carbs: Int, declined: Boolean = false) = MealEntity(
        id = id, userId = 1, description = "Carbs", createdAtUtc = 0, mealTimeUtc = minute * min,
        carbohydrates = carbs, imageId = "", recommendation = null, reasoning = null,
        sourceId = "aaps-$id", mergeDeclined = declined
    )

    @Test
    fun `same carbs a few minutes after the photo merge confidently`() {
        val m = MealMatcher.findMatches(listOf(logged(1, 0, 45), aaps(2, 12, 45))).single()
        assertEquals(1, m.logged.id); assertEquals(2, m.imported.id)
        assertTrue(m.confident)
    }

    @Test
    fun `slightly different carbs are a suggestion only when beyond the confident tolerance`() {
        assertTrue(MealMatcher.findMatches(listOf(logged(1, 0, 45), aaps(2, 10, 44))).single().confident)
        assertFalse(MealMatcher.findMatches(listOf(logged(1, 0, 45), aaps(2, 10, 40))).single().confident)
    }

    @Test
    fun `dose before the photo within 15 minutes still matches`() {
        assertEquals(1, MealMatcher.findMatches(listOf(logged(1, 20, 30), aaps(2, 8, 30))).size)
        assertTrue(MealMatcher.findMatches(listOf(logged(1, 40, 30), aaps(2, 8, 30))).isEmpty())
    }

    @Test
    fun `too late or too different does not match`() {
        assertTrue(MealMatcher.findMatches(listOf(logged(1, 0, 45), aaps(2, 100, 45))).isEmpty())
        assertTrue(MealMatcher.findMatches(listOf(logged(1, 0, 45), aaps(2, 10, 15))).isEmpty()) // hypo treatment during a meal
    }

    @Test
    fun `late within the window is only a suggestion`() {
        val m = MealMatcher.findMatches(listOf(logged(1, 0, 45), aaps(2, 75, 45))).single()
        assertFalse(m.confident)
    }

    @Test
    fun `declined entries are never matched`() {
        assertTrue(MealMatcher.findMatches(listOf(logged(1, 0, 45), aaps(2, 5, 45, declined = true))).isEmpty())
    }

    @Test
    fun `each entry is used once and ambiguity is not confident`() {
        val result = MealMatcher.findMatches(listOf(logged(1, 0, 45), logged(2, 20, 45), aaps(3, 10, 45)))
        assertEquals(1, result.size)
        assertFalse(result.single().confident)
    }

    @Test
    fun `two meals two doses pair up by time`() {
        val result = MealMatcher.findMatches(listOf(logged(1, 0, 45), logged(2, 240, 60), aaps(3, 5, 45), aaps(4, 245, 60)))
        assertEquals(setOf(1 to 3, 2 to 4), result.map { it.logged.id to it.imported.id }.toSet())
        assertTrue(result.all { it.confident })
    }

    @Test
    fun `one dose for a whole multi-course meal links at sitting level`() {
        val result = MealMatcher.findMatches(listOf(logged(1, 0, 30, "s"), logged(2, 40, 20, "s"), aaps(3, 5, 50)))
        val m = result.single()
        assertTrue(m.sittingLevel)
        assertEquals(1, m.logged.id)
    }

    @Test
    fun `a dose matching one course prefers that course over the sitting`() {
        val m = MealMatcher.findMatches(listOf(logged(1, 0, 30, "s"), logged(2, 40, 20, "s"), aaps(3, 5, 30))).single()
        assertFalse(m.sittingLevel)
        assertEquals(1, m.logged.id)
    }

    @Test
    fun `imported entries alone or already linked meals do not match`() {
        assertTrue(MealMatcher.findMatches(listOf(aaps(1, 0, 45))).isEmpty())
        assertTrue(MealMatcher.findMatches(listOf(logged(1, 0, 45).copy(aapsLinked = true), aaps(2, 5, 45))).isEmpty())
    }
}
