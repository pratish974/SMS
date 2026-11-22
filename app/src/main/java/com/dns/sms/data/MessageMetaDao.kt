package com.dns.sms.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageMetaDao {
    @Upsert
    suspend fun upsert(meta: MessageMeta)

    @Upsert
    suspend fun upsertAll(metas: List<MessageMeta>)

    @Query("SELECT * FROM message_meta WHERE kind = :kind AND messageId = :messageId")
    suspend fun get(kind: String, messageId: Long): MessageMeta?

    /** Messages the user moved individually. */
    @Query("SELECT * FROM message_meta WHERE category IS NOT NULL")
    fun observeMoved(): Flow<List<MessageMeta>>

    @Query("SELECT * FROM message_meta WHERE threadId = :threadId")
    fun observeThread(threadId: Long): Flow<List<MessageMeta>>

    @Query("SELECT * FROM message_meta WHERE starred = 1")
    fun observeStarred(): Flow<List<MessageMeta>>

    @Query("SELECT * FROM message_meta WHERE starred = 1")
    suspend fun starred(): List<MessageMeta>

    /** Newest starred first (SMS ids grow over time), a page at a time. */
    @Query("SELECT * FROM message_meta WHERE starred = 1 ORDER BY messageId DESC LIMIT :limit")
    fun observeStarredPage(limit: Int): Flow<List<MessageMeta>>

    @Query("SELECT COUNT(*) FROM message_meta WHERE starred = 1")
    fun observeStarredCount(): Flow<Int>

    @Query("DELETE FROM message_meta WHERE kind = :kind AND messageId = :messageId")
    suspend fun delete(kind: String, messageId: Long)

    @Query("UPDATE message_meta SET category = NULL WHERE kind = :kind AND messageId = :messageId")
    suspend fun clearCategory(kind: String, messageId: Long)

    @Query("SELECT COUNT(*) FROM message_meta WHERE starred = 1")
    suspend fun starredCount(): Int

    @Query("DELETE FROM message_meta WHERE threadId IN (:threadIds)")
    suspend fun deleteThreads(threadIds: List<Long>)
}
