package com.aicardgrader.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Insert
    suspend fun insert(record: CardRecord): Long

    @Query("SELECT * FROM card_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CardRecord>>

    @Query("SELECT * FROM card_records WHERE id = :id")
    fun observeById(id: Long): Flow<CardRecord?>

    @Delete
    suspend fun delete(record: CardRecord)
}
