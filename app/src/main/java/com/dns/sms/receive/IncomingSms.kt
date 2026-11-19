package com.dns.sms.receive

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.Telephony
import com.dns.sms.sms.Columns
import com.dns.sms.sms.ConversationRepository
import com.dns.sms.sms.DefaultSmsGate
import com.dns.sms.send.StatusMapper
import javax.inject.Inject
import javax.inject.Singleton

/** Writes delivered SMS into the provider. As default SMS app this is our job, not the system's. */
@Singleton
class IncomingSms @Inject constructor(
    private val resolver: ContentResolver,
    private val gate: DefaultSmsGate,
    private val repo: ConversationRepository,
) {
    /** Returns the thread id, or null if not stored (not default, or a duplicate broadcast). */
    fun store(msg: IncomingMessage, subId: Int): Long? {
        if (!gate.isDefaultNow()) return null
        if (isDuplicate(msg)) return null
        val threadId = repo.threadIdFor(listOf(msg.address))
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, msg.address)
            put(Telephony.Sms.BODY, msg.body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, msg.timestampMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, StatusMapper.SMS_INBOX)
            put(Telephony.Sms.THREAD_ID, threadId)
            if (subId >= 0) put(Columns.SUB_ID, subId)
        }
        return resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)?.let { threadId }
    }

    /** Some devices redeliver SMS_DELIVER; same sender + sent time + body means we already have it. */
    private fun isDuplicate(msg: IncomingMessage): Boolean =
        resolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf("_id"),
            "address = ? AND date_sent = ? AND body = ?",
            arrayOf(msg.address, msg.timestampMillis.toString(), msg.body), null,
        )?.use { it.count > 0 } ?: false
}
