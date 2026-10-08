package uk.scimone.diafit.core.data.repository

import android.content.Context
import android.util.Log
import uk.scimone.diafit.core.data.local.PumpEventDao
import uk.scimone.diafit.core.domain.model.PumpEventNormalizer

/**
 * One-time clean-up of events stored before their origin was recorded: stamps each with where it came from
 * (imported long after it happened = Nightscout backfill, else the live AAPS broadcast), turns profile Notes into
 * Profile Switches, and hides such a switch if a real one for the same profile is already there.
 */
class PumpEventRepair(private val dao: PumpEventDao, context: Context, private val userId: Int = 1) {
    private val prefs = context.getSharedPreferences("pump_event_repair", Context.MODE_PRIVATE)

    suspend fun runOnce() {
        if (prefs.getBoolean(KEY, false)) return
        try {
            val rows = dao.getUnstamped()
            // Real switches first, so converted notes are compared against them.
            for (row in rows.sortedBy { it.eventType.equals("Note", true) }) {
                val converted = PumpEventNormalizer.profileNoteToSwitch(row)
                val source = if (row.createdAtUtc - row.timestampUtc > IMPORT_LAG_MS) "Nightscout" else "AAPS"
                var event = PumpEventNormalizer.withSource(converted ?: row, source)
                if (converted != null) {
                    val others = dao.getBetween(row.timestampUtc - WINDOW_MS, row.timestampUtc + WINDOW_MS, userId)
                    if (others.any { it.id != row.id && PumpEventNormalizer.isSameEvent(it, event) }) event = event.copy(isDeleted = true)
                }
                dao.update(event)
            }
            prefs.edit().putBoolean(KEY, true).apply()
            Log.i(TAG, "Repaired ${rows.size} pump events")
        } catch (e: Exception) {
            Log.e(TAG, "Pump event repair failed", e)
        }
    }

    private companion object {
        const val TAG = "PumpEventRepair"
        const val KEY = "repair_v1"
        const val IMPORT_LAG_MS = 30 * 60_000L
        const val WINDOW_MS = 3 * 60_000L
    }
}
