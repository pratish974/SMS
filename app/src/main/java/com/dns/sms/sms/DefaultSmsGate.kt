package com.dns.sms.sms

import android.app.role.RoleManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Play policy (Requirement.md section 8): SMS data may only be touched while we are the
 * default SMS app. Every provider read/write checks [isDefaultNow]; UI and flows follow [isDefault].
 */
@Singleton
class DefaultSmsGate @Inject constructor(private val roleManager: RoleManager) {
    private val state = MutableStateFlow(isDefaultNow())
    val isDefault: StateFlow<Boolean> = state.asStateFlow()

    fun isDefaultNow(): Boolean =
        roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)

    fun refresh() { state.value = isDefaultNow() }

    fun requestIntent() = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
}
