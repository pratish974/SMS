package com.dns.sms.sms

import androidx.core.net.toUri
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.dns.sms.data.DeliveryReportDao
import com.dns.sms.data.MessageFtsDao
import com.dns.sms.data.ScanStateDao
import com.dns.sms.parse.SmsScanner
import com.dns.sms.send.MessageStatus
import com.dns.sms.send.StatusMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Reads conversations from the Telephony provider (the source of truth, Requirement.md section 6).
 * Every call is gated on being the default SMS app and returns empty otherwise.
 */
@Singleton
class ConversationRepository @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    private val resolver: ContentResolver,
    private val gate: DefaultSmsGate,
    private val contacts: ContactNames,
    private val deliveryReports: DeliveryReportDao,
    private val fts: MessageFtsDao,
    private val scanState: ScanStateDao,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeThreads(): Flow<List<ConversationThread>> = gate.isDefault.flatMapLatest { ok ->
        if (!ok) flowOf(emptyList()) else resolver.messageChanges().map { loadThreads() }
    }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMessages(threadId: Long, limit: Int): Flow<List<Message>> = gate.isDefault.flatMapLatest { ok ->
        if (!ok) flowOf(emptyList()) else resolver.messageChanges().map { loadMessages(threadId, limit) }
    }.flowOn(Dispatchers.IO)

    suspend fun thread(threadId: Long): ConversationThread? = withContext(Dispatchers.IO) {
        loadThreads(threadId).firstOrNull()
    }

    fun loadThreads(onlyId: Long? = null): List<ConversationThread> =
        if (onlyId != null) loadThreadsInner(onlyId)
        else com.dns.sms.util.Perf.time("loadThreads") { loadThreadsInner(null) }

    /**
     * [onlyId] (notifications) resolves just that thread: unread is counted for it alone and only its
     * recipients' canonical addresses are read, instead of every address and every unread SMS.
     */
    private fun loadThreadsInner(onlyId: Long?): List<ConversationThread> = safe(emptyList()) {
        val uri = Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build()
        val sel = if (onlyId != null) "_id = ?" else "message_count > 0"
        val args = onlyId?.let { arrayOf(it.toString()) }
        val rows = resolver.query(
            uri,
            arrayOf("_id", "date", "recipient_ids", "snippet"),
            sel, args, "date DESC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) add(ThreadRow(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty()))
            }
        }.orEmpty()
        if (rows.isEmpty()) return@safe emptyList()
        val canonical = canonicalAddresses(if (onlyId == null) null else rows.flatMap { it.recipientIds() }.toSet())
        val unread = com.dns.sms.util.Perf.time("unreadCounts") { unreadCounts(onlyId) }
        rows.map { r ->
            val addresses = r.recipientIds().mapNotNull(canonical::get)
            ConversationThread(
                id = r.id,
                addresses = addresses,
                title = addresses.joinToString(", ") { contacts.displayName(it) }.ifEmpty { "Unknown" },
                snippet = r.snippet,
                dateMillis = r.date,
                unread = unread[r.id] ?: 0,
                photo = addresses.singleOrNull()?.let { contacts.photoFor(it)?.toString() },
            )
        }
    }

    private class ThreadRow(val id: Long, val date: Long, val recipients: String, val snippet: String) {
        fun recipientIds(): List<Long> = recipients.split(' ').mapNotNull { it.toLongOrNull() }
    }

    /** All canonical addresses, or only those with [ids] (one thread's recipients). */
    private fun canonicalAddresses(ids: Set<Long>?): Map<Long, String> = buildMap {
        val sel = ids?.let { "_id IN (${it.joinToString(",")})" }
        resolver.query("content://mms-sms/canonical-addresses".toUri(), arrayOf("_id", "address"), sel, null, null)
            ?.use { c -> while (c.moveToNext()) put(c.getLong(0), c.getString(1).orEmpty()) }
    }

    private fun unreadCounts(onlyThread: Long?): Map<Long, Int> {
        val counts = HashMap<Long, Int>()
        val thread = onlyThread?.let { " AND thread_id = $it" }.orEmpty()
        resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("thread_id"), "read = 0 AND type = ${StatusMapper.SMS_INBOX}$thread", null, null)?.use { c ->
            while (c.moveToNext()) counts.merge(c.getLong(0), 1, Int::plus)
        }
        return counts
    }

    suspend fun loadMessages(threadId: Long, limit: Int): List<Message> = com.dns.sms.util.Perf.time("loadMessages(limit=$limit)") { loadMessagesInner(threadId, limit) }

    private suspend fun loadMessagesInner(threadId: Long, limit: Int): List<Message> = safe(emptyList()) {
        val args = arrayOf(threadId.toString())
        val sms = loadSms("thread_id = ?", args, "date DESC LIMIT $limit")
        val delivered = if (sms.isEmpty()) emptyMap() else
            deliveryReports.forMessages("sms", sms.filter { it.outgoing }.map { it.id })
                .associate { it.messageId to it.deliveredAt }
        val withDelivery = sms.map { m ->
            delivered[m.id]?.let { m.copy(deliveredAt = it, status = MessageStatus.DELIVERED) } ?: m
        }
        withDelivery.sortedBy { it.dateMillis }.takeLast(limit)
    }

    /** Specific messages (e.g. starred), newest first. */
    suspend fun loadByIds(smsIds: Collection<Long>): List<Message> = withContext(Dispatchers.IO) {
        safe(emptyList()) {
            if (smsIds.isEmpty()) emptyList() else loadSms("_id IN (${smsIds.joinToString(",")})", null, "date DESC")
        }
    }

    /**
     * Search over SMS bodies, newest first, capped. Uses the on-device FTS index once the first full
     * scan has built it (a few ms); until then, or if the query can't be expressed as FTS, falls back
     * to a LIKE scan of the provider (hundreds of ms on huge inboxes).
     */
    suspend fun search(query: String, limit: Int = 200): List<Message> = withContext(Dispatchers.IO) {
        safe(emptyList()) {
            com.dns.sms.util.Perf.time("search") {
                searchIndexed(query, limit) ?: searchLike(query, limit)
            }
        }
    }

    private fun searchLike(query: String, limit: Int): List<Message> {
        val escape = " ESCAPE '${SearchQuery.ESCAPE}'"
        return loadSms("body LIKE ?$escape", arrayOf(SearchQuery.likePattern(query)), "date DESC LIMIT $limit")
    }

    /** Null when the index can't answer (not built yet, unusable query, or an FTS error). */
    private suspend fun searchIndexed(query: String, limit: Int): List<Message>? {
        if ((scanState.get(SmsScanner.KEY_INDEX_READY) ?: 0L) == 0L) return null
        val match = SearchQuery.ftsMatch(query) ?: return null
        val ids = try { com.dns.sms.util.Perf.time("search.fts") { fts.search(match, limit) } } catch (_: android.database.sqlite.SQLiteException) { return null }
        if (ids.isEmpty()) return emptyList()
        val rows = com.dns.sms.util.Perf.time("search.load(${ids.size})") { loadSms("_id IN (${ids.joinToString(",")})", null, "date DESC") }
        // Messages deleted outside the app are still in the index: drop them from the results and prune.
        if (rows.size < ids.size) {
            val alive = rows.mapTo(HashSet()) { it.id }
            runCatching { fts.delete(ids.filter { it !in alive }) }
        }
        return rows
    }

    private fun loadSms(selection: String, args: Array<String>?, sort: String): List<Message> =
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf("_id", "address", "body", "date", "type", "status", "thread_id"),
            selection, args, sort,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val type = c.getInt(4)
                    add(
                        Message(
                            id = c.getLong(0), kind = MessageKind.SMS, threadId = c.getLong(6),
                            address = c.getString(1), body = c.getString(2).orEmpty(), subject = null,
                            dateMillis = c.getLong(3), outgoing = type != StatusMapper.SMS_INBOX,
                            status = StatusMapper.sms(type, c.getIntOr(5, -1), false), deliveredAt = null,
                        )
                    )
                }
            }
        } ?: emptyList()

    /** The SIM the thread last used, or null for the default. */
    suspend fun lastSubId(threadId: Long): Int? = withContext(Dispatchers.IO) {
        safe(null) {
            resolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Columns.SUB_ID), "thread_id = ?",
                arrayOf(threadId.toString()), "date DESC LIMIT 1")
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getInt(0).takeIf { it >= 0 } else null }
        }
    }

    suspend fun markThreadRead(threadId: Long) = markThreadsRead(listOf(threadId))

    suspend fun markThreadsRead(threadIds: Collection<Long>) = withContext(Dispatchers.IO) {
        if (threadIds.isEmpty()) return@withContext
        safe(Unit) {
            val v = ContentValues().apply { put("read", 1); put("seen", 1) }
            val sel = "thread_id IN (${threadIds.joinToString(",")}) AND read = 0"
            resolver.update(Telephony.Sms.CONTENT_URI, v, sel, null)
        }
    }

    suspend fun deleteThreads(threadIds: Collection<Long>) = withContext(Dispatchers.IO) {
        safe(Unit) {
            threadIds.forEach { resolver.delete(ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, it), null, null) }
        }
        runCatching { fts.deleteThreads(threadIds.toList()) }
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        safe(Unit) {
            resolver.delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id), null, null)
        }
        runCatching { fts.delete(listOf(id)) }
    }

    fun threadIdFor(addresses: Collection<String>): Long =
        Telephony.Threads.getOrCreateThreadId(ctx, addresses.toSet())

    /** Runs [block] only while default; provider errors (e.g. permission revoked) become [fallback]. */
    private inline fun <T> safe(fallback: T, block: () -> T): T {
        if (!gate.isDefaultNow()) return fallback
        return try { block() } catch (_: SecurityException) { fallback } catch (_: IllegalArgumentException) { fallback }
    }
}

internal fun Cursor.getIntOr(index: Int, default: Int): Int = if (isNull(index)) default else getInt(index)
