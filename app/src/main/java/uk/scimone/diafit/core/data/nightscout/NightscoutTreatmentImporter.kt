package uk.scimone.diafit.core.data.nightscout

import uk.scimone.diafit.core.data.local.BolusDao
import uk.scimone.diafit.core.data.networking.NightscoutApi
import uk.scimone.diafit.core.data.networking.dto.NightscoutTreatmentMapper
import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.model.PumpEventNormalizer
import uk.scimone.diafit.core.domain.model.dataType
import uk.scimone.diafit.core.domain.model.toProfileSwitch
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.repository.PumpEventRepository
import uk.scimone.diafit.core.domain.usecase.MergeCarbEntriesUseCase
import uk.scimone.diafit.core.domain.util.networking.Result
import uk.scimone.diafit.settings.domain.model.DataType
import kotlin.math.abs

/**
 * Imports Nightscout `treatments` (and the `profile` collection) into the local database. Used for the routine
 * live sync and for backfill alike; every insert is de-duplicated, so any range can be imported again.
 */
class NightscoutTreatmentImporter(
    private val nightscout: NightscoutApi,
    private val bolusDao: BolusDao,
    private val mealRepository: MealRepository,
    private val pumpEvents: PumpEventRepository,
    private val mergeCarbEntries: MergeCarbEntriesUseCase,
    private val userId: Int = 1
) {
    /** Data types that come out of Nightscout's treatments (everything except glucose). */
    companion object {
        val TYPES = setOf(DataType.BOLUS, DataType.FOOD, DataType.BASAL, DataType.PROFILE, DataType.DEVICE)
        private const val HOUR = 3_600_000L
        /** A live event and the same one from Nightscout can be stamped seconds apart. */
        private const val TEMP_BASAL_SAME_MS = 90_000L
    }

    /** Fetches treatments in [startMs, endMs) once and stores what [types] ask for. Returns the rows added per type. */
    suspend fun import(types: Set<DataType>, startMs: Long, endMs: Long): Map<DataType, Int> {
        val wanted = types.intersect(TYPES)
        if (wanted.isEmpty()) return emptyMap()
        val docs = when (val r = nightscout.getTreatments(startMs, endMs)) {
            is Result.Success -> r.data
            is Result.Error -> error("Nightscout treatments request failed (${r.error}).")
        }
        val added = mutableMapOf<DataType, Int>()
        if (DataType.BOLUS in wanted) added[DataType.BOLUS] = importBoluses(docs, startMs, endMs)
        if (DataType.FOOD in wanted) added[DataType.FOOD] = importMeals(docs)
        if (DataType.BASAL in wanted) added[DataType.BASAL] = importTempBasals(docs, startMs, endMs)
        val events = wanted.intersect(setOf(DataType.PROFILE, DataType.DEVICE))
        if (events.isNotEmpty()) added.putAll(importEvents(docs, events, startMs, endMs))
        if (DataType.PROFILE in wanted) added[DataType.PROFILE] = (added[DataType.PROFILE] ?: 0) + importBaselineProfile(startMs)
        return added
    }

    private suspend fun importBoluses(docs: List<kotlinx.serialization.json.JsonObject>, start: Long, end: Long): Int {
        val existing = bolusDao.getBolusBetween(start - HOUR, end + HOUR, userId).map { it.timestampUtc }.toHashSet()
        var added = 0
        docs.mapNotNull { NightscoutTreatmentMapper.toBolus(it, userId) }
            .filter { existing.add(it.timestampUtc) }
            .forEach { bolusDao.insertBolus(it); added++ }
        return added
    }

    private suspend fun importMeals(docs: List<kotlinx.serialization.json.JsonObject>): Int {
        var added = 0
        docs.mapNotNull { NightscoutTreatmentMapper.toMeal(it, userId) }.forEach { meal ->
            if (meal.sourceId != null && mealRepository.existsBySourceId(meal.sourceId)) return@forEach
            if (mealRepository.existsImportedAt(meal.mealTimeUtc, meal.carbohydrates)) return@forEach
            if (mealRepository.createMeal(meal).isSuccess) added++
        }
        if (added > 0) runCatching { mergeCarbEntries(userId) }
        return added
    }

    private suspend fun importTempBasals(docs: List<kotlinx.serialization.json.JsonObject>, start: Long, end: Long): Int {
        // A live temp basal (AAPS status broadcast) and the same one from Nightscout start seconds apart.
        val existing = pumpEvents.getBetween(start - HOUR, end + HOUR, userId)
            .filter { it.eventType == "Temp Basal" }.map { it.timestampUtc }
        var added = 0
        docs.mapNotNull { NightscoutTreatmentMapper.toTempBasal(it, userId) }
            .filter { e -> existing.none { abs(it - e.timestampUtc) < TEMP_BASAL_SAME_MS } }
            .forEach { if (pumpEvents.insert(it)) added++ }
        return added
    }

    /** Profile switches / temporary targets ([DataType.PROFILE]) and sensor / pump changes etc. ([DataType.DEVICE]). */
    private suspend fun importEvents(
        docs: List<kotlinx.serialization.json.JsonObject>, types: Set<DataType>, start: Long, end: Long
    ): Map<DataType, Int> {
        val existing = pumpEvents.getBetween(start - HOUR, end + HOUR, userId).toMutableList()
        val added = types.associateWith { 0 }.toMutableMap()
        for (event in docs.mapNotNull { NightscoutTreatmentMapper.toPumpEvent(it, userId) }) {
            val type = event.dataType
            if (type !in types) continue
            // The live AAPS copy may lack Nightscout's _id (then keyed by type + time): don't store it twice.
            if (existing.any { PumpEventNormalizer.isSameEvent(it, event) }) continue
            if (pumpEvents.insert(event)) { added[type] = added.getValue(type) + 1; existing += event }
        }
        return added
    }

    /**
     * Nightscout's stored profile as a starting point: only when no profile switch with a profile is known at or
     * before [startMs], so the basal / ISF / carb ratio of earlier time can be shown. Never replaces a real switch.
     */
    private suspend fun importBaselineProfile(startMs: Long): Int {
        val known = pumpEvents.getBefore("Profile Switch", startMs + 1, 10, userId).any { it.toProfileSwitch()?.profile != null }
        if (known) return 0
        val docs = when (val r = nightscout.getProfiles()) {
            is Result.Success -> r.data
            is Result.Error -> return 0 // optional extra: the treatments above already succeeded
        }
        val baseline: PumpEventEntity = docs
            .mapNotNull { NightscoutTreatmentMapper.profileDocToSwitch(it, userId) }
            .filter { it.timestampUtc <= startMs }
            .maxByOrNull { it.timestampUtc } ?: return 0
        return if (pumpEvents.insert(baseline)) 1 else 0
    }
}
