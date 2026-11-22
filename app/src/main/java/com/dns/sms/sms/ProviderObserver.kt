package com.dns.sms.sms

import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri
import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll

private val WATCHED = listOf(
    "content://sms".toUri(),
    "content://mms-sms/".toUri(),
)

/**
 * Emits once immediately, then (debounced) whenever SMS data changes. The first emission is NOT
 * debounced: that used to hold back the inbox's first load by 300 ms on every app start.
 */
@OptIn(FlowPreview::class)
fun ContentResolver.messageChanges(): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { trySend(Unit) }
    }
    // Without the SMS role these throw SecurityException; the gate keeps lists empty then anyway.
    WATCHED.forEach { runCatching { registerContentObserver(it, true, observer) } }
    trySend(Unit)
    awaitClose { unregisterContentObserver(observer) }
}.conflate().debounceAfterFirst(300.milliseconds)

/** Like [debounce], but the first value passes straight through (startup shouldn't wait). */
@OptIn(FlowPreview::class)
fun <T> Flow<T>.debounceAfterFirst(timeout: kotlin.time.Duration): Flow<T> = kotlinx.coroutines.flow.flow {
    var first = true
    emitAll(debounce { if (first) { first = false; kotlin.time.Duration.ZERO } else timeout })
}
