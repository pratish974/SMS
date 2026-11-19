package com.dns.sms.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * FEAT-1: a message written now and sent automatically at [sendAt]. Text and recipients live only in
 * this encrypted database; the alarm only knows "something is due".
 */
@Entity(tableName = "scheduled_message", indices = [Index("threadId"), Index(value = ["state", "sendAt"])])
data class ScheduledMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** null when written in New message to someone with no conversation yet. */
    val threadId: Long?,
    /** Newline-separated addresses. */
    val recipients: String,
    val body: String,
    val sendAt: Long,
    /** PENDING, SENT or FAILED. */
    val state: String = PENDING,
    val createdAt: Long,
    val sentAt: Long? = null,
    /** Why it failed ("not-default", "send"), for the UI. */
    val failure: String? = null,
) {
    val recipientList: List<String> get() = recipients.split('\n').filter { it.isNotBlank() }

    companion object {
        const val PENDING = "PENDING"
        const val SENT = "SENT"
        const val FAILED = "FAILED"
    }
}

@Dao
interface ScheduledDao {
    @Insert
    suspend fun insert(m: ScheduledMessage): Long

    @Update
    suspend fun update(m: ScheduledMessage)

    @Query("SELECT * FROM scheduled_message WHERE id = :id")
    suspend fun get(id: Long): ScheduledMessage?

    @Query("SELECT * FROM scheduled_message WHERE state = 'PENDING' ORDER BY sendAt ASC")
    suspend fun pending(): List<ScheduledMessage>

    @Query("SELECT * FROM scheduled_message WHERE state != 'SENT' ORDER BY sendAt ASC")
    fun observeOpen(): Flow<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_message WHERE threadId = :threadId AND state != 'SENT' ORDER BY sendAt ASC")
    fun observeForThread(threadId: Long): Flow<List<ScheduledMessage>>

    @Query("DELETE FROM scheduled_message WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scheduled_message WHERE state = 'SENT' AND sentAt < :before")
    suspend fun pruneSent(before: Long)
}
