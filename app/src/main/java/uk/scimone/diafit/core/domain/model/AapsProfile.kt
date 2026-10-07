package uk.scimone.diafit.core.domain.model

import org.json.JSONArray
import org.json.JSONObject

/** One step of a daily schedule: [value] applies from [startSeconds] after midnight until the next step. */
data class ProfileStep(val startSeconds: Int, val value: Double)

/**
 * The insulin profile AAPS sent with a Profile Switch (`profileJson`): the daily schedules for basal,
 * insulin sensitivity (ISF), carb ratio (IC) and glucose targets. Read-only: AAPS is the source of truth.
 */
data class AapsProfile(
    val units: String,
    val diaHours: Double?,
    val basal: List<ProfileStep>,
    val isf: List<ProfileStep>,
    val carbRatio: List<ProfileStep>,
    val targetLow: List<ProfileStep>,
    val targetHigh: List<ProfileStep>
) {
    val isMmol: Boolean get() = units.contains("mmol", ignoreCase = true)

    /** Basal insulin per day, in units. */
    val totalDailyBasal: Double
        get() = basal.sortedBy { it.startSeconds }.let { steps ->
            steps.mapIndexed { i, s ->
                val end = steps.getOrNull(i + 1)?.startSeconds ?: SECONDS_PER_DAY
                s.value * (end - s.startSeconds) / 3600.0
            }.sum()
        }

    /**
     * The profile as it runs at [percentage] percent: basal scales with it, ISF and carb ratio inversely
     * (AAPS' rule: a higher percentage means more insulin everywhere). Targets are unchanged.
     */
    fun atPercentage(percentage: Int): AapsProfile {
        if (percentage == 100 || percentage <= 0) return this
        val f = percentage / 100.0
        return copy(
            basal = basal.map { it.copy(value = it.value * f) },
            isf = isf.map { it.copy(value = it.value / f) },
            carbRatio = carbRatio.map { it.copy(value = it.value / f) }
        )
    }

    companion object {
        const val SECONDS_PER_DAY = 24 * 3600

        fun fromJson(json: String): AapsProfile? = try {
            val o = JSONObject(json)
            AapsProfile(
                units = o.optString("units", "mg/dl"),
                diaHours = o.optDouble("dia", Double.NaN).takeIf { !it.isNaN() },
                basal = o.steps("basal"),
                isf = o.steps("sens"),
                carbRatio = o.steps("carbratio"),
                targetLow = o.steps("target_low"),
                targetHigh = o.steps("target_high")
            ).takeIf { it.basal.isNotEmpty() || it.isf.isNotEmpty() || it.carbRatio.isNotEmpty() }
        } catch (e: Exception) {
            null
        }

        private fun JSONObject.steps(key: String): List<ProfileStep> {
            val array: JSONArray = optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val s = array.optJSONObject(i) ?: return@mapNotNull null
                val seconds = if (s.has("timeAsSeconds")) s.optInt("timeAsSeconds") else parseClock(s.optString("time"))
                val value = s.optDouble("value", Double.NaN)
                if (seconds == null || value.isNaN()) null else ProfileStep(seconds, value)
            }.sortedBy { it.startSeconds }
        }

        private fun parseClock(time: String): Int? {
            val parts = time.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return h * 3600 + m * 60
        }
    }
}

/** The value in force at [secondOfDay]: the last step that started at or before it (wrapping to the last step of the day). */
fun List<ProfileStep>.valueAt(secondOfDay: Int): Double? =
    lastOrNull { it.startSeconds <= secondOfDay }?.value ?: lastOrNull()?.value

/** A Profile Switch event from AAPS: which profile, at what percentage, for how long. */
data class ProfileSwitch(
    val eventId: Int,
    val startUtc: Long,
    /** Display name from AAPS, e.g. "Anna (90%)". */
    val label: String,
    /** The profile's own name, e.g. "Anna". */
    val baseName: String,
    val percentage: Int,
    /** 0 = until the next switch. */
    val durationMinutes: Int,
    val profile: AapsProfile?
) {
    val endUtc: Long? get() = if (durationMinutes > 0) startUtc + durationMinutes * 60_000L else null
    fun isActive(nowUtc: Long): Boolean = endUtc?.let { nowUtc < it } ?: true
}

/** Null unless this event is a Profile Switch. */
fun PumpEventEntity.toProfileSwitch(): ProfileSwitch? {
    if (!eventType.equals("Profile Switch", ignoreCase = true)) return null
    return try {
        val o = JSONObject(rawJson)
        val label = o.optString("profile", "").ifEmpty { "Profile" }
        ProfileSwitch(
            eventId = id,
            startUtc = timestampUtc,
            label = label,
            baseName = o.optString("originalProfileName", "").ifEmpty { label.substringBefore(" (") },
            percentage = o.optInt("percentage", 100),
            durationMinutes = o.optInt("duration", 0),
            profile = o.optString("profileJson", "").takeIf { it.isNotEmpty() }?.let(AapsProfile::fromJson)
        )
    } catch (e: Exception) {
        null
    }
}

/** "10 min", "1 h 30 min", "2 h". */
fun formatDurationMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** One-line detail for a journal card: the profile and duration of a switch, else the event's notes. */
fun PumpEventEntity.summary(): String? =
    toProfileSwitch()?.let { sw ->
        buildString {
            append(sw.label)
            if (sw.durationMinutes > 0) append(" · ").append(formatDurationMinutes(sw.durationMinutes))
        }
    } ?: notes
