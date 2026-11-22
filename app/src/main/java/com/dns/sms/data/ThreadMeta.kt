package com.dns.sms.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** App-side flags for a conversation. Ids and booleans only. */
@Entity(tableName = "thread_meta")
data class ThreadMeta(
    @PrimaryKey val threadId: Long,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    /** No notifications for this conversation (messages still arrive and count as unread). */
    @ColumnInfo(defaultValue = "0") val muted: Boolean = false,
    /** Unsent text typed in this conversation. Stored only in the encrypted database. */
    val draft: String? = null,
    /** SIM (subscription id) the user picked for this conversation; null = the one it last used. */
    val subId: Int? = null,
)

@Dao
interface ThreadMetaDao {
    @Upsert
    suspend fun upsertAll(rows: List<ThreadMeta>)

    @Query("SELECT * FROM thread_meta WHERE threadId IN (:ids)")
    suspend fun get(ids: List<Long>): List<ThreadMeta>

    @Query("SELECT * FROM thread_meta")
    fun observeAll(): Flow<List<ThreadMeta>>

    @Query("DELETE FROM thread_meta WHERE threadId IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT * FROM thread_meta WHERE threadId = :id")
    suspend fun one(id: Long): ThreadMeta?

    @Query("SELECT * FROM thread_meta WHERE threadId = :id")
    fun observeOne(id: Long): Flow<ThreadMeta?>

    @Query("INSERT OR IGNORE INTO thread_meta (threadId, pinned, archived, muted) VALUES (:id, 0, 0, 0)")
    suspend fun ensure(id: Long)

    @Query("UPDATE thread_meta SET draft = :draft WHERE threadId = :id")
    suspend fun updateDraft(id: Long, draft: String?)

    @Query("UPDATE thread_meta SET muted = :muted WHERE threadId = :id")
    suspend fun updateMuted(id: Long, muted: Boolean)

    @Query("UPDATE thread_meta SET subId = :subId WHERE threadId = :id")
    suspend fun updateSubId(id: Long, subId: Int?)

    /** Single-field updates that keep the other flags (pinned, archived...) as they are. */
    @androidx.room.Transaction
    suspend fun setDraft(id: Long, draft: String?) { ensure(id); updateDraft(id, draft?.takeIf { it.isNotBlank() }) }

    @androidx.room.Transaction
    suspend fun setMuted(id: Long, muted: Boolean) { ensure(id); updateMuted(id, muted) }

    @androidx.room.Transaction
    suspend fun setSubId(id: Long, subId: Int?) { ensure(id); updateSubId(id, subId) }
}
