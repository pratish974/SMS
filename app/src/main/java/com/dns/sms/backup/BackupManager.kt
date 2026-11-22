package com.dns.sms.backup

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.dns.sms.classify.Category
import com.dns.sms.data.SenderOverride
import com.dns.sms.data.SenderOverrideDao
import com.dns.sms.settings.AppSettings
import com.dns.sms.sms.DefaultSmsGate
import com.dns.sms.work.SmsScanWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Encrypted Google Drive backup and restore (Requirement.md sections 4 and 9). */
@Singleton
class BackupManager @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    private val resolver: ContentResolver,
    private val gate: DefaultSmsGate,
    private val drive: DriveClient,
    private val keys: BackupKeyStore,
    private val settings: AppSettings,
    private val overrides: SenderOverrideDao,
) {
    sealed interface Outcome {
        data class Done(val bytes: Long, val count: Int) : Outcome
        data class Restored(val added: Int, val skipped: Int) : Outcome
        /** Block Store can't end-to-end encrypt the key (no screen lock or Google backup off). */
        data object NeedsScreenLock : Outcome
        /** This phone has no key for the backup (not set up from the old phone's Google backup). */
        data object KeyNotOnThisPhone : Outcome
        data object NeedsSignIn : Outcome
        data object NoBackupFound : Outcome
        data object NotDefaultApp : Outcome
        data class Failed(val reason: String) : Outcome
    }

    suspend fun backup(): Outcome = withContext(Dispatchers.IO) {
        if (!gate.isDefaultNow()) return@withContext Outcome.NotDefaultApp
        if (drive.authorize() !is DriveClient.Auth.Token) return@withContext Outcome.NeedsSignIn
        // Owner's choice: the key must be restorable only via the phone's end-to-end encrypted backup.
        if (!keys.cloudKeyAvailable()) return@withContext fail("screen-lock").let { Outcome.NeedsScreenLock }
        val backupKey = keys.keyForBackup()
        val header = BackupCrypto.Header(backupKey.id)
        val key = backupKey.key
        val file = tempFile("upload.bin")
        try {
            var count = 0
            BackupCrypto.EncryptingStream(file.outputStream().buffered(), key, header).use { enc ->
                val w = BackupRecords.Writer(enc)
                writeSms(w)
                overrides.all().forEach { w.write(BackupRecord.Override(it.address, it.category.name)) }
                w.finish()
                count = w.count
            }
            val remote = drive.upload(file)
            settings.lastBackupAt.value = System.currentTimeMillis()
            settings.lastBackupBytes.value = remote.size
            settings.lastBackupError.value = ""
            Outcome.Done(remote.size, count)
        } catch (e: DriveClient.DriveException) {
            fail(if (e.code == 401) "sign-in" else "drive-${e.code}").let { if (e.code == 401) Outcome.NeedsSignIn else it }
        } catch (e: Exception) {
            fail(e.javaClass.simpleName)
        } finally {
            file.delete()
        }
    }

    suspend fun restore(): Outcome = withContext(Dispatchers.IO) {
        if (!gate.isDefaultNow()) return@withContext Outcome.NotDefaultApp
        if (drive.authorize() !is DriveClient.Auth.Token) return@withContext Outcome.NeedsSignIn
        val remote = drive.find() ?: return@withContext Outcome.NoBackupFound
        val file = tempFile("download.bin")
        try {
            drive.download(remote.id, file, MAX_BACKUP_BYTES)
            val input = file.inputStream().buffered()
            val header = BackupCrypto.readHeader(input)
            val key = keys.keyFor(header.keyId) ?: return@withContext Outcome.KeyNotOnThisPhone
            val existing = existingSmsKeys()
            var added = 0
            var skipped = 0
            val batch = ArrayList<ContentValues>(BATCH)
            val restoredOverrides = ArrayList<SenderOverride>()
            fun flush() {
                if (batch.isEmpty()) return
                added += resolver.bulkInsert(Telephony.Sms.CONTENT_URI, batch.toTypedArray())
                batch.clear()
            }
            BackupCrypto.DecryptingStream(input, key, header).use { dec ->
                BackupRecords.read(dec) { r ->
                    when (r) {
                        is BackupRecord.Sms -> {
                            if (!existing.add(SmsKey.of(r.type, r.address, r.date, r.body))) { skipped++; return@read }
                            batch += ContentValues().apply {
                                put(Telephony.Sms.TYPE, r.type); put(Telephony.Sms.ADDRESS, r.address); put(Telephony.Sms.BODY, r.body)
                                put(Telephony.Sms.DATE, r.date); put(Telephony.Sms.DATE_SENT, r.dateSent)
                                put(Telephony.Sms.READ, if (r.read) 1 else 0); put(Telephony.Sms.SEEN, 1)
                            }
                            if (batch.size >= BATCH) flush()
                        }
                        is BackupRecord.Override -> Category.entries.firstOrNull { it.name == r.category }
                            ?.let { c -> restoredOverrides += SenderOverride(r.key, c) }
                    }
                }
            }
            flush()
            restoredOverrides.forEach { overrides.upsert(it) }
            SmsScanWorker.enqueue(ctx, full = true)
            Outcome.Restored(added, skipped)
        } catch (_: AEADBadTagException) {
            Outcome.Failed("tampered")
        } catch (_: BackupFormatException) {
            Outcome.Failed("format")
        } catch (e: DriveClient.DriveException) {
            if (e.code == 401) Outcome.NeedsSignIn else Outcome.Failed("drive-${e.code}")
        } catch (e: Exception) {
            Outcome.Failed(e.javaClass.simpleName)
        } finally {
            file.delete()
        }
    }

    /** S9: deletes the Drive backup. The key copies stay (harmless without the file). */
    suspend fun deleteRemote(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            drive.find()?.let { drive.delete(it.id) }
            settings.lastBackupAt.value = 0L
            settings.lastBackupBytes.value = 0L
            true
        }.getOrDefault(false)
    }

    private fun fail(reason: String): Outcome {
        settings.lastBackupError.value = reason
        return Outcome.Failed(reason)
    }

    private fun writeSms(w: BackupRecords.Writer) {
        resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("type", "address", "body", "date", "date_sent", "read"), null, null, "_id ASC")
            ?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(0)
                    // Outbox/queued/failed rows are transient; only stored messages are backed up.
                    if (type != Telephony.Sms.MESSAGE_TYPE_INBOX && type != Telephony.Sms.MESSAGE_TYPE_SENT) continue
                    w.write(BackupRecord.Sms(type, c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getLong(3), c.getLong(4), c.getInt(5) == 1))
                }
            }
    }

    private fun existingSmsKeys(): SmsKeySet {
        val keys = SmsKeySet.Builder()
        resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("type", "address", "date", "body"), null, null, null)?.use { c ->
            while (c.moveToNext()) keys.add(SmsKey.of(c.getInt(0), c.getString(1).orEmpty(), c.getLong(2), c.getString(3).orEmpty()))
        }
        return keys.build()
    }

    private fun tempFile(name: String): File = File(File(ctx.cacheDir, "backup").apply { mkdirs() }, name).apply { delete() }

    companion object {
        const val MAX_BACKUP_BYTES = 500L * 1024 * 1024
        private const val BATCH = 200
    }
}
