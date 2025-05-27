package uk.scimone.diafit.core.data.networking.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NightscoutEntryDto(
    @SerialName("_id") val id: String,
    val device: String,
    val date: Long,
    val sgv: Int,
    val delta: Float? = null,
    val direction: String? = null,
)
