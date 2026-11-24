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
 * Dual-SIM support (owner decision 2026-11-21). Physical SIMs and eSIMs are both "active subscriptions",
 * so a phone with one physical SIM + one eSIM lists two. Whether the phone has two SIM slots is known
 * without any permission; listing the SIMs needs READ_PHONE_STATE, asked only when the user needs it.
 */
@Singleton
class SimCards @Inject constructor(@param:ApplicationContext private val ctx: Context) {
    /**
     * [carrier] is the SIM's display name ("Vi", "Jio"), or blank. [number] is the SIM's own number when
     * the phone exposes it (often it doesn't), else blank.
     */
    data class Sim(val subId: Int, val slot: Int, val carrier: String, val number: String) {
        /** "SIM 1 · Vi" */
        val label: String get() = "SIM ${slot + 1}" + if (carrier.isNotBlank()) " · $carrier" else ""
        /** "Vi" or "SIM 2" (the settings list, like the reference). */
        val shortName: String get() = carrier.ifBlank { "SIM ${slot + 1}" }
    }

    /** Two or more SIM slots (no permission needed). */
    fun isMultiSim(): Boolean = slotCount() > 1

    /** SIM slots / modems on this phone, physical or eSIM (no permission needed). */
    fun slotCount(): Int = runCatching { ctx.getSystemService(TelephonyManager::class.java).activeModemCount }.getOrDefault(1)

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
                    @Suppress("DEPRECATION") // Only a hint for the phone-number field; blank when not exposed.
                    val number = runCatching { info.number.orEmpty() }.getOrDefault("")
                    Sim(info.subscriptionId, info.simSlotIndex, name, number)
                }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /** The system's default SIM for SMS, or null when there is none ("ask every time"). */
    fun defaultSmsSubId(): Int? = SubscriptionManager.getDefaultSmsSubscriptionId().takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }

    /**
     * The SIM to send with when a conversation has no SIM of its own: the one picked in Settings > Default SIM
     * if it is still in the phone, else the system default. [appDefault] < 0 means "use the phone's setting".
     */
    fun preferredSubId(appDefault: Int): Int? {
        if (appDefault >= 0 && (!hasPermission() || active().any { it.subId == appDefault })) return appDefault
        return defaultSmsSubId()
    }

    /** Short label for the composer button, e.g. "SIM 2". */
    fun shortLabel(subId: Int?): String? {
        val sims = active()
        val sim = sims.firstOrNull { it.subId == (subId ?: defaultSmsSubId()) } ?: return null
        return "SIM ${sim.slot + 1}"
    }
}
