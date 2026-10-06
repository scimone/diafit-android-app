package uk.scimone.diafit.core.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun stringListToJson(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList() else runCatching { Json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
}
