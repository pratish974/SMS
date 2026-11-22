package com.dns.sms.backup

import java.io.DataInputStream
import java.io.EOFException
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupFormatException(message: String) : IOException(message)

/**
 * Backup file format v2 (Requirement.md section 9):
 *   header = "SMSBKP" | version(1) | keyId(16)
 * The key is random (no password); `keyId` says which key opened it. The key itself lives in the
 * Android Keystore on this phone and, end-to-end encrypted, in Google Block Store (see BackupKeyStore).
 *   then chunks: flags(1, bit0 = last) | length(int) | nonce(12) | AES-GCM ciphertext
 * Each chunk's AAD = header | chunk index (long) | flags, so reordering, truncation and
 * tampering are all detected. Data is streamed: plaintext never touches the disk.
 */
object BackupCrypto {
    private val MAGIC = "SMSBKP".toByteArray(Charsets.US_ASCII)
    const val VERSION = 2
    const val CHUNK = 64 * 1024
    private const val MAX_CHUNK = CHUNK + 16
    const val HEADER_SIZE = 6 + 1 + 16

    class Header(val keyId: ByteArray) {
        fun bytes(): ByteArray = ByteBuffer.allocate(HEADER_SIZE).put(MAGIC).put(VERSION.toByte()).put(keyId).array()
    }

    fun newKey(): Pair<ByteArray, SecretKey> {
        val r = SecureRandom()
        val id = ByteArray(16).also { r.nextBytes(it) }
        val raw = ByteArray(32).also { r.nextBytes(it) }
        return id to SecretKeySpec(raw, "AES")
    }

    fun readHeader(input: InputStream): Header {
        val b = ByteArray(HEADER_SIZE)
        DataInputStream(input).readFully(b)
        val buf = ByteBuffer.wrap(b)
        val magic = ByteArray(6).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) throw BackupFormatException("not a backup file")
        if (buf.get().toInt() != VERSION) throw BackupFormatException("unsupported backup version")
        return Header(ByteArray(16).also { buf.get(it) })
    }

    private fun aad(header: ByteArray, index: Long, flags: Int): ByteArray =
        ByteBuffer.allocate(header.size + 9).put(header).putLong(index).put(flags.toByte()).array()

    /** Writes the header immediately; data is encrypted in chunks; [close] writes the final chunk. */
    class EncryptingStream(out: OutputStream, private val key: SecretKey, header: Header) : FilterOutputStream(out) {
        private val headerBytes = header.bytes()
        private val buf = ByteArray(CHUNK)
        private var len = 0
        private var pending: ByteArray? = null
        private var index = 0L
        private var closed = false
        private val random = SecureRandom()

        init { out.write(headerBytes) }

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, n: Int) {
            var o = off
            var left = n
            while (left > 0) {
                val take = minOf(left, CHUNK - len)
                System.arraycopy(b, o, buf, len, take)
                len += take; o += take; left -= take
                if (len == CHUNK) {
                    // We only learn which chunk is last at close, so hold one full chunk back.
                    pending?.let { emit(it, last = false) }
                    pending = buf.copyOf()
                    len = 0
                }
            }
        }

        private fun emit(plain: ByteArray, last: Boolean) {
            val flags = if (last) 1 else 0
            val nonce = ByteArray(12).also { random.nextBytes(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            c.updateAAD(aad(headerBytes, index++, flags))
            val ct = c.doFinal(plain)
            out.write(flags)
            out.write(ByteBuffer.allocate(4).putInt(ct.size).array())
            out.write(nonce)
            out.write(ct)
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            val p = pending
            if (len > 0) {
                p?.let { emit(it, last = false) }
                emit(buf.copyOf(len), last = true)
            } else if (p != null) {
                emit(p, last = true)
            } else {
                emit(ByteArray(0), last = true)
            }
            out.close()
        }
    }

    /** Reads after [readHeader]. Throws on a wrong key, tampering, reordering or truncation. */
    class DecryptingStream(private val input: InputStream, private val key: SecretKey, header: Header) : InputStream() {
        private val headerBytes = header.bytes()
        private val din = DataInputStream(input)
        private var current = ByteArray(0)
        private var pos = 0
        private var index = 0L
        private var done = false

        private fun next(): Boolean {
            if (done) return false
            val flags = try { din.readUnsignedByte() } catch (_: EOFException) { throw BackupFormatException("backup is truncated") }
            if (flags > 1) throw BackupFormatException("bad chunk flags")
            val n = din.readInt()
            if (n < 16 || n > MAX_CHUNK) throw BackupFormatException("bad chunk length")
            val nonce = ByteArray(12).also { din.readFully(it) }
            val ct = ByteArray(n).also { din.readFully(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            c.updateAAD(aad(headerBytes, index++, flags))
            current = c.doFinal(ct) // AEADBadTagException: wrong key or tampered data
            pos = 0
            if (flags == 1) {
                done = true
                if (din.read() != -1) throw BackupFormatException("data after the last chunk")
            }
            return true
        }

        override fun read(): Int {
            while (pos >= current.size) if (!next()) return -1
            return current[pos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= current.size) if (!next()) return -1
            val n = minOf(len, current.size - pos)
            System.arraycopy(current, pos, b, off, n)
            pos += n
            return n
        }

        override fun close() = input.close()
    }
}
