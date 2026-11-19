package com.dns.sms.sms

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import com.dns.sms.classify.SenderKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Android's system block list (shared with the dialer). The default SMS app may read and write it.
 * Blocked numbers are then stopped by the platform; for senders it doesn't match (e.g. alphanumeric
 * ids), [isBlocked] also matches by [SenderKey] so "VM-SPAMCO" covers "AD-SPAMCO-P".
 */
@Singleton
class BlockList @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    private val gate: DefaultSmsGate,
) {
    private fun allowed(): Boolean =
        gate.isDefaultNow() && runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(ctx) }.getOrDefault(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<List<String>> = gate.isDefault.flatMapLatest { ok ->
        if (!ok) flowOf(emptyList()) else changes().map { load() }
    }.flowOn(Dispatchers.IO)

    fun load(): List<String> {
        if (!allowed()) return emptyList()
        return runCatching {
            ctx.contentResolver.query(BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null)
                ?.use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let(::add) } }
        }.getOrNull().orEmpty()
    }

    fun matches(blocked: List<String>, address: String): Boolean {
        val key = SenderKey.of(address)
        return blocked.any { SenderKey.of(it) == key }
    }

    fun isBlocked(address: String): Boolean {
        if (!allowed()) return false
        val system = runCatching { BlockedNumberContract.isBlocked(ctx, address) }.getOrDefault(false)
        return system || matches(load(), address)
    }

    fun block(address: String) {
        if (!allowed() || address.isBlank()) return
        runCatching {
            ctx.contentResolver.insert(BlockedNumbers.CONTENT_URI, ContentValues().apply {
                put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, address)
            })
        }
    }

    /** Removes every block entry that refers to the same sender. */
    fun unblock(address: String) {
        if (!allowed()) return
        val key = SenderKey.of(address)
        load().filter { SenderKey.of(it) == key }.forEach { entry ->
            runCatching { BlockedNumberContract.unblock(ctx, entry) }
        }
    }

    private fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        runCatching { ctx.contentResolver.registerContentObserver(BlockedNumbers.CONTENT_URI, true, observer) }
        trySend(Unit)
        awaitClose { ctx.contentResolver.unregisterContentObserver(observer) }
    }
}
