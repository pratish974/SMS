package com.dns.sms.perf

import android.app.role.RoleManager
import android.content.ContentValues
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dns.sms.data.AppDatabase
import com.dns.sms.data.MessageMeta
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Performance data: 2,00,000 SMS in each of Personal, Transactions and Promotions, plus 2,00,000
 * starred. **Emulator only and opt-in** (skipped in a normal `connectedAndroidTest`):
 *
 *   tools/perf/seed.sh            -> seeds  (-e perfSeed true)
 *   tools/perf/seed.sh clean      -> removes everything it inserted (-e perfClean true)
 *
 * Seeded senders: Personal "+919000xxxxxx", Transactions "VM-PFBxxxx-S", Promotions "AD-PFPxxxx-P".
 */
@RunWith(AndroidJUnit4::class)
class LargeInboxSeeder {
    private val args = InstrumentationRegistry.getArguments()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = ctx.contentResolver

    private fun isEmulator(): Boolean =
        Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") ||
            Build.PRODUCT.startsWith("sdk") || Build.FINGERPRINT.contains("emu64") || Build.FINGERPRINT.startsWith("generic")

    @Before
    fun guard() {
        assumeTrue("opt-in only: pass -e perfSeed true or -e perfClean true", args.getString("perfSeed") == "true" || args.getString("perfClean") == "true")
        // Never put test data into a real person's inbox.
        assumeTrue("refusing to run on a physical device", isEmulator())
        assumeTrue("the app must be the default SMS app", ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS))
    }

    @Test
    fun seed() {
        if (args.getString("perfSeed") != "true") return
        val perSection = args.getString("perfPerSection")?.toInt() ?: 200_000
        val threads = args.getString("perfThreads")?.toInt() ?: 1_000
        val perThread = perSection / threads
        val now = System.currentTimeMillis()
        val start = System.currentTimeMillis()

        for (section in Section.entries) {
            for (t in 0 until threads) {
                val address = section.address(t)
                // Resumable: if a previous run was interrupted, only add what this conversation is missing.
                val existing = resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("_id"), "address = ?", arrayOf(address), null)?.use { it.count } ?: 0
                if (existing >= perThread) continue
                val threadId = Telephony.Threads.getOrCreateThreadId(ctx, address)
                val rows = Array(perThread - existing) { k ->
                    val i = existing + k
                    // Spread messages over ~2 years, newest last; thread t's newest is t minutes old.
                    val date = now - (t * 60_000L) - ((perThread - 1 - i) * 3_600_000L)
                    ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, section.body(t, i))
                        put(Telephony.Sms.DATE, date)
                        put(Telephony.Sms.DATE_SENT, date)
                        put(Telephony.Sms.TYPE, if (section == Section.PERSONAL && i % 3 == 0) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX)
                        // Every 5th conversation has an unread newest message.
                        put(Telephony.Sms.READ, if (i == perThread - 1 && t % 5 == 0) 0 else 1)
                        put(Telephony.Sms.SEEN, 1)
                        put(Telephony.Sms.THREAD_ID, threadId)
                    }
                }
                resolver.bulkInsert(Telephony.Sms.CONTENT_URI, rows)
                if (t % 50 == 0) Log.i(TAG, "${section.name}: ${t * perThread}/$perSection inserted (${(System.currentTimeMillis() - start) / 1000}s)")
            }
        }
        if (runBlocking { AppDatabase.get(ctx).messageMetaDao().starredCount() } < perSection) starPersonal(perSection)
        Log.i(TAG, "DONE: ${3 * perSection} SMS + $perSection starred in ${(System.currentTimeMillis() - start) / 1000}s")
    }

    /** Stars the seeded Personal messages (the Starred folder test). */
    private fun starPersonal(count: Int) = runBlocking {
        val dao = AppDatabase.get(ctx).messageMetaDao()
        val batch = ArrayList<MessageMeta>(5_000)
        resolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf("_id", "thread_id"),
            "address LIKE '+919000%'", null, "_id ASC LIMIT $count",
        )?.use { c ->
            while (c.moveToNext()) {
                batch += MessageMeta("sms", c.getLong(0), c.getLong(1), starred = true)
                if (batch.size == 5_000) { dao.upsertAll(batch); batch.clear(); Log.i(TAG, "starred ${c.position + 1}") }
            }
        }
        if (batch.isNotEmpty()) dao.upsertAll(batch)
    }

    @Test
    fun clean() {
        if (args.getString("perfClean") != "true") return
        val where = "address LIKE '+919000%' OR address LIKE 'VM-PFB%' OR address LIKE 'AD-PFP%'"
        val threadIds = resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("DISTINCT thread_id"), where, null, null)
            ?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()
        val deleted = resolver.delete(Telephony.Sms.CONTENT_URI, where, null)
        runBlocking { threadIds.chunked(500).forEach { AppDatabase.get(ctx).messageMetaDao().deleteThreads(it) } }
        Log.i(TAG, "CLEANED: $deleted SMS in ${threadIds.size} conversations")
    }

    private enum class Section {
        PERSONAL {
            override fun address(t: Int) = "+919000%06d".format(t)
            override fun body(t: Int, i: Int) = when (i % 4) {
                0 -> "Hey, are we still meeting at ${i % 12 + 1}? Message $i"
                1 -> "Sure, see you there. Bringing the documents for item $t."
                2 -> "Running a bit late, about ${i % 20 + 5} minutes"
                else -> "Thanks! Talk tomorrow. नमस्ते 👋 ($i)"
            }
        },
        TRANSACTIONS {
            override fun address(t: Int) = "VM-PFB%04d-S".format(t)
            override fun body(t: Int, i: Int) =
                "Acct XX${1000 + t % 9000} debited for Rs ${100 + i}.00 on 19-Nov-26; UPI:${400000000000L + i}. Avbl Bal Rs ${50_000 + i * 7}.25"
        },
        PROMOTIONS {
            override fun address(t: Int) = "AD-PFP%04d-P".format(t)
            override fun body(t: Int, i: Int) = "Big sale! Flat ${10 + i % 60}% off on item $i this weekend only. T&C apply"
        };

        abstract fun address(t: Int): String
        abstract fun body(t: Int, i: Int): String
    }

    private companion object { const val TAG = "PerfSeed" }
}
