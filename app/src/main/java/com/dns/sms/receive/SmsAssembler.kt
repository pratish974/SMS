package com.dns.sms.receive

/** One PDU's worth of an incoming SMS, as Android hands it to us. */
data class SmsPart(val address: String, val body: String, val timestampMillis: Long)

/** A complete incoming message (multipart bodies joined in arrival order). */
data class IncomingMessage(val address: String, val body: String, val timestampMillis: Long)

object SmsAssembler {
    /** Joins the parts of one SMS_DELIVER broadcast, grouped by sender, keeping the first timestamp. */
    fun assemble(parts: List<SmsPart>): List<IncomingMessage> =
        parts.groupBy { it.address }.map { (address, ps) ->
            IncomingMessage(address, ps.joinToString("") { it.body }, ps.first().timestampMillis)
        }
}
