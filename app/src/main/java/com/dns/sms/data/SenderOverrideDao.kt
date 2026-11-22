package com.dns.sms.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SenderOverrideDao {
    @Upsert
    suspend fun upsert(override: SenderOverride)

    @Query("SELECT * FROM sender_override WHERE address = :key")
    suspend fun get(key: String): SenderOverride?

    @Query("SELECT * FROM sender_override")
    fun observeAll(): Flow<List<SenderOverride>>

    @Query("SELECT * FROM sender_override")
    suspend fun all(): List<SenderOverride>

    @Query("DELETE FROM sender_override WHERE address = :key")
    suspend fun delete(key: String)
}
