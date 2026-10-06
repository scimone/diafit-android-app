package uk.scimone.diafit

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.model.toSittings

class MealSittingTest {
    private val min = 60_000L

    private fun meal(id: Int, minute: Long, sitting: String? = null, carbs: Int = 10, sourceId: String? = null) = MealEntity(
        id = id, userId = 1, description = null, createdAtUtc = 0, mealTimeUtc = minute * min,
        carbohydrates = carbs, imageId = "", recommendation = null, reasoning = null,
        sittingId = sitting, sourceId = sourceId
    )

    private fun List<MealSitting>.ids() = map { s -> s.courses.map { it.id } }

    @Test
    fun `courses of one sitting stay together however far apart`() {
        val sittings = listOf(meal(1, 0, "a"), meal(2, 40, "a"), meal(3, 95, "a")).toSittings()
        assertEquals(listOf(listOf(1, 2, 3)), sittings.ids())
        assertEquals(30, sittings.single().totalCarbs)
    }

    @Test
    fun `two different sittings are never merged even when close`() {
        val sittings = listOf(meal(1, 0, "a"), meal(2, 5, "b")).toSittings()
        assertEquals(listOf(listOf(1), listOf(2)), sittings.ids())
    }

    @Test
    fun `untagged entries join a nearby sitting, also inside its span`() {
        val sittings = listOf(
            meal(1, 0, "a"), meal(2, 60, "a"),
            meal(3, 30, sourceId = "aaps"),   // inside the meal
            meal(4, 70, sourceId = "aaps"),   // 10 min after its last course
            meal(5, 200, sourceId = "aaps")   // unrelated
        ).toSittings()
        assertEquals(listOf(listOf(1, 3, 2, 4), listOf(5)), sittings.ids())
    }

    @Test
    fun `untagged entries chain like before`() {
        val sittings = listOf(meal(1, 0), meal(2, 10), meal(3, 20), meal(4, 60)).toSittings()
        assertEquals(listOf(listOf(1, 2, 3), listOf(4)), sittings.ids())
    }

    @Test
    fun `effect end is the latest absorption end of any course`() {
        val s = MealSitting(listOf(meal(1, 0, "a"), meal(2, 30, "a")))
        // MEDIUM = 240 min
        assertEquals((30 + 240) * min, s.effectEndTime)
    }
}
