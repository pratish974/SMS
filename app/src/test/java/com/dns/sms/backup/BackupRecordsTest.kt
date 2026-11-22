package com.dns.sms.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BackupRecordsTest {
    @Test fun roundTrip() {
        val records = listOf(
            BackupRecord.Sms(1, "VM-HDFCBK", "Rs 500 debited. नमस्ते 👋", 1_700_000_000_000, 1_699_999_999_000, true),
            BackupRecord.Override("HDFCBK", "TRANSACTIONS"),
        )
        val out = ByteArrayOutputStream()
        BackupRecords.Writer(out).apply { records.forEach(::write); finish() }
        val back = ArrayList<BackupRecord>()
        assertEquals(2, BackupRecords.read(ByteArrayInputStream(out.toByteArray())) { back += it })
        assertEquals(records, back)
    }

    @Test fun rejectsOversizedAndMissingEnd() {
        val bad = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeByte(3); writeInt(Int.MAX_VALUE) } }
        try { BackupRecords.read(ByteArrayInputStream(bad.toByteArray())) {}; fail() } catch (_: BackupFormatException) { }
        // A file cut off before the end marker must not be accepted as complete.
        val noEnd = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeByte(3); writeInt(1); write('A'.code); writeInt(1); write('B'.code) } }
        try { BackupRecords.read(ByteArrayInputStream(noEnd.toByteArray())) {}; fail() } catch (_: BackupFormatException) { }
    }
}
