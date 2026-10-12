package uk.scimone.diafit.core.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One request to an LLM endpoint, kept so the user can see what was sent, what came back and what it cost in tokens. */
@Entity(indices = [Index("timestampUtc")])
data class LlmRequestLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestampUtc: Long,
    /** What the request was for, e.g. "Meal estimate". */
    val purpose: String,
    val connectionName: String,
    val model: String,
    val durationMs: Long,
    /** The text sent (images are not stored, only their count). */
    val inputText: String,
    val imageCount: Int,
    val outputText: String?,
    /** Null when the endpoint reported no usage. */
    val inputTokens: Int?,
    val outputTokens: Int?,
    val success: Boolean,
    val error: String?
)

data class LlmLogTotals(val requests: Int, val inputTokens: Long, val outputTokens: Long)
