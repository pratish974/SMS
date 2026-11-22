package com.dns.sms.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A person you can message: a saved contact's number, or an address from a past conversation. */
data class ContactHit(
    val name: String?,
    val number: String,
    val typeLabel: String?,
    val photo: Uri?,
) {
    val display: String get() = name ?: number
}

/** New message recipient search: saved contacts first, then recent conversations. */
@Singleton
class ContactSearch @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    private val repo: ConversationRepository,
    private val contacts: ContactNames,
) {
    fun hasPermission(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun search(query: String, limit: Int = 60): List<ContactHit> = withContext(Dispatchers.IO) {
        val q = query.trim().take(64)
        if (q.isEmpty()) return@withContext emptyList()
        val saved = if (hasPermission()) savedContacts(q) else emptyList()
        val fromThreads = com.dns.sms.util.Perf.time("contactSearch.loadThreads") { repo.loadThreads() }
            .filter { !it.isGroup }
            .mapNotNull { t -> t.addresses.firstOrNull() }
            .filter { a -> saved.none { PhoneNumbers.same(it.number, a) } }
            .map { a -> ContactHit(contacts.nameFor(a), a, null, null) }
        (saved + fromThreads)
            .mapNotNull { h -> ContactMatch.rank(h.name, h.number, q)?.let { r -> r to h } }
            .sortedWith(compareBy({ it.first }, { it.second.display.lowercase() }))
            .map { it.second }
            .distinctBy { PhoneNumbers.normalize(it.number) }
            .take(limit)
    }

    /** People from your most recent one-to-one conversations. */
    suspend fun recents(limit: Int = 12): List<ContactHit> = withContext(Dispatchers.IO) {
        repo.loadThreads().asSequence()
            .filter { !it.isGroup }
            .mapNotNull { it.addresses.firstOrNull() }
            .filter { PhoneNumbers.isPhoneLike(it) }
            .take(limit)
            .map { a -> ContactHit(contacts.nameFor(a), a, null, contacts.photoFor(a)) }
            .toList()
    }

    private fun savedContacts(q: String): List<ContactHit> = runCatching {
        val uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(q))
        ctx.contentResolver.query(
            uri,
            arrayOf(Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.PHOTO_THUMBNAIL_URI),
            null, null, "${Phone.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext() && size < 200) {
                    val number = c.getString(1) ?: continue
                    val label = Phone.getTypeLabel(ctx.resources, c.getInt(2), c.getString(3)).toString()
                    add(ContactHit(c.getString(0), PhoneNumbers.normalize(number), label, c.getString(4)?.let(Uri::parse)))
                }
            }
        }
    }.getOrNull().orEmpty()
}
