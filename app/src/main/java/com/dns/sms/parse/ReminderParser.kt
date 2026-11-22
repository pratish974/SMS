package com.dns.sms.parse

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ParsedReminder(val kind: String, val title: String, val detail: String?, val dueAt: Long)

/**
 * Bills, movies, bus and flights from SMS (no train/PNR status, Requirement.md section 2).
 * Never throws. Ignores dates that are already in the past relative to the SMS.
 */
object ReminderParser {
    const val VERSION = 1
    private const val MAX_INPUT = 1000

    private val billDue = Regex("""\b(due date|due on|due by|is due|pay by|payment due|last date)\b""", RegexOption.IGNORE_CASE)
    private val billWord = Regex("""\b(bill|statement|emi|premium|dues|recharge|electricity|postpaid|credit card|invoice)\b""", RegexOption.IGNORE_CASE)
    private val movie = Regex("""\b(movie|cinema|screen|show ?time|pvr|inox|cinepolis|bookmyshow|tickets? booked)\b""", RegexOption.IGNORE_CASE)
    private val bus = Regex("""\bbus\b|\b(redbus|abhibus|boarding point)\b""", RegexOption.IGNORE_CASE)
    private val flight = Regex("""\b(flight|departure|boarding gate|check-?in|e-?ticket)\b""", RegexOption.IGNORE_CASE)
    private val flightNo = Regex("""\b(6E|AI|UK|SG|IX|QP|I5|G8|EK|QR|SQ|LH|BA|EY)[\s-]?(\d{2,4})\b""")
    private val booking = Regex("""\b(booking (?:id|confirmed|ref)|booked|ticket|pnr|seat)\b""", RegexOption.IGNORE_CASE)
    private val amount = Regex("""(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val train = Regex("""\b(train|irctc|coach|berth)\b""", RegexOption.IGNORE_CASE)
    private val notReminder = Regex("""\b(otp|paid successfully|payment (?:of .{0,20})?received|thank you for (?:your )?payment)\b""", RegexOption.IGNORE_CASE)

    /** Words at least one of which every reminder template needs; lets the scanner skip the regexes for most SMS. */
    private val keywords = listOf(
        "due", "pay by", "last date", "flight", "departure", "boarding", "check", "ticket", "bus",
        "movie", "cinema", "screen", "show", "pvr", "inox", "cinepolis", "bookmyshow",
    )

    fun mightMatch(body: String): Boolean = keywords.any { body.contains(it, ignoreCase = true) }

    fun parse(sender: String, body: String, receivedAt: Long, zone: ZoneId): ParsedReminder? =
        runCatching { parseUnsafe(sender, body.take(MAX_INPUT), receivedAt, zone) }.getOrNull()

    private fun parseUnsafe(sender: String, body: String, receivedAt: Long, zone: ZoneId): ParsedReminder? {
        if (notReminder.containsMatchIn(body) || train.containsMatchIn(body)) return null
        val ref = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(receivedAt), zone)
        val brand = Banks.nameFor(sender)
        // Raw sender ids like "INDGOI" aren't helpful as a subtitle; only show names we know.
        val knownBrand = brand.takeIf { Banks.isKnown(sender) }

        billDue.find(body)?.let { kw ->
            if (!billWord.containsMatchIn(body)) return@let
            val d = DateExtractor.firstAfter(body, kw.range.first, ref) ?: return@let
            val amt = amount.find(body)?.groupValues?.get(1)?.let(Money::parse)?.let(Money::formatInr)
            val due = d.date.atTime(LocalTime.of(9, 0)).atZone(zone)
            return result("BILL", listOfNotNull("$brand bill due", amt).joinToString(" · "), null, due, ref)
        }
        val firstDate = DateExtractor.findAll(body, ref).firstOrNull { it.time != null } ?: return null
        val at = firstDate.date.atTime(firstDate.time).atZone(zone)
        return when {
            flight.containsMatchIn(body) && flightNo.containsMatchIn(body) -> {
                val no = flightNo.find(body)!!.let { "${it.groupValues[1]} ${it.groupValues[2]}" }
                result("FLIGHT", "Flight $no", knownBrand, at, ref)
            }
            bus.containsMatchIn(body) && booking.containsMatchIn(body) -> result("BUS", "Bus journey", knownBrand, at, ref)
            movie.containsMatchIn(body) && booking.containsMatchIn(body) -> result("MOVIE", "Movie show", knownBrand, at, ref)
            else -> null
        }
    }

    private fun result(kind: String, title: String, detail: String?, due: ZonedDateTime, ref: ZonedDateTime): ParsedReminder? {
        if (due.isBefore(ref.minusDays(1))) return null
        return ParsedReminder(kind, title, detail, due.toInstant().toEpochMilli())
    }
}
