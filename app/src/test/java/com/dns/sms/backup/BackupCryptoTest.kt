package com.dns.sms.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class BackupCryptoTest {
    private val generated = BackupCrypto.newKey()
    private val header = BackupCrypto.Header(generated.first)
    private val key = generated.second

    private fun encrypt(plain: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.EncryptingStream(out, key, header).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun decrypt(file: ByteArray, k: javax.crypto.SecretKey = key): ByteArray {
        val input = ByteArrayInputStream(file)
        val h = BackupCrypto.readHeader(input)
        assertArrayEquals(header.keyId, h.keyId)
        return BackupCrypto.DecryptingStream(input, k, h).readBytes()
    }

    @Test fun roundTripsAllSizes() {
        for (size in listOf(0, 1, BackupCrypto.CHUNK - 1, BackupCrypto.CHUNK, BackupCrypto.CHUNK + 1, 3 * BackupCrypto.CHUNK + 17)) {
            val plain = Random(size).nextBytes(size)
            assertArrayEquals("size $size", plain, decrypt(encrypt(plain)))
        }
    }

    @Test fun wrongKeyFails() {
        val file = encrypt("secret".toByteArray())
        try { decrypt(file, BackupCrypto.newKey().second); fail() } catch (_: AEADBadTagException) { }
    }

    @Test fun truncationDetected() {
        val file = encrypt(Random(1).nextBytes(3 * BackupCrypto.CHUNK))
        // Drop the final chunk exactly: everything left is valid, but the "last" flag never arrives.
        val full = BackupCrypto.HEADER_SIZE + 3 * (1 + 4 + 12 + BackupCrypto.CHUNK + 16)
        assertEquals(full, file.size)
        val cut = file.copyOf(full - (1 + 4 + 12 + BackupCrypto.CHUNK + 16))
        try { decrypt(cut); fail() } catch (e: BackupFormatException) { assertTrue(e.message!!.contains("truncated")) }
    }

    @Test fun reorderingDetected() {
        val file = encrypt(Random(2).nextBytes(3 * BackupCrypto.CHUNK))
        val c = 1 + 4 + 12 + BackupCrypto.CHUNK + 16
        val h = BackupCrypto.HEADER_SIZE
        val swapped = file.copyOf()
        System.arraycopy(file, h, swapped, h + c, c)
        System.arraycopy(file, h + c, swapped, h, c)
        try { decrypt(swapped); fail() } catch (_: AEADBadTagException) { }
    }

    @Test fun tamperingAndGarbageDetected() {
        val file = encrypt("hello".toByteArray())
        file[file.size - 3] = (file[file.size - 3].toInt() xor 1).toByte()
        try { decrypt(file); fail() } catch (_: AEADBadTagException) { }
        try {
            BackupCrypto.readHeader(ByteArrayInputStream("not a backup at all, definitely not".toByteArray())); fail()
        } catch (_: BackupFormatException) { }
    }
}
