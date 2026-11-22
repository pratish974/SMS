package com.dns.sms.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * On-device full-text index of SMS bodies (rowid = the provider's SMS _id), kept in the encrypted
 * database so search doesn't scan the whole Telephony provider. unicode61 folds case and works for
 * Hindi and other scripts; thread and date are stored but not indexed.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["threadId", "date"])
@Entity(tableName = "message_fts")
data class MessageFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val body: String,
    val address: String,
    val threadId: Long,
    val date: Long,
)

@Dao
interface MessageFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<MessageFts>)

    /**
     * SMS ids matching [match] (see SearchQuery.ftsMatch), highest id first. Ordering by the unindexed date
     * column forced a lookup of every hit (200 ms for common words); ids grow with arrival, and the caller
     * re-sorts the page by date.
     */
    @Query("SELECT rowid FROM message_fts WHERE message_fts MATCH :match ORDER BY rowid DESC LIMIT :limit")
    suspend fun search(match: String, limit: Int): List<Long>

    @Query("DELETE FROM message_fts WHERE rowid IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM message_fts WHERE threadId IN (:threadIds)")
    suspend fun deleteThreads(threadIds: List<Long>)

    @Query("DELETE FROM message_fts")
    suspend fun clear()
}
