package com.dns.sms.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import com.dns.sms.R

/**
 * Copies a one-time code. Marked sensitive so Android 13+ hides it from the clipboard preview and
 * keyboards don't keep it in suggestions/history (Requirement.md section 9).
 */
object SensitiveClipboard {
    fun copy(ctx: Context, text: String) {
        val clip = ClipData.newPlainText(ctx.getString(R.string.otp_clip_label), text).apply {
            // ClipDescription.EXTRA_IS_SENSITIVE (API 33); older versions simply ignore the extra.
            description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(clip)
        // Android 13+ shows its own copy confirmation.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, R.string.code_copied, Toast.LENGTH_SHORT).show()
        scheduleClear(ctx.applicationContext, cm.primaryClipDescription?.timestamp)
    }

    /**
     * S13: one minute later, remove the code from the clipboard, but only if the clipboard still holds
     * exactly this copy (same label and timestamp), so nothing the user copied afterwards is wiped.
     * Android only lets the app read the clipboard while it is on screen; if it isn't, the check
     * can't be made and the clip is left (Android 13+ hides it and clears the clipboard itself).
     */
    private fun scheduleClear(app: Context, stamp: Long?) {
        if (stamp == null) return
        val label = app.getString(R.string.otp_clip_label)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching {
                val cm = app.getSystemService(ClipboardManager::class.java)
                val d = cm.primaryClipDescription ?: return@runCatching
                if (d.label == label && d.timestamp == stamp) cm.clearPrimaryClip()
            }
        }, CLEAR_AFTER_MS)
    }

    private const val CLEAR_AFTER_MS = 60_000L
}
