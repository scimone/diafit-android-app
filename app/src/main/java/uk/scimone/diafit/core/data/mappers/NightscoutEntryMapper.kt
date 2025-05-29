package uk.scimone.diafit.core.data.mappers

import uk.scimone.diafit.core.data.networking.dto.NightscoutEntryDto
import uk.scimone.diafit.core.domain.model.CgmEntity

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