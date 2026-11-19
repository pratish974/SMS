package com.dns.sms.scheduled

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dns.sms.InternalLinkActivity
import com.dns.sms.R
import com.dns.sms.data.ScheduledDao
import com.dns.sms.data.ScheduledMessage
import com.dns.sms.notify.MessageNotifier
import com.dns.sms.send.Outbox
import com.dns.sms.sms.DefaultSmsGate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** FEAT-1: schedule, edit, cancel and send scheduled messages (Requirement.md "Planned features"). */
@Singleton
class ScheduledMessages @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    private val dao: ScheduledDao,
    private val outbox: Outbox,
    private val gate: DefaultSmsGate,
    private val notifier: MessageNotifier,
) {
    private val alarms get() = ctx.getSystemService(AlarmManager::class.java)

    /** Exact timing needs the user's "Alarms & reminders" permission on Android 12+. */
    fun canSendOnTime(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    /** Android 12+ only (minSdk is 30): on 11 exact alarms need no permission, so app settings is the fallback. */
    fun exactTimingSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", ctx.packageName, null))
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))

    suspend fun schedule(recipients: List<String>, body: String, sendAt: Long, threadId: Long?): Long {
        val id = dao.insert(
            ScheduledMessage(
                threadId = threadId, recipients = recipients.joinToString("\n"), body = body,
                sendAt = sendAt, createdAt = System.currentTimeMillis(),
            ),
        )
        reschedule()
        return id
    }

    suspend fun changeTime(id: Long, sendAt: Long) {
        val m = dao.get(id) ?: return
        dao.update(m.copy(sendAt = sendAt, state = ScheduledMessage.PENDING, failure = null))
        reschedule()
    }

    suspend fun cancel(id: Long) {
        dao.delete(id)
        reschedule()
    }

    suspend fun sendNow(id: Long) {
        val m = dao.get(id) ?: return
        send(m, System.currentTimeMillis())
        reschedule()
    }

    /** Sends everything that is due (including messages missed while the phone was off). */
    suspend fun sendDue() {
        val now = System.currentTimeMillis()
        ScheduleLogic.due(dao.pending(), { it.sendAt }, now).forEach { send(it, now) }
        dao.pruneSent(now - 30L * 24 * 60 * 60 * 1000)
        reschedule()
    }

    private suspend fun send(m: ScheduledMessage, now: Long) {
        if (!gate.isDefaultNow()) {
            dao.update(m.copy(state = ScheduledMessage.FAILED, failure = FAIL_NOT_DEFAULT))
            notifyFailure(m, R.string.scheduled_failed_not_default)
            return
        }
        val ok = outbox.sendTo(m.recipientList, m.body, m.threadId) !is Outbox.Result.Failed
        if (ok) {
            dao.update(m.copy(state = ScheduledMessage.SENT, sentAt = now, failure = if (ScheduleLogic.isLate(m.sendAt, now)) LATE else null))
        } else {
            dao.update(m.copy(state = ScheduledMessage.FAILED, failure = FAIL_SEND))
            notifyFailure(m, R.string.scheduled_failed_send)
        }
    }

    /** One alarm for the earliest pending message. */
    suspend fun reschedule() {
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, ScheduledSendReceiver::class.java).setAction(ScheduledSendReceiver.ACTION_SEND_DUE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = ScheduleLogic.nextAlarm(dao.pending().map { it.sendAt })
        when {
            next == null -> alarms.cancel(pi)
            canSendOnTime() -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    private fun notifyFailure(m: ScheduledMessage, text: Int) {
        if (!notifier.canPost()) return
        val open = PendingIntent.getActivity(
            ctx, (m.id % Int.MAX_VALUE).toInt(),
            Intent(ctx, InternalLinkActivity::class.java).setAction(InternalLinkActivity.ACTION_VIEW_SCHEDULED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, MessageNotifier.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.scheduled_failed_title))
            .setContentText(ctx.getString(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify("scheduled", (m.id % Int.MAX_VALUE).toInt(), n) } catch (_: SecurityException) { }
    }

    companion object {
        const val FAIL_NOT_DEFAULT = "not-default"
        const val FAIL_SEND = "send"
        const val LATE = "late"
    }
}
