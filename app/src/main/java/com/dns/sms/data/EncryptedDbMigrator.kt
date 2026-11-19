package com.dns.sms.data

import android.content.Context
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * One-time move of a plaintext database (milestones 1–5) into an encrypted one. Uses SQLCipher's
 * sqlcipher_export so ids, overrides, pins and stars survive. If it fails the plaintext file is
 * removed: it only ever held ids and flags, never message content.
 */
object EncryptedDbMigrator {
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun isPlaintext(file: File): Boolean {
        if (!file.exists() || file.length() < SQLITE_HEADER.size) return false
        val head = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { it.read(head) }
        return head.contentEquals(SQLITE_HEADER)
    }

    fun encryptIfPlaintext(ctx: Context, name: String, pass: String) {
        val db = ctx.getDatabasePath(name)
        if (!isPlaintext(db)) return
        val tmp = File(db.parentFile, "$name.enc")
        tmp.delete()
        val ok = runCatching {
            val plain = SQLiteDatabase.openDatabase(db.absolutePath, "", null, SQLiteDatabase.OPEN_READWRITE, null, null)
            try {
                val version = plain.version
                plain.rawExecSQL("ATTACH DATABASE '${tmp.absolutePath}' AS encrypted KEY '$pass'")
                plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
                plain.rawExecSQL("PRAGMA encrypted.user_version = $version")
                plain.rawExecSQL("DETACH DATABASE encrypted")
            } finally {
                plain.close()
            }
        }.isSuccess
        if (ok && tmp.exists()) {
            ctx.deleteDatabase(name)
            tmp.renameTo(db)
        } else {
            tmp.delete()
            ctx.deleteDatabase(name)
        }
    }
}
