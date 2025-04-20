package com.example.apptodos.room

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Priority{
    Tinggi,
    Sedang,
    Rendah
}

@Entity
data class Task (
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val priority: Priority,
    val dueDateTimeMillis: Long?
)