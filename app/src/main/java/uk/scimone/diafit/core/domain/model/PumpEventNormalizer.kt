package uk.scimone.diafit.core.domain.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Cleans up pump events as they come in from AAPS / Nightscout: marks where they came from, reads profile notes as switches, spots duplicates. */
object PumpEventNormalizer {
    /** Key added to [PumpEventEntity.rawJson]: which connector delivered the event ("AAPS", "Nightscout"). */
    const val SOURCE_KEY = "_diafitSource"
    private const val PROFILE_SAME_MS = 2 * 60_000L
    private const val EVENT_SAME_MS = 5_000L
    private val PERCENT = Regex("""\((\d+)\s*%\)""")

    fun sourceOf(event: PumpEventEntity): String? =
        runCatching { JSONObject(event.rawJson).optString(SOURCE_KEY, "").ifEmpty { null } }.getOrNull()

    fun withSource(event: PumpEventEntity, source: String): PumpEventEntity {
        val raw = runCatching { JSONObject(event.rawJson).put(SOURCE_KEY, source).toString() }.getOrNull() ?: return event
        return event.copy(rawJson = raw)
    }

    /**
     * AAPS logs a (temporary) profile change a second time as a Note named like the profile ("Anna", "Anna (90%)")
     * that carries the profile. Returns that note as the Profile Switch it stands for, or null for any other event.
     * The note's profile has the percentage already applied, so it is scaled back to the saved profile.
     */
    fun profileNoteToSwitch(event: PumpEventEntity): PumpEventEntity? {
        if (!event.eventType.equals("Note", ignoreCase = true)) return null
        return try {
            val o = JSONObject(event.rawJson)
            val profileJson = o.optString("profileJson", "")
            if (profileJson.isEmpty() && !o.has("originalProfileName")) return null
            val label = (event.notes ?: o.optString("notes", "")).ifEmpty { o.optString("originalCustomizedName", "") }
            if (label.isEmpty()) return null
            val pct = PERCENT.find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 100
            o.put("eventType", "Profile Switch")
            o.put("profile", label)
            o.put("originalProfileName", o.optString("originalProfileName", "").ifEmpty { label.substringBefore(" (") })
            o.put("percentage", pct)
            o.put("duration", 0)
            o.put("timeshift", o.optInt("originalTimeshift", 0))
            if (profileJson.isNotEmpty()) o.put("profileJson", unscale(profileJson, pct))
            event.copy(eventType = "Profile Switch", notes = null, durationMinutes = 0.0, rawJson = o.toString())
        } catch (e: Exception) {
            null
        }
    }

    private fun unscale(profileJson: String, pct: Int): String {
        if (pct == 100 || pct <= 0) return profileJson
        val f = pct / 100.0
        val o = JSONObject(profileJson)
        fun scale(key: String, factor: Double) {
            val a: JSONArray = o.optJSONArray(key) ?: return
            for (i in 0 until a.length()) {
                val s = a.optJSONObject(i) ?: continue
                if (s.has("value")) s.put("value", Math.round(s.optDouble("value") * factor * 10_000) / 10_000.0)
            }
        }
        scale("basal", 1 / f); scale("sens", f); scale("carbratio", f)
        return o.toString()
    }

    /** The same event stored twice: same type within 5 s, or the same profile switch within 2 min (AAPS sends a note a few seconds later). */
    fun isSameEvent(a: PumpEventEntity, b: PumpEventEntity): Boolean {
        if (!a.eventType.equals(b.eventType, ignoreCase = true)) return false
        val gap = abs(a.timestampUtc - b.timestampUtc)
        if (a.eventType.equals("Profile Switch", ignoreCase = true)) {
            val la = a.toProfileSwitch()?.label; val lb = b.toProfileSwitch()?.label
            return gap < PROFILE_SAME_MS && la == lb
        }
        return gap < EVENT_SAME_MS
    }
}
