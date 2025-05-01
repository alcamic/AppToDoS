package com.example.apptodos.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy // Import this
import androidx.room.Query
import androidx.room.Update

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTask(task: Task) // NO BODY HERE

    @Update
    suspend fun updateTask(task: Task) // NO BODY HERE

    @Delete
    suspend fun deleteTask(task: Task) // NO BODY HERE

    @Query("SELECT * FROM Task")
    suspend fun getAllTasks(): List<Task>

    @Query("SELECT * FROM task WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): Task?

    @Query("SELECT * FROM Task WHERE isCompleted = 1")
    suspend fun getCompletedTasks(): List<Task>

    @Query("""
        SELECT * FROM task
        WHERE isCompleted = 0
        ORDER BY CASE priority
            WHEN 'Tinggi' THEN 1
            WHEN 'Sedang' THEN 2
            WHEN 'Rendah' THEN 3
            ELSE 4
        END ASC
    """)
    suspend fun getPrioritySortedTasks(): List<Task>

    @Query("SELECT * FROM Task WHERE isCompleted = 0")
    suspend fun getUncompletedTasks(): List<Task>

}