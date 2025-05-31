package uk.scimone.diafit.core.data.networking.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uk.scimone.diafit.core.domain.model.CgmEntity

@Serializable
data class NightscoutEntryDto(
    @SerialName("_id") val id: String,
    val device: String,
    val date: Long,
    val sgv: Int,
    val delta: Float? = null,
    val direction: String? = null,
)

// Mapper extension function
fun NightscoutEntryDto.toCgmEntity(userId: Int = 1): CgmEntity {
    return CgmEntity(
        userId = userId,
        timestamp = this.date,
        valueMgdl = this.sgv,
        fiveMinuteRateMgdl = this.delta ?: 0f,
        direction = this.direction ?: "Flat",
        device = this.device,
        source = "Nightscout",
        sourceId = this.id
    )
}
