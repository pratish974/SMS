package com.dns.sms.ui.settings

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dns.sms.backup.BackupManager
import com.dns.sms.backup.DriveClient
import com.dns.sms.settings.AppSettings
import com.dns.sms.sms.DefaultSmsGate
import com.dns.sms.work.BackupWorker
import com.dns.sms.work.MessageRulesWorker
import com.dns.sms.work.RestoreWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    val settings: AppSettings,
    val gate: DefaultSmsGate,
    private val backups: BackupManager,
    private val drive: DriveClient,
    val sims: com.dns.sms.sms.SimCards,
) : ViewModel() {

    // --- SIMs. Re-read on every visit (a SIM or eSIM can be added or removed while the app is open).
    val simList = MutableStateFlow<List<com.dns.sms.sms.SimCards.Sim>>(emptyList())
    fun refreshSims() { simList.value = sims.active() }

    fun rulesChanged() = MessageRulesWorker.schedule(ctx, settings)
    fun autoBackupChanged() = BackupWorker.schedule(ctx, settings)

    fun isIgnoringBatteryOptimizations(): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    // --- Backup
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val consent = MutableStateFlow<android.app.PendingIntent?>(null)

    val backupRunning: StateFlow<Boolean> = WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(BackupWorker.NOW)
        .map { list -> list.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun connectDrive() = viewModelScope.launch {
        val a = try {
            drive.authorize()
        } catch (e: Exception) {
            message.value = "Couldn't connect Google Drive (${describe(e)})"
            return@launch
        }
        when (a) {
            is DriveClient.Auth.NeedsConsent -> consent.value = a.intent
            is DriveClient.Auth.Token -> afterConnected()
        }
    }

    fun onConsentResult(resultCode: Int, data: Intent?) = viewModelScope.launch {
        consent.value = null
        val auth = try {
            drive.fromConsentResult(data)
        } catch (e: Exception) {
            message.value = "Google Drive wasn't connected (${describe(e)}, result $resultCode)"
            return@launch
        }
        if (auth is DriveClient.Auth.Token) afterConnected() else message.value = "Google Drive wasn't connected"
    }

    /**
     * Google's error code, e.g. "10 DEVELOPER_ERROR" = the OAuth client / SHA-1 isn't registered.
     * Logged in debug builds only; it never contains message data.
     */
    private fun describe(e: Exception): String {
        val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
        // Google reports a missing/mismatched Android OAuth client (package + SHA-1) this way.
        if (e.message?.contains("UNREGISTERED_ON_API_CONSOLE") == true) {
            if (com.dns.sms.BuildConfig.DEBUG) android.util.Log.w("DriveAuth", "OAuth client not registered for this package/SHA-1", e)
            return "this app build isn't registered with Google yet: create an Android OAuth client for its package and SHA-1"
        }
        val text = code?.let { "$it ${com.google.android.gms.common.api.CommonStatusCodes.getStatusCodeString(it)}" } ?: e.javaClass.simpleName
        if (com.dns.sms.BuildConfig.DEBUG) android.util.Log.w("DriveAuth", "Drive authorization failed: $text", e)
        return text
    }

    /** Android's Google account chooser (no GET_ACCOUNTS permission needed). */
    fun accountChooserIntent(): Intent = android.accounts.AccountManager.newChooseAccountIntent(
        settings.driveAccount.value.takeIf { it.contains('@') }?.let { android.accounts.Account(it, DriveClient.GOOGLE_ACCOUNT_TYPE) },
        null, arrayOf(DriveClient.GOOGLE_ACCOUNT_TYPE), null, null, null, null,
    )

    /** Stores the picked account, then asks Google for Drive access with that account. */
    fun onAccountChosen(data: Intent?) {
        val name = data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME) ?: return
        settings.driveAccount.value = name
        connectDrive()
    }

    private suspend fun afterConnected() {
        // Keep the account the user picked; only fill it in if Google chose one for us.
        if (!settings.driveAccount.value.contains('@')) settings.driveAccount.value = drive.accountEmail() ?: "Connected"
    }

    fun backupNow() = BackupWorker.runNow(ctx)

    /** S8: restore is a background job; its progress is [restoreRunning]. */
    fun restore() = RestoreWorker.start(ctx)

    val restoreRunning: StateFlow<Boolean> = WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(RestoreWorker.NAME)
        .map { list -> list.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var reportedRestore: java.util.UUID? = null

    init {
        viewModelScope.launch {
            WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(RestoreWorker.NAME).collect { list ->
                val done = list.lastOrNull { it.state == WorkInfo.State.SUCCEEDED } ?: return@collect
                if (done.id == reportedRestore) return@collect
                reportedRestore = done.id
                val out = done.outputData
                message.value = when (out.getString(RestoreWorker.KEY_RESULT)) {
                    "restored" -> "Restored ${out.getInt(RestoreWorker.KEY_ADDED, 0)} messages (${out.getInt(RestoreWorker.KEY_SKIPPED, 0)} were already here)"
                    "no-key" -> "This phone doesn't have the backup's key. Backups can be restored on a phone set up from your old phone's Google backup."
                    "no-backup" -> "No backup found in this Google account"
                    "sign-in" -> "Choose a Google account first"
                    "not-default" -> "Set this app as your default SMS app first"
                    else -> "Restore failed"
                }
                // Don't repeat this result when the screen is opened again.
                WorkManager.getInstance(ctx).pruneWork()
            }
        }
    }

    fun deleteDriveBackup() = viewModelScope.launch {
        busy.value = true
        message.value = if (backups.deleteRemote()) "Backup deleted from Google Drive" else "Couldn't delete the backup"
        busy.value = false
    }
}
