package com.dns.sms.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Latest known balance per account, parsed from SMS (Requirement.md section 4, Finance).
 * Amounts are decimal strings (exact; no floating point). Stored only in the encrypted database.
 */
@Entity(tableName = "account", primaryKeys = ["bank", "mask"])
data class Account(
    val bank: String,
    /** Last digits as printed, e.g. "XXX439". */
    val mask: String,
    val balance: String?,
    val balanceAt: Long,
    val sourceMessageId: Long,
    val parserVersion: Int,
)

@Entity(tableName = "finance_txn", indices = [Index(value = ["bank", "mask"])])
data class FinanceTxn(
    @PrimaryKey val messageId: Long,
    val bank: String,
    val mask: String,
    /** "DEBIT" or "CREDIT". */
    val type: String,
    val amount: String,
    val at: Long,
)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM account ORDER BY balanceAt DESC")
    fun observeAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM account WHERE bank = :bank AND mask = :mask")
    suspend fun account(bank: String, mask: String): Account?

    @Upsert
    suspend fun upsertAccount(a: Account)

    @Upsert
    suspend fun upsertTxn(t: FinanceTxn)

    /** Transactions of one account reported under several masks (see AccountGroups). */
    @Query("SELECT * FROM finance_txn WHERE bank = :bank AND mask IN (:masks) ORDER BY at DESC LIMIT 200")
    fun observeTxnsIn(bank: String, masks: List<String>): Flow<List<FinanceTxn>>

    @Query("SELECT * FROM account WHERE bank = :bank AND mask IN (:masks) ORDER BY balanceAt DESC LIMIT 1")
    suspend fun latestAccountIn(bank: String, masks: List<String>): Account?

    @Query("SELECT * FROM account WHERE bank = :bank")
    suspend fun accountsOf(bank: String): List<Account>

    @Query("SELECT DISTINCT mask FROM finance_txn WHERE bank = :bank")
    suspend fun txnMasksOf(bank: String): List<String>

    @Query("DELETE FROM account")
    suspend fun clearAccounts()

    @Query("DELETE FROM finance_txn")
    suspend fun clearTxns()
}

/** Small key/value store for background scan progress. */
@Entity(tableName = "scan_state")
data class ScanState(@PrimaryKey val key: String, val value: Long)

@Dao
interface ScanStateDao {
    @Query("SELECT value FROM scan_state WHERE `key` = :key")
    suspend fun get(key: String): Long?

    @Upsert
    suspend fun put(s: ScanState)

    @Query("DELETE FROM scan_state")
    suspend fun clear()
}
