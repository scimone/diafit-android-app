package uk.scimone.diafit.core.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    indices = [Index(value = ["timestamp"], unique = true)]
)
data class CgmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val timestamp: Long,
    val valueMgdl: Int,
    val device: String = "Unknown",
    val source: String = "Unknown",
)
