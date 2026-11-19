package com.dns.sms.scheduled

/** Pure timing rules for scheduled messages (unit tested with an injected clock). */
object ScheduleLogic {
    /** Sent more than this after the chosen time (phone was off, Doze) counts as "sent late". */
    const val LATE_MS = 5 * 60 * 1000L

    /** Earliest pending send time, or null when nothing is pending. Past times stay eligible. */
    fun nextAlarm(pendingSendAts: List<Long>): Long? = pendingSendAts.minOrNull()

    /** Which pending messages are due at [now] (includes ones missed while the phone was off). */
    fun <T> due(items: List<T>, sendAtOf: (T) -> Long, now: Long): List<T> = items.filter { sendAtOf(it) <= now }

    fun isLate(sendAt: Long, sentAt: Long): Boolean = sentAt - sendAt > LATE_MS

    /**
     * A chosen time is acceptable if it is still in the future. The time picker works in whole
     * minutes, so the next minute (e.g. 18:31 at 18:30:40) must be accepted; the old "at least 60 s
     * ahead" rule rejected it, and Send then went out immediately.
     */
    fun isValidChoice(sendAt: Long, now: Long): Boolean = sendAt > now

    /** What pressing Send does. */
    enum class SendMode { NOW, SCHEDULE, TIME_PASSED }

    /**
     * Send with no time picked = now. With a time picked it is ALWAYS scheduled, never sent now; if
     * the time has already passed while typing, nothing is sent and the user picks a new time.
     */
    fun sendMode(scheduledAt: Long?, now: Long): SendMode = when {
        scheduledAt == null -> SendMode.NOW
        scheduledAt > now -> SendMode.SCHEDULE
        else -> SendMode.TIME_PASSED
    }
}
