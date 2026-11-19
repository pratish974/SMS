package com.dns.sms.ui.conversation

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface TimelineItem<out T> {
    data class DateSeparator(val date: LocalDate) : TimelineItem<Nothing>
    data class Entry<T>(val value: T) : TimelineItem<T>
}

object Timeline {
    /** Inserts a date separator before the first message of each local day. [items] must be oldest first. */
    fun <T> build(items: List<T>, timeOf: (T) -> Long, zone: ZoneId): List<TimelineItem<T>> {
        val out = ArrayList<TimelineItem<T>>(items.size + 8)
        var last: LocalDate? = null
        for (item in items) {
            val day = Instant.ofEpochMilli(timeOf(item)).atZone(zone).toLocalDate()
            if (day != last) { out += TimelineItem.DateSeparator(day); last = day }
            out += TimelineItem.Entry(item)
        }
        return out
    }
}
