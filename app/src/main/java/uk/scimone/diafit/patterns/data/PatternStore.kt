package uk.scimone.diafit.patterns.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import uk.scimone.diafit.patterns.domain.PatternCard
import uk.scimone.diafit.patterns.domain.PatternState
import uk.scimone.diafit.patterns.domain.StoredPattern

/** Reads and writes [StoredPattern]s through Room, with the card serialised as JSON. */
class PatternStore(private val dao: PatternDao) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeAll(): Flow<List<StoredPattern>> = dao.observeAll().map { rows -> rows.mapNotNull(::decode) }

    fun observe(id: String): Flow<StoredPattern?> = dao.observe(id).map { it?.let(::decode) }

    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    suspend fun getAll(): List<StoredPattern> = dao.getAll().mapNotNull(::decode)

    /** Replaces the stored set with [patterns] (rows no longer in it are deleted). */
    suspend fun replaceAll(patterns: List<StoredPattern>) {
        val keep = patterns.map { it.card.id }.toSet()
        dao.delete(dao.getAll().map { it.id }.filter { it !in keep })
        dao.upsertAll(patterns.map(::encode))
    }

    suspend fun markRead(id: String) = dao.markRead(id)
    suspend fun markAllRead() = dao.markAllRead()
    suspend fun dismiss(id: String) = dao.setState(id, PatternState.DISMISSED.name)

    /** Bringing a dismissed pattern back: ongoing and reviewed, it only counts again if it changes. */
    suspend fun restore(id: String) = dao.setState(id, PatternState.ONGOING.name)

    suspend fun markNotified(ids: List<String>, at: Long) = dao.markNotified(ids, at)

    private fun encode(s: StoredPattern) = PatternEntity(
        id = s.card.id, state = s.state.name, isRead = s.isRead, priority = s.card.priority,
        firstSeenUtc = s.firstSeenUtc, lastSeenUtc = s.lastSeenUtc, missedScans = s.missedScans,
        referenceEffect = s.referenceEffect, notifiedAtUtc = s.notifiedAtUtc,
        payload = json.encodeToString(PatternCard.serializer(), s.card)
    )

    private fun decode(e: PatternEntity): StoredPattern? = runCatching {
        StoredPattern(
            card = json.decodeFromString(PatternCard.serializer(), e.payload),
            state = PatternState.valueOf(e.state), isRead = e.isRead,
            firstSeenUtc = e.firstSeenUtc, lastSeenUtc = e.lastSeenUtc, missedScans = e.missedScans,
            referenceEffect = e.referenceEffect, notifiedAtUtc = e.notifiedAtUtc
        )
    }.getOrNull() // an unreadable row (older card format) is simply skipped; the next scan rewrites it
}
