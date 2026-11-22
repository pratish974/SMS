package com.dns.sms.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import javax.inject.Singleton

/** Display names and photos from contacts, used only when READ_CONTACTS is granted. Cached per process. */
@Singleton
class ContactNames @Inject constructor(@param:ApplicationContext private val ctx: Context) {
    private class Entry(val name: String, val photo: String?)

    /**
     * Every phone number in the address book, read in ONE query (name + photo thumbnail), keyed by
     * its last 10 digits. Replaces one PhoneLookup per number: with 1,000 personal conversations
     * that cost 2.2 s on every inbox load (perf test, 10 lakh SMS, 2026-11-22).
     */
    @Volatile private var directory: Map<String, Entry>? = null

    private fun directory(): Map<String, Entry> = directory ?: synchronized(this) {
        directory ?: loadDirectory().also { directory = it }
    }

    private fun loadDirectory(): Map<String, Entry> = runCatching {
        val out = HashMap<String, Entry>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val key = keyOf(c.getString(0) ?: continue) ?: continue
                val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val existing = out[key]
                // Same number saved twice: keep the entry that has a photo.
                if (existing == null || (existing.photo == null && c.getString(2) != null)) out[key] = Entry(name, c.getString(2))
            }
        }
        out
    }.getOrDefault(emptyMap())

    /** Last 10 digits (handles +91 / 0 prefixes and formatting); null for non-numbers. */
    private fun keyOf(address: String): String? {
        if (!PhoneNumbers.isPhoneLike(address)) return null
        val digits = address.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits.ifEmpty { null }
    }

    private fun granted() = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun entry(address: String): Entry? {
        if (!granted()) return null
        val key = keyOf(address) ?: return null
        return directory()[key]
    }

    fun nameFor(address: String): String? = entry(address)?.name

    fun displayName(address: String): String = nameFor(address) ?: address

    /** Contact photo thumbnail (as saved in Google/phone contacts) for a number, if it has one. */
    fun photoFor(address: String): Uri? = entry(address)?.photo?.let(Uri::parse)

    fun isSavedContact(address: String): Boolean = nameFor(address) != null

    private val manualInvalidations = MutableStateFlow(0)
    @Volatile private var lastGranted: Boolean? = null

    /** Cheap; call on resume. Recomputes only if the contacts permission was granted or revoked. */
    fun refreshPermission() {
        val granted = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (lastGranted != null && lastGranted != granted) invalidate()
        lastGranted = granted
    }

    /** Call after the contacts permission changes; names and "saved contact" checks are recomputed. */
    fun invalidate() {
        directory = null
        manualInvalidations.value++
    }

    /** Emits now and whenever contacts (or our permission to read them) change. */
    @OptIn(FlowPreview::class)
    fun changes(): Flow<Unit> = combine(contactsObserver().debounceAfterFirst(500.milliseconds), manualInvalidations) { _, _ -> }

    private fun contactsObserver(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { directory = null; trySend(Unit) }
        }
        // Without READ_CONTACTS, registering throws SecurityException. Then we just don't watch;
        // invalidate() (called when the permission is granted) refreshes instead.
        val registered = runCatching {
            ctx.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        }.isSuccess
        trySend(Unit)
        awaitClose { if (registered) ctx.contentResolver.unregisterContentObserver(observer) }
    }
}
