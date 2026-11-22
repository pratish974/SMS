package com.dns.sms.scheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dns.sms.di.entry
import com.dns.sms.receive.runAsync

/** Alarm for due scheduled messages. Not exported; re-armed by ReminderAlarmReceiver after reboot etc. */
class ScheduledSendReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND_DUE) return
        runAsync { context.entry().scheduled().sendDue() }
    }

    companion object { const val ACTION_SEND_DUE = "com.dns.sms.SEND_SCHEDULED" }
}
