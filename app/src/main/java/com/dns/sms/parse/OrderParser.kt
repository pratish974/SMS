package com.dns.sms.parse

import com.dns.sms.classify.SenderKey
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ParsedOrder(
    val merchant: String,
    val orderId: String?,
    val title: String,
    val status: String,
    val url: String?,
    val expectedAt: Long?,
) {
    /** Groups updates for the same shipment. */
    val key: String get() = merchant.lowercase() + "|" + (orderId ?: title.lowercase())
}

/** Shipping SMS -> order status (Requirement.md section 4, Orders). Never throws. */
object OrderParser {
    const val VERSION = 1
    private const val MAX_INPUT = 1000

    private val merchants = mapOf(
        "AMAZON" to "Amazon", "AMAZNN" to "Amazon", "FLPKRT" to "Flipkart", "FLIPKT" to "Flipkart", "MYNTRA" to "Myntra",
        "AJIOIN" to "AJIO", "AJIO" to "AJIO", "BLNKIT" to "Blinkit", "SWIGGY" to "Swiggy", "ZOMATO" to "Zomato",
        "EKARTL" to "Ekart", "EKART" to "Ekart", "DELHVY" to "Delhivery", "BLUDRT" to "Blue Dart", "BLUEDT" to "Blue Dart",
        "XPRSBS" to "Xpressbees", "SHDWFX" to "Shadowfax", "MEESHO" to "Meesho", "NYKAA" to "Nykaa", "JIOMRT" to "JioMart",
        "BIGBKT" to "bigbasket", "TATACQ" to "Tata CLiQ", "DTDCIN" to "DTDC", "INDPST" to "India Post",
    )
    private val context = Regex("""\b(order|shipment|package|parcel|awb|consignment|delivery)\b""", RegexOption.IGNORE_CASE)
    private val cancelled = Regex("""\b(cancell?ed)\b""", RegexOption.IGNORE_CASE)
    private val delivered = Regex("""\b(has been delivered|was delivered|delivered successfully|is delivered|delivered today|delivered to)\b""", RegexOption.IGNORE_CASE)
    private val outFor = Regex("""\bout for delivery\b""", RegexOption.IGNORE_CASE)
    private val arriving = Regex("""\b(arriving|expected (?:delivery|by)|will be delivered|delivery by|arrives)\b""", RegexOption.IGNORE_CASE)
    private val dispatched = Regex("""\b(shipped|dispatched|in transit|handed over)\b""", RegexOption.IGNORE_CASE)
    private val placed = Regex("""\b(order (?:placed|confirmed|received)|thank you for (?:your )?order)\b""", RegexOption.IGNORE_CASE)
    private val orderId = Regex("""\b(?:order|awb|tracking)\s*(?:id|no\.?|number|#)?\s*[:#]?\s*#?([A-Z0-9][A-Z0-9-]{4,24})\b""", RegexOption.IGNORE_CASE)
    private val item = Regex("""\b(?:order|package|shipment)\s+(?:for|of|containing)\s+(.{3,60}?)(?=\s+(?:has|is|will|was|with|from)\b|[.,!]|$)""", RegexOption.IGNORE_CASE)
    private val url = Regex("""https?://[^\s<>"]{4,200}""", RegexOption.IGNORE_CASE)

    fun parse(sender: String, body: String, receivedAt: Long, zone: ZoneId): ParsedOrder? =
        runCatching { parseUnsafe(sender, body.take(MAX_INPUT), receivedAt, zone) }.getOrNull()

    private fun parseUnsafe(sender: String, body: String, receivedAt: Long, zone: ZoneId): ParsedOrder? {
        if (!context.containsMatchIn(body)) return null
        val status = when {
            cancelled.containsMatchIn(body) -> "CANCELLED"
            delivered.containsMatchIn(body) -> "DELIVERED"
            outFor.containsMatchIn(body) -> "OUT_FOR_DELIVERY"
            arriving.containsMatchIn(body) -> "ARRIVING"
            dispatched.containsMatchIn(body) -> "DISPATCHED"
            placed.containsMatchIn(body) -> "PLACED"
            else -> return null
        }
        val id = orderId.findAll(body).map { it.groupValues[1] }.firstOrNull { s -> s.any { it.isDigit() } }
        val title = item.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',')
        if (id == null && title == null) return null
        val key = SenderKey.of(sender)
        val merchant = merchants[key] ?: key.lowercase().replaceFirstChar { it.uppercase() }
        val ref = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(receivedAt), zone)
        val expected = if (status == "ARRIVING" || status == "DISPATCHED") {
            arriving.find(body)?.let { DateExtractor.firstAfter(body, it.range.first, ref) }
                ?.let { it.date.atTime(it.time ?: LocalTime.of(20, 0)).atZone(zone).toInstant().toEpochMilli() }
        } else null
        return ParsedOrder(
            merchant = merchant,
            orderId = id,
            title = title ?: "Order #$id",
            status = status,
            url = url.find(body)?.value?.trimEnd('.', ',', ')'),
            expectedAt = expected,
        )
    }
}
