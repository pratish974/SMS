package com.dns.sms.security

import com.dns.sms.classify.OtpDetector
import com.dns.sms.sms.PhoneNumbers
import com.dns.sms.util.LinkFinder

/**
 * Offline link-risk heuristics for SMS (phishing is the main SMS scam in India). Pure Kotlin, no Android
 * deps. It never fetches anything: it only looks at the address, the sender and the wording. Input is
 * capped and the regexes are simple alternations, like [com.dns.sms.classify.RuleClassifier].
 */
object UrlRisk {
    private const val MAX_INPUT = 2000

    enum class Level { SAFE, UNKNOWN, SUSPICIOUS, DANGEROUS }

    /** [severity]: 2 dangerous on its own, 1 suspicious on its own, 0 only strengthens other signals. */
    enum class Reason(val severity: Int) {
        BRAND_IMPERSONATION(2), IP_OR_PORT(2), USERINFO_TRICK(2), LOOKALIKE(2), HIDDEN_CHARS(2),
        SENDER_DOMAIN_MISMATCH(1), SHORTENER(1), RISKY_TLD(1), UNUSUAL_SHAPE(1), UNVERIFIED_SENDER(1),
        NOT_HTTPS(0), SCAM_WORDING(0),
    }

    data class Assessment(val level: Level, val reasons: List<Reason>)

    private val genuine = listOf(
        "hdfcbank.com", "sbi.co.in", "onlinesbi.sbi", "icicibank.com", "axisbank.com", "kotak.com", "yesbank.in",
        "pnbindia.in", "bankofbaroda.in", "canarabank.com", "unionbankofindia.co.in", "idfcfirstbank.com",
        "indusind.com", "paytm.com", "phonepe.com", "amazon.in", "amazon.com", "flipkart.com", "myntra.com",
        "swiggy.com", "zomato.com", "airtel.in", "jio.com", "vi.in", "bsnl.co.in", "irctc.co.in", "gov.in",
        "nic.in", "uidai.gov.in", "incometax.gov.in", "npci.org.in", "google.com", "youtube.com", "whatsapp.com",
        "apple.com", "microsoft.com", "lic.co.in", "bookmyshow.com", "makemytrip.com", "uber.com", "olacabs.com",
        "meesho.com", "nykaa.com", "ajio.com", "sbicard.com", "hdfcsec.com", "zerodha.com", "groww.in",
    )

