package com.example.appcloner

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CloneHistoryDao {
    @Query("SELECT * FROM clone_history ORDER BY cloneDate DESC")
    fun getAllHistory(): Flow<List<CloneHistory>>

    @Insert
    suspend fun insert(history: CloneHistory)

    @Query("DELETE FROM clone_history")
    suspend fun clearAll()
}