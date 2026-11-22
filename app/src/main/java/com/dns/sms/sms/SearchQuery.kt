package com.dns.sms.sms

/** Builds a safe SQL LIKE pattern from user input: wildcards are escaped, length capped. */
object SearchQuery {
    const val MAX_LENGTH = 100
    const val ESCAPE = '\\'

    fun normalize(raw: String): String? = raw.trim().take(MAX_LENGTH).takeIf { it.length >= 2 }

    fun likePattern(query: String): String {
        val sb = StringBuilder("%")
        for (c in query) {
            if (c == '%' || c == '_' || c == ESCAPE) sb.append(ESCAPE)
            sb.append(c)
        }
        return sb.append('%').toString()
    }

    /**
     * FTS4 MATCH expression: every word becomes a quoted prefix phrase ("rs*"), joined by spaces (AND),
     * so "rs 432" finds "Rs 4321". Punctuation splits a word the way the unicode61 tokenizer does
     * (combining marks of Hindi and other scripts stay inside a word); null when no word is left, so
     * the caller uses the LIKE path.
     */
    fun ftsMatch(query: String): String? =
        query.split(Regex("\\s+")).map { w -> tokens(w) }.filter { it.isNotEmpty() }
            .joinToString(" ") { "\"" + it.joinToString(" ") + "*\"" }
            .takeIf { it.isNotEmpty() }

    private fun isTokenChar(c: Char): Boolean =
        c.isLetterOrDigit() || Character.getType(c).let {
            it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
        }

    private fun tokens(word: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (c in word) {
            if (isTokenChar(c)) sb.append(c) else if (sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }
}
