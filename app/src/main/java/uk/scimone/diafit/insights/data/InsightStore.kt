package uk.scimone.diafit.insights.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import uk.scimone.diafit.insights.domain.InsightCard
import uk.scimone.diafit.insights.domain.InsightState
import uk.scimone.diafit.insights.domain.StoredInsight

/** Reads and writes [StoredInsight]s through Room, with the card serialised as JSON. */
class InsightStore(private val dao: InsightDao) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeAll(): Flow<List<StoredInsight>> = dao.observeAll().map { rows -> rows.mapNotNull(::decode) }

    fun observe(id: String): Flow<StoredInsight?> = dao.observe(id).map { it?.let(::decode) }

    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    suspend fun getAll(): List<StoredInsight> = dao.getAll().mapNotNull(::decode)

    /** Replaces the stored set with [insights] (rows no longer in it are deleted). */
    suspend fun replaceAll(insights: List<StoredInsight>) {
        val keep = insights.map { it.card.id }.toSet()
        dao.delete(dao.getAll().map { it.id }.filter { it !in keep })
        dao.upsertAll(insights.map(::encode))
    }

    suspend fun markRead(id: String) = dao.markRead(id)
    suspend fun markAllRead() = dao.markAllRead()
    suspend fun dismiss(id: String) = dao.setState(id, InsightState.DISMISSED.name)

    /** Bringing a dismissed insight back: ongoing and reviewed, it only counts again if it changes. */
    suspend fun restore(id: String) = dao.setState(id, InsightState.ONGOING.name)

    suspend fun markNotified(ids: List<String>, at: Long) = dao.markNotified(ids, at)

    private fun encode(s: StoredInsight) = InsightEntity(
        id = s.card.id, state = s.state.name, isRead = s.isRead, priority = s.card.priority,
        firstSeenUtc = s.firstSeenUtc, lastSeenUtc = s.lastSeenUtc, missedScans = s.missedScans,
        referenceEffect = s.referenceEffect, notifiedAtUtc = s.notifiedAtUtc,
        payload = json.encodeToString(InsightCard.serializer(), s.card)
    )

    private fun decode(e: InsightEntity): StoredInsight? = runCatching {
        StoredInsight(
            card = json.decodeFromString(InsightCard.serializer(), e.payload),
            state = InsightState.valueOf(e.state), isRead = e.isRead,
            firstSeenUtc = e.firstSeenUtc, lastSeenUtc = e.lastSeenUtc, missedScans = e.missedScans,
            referenceEffect = e.referenceEffect, notifiedAtUtc = e.notifiedAtUtc
        )
    }.getOrNull() // an unreadable row (older card format) is simply skipped; the next scan rewrites it
}
