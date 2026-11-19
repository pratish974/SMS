package com.dns.sms.util

/**
 * Finds links in SMS text so they can be underlined and opened only after the user sees the full
 * address (Requirement.md section 9: never auto-open). Conservative on purpose: a bare "word.word"
 * like "Avbl.Bal" is not a link; it needs a scheme, "www.", or a domain followed by a path.
 */
object LinkFinder {
    private val pattern = Regex(
        """https?://[^\s<>"]+|\bwww\.[^\s<>"]+|\b(?:[a-z0-9-]+\.)+[a-z]{2,}/[^\s<>"]*""",
        RegexOption.IGNORE_CASE,
    )

    fun find(text: String): List<IntRange> = pattern.findAll(text.take(2000)).map { m ->
        // Trailing punctuation belongs to the sentence, not the link.
        var end = m.range.last
        while (end > m.range.first && text[end] in ".,;:!?)'\"") end--
        m.range.first..end
    }.toList()

    /** The address to open: always http(s). */
    fun toUrl(link: String): String =
        if (link.startsWith("http://", true) || link.startsWith("https://", true)) link else "https://$link"

    /**
     * S10: the host as the network sees it. Non-ASCII hosts (look-alike letters, e.g. Cyrillic "а")
     * are shown in punycode ("xn--...") so a fake bank domain can't pass as the real one.
     */
    fun asciiHost(url: String): String? {
        val host = url.substringAfter("://", url).substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').substringBefore(':')
        if (host.isEmpty()) return null
        return runCatching { java.net.IDN.toASCII(host, java.net.IDN.ALLOW_UNASSIGNED) }.getOrNull()
    }

    /** True when the link's host contains non-ASCII characters (possible look-alike). */
    fun isLookalike(url: String): Boolean {
        val host = url.substringAfter("://", url).substringBefore('/').substringBefore('?')
        return host.any { it.code > 127 }
    }
}
