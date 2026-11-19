package com.dns.sms.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** What a backup holds. Finance, reminders and orders are not backed up: a rescan rebuilds them. */
sealed interface BackupRecord {
    data class Sms(val type: Int, val address: String, val body: String, val date: Long, val dateSent: Long, val read: Boolean) : BackupRecord
    data class Override(val key: String, val category: String) : BackupRecord
}

/** Length-prefixed binary records with hard caps on read (the file is untrusted on restore). */
object BackupRecords {
    private const val SMS = 1
    // 2 was MMS text; reserved so a future MMS milestone can reuse it.
    private const val OVERRIDE = 3
    private const val END = 0
    const val MAX_STRING_BYTES = 100_000
    const val MAX_RECORDS = 2_000_000

    class Writer(out: OutputStream) {
        private val d = DataOutputStream(out.buffered())
        var count = 0
            private set

        fun write(r: BackupRecord) {
            when (r) {
                is BackupRecord.Sms -> {
                    d.writeByte(SMS); d.writeInt(r.type); str(r.address); str(r.body); d.writeLong(r.date); d.writeLong(r.dateSent); d.writeBoolean(r.read)
                }
                is BackupRecord.Override -> { d.writeByte(OVERRIDE); str(r.key); str(r.category) }
            }
            count++
        }

        fun finish() { d.writeByte(END); d.flush() }

        private fun str(s: String) {
            val b = s.toByteArray(Charsets.UTF_8).let { if (it.size > MAX_STRING_BYTES) it.copyOf(MAX_STRING_BYTES) else it }
            d.writeInt(b.size); d.write(b)
        }
    }

    /** Calls [onRecord] for each record; throws [BackupFormatException] on malformed or oversized data. */
    fun read(input: InputStream, onRecord: (BackupRecord) -> Unit): Int {
        val d = DataInputStream(input.buffered())
        var n = 0
        while (true) {
            val tag = try { d.readUnsignedByte() } catch (_: EOFException) { throw BackupFormatException("missing end marker") }
            if (tag == END) return n
            if (++n > MAX_RECORDS) throw BackupFormatException("too many records")
            fun str(): String {
                val len = d.readInt()
                if (len < 0 || len > MAX_STRING_BYTES) throw BackupFormatException("string too long")
                return String(ByteArray(len).also { d.readFully(it) }, Charsets.UTF_8)
            }
            val r = when (tag) {
                SMS -> BackupRecord.Sms(d.readInt(), str(), str(), d.readLong(), d.readLong(), d.readBoolean())
                OVERRIDE -> BackupRecord.Override(str(), str())
                else -> throw BackupFormatException("unknown record $tag")
            }
            onRecord(r)
        }
    }
}
