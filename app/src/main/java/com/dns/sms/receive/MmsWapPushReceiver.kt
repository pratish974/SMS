package com.dns.sms.receive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dns.sms.R
import com.dns.sms.di.entry
import com.dns.sms.notify.MessageNotifier

/**
 * MMS is not supported yet (planned milestone, Requirement.md). Android only lets an app be the
 * default SMS app if it declares this WAP_PUSH_DELIVER receiver, so it stays, but it only tells the
 * user that an MMS arrived and could not be received. Nothing from the push is parsed or stored.
 */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val app = context.entry()
        if (!app.gate().isDefaultNow() || !app.notifier().canPost()) return
        val n = NotificationCompat.Builder(context, MessageNotifier.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.mms_unsupported_title))
            .setContentText(context.getString(R.string.mms_unsupported_body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(TAG, 1, n) } catch (_: SecurityException) { }
    }

    private companion object { const val TAG = "mms-unsupported" }
}
