package com.example.apptodos.room

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromPriority(priority: Priority?): String? {
        return when(priority) {
            Priority.Tinggi -> "Tinggi"
            Priority.Sedang -> "Sedang"
            Priority.Rendah -> "Rendah"
            null -> null
        }
    }
    @TypeConverter
    fun toPriority(priorityName: String?): Priority? {
        return when(priorityName) {
            "Tinggi" -> Priority.Tinggi
            "Sedang" -> Priority.Sedang
            "Rendah" -> Priority.Rendah
            else -> null
        }

    }
}