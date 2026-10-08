package uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource

import org.json.JSONObject
import uk.scimone.diafit.core.domain.model.PumpEventEntity

/**
 * Reads the temp basal the AAPS loop actually enacted from the `info.nightscout.androidaps.status`
 * broadcast: `enacted` (OpenAPS JSON with `rate` U/h and `duration` min) and `enactedTimeStamp`.
 * `suggested` is only the algorithm's proposal and is ignored. The status broadcast arrives several times
 * per cycle and repeats the last `enacted`, so [PumpEventEntity.sourceId] is keyed on the enact time.
 */
object AapsTempBasalParser {
    const val EVENT_TYPE = "Temp Basal"

    data class TempBasal(val enactedAt: Long, val rate: Double, val durationMinutes: Double, val baseBasal: Double?)

    fun parse(enactedJson: String?, enactedTimeStamp: Long, baseBasal: Double?): TempBasal? {
        if (enactedJson.isNullOrEmpty() || enactedTimeStamp <= 0) return null
        val enacted = runCatching { JSONObject(enactedJson) }.getOrNull() ?: return null
        val rate = enacted.optDouble("rate", Double.NaN)
        val duration = enacted.optDouble("duration", Double.NaN)
        if (rate.isNaN() || duration.isNaN()) return null
        return TempBasal(enactedTimeStamp, rate, duration, baseBasal?.takeIf { !it.isNaN() })
    }

    fun toEvent(t: TempBasal, userId: Int = 1) = PumpEventEntity(
        userId = userId,
        timestampUtc = t.enactedAt,
        createdAtUtc = System.currentTimeMillis(),
        eventType = EVENT_TYPE,
        durationMinutes = t.durationMinutes,
        rate = t.rate,
        sourceId = "temp-basal-${t.enactedAt}",
        rawJson = JSONObject()
            .put("rate", t.rate).put("duration", t.durationMinutes)
            .apply { t.baseBasal?.let { put("baseBasal", it) } }
            .toString()
    )
}
