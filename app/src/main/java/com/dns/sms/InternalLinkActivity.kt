package com.dns.sms

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.dns.sms.ui.NavRequest

/**
 * Non-exported entry for our own PendingIntents and launcher shortcuts. MainActivity is exported, so it
 * cannot trust any action or extra it receives. This activity can only be started by our own app (or by the
 * system on behalf of a PendingIntent/shortcut we created), so it records the destination in memory
 * ([InternalNav]) and starts MainActivity with a plain intent. Another app can reach neither this
 * activity nor our process memory, so it cannot forge an internal destination.
 */
class InternalLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = when (intent?.action) {
            ACTION_VIEW_THREAD -> intent.getLongExtra(EXTRA_THREAD_ID, -1).takeIf { it > 0 }?.let { NavRequest.Thread(it) }
            ACTION_VIEW_REMINDERS -> NavRequest.Reminders
            ACTION_VIEW_SCHEDULED -> NavRequest.Scheduled
            else -> null
        }
        request?.let(InternalNav::post)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    companion object {
        const val ACTION_VIEW_THREAD = "com.dns.sms.VIEW_THREAD"
        const val ACTION_VIEW_REMINDERS = "com.dns.sms.VIEW_REMINDERS"
        const val ACTION_VIEW_SCHEDULED = "com.dns.sms.VIEW_SCHEDULED"
        const val EXTRA_THREAD_ID = "thread_id"
    }
}

/** One pending in-process navigation request; expires quickly so a stale one can't surface later. */
object InternalNav {
    private const val TTL_MS = 5_000L
    private var pending: NavRequest? = null
    private var postedAt = 0L

    @Synchronized fun post(r: NavRequest) { pending = r; postedAt = System.currentTimeMillis() }

    @Synchronized fun take(): NavRequest? {
        val r = pending?.takeIf { System.currentTimeMillis() - postedAt <= TTL_MS }
        pending = null
        return r
    }
}
