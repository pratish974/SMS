package com.dns.sms.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dns.sms.InternalLinkActivity
import com.dns.sms.R
import com.dns.sms.di.entry
import com.dns.sms.notify.MessageNotifier
import com.dns.sms.receive.runAsync
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fires reminder notifications, then schedules the next alarm. Also reschedules after reboot,
 * app update, and time or time-zone changes. Not exported: those system broadcasts still reach it.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.entry()
        runAsync {
            val scheduler = app.reminderScheduler()
            if (intent.action == ACTION_FIRE) {
                val now = System.currentTimeMillis()
                val last = scheduler.lastFired()
                val upcoming = app.reminderDao().upcoming(now - 3L * 24 * 60 * 60 * 1000)
                val due = ReminderTiming.due(upcoming, { it.kind }, { it.dueAt }, last, now)
                if (app.settings().notifyReminders.value && app.notifier().canPost()) {
                    due.forEach { r -> post(context, r.id, r.title, r.detail, r.dueAt) }
                }
                scheduler.markFired(now)
            }
            scheduler.reschedule()
            // Same system events (boot, update, time/time-zone change) re-arm scheduled messages;
            // anything missed while the phone was off is sent now.
            if (intent.action != ACTION_FIRE) app.scheduled().sendDue()
        }
    }

    private fun post(ctx: Context, id: Long, title: String, detail: String?, dueAt: Long) {
        val whenText = DateTimeFormatter.ofPattern("EEE, d MMM, h:mm a", Locale.getDefault())
            .format(Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()))
        val open = PendingIntent.getActivity(
            ctx, (id % Int.MAX_VALUE).toInt(),
            Intent(ctx, InternalLinkActivity::class.java).setAction(InternalLinkActivity.ACTION_VIEW_REMINDERS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, MessageNotifier.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(listOfNotNull(detail, whenText).joinToString(" · "))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(ctx, MessageNotifier.CHANNEL_REMINDERS)
                    .setSmallIcon(R.drawable.ic_notification).setContentTitle(ctx.getString(R.string.reminder_due)).build(),
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify("reminder", (id % Int.MAX_VALUE).toInt(), n) } catch (_: SecurityException) { }
    }

    companion object { const val ACTION_FIRE = "com.dns.sms.REMINDER_FIRE" }
}
