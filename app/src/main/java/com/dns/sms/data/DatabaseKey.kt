package com.dns.sms.data

import androidx.core.content.edit
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The database passphrase (Requirement.md section 9): 32 random bytes, stored only wrapped by an
 * AES-GCM key that lives in the Android Keystore and never leaves it.
 */
object DatabaseKey {
    private const val ALIAS = "sms_db_wrap"
    private const val PREFS = "db_key"
    private const val KEY = "wrapped"

    /**
     * Returns the passphrase, creating it on first use. If the stored one can never be unwrapped again
     * (Keystore alias gone or permanently invalid, e.g. data restored onto another device), [onLost]
     * runs so the caller can drop the unreadable database, and a new key is made.
     * Transient Keystore errors are rethrown: we never wipe or replace a key because of them.
     */
    fun passphrase(ctx: Context, onLost: () -> Unit): String {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = sp.getString(KEY, null)
        if (stored != null) {
            if (hasAlias()) {
                try {
                    return unwrap(stored)
                } catch (e: Exception) {
                    if (!isPermanent(e)) throw e
                }
            }
            onLost()
            deleteAlias()
            sp.edit(commit = true) { clear() }
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val pass = Base64.encodeToString(raw, Base64.NO_WRAP)
        // commit (not apply): the key must be on disk before the database is created with it.
        val wrapped = wrap(pass)
        sp.edit(commit = true) { putString(KEY, wrapped) }
        check(sp.getString(KEY, null) == wrapped) { "could not store database key" }
        return pass
    }

    private fun isPermanent(e: Throwable): Boolean =
        e is KeyPermanentlyInvalidatedException || e is UnrecoverableKeyException || e is AEADBadTagException

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun hasAlias() = keyStore().containsAlias(ALIAS)
    private fun deleteAlias() { runCatching { keyStore().deleteEntry(ALIAS) } }

    private fun keystoreKey(): SecretKey {
        val ks = keyStore()
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun wrap(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val ct = c.doFinal(plain.toByteArray())
        return Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    private fun unwrap(stored: String): String {
        val all = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, all, 0, 12))
        return String(c.doFinal(all, 12, all.size - 12))
    }
}
