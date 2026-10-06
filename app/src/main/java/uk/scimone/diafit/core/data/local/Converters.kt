package uk.scimone.diafit.core.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import uk.scimone.diafit.core.domain.model.MealComponent

class Converters {
    @TypeConverter
    fun stringListToJson(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList() else runCatching { Json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun componentsToJson(value: List<MealComponent>): String = Json.encodeToString(value)

    @TypeConverter
    fun jsonToComponents(value: String?): List<MealComponent> =
        if (value.isNullOrBlank()) emptyList() else runCatching { Json.decodeFromString<List<MealComponent>>(value) }.getOrDefault(emptyList())
}
