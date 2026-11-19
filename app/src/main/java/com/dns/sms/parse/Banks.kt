package com.dns.sms.parse

import com.dns.sms.classify.SenderKey

/** Short bank names by DLT sender brand. Unknown senders fall back to the brand itself. */
object Banks {
    private val byBrand = mapOf(
        "HDFCBK" to "HDFC", "HDFCBN" to "HDFC",
        "ICICIB" to "ICICI", "ICICIT" to "ICICI", "ICICIO" to "ICICI",
        "SBIINB" to "SBI", "SBIPSG" to "SBI", "SBIUPI" to "SBI", "CBSSBI" to "SBI", "SBICRD" to "SBI Card",
        "AXISBK" to "Axis", "AXISMR" to "Axis",
        "KOTAKB" to "Kotak", "KOTAKM" to "Kotak",
        "PNBSMS" to "PNB", "BOIIND" to "BOI", "CANBNK" to "Canara", "UNIONB" to "Union Bank",
        "IDFCFB" to "IDFC First", "INDUSB" to "IndusInd", "YESBNK" to "Yes Bank", "FEDBNK" to "Federal",
        "AUBANK" to "AU Bank", "BOBTXN" to "Bank of Baroda", "BOBSMS" to "Bank of Baroda",
        "NKGSBK" to "NKGSB", "EPFOHO" to "EPFO", "PAYTMB" to "Paytm Bank", "AIRBNK" to "Airtel Bank",
        "IPBMSG" to "India Post", "SCBANK" to "StanChart", "CITIBK" to "Citi", "HSBCIN" to "HSBC",
    )

    fun nameFor(sender: String): String {
        val key = SenderKey.of(sender)
        return byBrand[key] ?: key
    }

    fun isKnown(sender: String): Boolean = SenderKey.of(sender) in byBrand
}
