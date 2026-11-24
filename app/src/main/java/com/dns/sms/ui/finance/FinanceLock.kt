package com.dns.sms.ui.finance

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dns.sms.R
import com.dns.sms.settings.AppSettings
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether balances are visible right now. In memory only (never persisted, Requirement.md section 9)
 * and reset whenever the app goes to the background.
 *
 * The unlock is bound to the Android Keystore (CodeQL "insecure local authentication"): the prompt
 * carries a cipher whose key can only be used right after a successful fingerprint/face/screen-lock
 * check, and balances are revealed only if that cipher then actually works. A faked success callback
 * (e.g. on a hooked/rooted phone) can't use the key, so it can't reveal balances.
 */
@Singleton
class FinanceLock @Inject constructor(private val settings: AppSettings) {
    private val state = MutableStateFlow(false)
    val revealed: StateFlow<Boolean> = state.asStateFlow()

    fun hide() { state.value = false }

    /**
     * Shows balances, asking for fingerprint/face or the screen lock first when "Enable security for
     * Finance" is on. Fails closed (BUG-3): if the phone has no usable lock or biometric, balances stay
     * masked and [onUnavailable] lets the UI send the user to set one up.
     */
    fun reveal(activity: FragmentActivity, onUnavailable: () -> Unit) {
        if (!settings.financeSecurity.value) { state.value = true; return }
        val cipher = if (BiometricManager.from(activity).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS) unlockCipher() else null
        if (cipher == null) {
            state.value = false
            onUnavailable()
            return
        }
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // Only a Keystore-approved cipher can do this; anything else keeps balances hidden.
                    state.value = runCatching { result.cryptoObject?.cipher?.doFinal(CHALLENGE) != null }.getOrDefault(false)
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.finance_unlock_title))
                .setSubtitle(activity.getString(R.string.finance_unlock_subtitle))
                .setAllowedAuthenticators(ALLOWED)
                .build(),
            BiometricPrompt.CryptoObject(cipher),
        )
    }

    /**
     * A cipher over the auth-bound key, ready for the prompt. A key invalidated by a new fingerprint
     * enrolment or a removed screen lock is replaced once. Null if the Keystore can't provide one.
     */
    private fun unlockCipher(): Cipher? {
        repeat(2) {
            try {
                return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            } catch (_: KeyPermanentlyInvalidatedException) {
                runCatching { keyStore().deleteEntry(KEY_ALIAS) }
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun key(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // 0 = every use needs a fresh check, by strong biometric or the screen lock (same as ALLOWED).
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run { init(spec); generateKey() }
    }

    companion object {
        const val ALLOWED = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "finance_unlock"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val CHALLENGE = "finance-unlock".toByteArray()

        /** Opens Android's screen-lock / biometric setup for the authenticators Finance accepts. */
        fun setupIntent(): android.content.Intent =
            android.content.Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL)
                .putExtra(android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, ALLOWED)
    }
}
