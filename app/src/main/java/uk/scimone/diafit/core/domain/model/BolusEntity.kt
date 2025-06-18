package uk.scimone.diafit.core.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [Index(value = ["timestamp"], unique = true)]
)
data class BolusEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val timestampUtc: Long,
    val createdAtUtc: Long,
    val updatedAtUtc: Long,
    val value: Float,
    val eventType: String,
    val isSmb: Boolean,
    val pumpType: String,
    val pumpSerial: String,
    val sourceId: String? = null
)
