package com.dns.sms.send

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.dns.sms.receive.SmsStatusReceiver
import com.dns.sms.sms.Columns
import com.dns.sms.sms.ConversationRepository
import com.dns.sms.sms.DefaultSmsGate
import com.dns.sms.sms.smsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SmsSender @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    private val resolver: ContentResolver,
    private val gate: DefaultSmsGate,
    private val repo: ConversationRepository,
    private val threadMetas: com.dns.sms.data.ThreadMetaDao,
) {
    /** Writes the message to the outbox and sends it. Returns the thread id, or null if not allowed. */
    suspend fun send(address: String, body: String, threadId: Long? = null): Long? = withContext(Dispatchers.IO) {
        if (!gate.isDefaultNow() || body.isBlank() || address.isBlank()) return@withContext null
        val tid = threadId ?: repo.threadIdFor(listOf(address))
        // The SIM picked for this conversation wins; otherwise the one it last used.
        val subId = threadMetas.one(tid)?.subId ?: repo.lastSubId(tid)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, StatusMapper.SMS_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            put(Telephony.Sms.THREAD_ID, tid)
            subId?.let { put(Columns.SUB_ID, it) }
        }
        val uri = resolver.insert(Telephony.Sms.CONTENT_URI, values) ?: return@withContext null
        dispatch(uri, address, body, subId)
        // Index the sent message for search right away (the scanner also feeds the search index).
        com.dns.sms.work.SmsScanWorker.enqueue(ctx)
        tid
    }

    /** Resends a failed message, reusing its row. */
    suspend fun retry(messageId: Long) = withContext(Dispatchers.IO) {
        if (!gate.isDefaultNow()) return@withContext
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId)
        val row = resolver.query(uri, arrayOf("address", "body", Columns.SUB_ID), null, null, null)?.use { c ->
            if (c.moveToFirst()) Triple(c.getString(0), c.getString(1), if (c.isNull(2)) null else c.getInt(2)) else null
        } ?: return@withContext
        resolver.update(uri, ContentValues().apply {
            put(Telephony.Sms.TYPE, StatusMapper.SMS_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
        }, null, null)
        dispatch(uri, row.first.orEmpty(), row.second.orEmpty(), row.third)
    }

    private fun dispatch(uri: Uri, address: String, body: String, subId: Int?) {
        val sm = ctx.smsManager(subId)
        val parts = sm.divideMessage(body)
        val sent = ArrayList<PendingIntent>(parts.size)
        val delivered = ArrayList<PendingIntent?>(parts.size)
        parts.indices.forEach { i ->
            val last = i == parts.lastIndex
            sent += PendingIntent.getBroadcast(
                ctx, i,
                statusIntent(SmsStatusReceiver.ACTION_SENT, uri).putExtra(SmsStatusReceiver.EXTRA_LAST_PART, last),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // The delivery report PDU arrives as fill-in extras, which an immutable PendingIntent would drop.
            // The intent is explicit to our non-exported receiver, so mutability can't redirect it.
            // S16: still mutable (the platform must add the report), but one-shot, explicit and package-locked.
            delivered += if (last) PendingIntent.getBroadcast(
                ctx, 0, statusIntent(SmsStatusReceiver.ACTION_DELIVERED, uri),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT,
            ) else null
        }
        try {
            sm.sendMultipartTextMessage(address, null, parts, sent, delivered)
        } catch (_: RuntimeException) {
            resolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, StatusMapper.SMS_FAILED) }, null, null)
        }
    }

    private fun statusIntent(action: String, uri: Uri) =
        Intent(ctx, SmsStatusReceiver::class.java).setAction(action).setData(uri).setPackage(ctx.packageName)
}
