package com.dns.sms.receive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.dns.sms.di.entry
import com.dns.sms.classify.OtpDetector
import com.dns.sms.intent.ComposeIntentParser
import com.dns.sms.sms.MessageKind
import com.dns.sms.util.SensitiveClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reply and Mark read from a message notification. Not exported. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1).takeIf { it > 0 } ?: return
        val app = context.entry()
        when (intent.action) {
            ACTION_REPLY -> {
                // S16: only the RemoteInput text is read from the fill-in; it is trimmed and capped.
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
                    ?.let(ComposeIntentParser::cleanBody) ?: return
                runAsync {
                    app.outbox().sendToThread(threadId, text)
                    app.repo().markThreadRead(threadId)
                    app.notifier().cancel(threadId)
                }
            }
            ACTION_COPY_CODE -> {
                // Only act on a well-formed request (known message kind + id).
                intent.getStringExtra(EXTRA_MESSAGE_KIND)?.takeIf { k -> MessageKind.entries.any { it.name == k } } ?: return
                val id = intent.getLongExtra(EXTRA_MESSAGE_ID, -1).takeIf { it > 0 } ?: return
                runAsync {
                    // Re-read the message rather than trusting a code carried in the PendingIntent.
                    val repo = app.repo()
                    val m = repo.loadByIds(listOf(id))
                        .firstOrNull() ?: return@runAsync
                    val code = OtpDetector.extract(m.body) ?: return@runAsync
                    withContext(Dispatchers.Main) { SensitiveClipboard.copy(context, code) }
                    repo.markThreadRead(threadId)
                    app.notifier().cancel(threadId)
                    if (app.settings().deleteOtpAfterCopy.value) app.actions().deleteMessage(m)
                }
            }
            ACTION_DELETE_MESSAGE -> {
                // Only act on a well-formed request (known message kind + id).
                intent.getStringExtra(EXTRA_MESSAGE_KIND)?.takeIf { k -> MessageKind.entries.any { it.name == k } } ?: return
                val id = intent.getLongExtra(EXTRA_MESSAGE_ID, -1).takeIf { it > 0 } ?: return
                runAsync {
                    val repo = app.repo()
                    val m = repo.loadByIds(listOf(id))
                        .firstOrNull() ?: return@runAsync
                    // Only messages in the thread this notification was for.
                    if (m.threadId != threadId) return@runAsync
                    app.actions().deleteMessage(m)
                    app.notifier().cancel(threadId)
                }
            }
            ACTION_MARK_READ -> runAsync {
                app.repo().markThreadRead(threadId)
                app.notifier().cancel(threadId)
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.dns.sms.REPLY"
        const val ACTION_MARK_READ = "com.dns.sms.MARK_READ"
        const val ACTION_COPY_CODE = "com.dns.sms.COPY_CODE"
        const val ACTION_DELETE_MESSAGE = "com.dns.sms.DELETE_MESSAGE"
        const val EXTRA_MESSAGE_KIND = "message_kind"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_THREAD_ID = "thread_id"
        const val KEY_REPLY = "reply_text"
    }
}
