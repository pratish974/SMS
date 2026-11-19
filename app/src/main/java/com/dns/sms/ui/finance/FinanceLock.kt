package com.dns.sms.ui.finance

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dns.sms.R
import com.dns.sms.settings.AppSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether balances are visible right now. In memory only (never persisted, Requirement.md section 9)
 * and reset whenever the app goes to the background.
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
        if (BiometricManager.from(activity).canAuthenticate(ALLOWED) != BiometricManager.BIOMETRIC_SUCCESS) {
            state.value = false
            onUnavailable()
            return
        }
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { state.value = true }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.finance_unlock_title))
                .setSubtitle(activity.getString(R.string.finance_unlock_subtitle))
                .setAllowedAuthenticators(ALLOWED)
                .build(),
        )
    }

    companion object {
        const val ALLOWED = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

        /** Opens Android's screen-lock / biometric setup for the authenticators Finance accepts. */
        fun setupIntent(): android.content.Intent =
            android.content.Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL)
                .putExtra(android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, ALLOWED)
    }
}
