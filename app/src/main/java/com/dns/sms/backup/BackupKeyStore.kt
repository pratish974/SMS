package com.dns.sms.backup

import androidx.core.content.edit
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The backup key (owner decision 2026-11-21: no password, key tied to the phone's Google backup).
 * - On this phone: wrapped by an Android Keystore key, so automatic backups need no prompt.
 * - Off the phone: Google Block Store, only when it is end-to-end encrypted with the screen lock.
 *   It moves to a new phone when that phone is set up from this phone's Google backup.
 */
@Singleton
class BackupKeyStore @Inject constructor(@param:ApplicationContext private val ctx: Context) {
    private val sp get() = ctx.getSharedPreferences("backup_key", Context.MODE_PRIVATE)
    private val blockstore get() = Blockstore.getClient(ctx)

    class BackupKey(val id: ByteArray, val key: SecretKey)

    /** Block Store only end-to-end encrypts when the phone has a screen lock and Google backup on. */
    suspend fun cloudKeyAvailable(): Boolean = runCatching { blockstore.isEndToEndEncryptionAvailable.await() }.getOrDefault(false)

    /**
     * The key to back up with: this phone's key, else one restored via Block Store, else a new one.
     * Every call also re-saves it to Block Store, so it survives app reinstalls on this phone too.
     */
    suspend fun keyForBackup(): BackupKey {
        val k = loadLocal() ?: fromBlockstore()?.also(::saveLocal) ?: BackupCrypto.newKey().let { (id, key) -> BackupKey(id, key) }.also(::saveLocal)
        blockstore.storeBytes(
            StoreBytesData.Builder().setKey(BLOCKSTORE_KEY).setBytes(k.id + k.key.encoded).setShouldBackupToCloud(true).build(),
        ).await()
        return k
    }

    /** The key matching a backup's header, from this phone or Block Store; null if neither has it. */
    suspend fun keyFor(id: ByteArray): SecretKey? {
        loadLocal()?.takeIf { it.id.contentEquals(id) }?.let { return it.key }
        return fromBlockstore()?.takeIf { it.id.contentEquals(id) }?.also(::saveLocal)?.key
    }

    private suspend fun fromBlockstore(): BackupKey? = runCatching {
        val res = blockstore.retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(BLOCKSTORE_KEY)).build()).await()
        val bytes = res.blockstoreDataMap[BLOCKSTORE_KEY]?.bytes ?: return@runCatching null
        if (bytes.size != 48) return@runCatching null
        BackupKey(bytes.copyOfRange(0, 16), SecretKeySpec(bytes.copyOfRange(16, 48), "AES"))
    }.getOrNull()

    private fun saveLocal(k: BackupKey) {
        // commit (not apply): the key must be on disk before the backup that uses it is uploaded.
        sp.edit(commit = true) {
            putString(KEY_ID, Base64.encodeToString(k.id, Base64.NO_WRAP))
            putString(KEY, wrap(k.key.encoded))
        }
    }

    private fun loadLocal(): BackupKey? {
        val id = sp.getString(KEY_ID, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val raw = sp.getString(KEY, null)?.let { runCatching { unwrap(it) }.getOrNull() } ?: return null
        return BackupKey(id, SecretKeySpec(raw, "AES"))
    }

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
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

    private fun wrap(raw: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrapKey()) }
        return Base64.encodeToString(c.iv + c.doFinal(raw), Base64.NO_WRAP)
    }

    private fun unwrap(s: String): ByteArray {
        val all = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, all, 0, 12))
        return c.doFinal(all, 12, all.size - 12)
    }

    private companion object {
        const val ALIAS = "sms_backup_wrap"
        const val KEY = "wrapped_v2"
        const val KEY_ID = "key_id_v2"
        const val BLOCKSTORE_KEY = "com.dns.sms.backup_key_v2"
    }
}
