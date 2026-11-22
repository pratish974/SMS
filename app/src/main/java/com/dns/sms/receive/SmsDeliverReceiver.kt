package com.dns.sms.receive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.dns.sms.di.entry

/** SMS_DELIVER: only sent to the default SMS app. We must store the message ourselves. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val app = context.entry()
        if (!app.gate().isDefaultNow()) return
        val pdus = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val parts = pdus.filterNotNull().map {
            SmsPart(it.displayOriginatingAddress.orEmpty(), it.displayMessageBody.orEmpty(), it.timestampMillis)
        }
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, intent.getIntExtra("subscription", -1))
        runAsync {
            SmsAssembler.assemble(parts).forEach { msg ->
                app.incoming().store(msg, subId)?.let { threadId -> app.actions().onIncoming(threadId, msg.address) }
            }
        }
    }
}
