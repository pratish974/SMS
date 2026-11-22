package com.dns.sms.notify

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.dns.sms.InternalLinkActivity
import com.dns.sms.sms.ConversationThread
import com.dns.sms.sms.PhoneNumbers
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Long-lived conversation shortcuts (Android 11+ "Conversations" notification section: priority,
 * bubbles, per-conversation settings). Contain the conversation's name and an initials avatar only,
 * never message text. Removed when the conversation is deleted.
 */
@Singleton
class ConversationShortcuts @Inject constructor(@param:ApplicationContext private val ctx: Context) {

    fun id(threadId: Long) = "thread-$threadId"

    /** Publishes (or refreshes) the shortcut and returns its id, or null if the launcher refused. */
    fun publish(t: ConversationThread): String? = runCatching {
        val id = id(t.id)
        val icon = IconCompat.createWithAdaptiveBitmap(avatar(t.title, t.addresses.firstOrNull() ?: t.title))
        val person = Person.Builder().setName(t.title).setIcon(icon).setKey(t.addresses.joinToString(",")).build()
        val shortcut = ShortcutInfoCompat.Builder(ctx, id)
            .setShortLabel(t.title.take(SHORT_LABEL_MAX))
            .setLongLived(true)
            .setIcon(icon)
            .setPerson(person)
            .setLocusId(LocusIdCompat(id))
            .setCategories(setOf(CATEGORY))
            .setIntent(
                Intent(ctx, InternalLinkActivity::class.java).setAction(InternalLinkActivity.ACTION_VIEW_THREAD)
                    .putExtra(InternalLinkActivity.EXTRA_THREAD_ID, t.id),
            )
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(ctx, shortcut)
        id
    }.getOrNull()

    fun remove(threadIds: Collection<Long>) {
        runCatching { ShortcutManagerCompat.removeLongLivedShortcuts(ctx, threadIds.map(::id)) }
    }

    /** Adaptive-icon bitmap: coloured background with initials in the safe zone. */
    private fun avatar(name: String, key: String): Bitmap {
        val size = 216 // 108 dp at xxhdpi-ish; launchers scale it
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        c.drawColor(COLORS[PhoneNumbers.colorIndex(key, COLORS.size)])
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; textSize = size * 0.26f; isFakeBoldText = true
        }
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(PhoneNumbers.initials(name), size / 2f, y, p)
        return bmp
    }

    private companion object {
        const val CATEGORY = "com.dns.sms.category.CONVERSATION"
        const val SHORT_LABEL_MAX = 25
        val COLORS = intArrayOf( // same palette as the in-app avatars (ui/Avatar.kt)
            0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFEF6C00.toInt(), 0xFF8E24AA.toInt(),
            0xFF43A047.toInt(), 0xFFD81B60.toInt(), 0xFF1E88E5.toInt(), 0xFF6D4C41.toInt(),
        )
    }
}
