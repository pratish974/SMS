package com.dns.sms.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/** SmsManager for a subscription, or the default one when [subId] is unknown. */
@Suppress("DEPRECATION")
fun Context.smsManager(subId: Int?): SmsManager {
    val sub = subId?.takeIf { it >= 0 }
    if (Build.VERSION.SDK_INT >= 31) {
        val base = getSystemService(SmsManager::class.java)
        return if (sub != null) base.createForSubscriptionId(sub) else base
    }
    return if (sub != null) SmsManager.getSmsManagerForSubscriptionId(sub) else SmsManager.getDefault()
}

object Columns {
    const val SUB_ID = "sub_id"
}
