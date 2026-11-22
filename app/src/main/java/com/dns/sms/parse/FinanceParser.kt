package com.dns.sms.parse

import java.math.BigDecimal

/** What one bank SMS tells us. Any field may be missing. */
data class FinanceUpdate(
    val bank: String,
    val mask: String,
    val balance: BigDecimal?,
    val txnType: String?,
    val txnAmount: BigDecimal?,
)

/**
 * Bank SMS templates (Requirement.md section 7: regex templates, not ML). Versioned: bump [VERSION]
 * when templates change and stored accounts are re-parsed. Never throws.
 */
object FinanceParser {
    const val VERSION = 1
    private const val MAX_INPUT = 1000
    private const val AMT = """(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)"""

    private val account = Regex(
        """\b(?:a/?c|acct|account|ac)\b\.?\s*(?:no\.?|number)?\s*[:\-]?\s*(?:ending\s*(?:with\s*)?)?([x*]{0,8}\d{3,6})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val epfoMember = Regex("""\bagainst\s+([x*]{0,10}\d{3,6})\b""", RegexOption.IGNORE_CASE)
    private val balances = listOf(
        Regex("""\b(?:avbl|avl|available|avail|clr|clear|closing|total|net)\.?\s*(?:bal|balance|bal\.)\b\.?\s*(?:is|:|-|of|in .{0,30}? is)?\s*$AMT""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:bal|balance)\b\s*(?:is|:|-)?\s*$AMT""", RegexOption.IGNORE_CASE),
        Regex("""\bpassbook balance against\s+[x*]{0,10}\d{3,6}\s+is\s+$AMT""", RegexOption.IGNORE_CASE),
    )
    private val debitBefore = Regex("""$AMT\s*(?:has been|is|was)?\s*(debited|spent|withdrawn|paid|sent)\b""", RegexOption.IGNORE_CASE)
    private val creditBefore = Regex("""$AMT\s*(?:has been|is|was)?\s*(credited|received|deposited|added)\b""", RegexOption.IGNORE_CASE)
    private val debitAfter = Regex("""\b(debited)\b[^.]{0,40}?(?:\b(?:by|for|with|of)\s*)?$AMT""", RegexOption.IGNORE_CASE)
    private val creditAfter = Regex("""\b(credited)\b[^.]{0,40}?(?:\b(?:by|for|with|of)\s*)?$AMT""", RegexOption.IGNORE_CASE)
    private val notFinance = Regex("""\b(otp|offer|cashback|loan of up to|pre-?approved|win|reward points)\b""", RegexOption.IGNORE_CASE)

    /** Cheap pre-filter: every balance and transaction template needs an amount ("rs", "inr" or the rupee sign). */
    fun mightMatch(body: String): Boolean =
        body.contains("rs", ignoreCase = true) || body.contains("inr", ignoreCase = true) || body.contains('₹')

    fun parse(sender: String, body: String): FinanceUpdate? = runCatching { parseUnsafe(sender, body) }.getOrNull()

    private fun parseUnsafe(sender: String, raw: String): FinanceUpdate? {
        val body = raw.take(MAX_INPUT)
        val balance = balances.firstNotNullOfOrNull { it.find(body)?.groupValues?.get(1) }?.let(Money::parse)
        val txn = txnOf(body)
        if (balance == null && txn == null) return null
        if (balance == null && notFinance.containsMatchIn(body)) return null
        val digits = (account.find(body) ?: epfoMember.find(body))?.groupValues?.get(1)?.filter { it.isDigit() } ?: return null
        val mask = "X".repeat(if (digits.length >= 4) 2 else 3) + digits.takeLast(4).takeLast(maxOf(3, minOf(4, digits.length)))
        return FinanceUpdate(Banks.nameFor(sender), mask, balance, txn?.first, txn?.second)
    }

    private fun txnOf(body: String): Pair<String, BigDecimal>? {
        debitBefore.find(body)?.let { m -> Money.parse(m.groupValues[1])?.let { return "DEBIT" to it } }
        creditBefore.find(body)?.let { m -> Money.parse(m.groupValues[1])?.let { return "CREDIT" to it } }
        debitAfter.find(body)?.let { m -> Money.parse(m.groupValues[2])?.let { return "DEBIT" to it } }
        creditAfter.find(body)?.let { m -> Money.parse(m.groupValues[2])?.let { return "CREDIT" to it } }
        return null
    }
}
