package com.dns.sms.parse

import com.dns.sms.classify.OtpDetector
import com.dns.sms.sms.PhoneNumbers
import java.math.BigDecimal

/**
 * What a bank SMS notification shows (reference 2026-11-24): "- ₹ 3,500 UPI / From ICICI XX439" for money
 * leaving an account, "+ ₹ 200 IMPS / To SBI XX3487" for money arriving. Pure and bounded; never throws.
 */
data class TransactionAlert(
    val debit: Boolean,
    val amount: BigDecimal,
    /** "UPI", "IMPS", "Card", ... or "DEBIT"/"CREDIT" when the SMS doesn't say. */
    val mode: String,
    val bank: String,
    /** "XX439", or blank when the SMS names no account or card. */
    val mask: String,
) {
    companion object {
        private const val MAX_INPUT = 1000
        private val card = Regex("""\bcard\b\s*(?:no\.?|number|ending\s*(?:with)?)?\s*[:\-]?\s*([x*]{0,12}\d{4})\b""", RegexOption.IGNORE_CASE)
        /** Requests, reminders and future debits are not money that has moved. */
        private val notYet = Regex(
            """\b(?:will be (?:debited|credited|deducted)|is due|due (?:on|date)|requested|request(?:ing)? (?:money|rs|inr|₹)|collect request|mandate|autopay|failed|declined|reversed|refund initiated)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val modes = listOf(
            Regex("""\bupi\b""", RegexOption.IGNORE_CASE) to "UPI",
            Regex("""\bimps\b""", RegexOption.IGNORE_CASE) to "IMPS",
            Regex("""\bneft\b""", RegexOption.IGNORE_CASE) to "NEFT",
            Regex("""\brtgs\b""", RegexOption.IGNORE_CASE) to "RTGS",
            Regex("""\batm\b""", RegexOption.IGNORE_CASE) to "ATM",
            Regex("""\bnet\s?banking\b""", RegexOption.IGNORE_CASE) to "Net Banking",
            Regex("""\b(?:debit|credit)?\s?card\b""", RegexOption.IGNORE_CASE) to "Card",
        )

        fun from(sender: String, rawBody: String): TransactionAlert? = runCatching {
            val body = rawBody.take(MAX_INPUT)
            // Banks send from alphanumeric ids; a person's number is never a bank alert.
            if (PhoneNumbers.isPhoneLike(sender) && !Banks.isKnown(sender)) return null
            if (!FinanceParser.mightMatch(body) || OtpDetector.isOtp(body) || notYet.containsMatchIn(body)) return null
            val (type, amount) = FinanceParser.transaction(body) ?: return null
            if (amount.signum() <= 0) return null
            val debit = type == "DEBIT"
            val mask = FinanceParser.parse(sender, body)?.mask
                ?: card.find(body)?.groupValues?.get(1)?.filter { it.isDigit() }?.let { "XX$it" }
                ?: ""
            val mode = modes.firstOrNull { it.first.containsMatchIn(body) }?.second ?: type
            TransactionAlert(debit, amount, mode, Banks.nameFor(sender), mask)
        }.getOrNull()
    }
}
