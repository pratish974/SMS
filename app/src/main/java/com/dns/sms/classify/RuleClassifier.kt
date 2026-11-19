package com.dns.sms.classify

/**
 * Rule-based classifier, Requirement.md section 7. Pure Kotlin, no Android deps.
 *
 * Order: user override -> saved contact -> sender-id suffix -> keywords -> default.
 * Regexes are plain alternations (no nested quantifiers) and input is capped,
 * so hostile message bodies cannot cause catastrophic backtracking.
 */
object RuleClassifier {

    const val MAX_INPUT_LENGTH = 1000

    private val transactionalKeywords = Regex(
        """\b(otp|one[- ]time password|verification code|debited|credited|a/c|avbl\.? bal|upi|txn)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val promotionalKeywords = Regex(
        """\b(offers?|sale|cashback|t&c)\b|%\s?off\b""",
        RegexOption.IGNORE_CASE,
    )

    private val numericSender = Regex("""\+?[0-9][0-9 \-]*""")

    fun classify(
        sender: String,
        body: String,
        isSavedContact: Boolean = false,
        override: Category? = null,
    ): Category {
        override?.let { return it }
        if (isSavedContact) return Category.PERSONAL

        val address = sender.take(MAX_INPUT_LENGTH).trim()
        suffixCategory(address)?.let { return it }

        val text = body.take(MAX_INPUT_LENGTH)
        if (transactionalKeywords.containsMatchIn(text)) return Category.TRANSACTIONS
        if (promotionalKeywords.containsMatchIn(text)) return Category.PROMOTIONS

        return if (numericSender.matches(address)) Category.PERSONAL else Category.TRANSACTIONS
    }

    /** Indian DLT sender ids end in a type letter, e.g. "AD-AMAZON-P", "VM-HDFCBK-S". */
    private fun suffixCategory(address: String): Category? {
        val dash = address.lastIndexOf('-')
        if (dash <= 0 || dash != address.length - 2) return null
        return when (address.last().uppercaseChar()) {
            'P' -> Category.PROMOTIONS
            'S', 'T', 'G' -> Category.TRANSACTIONS
            else -> null
        }
    }
}
