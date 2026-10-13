package uk.scimone.diafit.dailyrecap.data

import uk.scimone.diafit.core.data.nightscout.DeviceLifetimeStore
import uk.scimone.diafit.core.data.nightscout.DeviceStatusStore
import uk.scimone.diafit.core.domain.model.GlucoseSample
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.core.domain.model.deliveredUnits
import uk.scimone.diafit.core.domain.model.deviceAges
import uk.scimone.diafit.core.domain.model.toSittings
import uk.scimone.diafit.core.domain.repository.ActivityRepository
import uk.scimone.diafit.core.domain.repository.BolusRepository
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.GetBasalTimelineUseCase
import uk.scimone.diafit.dailyrecap.domain.RecapAggregator
import uk.scimone.diafit.dailyrecap.domain.RecapInput
import uk.scimone.diafit.dailyrecap.domain.RecapSource
import uk.scimone.diafit.dailyrecap.domain.RecapVisual
import uk.scimone.diafit.devices.presentation.DeviceInputsSource
import uk.scimone.diafit.patterns.data.PatternStore
import uk.scimone.diafit.patterns.domain.PatternState
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import java.time.LocalDate
import java.time.ZoneId

/** Reads one day (and the week before it) from the local database and hands it to [RecapAggregator]. */
class RecapDataLoader(
    private val cgm: CgmRepository,
    private val boluses: BolusRepository,
    private val meals: MealRepository,
    private val activity: ActivityRepository,
    private val basalTimeline: GetBasalTimelineUseCase,
    private val getTargetRange: GetTargetRangeUseCase,
    private val patterns: PatternStore,
    private val deviceInputs: DeviceInputsSource,
    private val lifetimes: DeviceLifetimeStore,
    private val deviceStatus: DeviceStatusStore
) {
    suspend fun load(date: LocalDate, userId: Int, nowUtc: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Pair<RecapInput, RecapVisual> {
        val start = RecapAggregator.dayStart(date, zone)
        val end = RecapAggregator.dayEnd(date, zone)
        val target: GlucoseTargetRange = getTargetRange().toCore()

        val weekStart = RecapAggregator.dayStart(date.minusDays(7), zone)
        val weekReadings = cgm.getEntriesBetween(weekStart, start, userId)
        val previousDays = (7 downTo 1).map { back ->
            val from = RecapAggregator.dayStart(date.minusDays(back.toLong()), zone)
            val to = RecapAggregator.dayEnd(date.minusDays(back.toLong()), zone)
            weekReadings.filter { it.timestamp in from until to }.map { GlucoseSample(it.timestamp, it.valueMgdl) }
        }

        val sittings = meals.getUnmergedSince(userId, start - MARGIN_MS).filter { it.mealTimeUtc < end }.toSittings()
        val basalUntil = minOf(end, nowUtc)
        val basal = runCatching { basalTimeline(start, basalUntil, userId).deliveredUnits(start, basalUntil) }.getOrNull()?.takeIf { it > 0 }

        val inputs = deviceInputs.load(userId, nowUtc)
        val status = deviceStatus.status.value
        val dueSoon = deviceAges(
            inputs.events, nowUtc, lifetimes.lifetimes.value,
            batteryReported = status?.let { it.pumpBatteryPercent != null || it.pumpBatteryVolt != null } == true,
            pumpType = inputs.pumpType
        ).mapNotNull { age ->
            val left = age.remainingMs ?: return@mapNotNull null
            if (left > DUE_SOON_MS) return@mapNotNull null
            val hours = kotlin.math.abs(left) / 3_600_000
            "${age.kind.name.lowercase()} ${if (left < 0) "overdue by $hours h" else "due in $hours h"}"
        }

        val known = patterns.getAll()
            .filter { it.state != PatternState.RESOLVED && it.state != PatternState.DISMISSED }
            .sortedByDescending { it.card.priority }.take(MAX_PATTERNS)
            .map { it.card.title.text() }

        return RecapAggregator.build(
            RecapSource(
                date = date, zone = zone, nowUtc = nowUtc, target = target,
                readings = cgm.getEntriesBetween(start, end + TAIL_MS, userId),
                previousDays = previousDays,
                boluses = boluses.getBolusBetween(start - MARGIN_MS, end + MARGIN_MS, userId),
                basalUnits = basal,
                sittings = sittings,
                activity = activity.getBetween(start - 12 * 3_600_000L, end, userId),
                knownPatterns = known,
                devicesDueSoon = dueSoon
            )
        )
    }

    private companion object {
        const val MARGIN_MS = 60 * 60_000L
        const val TAIL_MS = 4 * 60 * 60_000L
        const val DUE_SOON_MS = 24 * 3_600_000L
        const val MAX_PATTERNS = 4
    }
}
