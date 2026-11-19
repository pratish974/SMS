package com.dns.sms.reminders

/** When to notify for a reminder (pure, tested). */
object ReminderTiming {
    private const val HOUR = 60 * 60 * 1000L

    /** Bills: the evening before and the morning of the due day are both useful; we use the day before. */
    fun notifyAt(kind: String, dueAt: Long): Long = when (kind) {
        "BILL" -> dueAt - 24 * HOUR
        "MOVIE", "BUS", "FLIGHT" -> dueAt - 2 * HOUR
        else -> dueAt
    }

    /** The earliest notify time strictly after [after], or null. */
    fun next(items: List<Pair<String, Long>>, after: Long): Long? =
        items.map { (kind, due) -> notifyAt(kind, due) }.filter { it > after }.minOrNull()

    /** Reminders whose notify time falls in (lastFired, now]. */
    fun <T> due(items: List<T>, kindOf: (T) -> String, dueOf: (T) -> Long, lastFired: Long, now: Long): List<T> =
        items.filter { val n = notifyAt(kindOf(it), dueOf(it)); n in (lastFired + 1)..now }
}
