package com.sldevelopers.alsalarm.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringSet(value: Set<String>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toStringSet(value: String): Set<String> {
        return if (value.isEmpty()) emptySet() else value.split(",").toSet()
    }
}
