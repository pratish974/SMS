package com.dns.sms.receive

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.dns.sms.data.DeliveryReport
import com.dns.sms.di.entry
import com.dns.sms.send.StatusMapper

/** Sent / delivered results for our own outgoing SMS. Not exported. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        if (uri.authority != Telephony.Sms.CONTENT_URI.authority) return
        val app = context.entry()
        if (!app.gate().isDefaultNow()) return
        val resolver = context.contentResolver
        val code = resultCode
        when (intent.action) {
            ACTION_SENT -> {
                val last = intent.getBooleanExtra(EXTRA_LAST_PART, true)
                runAsync {
                    if (code != Activity.RESULT_OK) {
                        resolver.update(uri, ContentValues().apply {
                            put(Telephony.Sms.TYPE, StatusMapper.SMS_FAILED)
                            put(Telephony.Sms.ERROR_CODE, code)
                        }, null, null)
                    } else if (last) {
                        // Don't overwrite a failure reported by an earlier part.
                        resolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, StatusMapper.SMS_SENT) },
                            "type != ${StatusMapper.SMS_FAILED}", null)
                    }
                }
            }
            ACTION_DELIVERED -> {
                // S16: the report comes in as fill-in extras; accept only a plausible status-report PDU.
                val pdu = intent.getByteArrayExtra("pdu")?.takeIf { it.size in 1..MAX_PDU } ?: return
                val format = intent.getStringExtra("format")
                if (format != null && format != SmsMessage.FORMAT_3GPP && format != SmsMessage.FORMAT_3GPP2) return
                val status = runCatching { SmsMessage.createFromPdu(pdu, format).status }
                    .getOrNull() ?: return
                // TP-Status: 0x00-0x1F delivered, 0x20-0x3F still trying, 0x40+ failed.
                val value = when {
                    status < 0x20 -> Telephony.Sms.STATUS_COMPLETE
                    status < 0x40 -> return
                    else -> Telephony.Sms.STATUS_FAILED
                }
                runAsync {
                    resolver.update(uri, ContentValues().apply { put(Telephony.Sms.STATUS, value) }, null, null)
                    if (value == Telephony.Sms.STATUS_COMPLETE) {
                        app.deliveryReports().upsert(DeliveryReport("sms", ContentUris.parseId(uri), System.currentTimeMillis()))
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SENT = "com.dns.sms.SMS_SENT"
        const val ACTION_DELIVERED = "com.dns.sms.SMS_DELIVERED"
        const val EXTRA_LAST_PART = "last_part"
        private const val MAX_PDU = 512
    }
}