    /** Keywords of brands that scammers imitate. Short ones must start a host word ("axis" must not match "praxis"). */
    private val brands = listOf(
        "hdfc", "sbi", "icici", "axis", "kotak", "paytm", "phonepe", "amazon", "flipkart", "airtel", "jio",
        "irctc", "uidai", "aadhaar", "incometax", "npci", "gpay", "googlepay", "bescom", "msedcl",
        "electricity", "kyc", "lic",
    )
    private const val SHORT_BRAND = 5

    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "cutt.ly", "t.co", "goo.gl", "is.gd", "rb.gy", "shorturl.at", "tiny.cc", "ow.ly",
        "s.id", "rebrand.ly", "bit.do", "t.ly",
    )

    private val riskyTlds = setOf(
        "xyz", "top", "click", "tk", "ml", "ga", "cf", "gq", "icu", "buzz", "rest", "monster", "cyou", "sbs", "cfd",
        "lol", "work", "live", "shop", "online", "site", "fun", "info",
    )

    private val senderId = Regex("""^[A-Z]{2}-[A-Z0-9]{3,12}(-[PSTG])?$""")
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val hiddenChars = Regex("[\u202A-\u202E\u2066-\u2069\u200E\u200F\u200B-\u200D\u2060\uFEFF]")
    private val scamWords = Regex(
        """\b(kyc|blocked|suspended|update pan|pan card|aadhaar update|electricity.{0,30}(cut|disconnect)|reward|claim|lottery|won|refund|verify your account|click (here|the link)|urgent|expire)""",
        RegexOption.IGNORE_CASE,
    )
    private val bankWords = Regex("""\b(account|a/c|kyc|debit|credit card|bank)""", RegexOption.IGNORE_CASE)

    fun hasHidden(text: String): Boolean = hiddenChars.containsMatchIn(text.take(MAX_INPUT))

    fun stripHidden(text: String): String = hiddenChars.replace(text, "")

    fun assess(sender: String, body: String, url: String): Assessment {
        val raw = url.take(MAX_INPUT)
        val from = sender.take(MAX_INPUT)
        val text = body.take(MAX_INPUT)
        val reasons = linkedSetOf<Reason>()
        if (hasHidden(raw)) reasons += Reason.HIDDEN_CHARS
        val clean = stripHidden(raw)
        val https = clean.startsWith("https://", true)
        val afterScheme = clean.substringAfter("://", clean)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if ('@' in authority) reasons += Reason.USERINFO_TRICK
        val hostPort = authority.substringAfterLast('@')
        val port = if (hostPort.startsWith("[")) hostPort.substringAfter("]:", "") else hostPort.substringAfter(':', "")
        val rawHost = hostPort.substringBefore(':').lowercase()
        val host = (LinkFinder.asciiHost(clean) ?: rawHost).lowercase().trimEnd('.')

        if (LinkFinder.isLookalike(clean) || host.split('.').any { it.startsWith("xn--") }) reasons += Reason.LOOKALIKE
        val isIp = hostPort.startsWith("[") || ipv4.matches(host) || (host.isNotEmpty() && host.all { it.isDigit() })
        if (isIp || (port.isNotEmpty() && port != "80" && port != "443")) reasons += Reason.IP_OR_PORT
        if (!https) reasons += Reason.NOT_HTTPS

        val isGenuine = !isIp && genuine.any { host == it || host.endsWith(".$it") }
        val labels = host.split('.')
        if (!isGenuine) {
            if (hasBrandWord(host)) reasons += Reason.BRAND_IMPERSONATION
            if (shorteners.any { host == it }) reasons += Reason.SHORTENER
            if (labels.size > 1 && labels.last() in riskyTlds) reasons += Reason.RISKY_TLD
            if (senderBrandMismatch(from)) reasons += Reason.SENDER_DOMAIN_MISMATCH
        }
        if (clean.length > 200 || labels.size - 2 > 4) reasons += Reason.UNUSUAL_SHAPE
        if (PhoneNumbers.isPhoneLike(from) && from.count { it.isDigit() } >= 10 && bankWords.containsMatchIn(text)) {
            reasons += Reason.UNVERIFIED_SENDER
        }
        val scam = !isGenuine && scamWords.containsMatchIn(text)
        if (scam) reasons += Reason.SCAM_WORDING

        val worst = reasons.maxOfOrNull { it.severity } ?: 0
        var level = when {
            worst >= 2 -> Level.DANGEROUS
            worst == 1 -> Level.SUSPICIOUS
            isGenuine -> Level.SAFE
            else -> Level.UNKNOWN
        }
        if (scam) level = when (level) {
            Level.UNKNOWN -> Level.SUSPICIOUS
            Level.SUSPICIOUS -> Level.DANGEROUS
            else -> level
        }
        return Assessment(level, reasons.toList())
    }

    /** Ranges (in [body]) of links that assess as dangerous; used to hide them in notifications. */
    fun dangerousLinks(sender: String, body: String): List<IntRange> =
        LinkFinder.find(body).filter { assess(sender, body, LinkFinder.toUrl(body.substring(it))).level == Level.DANGEROUS }

    /** Words of the host: "hdfc-kyc-update.xyz" -> hdfc, kyc, update, xyz. */
    private fun hasBrandWord(host: String): Boolean {
        val words = host.split('.', '-', '_').filter { it.isNotEmpty() }
        return brands.any { b ->
            if (b.length >= SHORT_BRAND) host.contains(b)
            else if (b == "lic") words.any { it == b }
            else words.any { it.startsWith(b) }
        }
    }

    /** An official-looking sender id ("VM-HDFCBK-S") whose brand is one we know, so its links should be that brand's. */
    private fun senderBrandMismatch(sender: String): Boolean {
        val s = sender.trim().uppercase()
        if (!senderId.matches(s)) return false
        val name = s.substringAfter('-').removeSuffix("-P").removeSuffix("-S").removeSuffix("-T").removeSuffix("-G").lowercase()
        return brands.any { it != "kyc" && (if (it.length >= SHORT_BRAND) name.contains(it) else name.startsWith(it)) }
    }
}

/** Message-level scam patterns that don't depend on a link. */
object ScamSignals {
    private val ask = Regex("""\b(share|call|agent|executive|refund|tell)\b""", RegexOption.IGNORE_CASE)
    private val negation = Regex("""\b(do not|don.t|dont|never|not to|kindly not to)\s+(share|tell|disclose|give|reveal)\b|\bnever (ask|call)""", RegexOption.IGNORE_CASE)

    /** Someone is being asked to hand over a one-time code. Normal OTPs say "do not share", which is skipped. */
    fun otpScam(body: String): Boolean {
        val t = body.take(1000)
        return OtpDetector.extract(t) != null && ask.containsMatchIn(t) && !negation.containsMatchIn(t)
    }
}
