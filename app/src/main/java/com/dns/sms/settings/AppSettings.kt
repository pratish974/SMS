package com.dns.sms.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LaunchFolder { LAST_RECEIVED, PERSONAL, TRANSACTIONS, PROMOTIONS }
enum class FontScale(val factor: Float) { SMALL(0.9f), NORMAL(1f), LARGE(1.15f) }
enum class SwipeAction { DELETE, MARK_READ, ARCHIVE, NONE }
enum class AutoBackup { MANUAL, DAILY, WEEKLY, MONTHLY }

/** How long to keep a class of messages before the auto-delete rule removes it. */
enum class Retention(val days: Int) { NEVER(0), DAY_1(1), DAYS_3(3), WEEK_1(7), MONTH_1(30) }

/**
 * User settings (Requirement.md section 4). Plain SharedPreferences: no message content lives here.
 * Each setting is a live [Setting] so screens and workers react to changes.
 */
@Singleton
class AppSettings @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    inner class Setting<T>(private val key: String, private val default: T, read: (String, T) -> T, private val write: SharedPreferences.Editor.(String, T) -> Unit) {
        private val state = MutableStateFlow(read(key, default))
        val flow: StateFlow<T> = state.asStateFlow()
        var value: T
            get() = state.value
            set(v) { sp.edit { write(key, v) }; state.value = v }
        fun reset() { sp.edit { remove(key) }; state.value = default }
    }

    private fun bool(key: String, default: Boolean) =
        Setting(key, default, { k, d -> sp.getBoolean(k, d) }, { k, v -> putBoolean(k, v) })
    /** Text settings (all default to empty). */
    private fun string(key: String) =
        Setting(key, "", { k, d -> sp.getString(k, d) ?: d }, { k, v -> putString(k, v) })
    private inline fun <reified E : Enum<E>> enum(key: String, default: E) = Setting(
        key, default,
        { k, d -> sp.getString(k, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: d },
        { k, v -> putString(k, v.name) },
    )

    // General
    val signature = string("signature")
    val launchFolder = enum("launch_folder", LaunchFolder.LAST_RECEIVED)
    val fontScale = enum("font_scale", FontScale.NORMAL)
    /** null-like "follow system" is represented by [use24hFollowSystem]. */
    val use24h = bool("use_24h", false)
    val use24hFollowSystem = bool("use_24h_follow_system", true)

    // Notifications
    val notifyDefault = bool("notify_default", true)
    val notifyPromotional = bool("notify_promotional", false)
    val notifyOtp = bool("notify_otp", true)
    val notifyReminders = bool("notify_reminders", true)

    // OTP
    val deleteOtpAfterCopy = bool("otp_delete_after_copy", false)

    // Messaging
    /** This phone's number; with several SIMs, the number of SIM 1 (see [ownNumberFor]). */
    val ownNumber = string("own_number")
    private val slotNumbers = HashMap<Int, Setting<String>>()
    /** The number of the SIM in [slot] (0-based), typed by the user. Slot 0 keeps the single-SIM key. */
    @Synchronized fun ownNumberFor(slot: Int): Setting<String> =
        if (slot <= 0) ownNumber else slotNumbers.getOrPut(slot) { string("own_number_slot_$slot") }
    /** Subscription id of the SIM new conversations send from; -1 = the phone's default SMS SIM. */
    val defaultSubId = Setting("default_sub_id", -1, { k, d -> sp.getInt(k, d) }, { k, v -> putInt(k, v) })
    val showQuickReplies = bool("show_quick_replies", true)
    /** Editable quick replies (Settings > Quick replies); defaults from resources. */
    val quickReplies = Setting(
        "quick_replies",
        ctx.resources.getStringArray(com.dns.sms.R.array.quick_replies).toList(),
        { k, d -> sp.getString(k, null)?.split(SEP)?.filter { it.isNotBlank() } ?: d },
        { k, v -> putString(k, v.filter { it.isNotBlank() }.joinToString(SEP)) },
    )

    // Swipes
    val changeTabsOnSwipe = bool("change_tabs_on_swipe", false)
    val leftSwipe = enum("left_swipe", SwipeAction.DELETE)
    val rightSwipe = enum("right_swipe", SwipeAction.MARK_READ)

    // Finance
    val financeSecurity = bool("finance_security", true)

    // Message rules: explicit opt-in, default Never (Requirement.md section 4).
    val deleteOtpAfter = enum("rule_otp", Retention.NEVER)
    val deletePromotionalAfter = enum("rule_promotional", Retention.NEVER)
    val deleteBlockedAfter = enum("rule_blocked", Retention.NEVER)

    // Backup
    val autoBackup = enum("auto_backup", AutoBackup.MANUAL)
    val lastBackupAt = Setting("last_backup_at", 0L, { k, d -> sp.getLong(k, d) }, { k, v -> putLong(k, v) })
    val lastBackupBytes = Setting("last_backup_bytes", 0L, { k, d -> sp.getLong(k, d) }, { k, v -> putLong(k, v) })
    val lastBackupError = string("last_backup_error")
    val driveAccount = string("drive_account")

    private companion object { const val SEP = "\u001F" }
}
