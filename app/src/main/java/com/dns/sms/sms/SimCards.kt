package com.dns.sms.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dual-SIM support (owner decision 2026-11-21). Whether the phone has two SIM slots is known without
 * any permission; listing the SIMs needs READ_PHONE_STATE, asked only when the user taps the SIM button.
 */
@Singleton
class SimCards @Inject constructor(@param:ApplicationContext private val ctx: Context) {
    data class Sim(val subId: Int, val slot: Int, val label: String)

    /** Two or more SIM slots (no permission needed). */
    fun isMultiSim(): Boolean = runCatching { ctx.getSystemService(TelephonyManager::class.java).activeModemCount > 1 }.getOrDefault(false)

    fun hasPermission(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** Active SIMs, in slot order. Empty without the permission. */
    fun active(): List<Sim> {
        if (!hasPermission()) return emptyList()
        return try {
            ctx.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
                .sortedBy { it.simSlotIndex }
                .map { info ->
                    val name = info.displayName?.toString()?.takeIf { it.isNotBlank() } ?: info.carrierName?.toString().orEmpty()
                    Sim(info.subscriptionId, info.simSlotIndex, "SIM ${info.simSlotIndex + 1}" + if (name.isNotBlank()) " · $name" else "")
                }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /** The system's default SIM for SMS, or null when there is none ("ask every time"). */
    fun defaultSmsSubId(): Int? = SubscriptionManager.getDefaultSmsSubscriptionId().takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }

    /** Short label for the composer button, e.g. "SIM 2". */
    fun shortLabel(subId: Int?): String? {
        val sims = active()
        val sim = sims.firstOrNull { it.subId == (subId ?: defaultSmsSubId()) } ?: return null
        return "SIM ${sim.slot + 1}"
    }
}
