package com.dns.sms.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dns.sms.data.ReminderDao
import com.dns.sms.data.ScanState
import com.dns.sms.data.ScanStateDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One alarm for the next reminder. No exact-alarm permission (Play policy, Requirement.md section 8):
 * setAndAllowWhileIdle fires within minutes even in Doze, which is fine for reminders with lead time.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    private val reminders: ReminderDao,
    private val state: ScanStateDao,
) {
    suspend fun reschedule() {
        val now = System.currentTimeMillis()
        val last = state.get(KEY_LAST_FIRED) ?: now
        val items = reminders.upcoming(now - 3L * 24 * 60 * 60 * 1000).map { it.kind to it.dueAt }
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending()
        val next = ReminderTiming.next(items, maxOf(last, now - 60_000))
        if (next == null) am.cancel(pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        if (state.get(KEY_LAST_FIRED) == null) state.put(ScanState(KEY_LAST_FIRED, now))
    }

    suspend fun lastFired(): Long = state.get(KEY_LAST_FIRED) ?: System.currentTimeMillis()
    suspend fun markFired(at: Long) = state.put(ScanState(KEY_LAST_FIRED, at))

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object { const val KEY_LAST_FIRED = "reminder_last_fired" }
}
