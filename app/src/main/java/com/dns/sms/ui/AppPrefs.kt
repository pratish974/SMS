package com.dns.sms.ui

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small UI flags. No message data lives here. */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    private val acceptedState = MutableStateFlow(sp.getBoolean(KEY_ACCEPTED, false))
    val disclosureAccepted: StateFlow<Boolean> = acceptedState.asStateFlow()

    fun setDisclosureAccepted(value: Boolean) {
        sp.edit { putBoolean(KEY_ACCEPTED, value) }
        acceptedState.value = value
    }

    var wasDefaultOnce: Boolean
        get() = sp.getBoolean(KEY_WAS_DEFAULT, false)
        set(v) = sp.edit { putBoolean(KEY_WAS_DEFAULT, v) }

    var askedRuntimePermissions: Boolean
        get() = sp.getBoolean(KEY_ASKED_PERMS, false)
        set(v) = sp.edit { putBoolean(KEY_ASKED_PERMS, v) }

    private companion object {
        const val KEY_ACCEPTED = "disclosure_accepted"
        const val KEY_WAS_DEFAULT = "was_default_once"
        const val KEY_ASKED_PERMS = "asked_runtime_permissions"
    }
}
