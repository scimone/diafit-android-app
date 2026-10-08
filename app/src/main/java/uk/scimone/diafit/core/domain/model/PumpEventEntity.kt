package uk.scimone.diafit.core.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import uk.scimone.diafit.settings.domain.model.DataType

/**
 * A non-insulin, non-carb treatment from AAPS (pod/site change, temp basal, profile switch, note, ...).
 * The whole treatment is kept in [rawJson] so nothing AAPS sends is lost, whatever is parsed out of it.
 */
@Entity(
    indices = [
        Index(value = ["sourceId"], unique = true),
        Index(value = ["timestampUtc"])
    ]
)
data class PumpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val timestampUtc: Long,
    val createdAtUtc: Long,
    /** AAPS/Nightscout `eventType`, e.g. "Site Change", "Temp Basal". */
    val eventType: String,
    val notes: String? = null,
    val durationMinutes: Double? = null,
    /** Temp basal rate in U/h. */
    val rate: Double? = null,
    val sourceId: String,
    val rawJson: String,
    /** Removed by the user: kept (not hard-deleted) so a repeated broadcast of the same treatment is not re-imported. */
    val isDeleted: Boolean = false
) {
    /** Worth a journal entry (a device/therapy milestone), as opposed to routine loop chatter such as temp basals. */
    val isMilestone: Boolean
        get() = MILESTONE.containsMatchIn(eventType)

    private companion object {
        val MILESTONE = Regex("change|start|battery|site|pod|reservoir|cannula|profile switch|temporary target|note|announcement|exercise", RegexOption.IGNORE_CASE)
    }
}

/** Which data type an event belongs to, so each can be switched on / off per connector in Settings. */
fun pumpEventDataType(eventType: String): DataType = when {
    eventType.equals("Profile Switch", ignoreCase = true) || eventType.equals("Temporary Target", ignoreCase = true) -> DataType.PROFILE
    eventType.startsWith("Temp Basal", ignoreCase = true) -> DataType.BASAL
    else -> DataType.DEVICE
}

val PumpEventEntity.dataType: DataType get() = pumpEventDataType(eventType)
