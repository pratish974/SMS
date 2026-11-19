package com.dns.sms.parse

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** A date (and maybe a time) found in text, with where it was found. */
data class FoundDate(val date: LocalDate, val time: LocalTime?, val start: Int)

/**
 * Extracts dates from Indian SMS. Always day-first (dd/MM), never MM/dd. A missing year is inferred
 * forward from [reference] (the SMS's received time): "05 Jan" received on 28 Dec is next January.
 * Pure; patterns are simple so hostile input stays fast.
 */
object DateExtractor {
    private const val MAX_INPUT = 1000
    private val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )
    private const val MON = """(jan|feb|mar|apr|may|jun|jul|aug|sept?|oct|nov|dec)[a-z]{0,6}"""

    private val numeric = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2}|\d{4})\b""")
    private val iso = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val dayMonth = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[\s\-/]*$MON\.?,?[\s\-/]*(\d{4}|\d{2})?\b""", RegexOption.IGNORE_CASE)
    private val monthDay = Regex("""\b$MON\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s*(\d{4})?\b""", RegexOption.IGNORE_CASE)
    private val time = Regex("""\b(\d{1,2})(?::|\.)(\d{2})\s*(am|pm|hrs|hours)?\b|\b(\d{1,2})\s*(am|pm)\b""", RegexOption.IGNORE_CASE)

    fun findAll(text: String, reference: ZonedDateTime): List<FoundDate> {
        val t = text.take(MAX_INPUT)
        val out = ArrayList<FoundDate>()
        iso.findAll(t).forEach { m ->
            date(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { out += FoundDate(it, timeNear(t, m.range.last), m.range.first) }
        }
        numeric.findAll(t).forEach { m ->
            if (out.any { it.start == m.range.first }) return@forEach
            val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), y)?.let { out += FoundDate(it, timeNear(t, m.range.last), m.range.first) }
        }
        dayMonth.findAll(t).forEach { m ->
            val month = monthOf(m.groupValues[2]) ?: return@forEach
            val day = m.groupValues[1].toInt()
            val d = withYear(day, month, m.groupValues[3], reference) ?: return@forEach
            out += FoundDate(d, timeNear(t, m.range.last), m.range.first)
        }
        monthDay.findAll(t).forEach { m ->
            if (out.any { kotlin.math.abs(it.start - m.range.first) < 4 }) return@forEach
            val month = monthOf(m.groupValues[1]) ?: return@forEach
            val d = withYear(m.groupValues[2].toInt(), month, m.groupValues[3], reference) ?: return@forEach
            out += FoundDate(d, timeNear(t, m.range.last), m.range.first)
        }
        return out.sortedBy { it.start }
    }

    /** The first date at or after [from] in the text (e.g. right after "due on"). */
    fun firstAfter(text: String, from: Int, reference: ZonedDateTime): FoundDate? =
        findAll(text, reference).firstOrNull { it.start >= from }

    private fun timeNear(t: String, after: Int): LocalTime? {
        val window = t.substring(minOf(after + 1, t.length), minOf(t.length, after + 25))
        val m = time.find(window) ?: return null
        return if (m.groupValues[1].isNotEmpty()) {
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            when (m.groupValues[3].lowercase()) { "pm" -> if (h < 12) h += 12; "am" -> if (h == 12) h = 0 }
            if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
        } else {
            var h = m.groupValues[4].toInt()
            if (m.groupValues[5].equals("pm", true) && h < 12) h += 12
            if (m.groupValues[5].equals("am", true) && h == 12) h = 0
            if (h in 0..23) LocalTime.of(h, 0) else null
        }
    }

    private fun monthOf(s: String): Int? = months[s.lowercase().take(4)] ?: months[s.lowercase().take(3)]

    private fun withYear(day: Int, month: Int, year: String, ref: ZonedDateTime): LocalDate? {
        if (year.isNotEmpty()) return date(day, month, year.toInt().let { if (it < 100) 2000 + it else it })
        val sameYear = date(day, month, ref.year) ?: return null
        // A date well before the SMS without a year means next year's occurrence.
        return if (sameYear.isBefore(ref.toLocalDate().minusDays(30))) date(day, month, ref.year + 1) else sameYear
    }

    private fun date(d: Int, m: Int, y: Int): LocalDate? =
        if (y !in 2000..2100) null else runCatching { LocalDate.of(y, m, d) }.getOrNull()
}
