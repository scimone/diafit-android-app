package uk.scimone.diafit.dailyrecap.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.scimone.diafit.dailyrecap.domain.DailyRecap
import uk.scimone.diafit.dailyrecap.domain.RecapAggregator
import uk.scimone.diafit.dailyrecap.domain.RecapPoint
import uk.scimone.diafit.settings.domain.model.DEFAULT_AI_MODEL
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Builds, writes, stores and (optionally) announces the recap of one day. */
class DailyRecapService(
    private val loader: RecapDataLoader,
    private val llm: RecapLlm,
    private val store: DailyRecapStore,
    private val settings: SettingsRepository,
    private val notifier: RecapNotifier,
    private val notifications: uk.scimone.diafit.core.data.local.NotificationDao
) {
    /** Generates the full recap of [date], stores it, adds it to the bell and pushes it unless push is switched off. */
    suspend fun generate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Result<DailyRecap> = withContext(Dispatchers.IO) {
        try {
            if (!settings.isAiEnabled()) return@withContext Result.failure(IllegalStateException("AI is switched off in the menu."))
            val config = store.config.value
            val connections = settings.getAiConnections()
            val connection = connections.firstOrNull { it.id == config.connectionId } ?: connections.firstOrNull()
            if (connection == null || connection.apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("No AI connection set up. Add one under Menu → AI."))
            }
            val model = config.model.ifBlank { settings.getMealEstimationSettings().model.ifBlank { DEFAULT_AI_MODEL } }
            val now = System.currentTimeMillis()
            val (input, visual) = loader.load(date, USER_ID, now, zone)
            if (input.glucose == null && input.meals.isEmpty() && input.insulin == null) {
                return@withContext Result.failure(IllegalStateException("There is no data for $date."))
            }
            val language = when (Locale.getDefault().language) { "de" -> "German"; "it" -> "Italian"; else -> null }
            val text = llm.write(connection, model, input, language, config.prompt).getOrElse { return@withContext Result.failure(it) }

            fun point(p: Pair<String, String>) = RecapPoint(p.first, timeOn(date, p.second, zone))
            val recap = DailyRecap(
                epochDay = date.toEpochDay(), generatedAtUtc = now, partialDay = input.partialDayUntil != null,
                headline = text.headline, summary = text.summary, mood = text.mood,
                wentWell = text.wentWell.map(::point), wentWrong = text.wentWrong.map(::point), doToday = text.doToday,
                visual = visual
            )
            store.save(recap)
            // A regenerated recap replaces its earlier bell entry (the key is unique per day).
            notifications.deleteByKey("recap-${recap.epochDay}")
            notifications.insert(
                uk.scimone.diafit.core.domain.model.AppNotificationEntity(
                    timestampUtc = now, title = recap.headline, text = recap.notificationText(),
                    dedupeKey = "recap-${recap.epochDay}", link = recapLink(recap.epochDay)
                )
            )
            if (config.push) notifier.push(recap)
            Result.success(recap)
        } catch (e: Exception) {
            Log.e(TAG, "Recap for $date failed", e)
            Result.failure(e)
        }
    }

    /** "HH:mm" on [date] as epoch ms; null for a blank or unparsable answer. */
    internal fun timeOn(date: LocalDate, hhmm: String, zone: ZoneId): Long? =
        runCatching { date.atTime(LocalTime.parse(hhmm.trim().take(5))).atZone(zone).toInstant().toEpochMilli() }.getOrNull()

    companion object {
        private const val TAG = "DailyRecap"
        const val USER_ID = 1
    }
}
