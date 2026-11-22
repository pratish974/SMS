package com.dns.sms.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    /** Settings > General > Use 24 hours format; null follows the system. */
    @Volatile var force24h: Boolean? = null

    private fun zone() = ZoneId.systemDefault()

    /** Formatters are cached: building one per row per frame was a scroll hot spot. */
    private val formatters = java.util.concurrent.ConcurrentHashMap<String, DateTimeFormatter>()
    private fun fmt(pattern: String): DateTimeFormatter {
        val locale = Locale.getDefault()
        return formatters.getOrPut("$pattern|$locale") { DateTimeFormatter.ofPattern(pattern, locale) }
    }

    fun time(ctx: Context, millis: Long): String {
        val pattern = if (force24h ?: DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a"
        return fmt(pattern).format(Instant.ofEpochMilli(millis).atZone(zone()))
    }

    /** Conversation list: time today, "19 Nov" this year, "19/11/25" otherwise. */
    fun listDate(ctx: Context, millis: Long): String {
        val dt = Instant.ofEpochMilli(millis).atZone(zone())
        val today = LocalDate.now(zone())
        return when {
            dt.toLocalDate() == today -> time(ctx, millis)
            dt.year == today.year -> fmt("d MMM").format(dt)
            else -> fmt("dd/MM/yy").format(dt)
        }
    }


    /** "09 Nov 26 at 19:43" (delivery reports). */
    fun deliveredAt(ctx: Context, millis: Long): String {
        val dt = Instant.ofEpochMilli(millis).atZone(zone())
        return fmt("dd MMM yy").format(dt) + " at " + time(ctx, millis)
    }

    fun separator(date: LocalDate): String {
        val today = LocalDate.now(zone())
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> fmt(if (date.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy").format(date)
        }
    }
}
