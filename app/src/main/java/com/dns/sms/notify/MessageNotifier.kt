package com.dns.sms.notify

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.RemoteViews
import com.dns.sms.parse.Money
import com.dns.sms.parse.TransactionAlert
import com.dns.sms.InternalLinkActivity
import com.dns.sms.security.UrlRisk
import com.dns.sms.R
import com.dns.sms.classify.Category
import com.dns.sms.classify.FolderResolver
import com.dns.sms.classify.Folders
import com.dns.sms.classify.OtpDetector
import com.dns.sms.data.SenderOverrideDao
import com.dns.sms.receive.NotificationActionReceiver
import com.dns.sms.settings.AppSettings
import com.dns.sms.sms.ContactNames
import com.dns.sms.sms.ConversationRepository
import com.dns.sms.sms.ConversationThread
import com.dns.sms.sms.Message
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One notification per conversation. Content is hidden on the lock screen (VISIBILITY_PRIVATE).
 * OTPs use their own channel with a "Copy code" action (Requirement.md section 4, OTP).
 */
@Singleton
class MessageNotifier @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    private val repo: ConversationRepository,
    private val contacts: ContactNames,
    private val manager: NotificationManagerCompat,
    private val settings: AppSettings,
    private val overrides: SenderOverrideDao,
    private val threadMetas: com.dns.sms.data.ThreadMetaDao,
    private val shortcuts: ConversationShortcuts,
) {
    fun canPost(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled()

    suspend fun notifyThread(threadId: Long) {
        if (!canPost()) return
        val thread = com.dns.sms.util.Perf.time("notify.thread") { repo.thread(threadId) } ?: return
        if (thread.unread == 0) { cancel(threadId); return }
        // Muted conversation: messages still arrive and count as unread, but no notification.
        if (threadMetas.one(threadId)?.muted == true) { cancel(threadId); return }
        val incoming = repo.loadMessages(threadId, 50).filter { !it.outgoing }.takeLast(thread.unread.coerceIn(1, 7))
        val latest = incoming.lastOrNull() ?: return

        val otp = OtpDetector.extract(latest.body)
        val txn = if (otp == null) TransactionAlert.from(latest.address.orEmpty(), latest.body) else null
        when {
            otp != null -> if (settings.notifyOtp.value) postOtp(thread, latest, otp) else cancel(threadId)
            category(thread) == Category.PROMOTIONS && !settings.notifyPromotional.value -> cancel(threadId)
            !settings.notifyDefault.value -> cancel(threadId)
            txn != null -> postTransaction(thread, latest, txn)
            else -> postConversation(thread, incoming)
        }
    }

    private suspend fun category(t: ConversationThread): Category {
        val key = Folders.keyFor(t)
        val overrideMap = overrides.get(key)?.let { mapOf(key to it.category) }.orEmpty()
        return FolderResolver.threadCategory(t.id, t.addresses, t.snippet, overrideMap, contacts::isSavedContact)
    }

    private fun postConversation(thread: ConversationThread, incoming: List<Message>) {
        val me = Person.Builder().setName(ctx.getString(R.string.you)).build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(if (thread.isGroup) thread.title else null)
            .setGroupConversation(thread.isGroup)
        incoming.forEach { m ->
            val sender = Person.Builder().setName(m.address?.let(contacts::displayName) ?: thread.title).build()
            style.addMessage(textOf(m), m.dateMillis, sender)
        }
        // A long-lived conversation shortcut puts this in Android's "Conversations" section (priority,
        // bubbles, per-conversation settings).
        val shortcutId = shortcuts.publish(thread)
        val n = NotificationCompat.Builder(ctx, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .apply { shortcutId?.let { setShortcutId(it); setLocusId(androidx.core.content.LocusIdCompat(it)) } }
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(CHANNEL_MESSAGES, R.string.new_message))
            .setContentIntent(openThreadIntent(thread.id))
            .setAutoCancel(true)
            .addAction(replyAction(thread.id))
            .addAction(markReadAction(thread.id))
            .build()
        post(thread.id, n)
    }

    /**
     * OTP notification (reference 2026-11-19): the code large, "OTP from <sender>" below, and Copy / Delete
     * buttons right in the notification, so the code can be copied without expanding it. Content stays
     * hidden on the lock screen (public version).
     */
    private fun postOtp(thread: ConversationThread, m: Message, code: String) {
        val copy = messageIntent(NotificationActionReceiver.ACTION_COPY_CODE, thread.id, m)
        val delete = messageIntent(NotificationActionReceiver.ACTION_DELETE_MESSAGE, thread.id, m)
        val view = RemoteViews(ctx.packageName, R.layout.notif_otp).apply {
            setTextViewText(R.id.otp_code, code)
            setTextViewText(R.id.otp_from, ctx.getString(R.string.otp_from, thread.title))
            setOnClickPendingIntent(R.id.otp_copy, copy)
            setOnClickPendingIntent(R.id.otp_delete, delete)
        }
        // The buttons live in the custom view; watches and Auto get the same two as regular actions.
        val wear = NotificationCompat.WearableExtender()
            .addAction(NotificationCompat.Action.Builder(R.drawable.ic_notif_copy, ctx.getString(R.string.copy), copy).build())
            .addAction(NotificationCompat.Action.Builder(R.drawable.ic_notif_delete, ctx.getString(R.string.delete), delete).build())
        val n = NotificationCompat.Builder(ctx, CHANNEL_OTP)
            .setSmallIcon(R.drawable.ic_notification)
            // Title/text stay set for accessibility, watches and the "ongoing" summary.
            .setContentTitle(code)
            .setContentText(ctx.getString(R.string.otp_from, thread.title))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(view)
            .setCustomHeadsUpContentView(view)
            .setCustomBigContentView(view)
            .extend(wear)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(CHANNEL_OTP, R.string.otp_received))
            .setContentIntent(openThreadIntent(thread.id))
            .setAutoCancel(true)
            .setTimeoutAfter(OTP_TIMEOUT_MS)
            .build()
        post(thread.id, n)
    }

    /**
     * Bank transaction (reference 2026-11-24): "- ₹ 3,500 UPI" in red when money left the account,
     * "+ ₹ 200 IMPS" in green when it arrived, "From/To <bank> <account>" below, and a bank icon.
     * Expanded: Mark read and Balance (opens the Finance tab, which keeps its own lock).
     */
    private fun postTransaction(thread: ConversationThread, m: Message, t: TransactionAlert) {
        val sign = if (t.debit) "- " else "+ "
        val amount = sign + Money.formatInr(t.amount)
        val title = SpannableStringBuilder(amount).apply {
            setSpan(ForegroundColorSpan(ctx.getColor(if (t.debit) R.color.notif_debit else R.color.notif_credit)), 0, amount.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, amount.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append("  ").append(t.mode)
        }
        val account = listOf(t.bank, t.mask).filter { it.isNotBlank() }.joinToString(" ")
        val sub = ctx.getString(if (t.debit) R.string.txn_from else R.string.txn_to, account)
        val view = RemoteViews(ctx.packageName, R.layout.notif_txn).apply {
            setTextViewText(R.id.txn_title, title)
            setTextViewText(R.id.txn_account, sub)
        }
        val balance = NotificationCompat.Action.Builder(
            R.drawable.ic_notif_bank, ctx.getString(R.string.balance),
            PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, InternalLinkActivity::class.java).setAction(InternalLinkActivity.ACTION_VIEW_FINANCE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()
        val n = NotificationCompat.Builder(ctx, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(sub)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(view)
            .setCustomHeadsUpContentView(view)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(CHANNEL_MESSAGES, R.string.new_message))
            .setContentIntent(openThreadIntent(thread.id))
            .setWhen(m.dateMillis)
            .setAutoCancel(true)
            .addAction(markReadAction(thread.id))
            .addAction(balance)
            .build()
        post(thread.id, n)
    }

    /** Copy / Delete for one message. The receiver re-reads the message; nothing sensitive rides in the intent. */
    private fun messageIntent(action: String, threadId: Long, m: Message): PendingIntent = PendingIntent.getBroadcast(
        ctx, (threadId.toInt() * 4) + (action.hashCode() and 3),
        Intent(ctx, NotificationActionReceiver::class.java).setAction(action).setPackage(ctx.packageName)
            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            .putExtra(NotificationActionReceiver.EXTRA_MESSAGE_KIND, m.kind.name)
            .putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, m.id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(threadId: Long, n: Notification) {
        try {
            manager.notify(TAG, threadId.toInt(), n)
        } catch (_: SecurityException) {
            // Notification permission revoked between the check and here.
        }
    }

    /** A dangerous link is not shown at all, so a half-awake tap or a smartwatch can't be tricked by it. */
    private fun textOf(m: Message): String {
        val bad = UrlRisk.dangerousLinks(m.address.orEmpty(), m.body)
        if (bad.isEmpty()) return m.body
        val hidden = ctx.getString(R.string.notif_link_hidden)
        val sb = StringBuilder(m.body)
        bad.sortedByDescending { it.first }.forEach { sb.replace(it.first, it.last + 1, hidden) }
        return ctx.getString(R.string.notif_scam_prefix) + sb
    }

    private fun publicVersion(channel: String, title: Int) = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(ctx.getString(title))
        .build()

    private fun replyAction(threadId: Long) = NotificationCompat.Action.Builder(
        R.drawable.ic_notification, ctx.getString(R.string.reply), actionIntent(NotificationActionReceiver.ACTION_REPLY, threadId, mutable = true),
    ).addRemoteInput(RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY).setLabel(ctx.getString(R.string.reply)).build())
        .setAllowGeneratedReplies(false)
        .build()

    private fun markReadAction(threadId: Long) = NotificationCompat.Action.Builder(
        R.drawable.ic_notification, ctx.getString(R.string.mark_read), actionIntent(NotificationActionReceiver.ACTION_MARK_READ, threadId, mutable = false),
    ).build()

    fun cancel(threadId: Long) = manager.cancel(TAG, threadId.toInt())

    fun openThreadIntent(threadId: Long): PendingIntent = PendingIntent.getActivity(
        ctx, threadId.toInt(),
        Intent(ctx, InternalLinkActivity::class.java).setAction(InternalLinkActivity.ACTION_VIEW_THREAD)
            .putExtra(InternalLinkActivity.EXTRA_THREAD_ID, threadId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Reply must be mutable (RemoteInput fills in the text); it is explicit to our non-exported receiver. */
    private fun actionIntent(action: String, threadId: Long, mutable: Boolean): PendingIntent = PendingIntent.getBroadcast(
        ctx, threadId.toInt(),
        Intent(ctx, NotificationActionReceiver::class.java).setAction(action).setPackage(ctx.packageName)
            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId),
        // S16: the reply must stay mutable for RemoteInput; it is also one-shot (the notification is
        // replaced after each reply) and package-locked.
        (if (mutable) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT else PendingIntent.FLAG_IMMUTABLE) or
            PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_OTP = "otp"
        const val CHANNEL_REMINDERS = "reminders"
        private const val TAG = "thread"
        private const val OTP_TIMEOUT_MS = 15 * 60 * 1000L
    }
}
