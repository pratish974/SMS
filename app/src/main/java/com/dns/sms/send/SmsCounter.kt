package com.dns.sms.send

/**
 * How many SMS a text needs (shown next to Send). Pure, unit tested.
 * - GSM 7-bit alphabet: 160 characters in one SMS, 153 per part when split; ^{}\[~]|€ count as 2.
 * - Anything else (Hindi, emoji, most accents): Unicode, 70 per SMS, 67 per part; counted in
 *   UTF-16 units, so an emoji takes 2.
 */
object SmsCounter {
    data class Count(val parts: Int, val remaining: Int, val unicode: Boolean)

    private const val GSM_BASIC =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private const val GSM_EXTENDED = "^{}\\[~]|€\u000C"
    private val basic = GSM_BASIC.toSet()
    private val extended = GSM_EXTENDED.toSet()

    fun count(text: String): Count {
        if (text.isEmpty()) return Count(0, 160, false)
        var septets = 0
        var gsm = true
        for (c in text) {
            when (c) {
                in basic -> septets += 1
                in extended -> septets += 2
                else -> { gsm = false; break }
            }
        }
        val (units, single, multi) = if (gsm) Triple(septets, 160, 153) else Triple(text.length, 70, 67)
        if (units <= single) return Count(1, single - units, !gsm)
        val parts = (units + multi - 1) / multi
        return Count(parts, parts * multi - units, !gsm)
    }

    /** Show the counter only when it matters: close to the limit, or already more than one SMS. */
    fun worthShowing(c: Count): Boolean = c.parts > 1 || (c.parts == 1 && c.remaining <= 20)
}
