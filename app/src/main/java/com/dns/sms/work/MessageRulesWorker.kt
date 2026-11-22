package com.dns.sms.work

import android.content.ContentResolver
import android.content.Context
import android.provider.Telephony
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dns.sms.classify.Category
import com.dns.sms.classify.FolderResolver
import com.dns.sms.classify.OtpDetector
import com.dns.sms.data.MessageMetaDao
import com.dns.sms.data.SenderOverrideDao
import com.dns.sms.settings.AppSettings
import com.dns.sms.settings.Retention
import com.dns.sms.sms.BlockList
import com.dns.sms.sms.ContactNames
import com.dns.sms.sms.ConversationRepository
import com.dns.sms.sms.DefaultSmsGate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Settings > Message rules: deletes old OTP, promotional and blocked messages. Every rule defaults to
 * Never (explicit opt-in). Starred messages and saved contacts are never touched.
 */
@HiltWorker
class MessageRulesWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val gate: DefaultSmsGate,
    private val settings: AppSettings,
    private val repo: ConversationRepository,
    private val resolver: ContentResolver,
    private val metas: MessageMetaDao,
    private val contacts: ContactNames,
    private val blockList: BlockList,
    private val overrides: SenderOverrideDao,
    private val ftsDao: com.dns.sms.data.MessageFtsDao,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!gate.isDefaultNow()) return Result.success()
        val now = System.currentTimeMillis()
        val stars = metas.starred()
        val starredSms = stars.filter { it.kind == "sms" }.map { it.messageId }.toSet()

        settings.deleteOtpAfter.value.cutoff(now)?.let { cutoff ->
            val ids = ArrayList<Long>()
            resolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("_id", "address", "body"), "date < ?", arrayOf(cutoff.toString()), null)
                ?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        if (id in starredSms || contacts.isSavedContact(c.getString(1).orEmpty())) continue
                        if (OtpDetector.isOtp(c.getString(2).orEmpty())) ids += id
                    }
                }
            deleteIds(ids, starredSms)
        }

        val threads = repo.loadThreads()
        settings.deletePromotionalAfter.value.cutoff(now)?.let { cutoff ->
            val overrideMap = overrides.all().associate { it.address to it.category }
            val promo = threads.filter { t ->
                !t.isGroup && FolderResolver.threadCategory(t.id, t.addresses, t.snippet, overrideMap, contacts::isSavedContact) == Category.PROMOTIONS
            }.map { it.id }
            deleteInThreads(promo, cutoff, starredSms)
        }
        settings.deleteBlockedAfter.value.cutoff(now)?.let { cutoff ->
            val blocked = blockList.load()
            val ids = threads.filter { t -> !t.isGroup && t.addresses.firstOrNull()?.let { blockList.matches(blocked, it) } == true }.map { it.id }
            deleteInThreads(ids, cutoff, starredSms)
        }
        return Result.success()
    }

    private fun Retention.cutoff(now: Long): Long? = if (this == Retention.NEVER) null else now - days * DAY_MS

    /** Old messages of [threadIds]: ids are read per thread chunk, starred ones filtered in Kotlin, then deleted. */
    private suspend fun deleteInThreads(threadIds: List<Long>, cutoff: Long, starredSms: Set<Long>) {
        var deleted = 0
        var failed = 0
        threadIds.chunked(THREAD_CHUNK).forEach { chunk ->
            val old = ArrayList<Long>()
            runCatching {
                resolver.query(
                    Telephony.Sms.CONTENT_URI, arrayOf("_id"),
                    "thread_id IN (${chunk.joinToString(",")}) AND date < ?", arrayOf(cutoff.toString()), null,
                )?.use { c -> while (c.moveToNext()) old += c.getLong(0) }
            }.onFailure { failed++ }
            val r = deleteIds(old, starredSms)
            deleted += r.first
            failed += r.second
        }
        com.dns.sms.util.Perf.log("rules.deleteInThreads: ${threadIds.size} threads, deleted=$deleted failedChunks=$failed")
        if (failed > 0) android.util.Log.w("MessageRules", "delete failed for $failed chunks")
    }

    /** Deletes [ids] except starred ones; returns (deleted count, failed chunks). Also drops them from the search index. */
    private suspend fun deleteIds(ids: List<Long>, starredSms: Set<Long> = emptySet()): Pair<Int, Int> {
        var deleted = 0
        var failed = 0
        RuleDeletion.deletionChunks(ids, starredSms).forEach { chunk ->
            runCatching { resolver.delete(Telephony.Sms.CONTENT_URI, "_id IN (${chunk.joinToString(",")})", null) }
                .onSuccess { deleted += it; runCatching { ftsDao.delete(chunk) } }
                .onFailure { failed++; android.util.Log.w("MessageRules", "delete chunk of ${chunk.size} failed: ${it.javaClass.simpleName}") }
        }
        return deleted to failed
    }

    companion object {
        private const val NAME = "message-rules"
        private const val THREAD_CHUNK = 200
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /** Runs daily only while at least one rule is on. Call after any rule changes. */
        fun schedule(ctx: Context, settings: AppSettings) {
            val wm = WorkManager.getInstance(ctx)
            val anyOn = listOf(settings.deleteOtpAfter, settings.deletePromotionalAfter, settings.deleteBlockedAfter)
                .any { it.value != Retention.NEVER }
            if (!anyOn) { wm.cancelUniqueWork(NAME); return }
            wm.enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.UPDATE,
                // FEAT-4: housekeeping can wait for a healthy battery.
                PeriodicWorkRequestBuilder<MessageRulesWorker>(1, TimeUnit.DAYS)
                    .setConstraints(androidx.work.Constraints.Builder().setRequiresBatteryNotLow(true).build()).build(),
            )
        }
    }
}
