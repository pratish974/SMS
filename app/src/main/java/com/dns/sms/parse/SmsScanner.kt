package com.dns.sms.parse

import android.content.ContentResolver
import android.provider.Telephony
import androidx.room.withTransaction
import com.dns.sms.data.AppDatabase
import com.dns.sms.data.ScanState
import com.dns.sms.data.ScanStateDao
import com.dns.sms.send.StatusMapper
import com.dns.sms.sms.DefaultSmsGate
import javax.inject.Inject
import javax.inject.Singleton

/** One SMS handed to the processors; [inbox] is false for sent and other outgoing messages. */
data class ScannedSms(val id: Long, val threadId: Long, val address: String, val body: String, val date: Long, val inbox: Boolean = true)

/** A parser stage (finance, reminders, orders). Must be idempotent: the same SMS may be seen twice. */
interface MessageProcessor {
    /** Bump to make the scanner re-read the whole inbox for this stage. */
    val version: Int
    /** False for processors that also want sent messages (the search index); parsers stay inbox-only. */
    val inboxOnly: Boolean get() = true
    suspend fun process(sms: ScannedSms)

    /** Whole batch inside one transaction; processors may override to write in bulk. */
    suspend fun processBatch(batch: List<ScannedSms>) {
        for (sms in batch) runCatching { process(sms) }
    }

    /** Called once before a scan starts (drop caches). */
    suspend fun onScanStart() {}

    /** Called after each batch (flush deferred work). */
    suspend fun onBatchDone() {}
    suspend fun clear()
}

/**
 * Feeds new SMS to every [MessageProcessor], resuming from the last id it saw.
 * Each processor is wrapped: one bad message or parser can never stop the scan.
 */
@Singleton
class SmsScanner @Inject constructor(
    private val resolver: ContentResolver,
    private val gate: DefaultSmsGate,
    private val db: AppDatabase,
    private val state: ScanStateDao,
    private val processors: Set<@JvmSuppressWildcards MessageProcessor>,
) {
    suspend fun scan(full: Boolean) {
        if (!gate.isDefaultNow()) return
        val version = processors.sumOf { it.version * 31 }.toLong() + processors.size
        if (full || state.get(KEY_VERSION) != version) {
            processors.forEach { runCatching { it.clear() } }
            state.put(ScanState(KEY_LAST_ID, 0))
            state.put(ScanState(KEY_VERSION, version))
            // Search falls back to LIKE until this rebuild of the index finishes.
            state.put(ScanState(KEY_INDEX_READY, 0))
        }
        processors.forEach { runCatching { it.onScanStart() } }
        var lastId = state.get(KEY_LAST_ID) ?: 0
        val started = com.dns.sms.util.Perf.now()
        var scanned = 0
        while (gate.isDefaultNow()) {
            val batch = readBatch(lastId)
            if (batch.isEmpty()) break
            lastId = batch.last().id
            val inbox = batch.filter { it.inbox }
            // One transaction per batch: a commit (disk sync) per SMS made the first scan take minutes.
            db.withTransaction {
                processors.forEach { p ->
                    runCatching {
                        p.processBatch(if (p.inboxOnly) inbox else batch)
                        p.onBatchDone()
                    }
                }
                state.put(ScanState(KEY_LAST_ID, lastId))
            }
            scanned += batch.size
        }
        com.dns.sms.util.Perf.log("scan(full=$full): $scanned SMS in ${com.dns.sms.util.Perf.now() - started} ms")
        state.put(ScanState(KEY_DONE_AT, System.currentTimeMillis()))
        state.put(ScanState(KEY_INDEX_READY, 1))
    }

    suspend fun hasCompletedOnce(): Boolean = (state.get(KEY_DONE_AT) ?: 0) > 0

    private fun readBatch(afterId: Long): List<ScannedSms> = runCatching {
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf("_id", "thread_id", "address", "body", "date", "type"),
            "_id > ?", arrayOf(afterId.toString()), "_id ASC LIMIT $BATCH",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) add(ScannedSms(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getLong(4), c.getInt(5) == StatusMapper.SMS_INBOX))
            }
        }
    }.getOrNull().orEmpty()

    companion object {
        private const val BATCH = 2000
        private const val KEY_LAST_ID = "scan_last_sms_id"
        private const val KEY_VERSION = "scan_version"
        const val KEY_DONE_AT = "scan_done_at"

        /** 1 once the search index covers every SMS; 0 while a full rebuild runs. */
        const val KEY_INDEX_READY = "search_index_ready"
    }
}
