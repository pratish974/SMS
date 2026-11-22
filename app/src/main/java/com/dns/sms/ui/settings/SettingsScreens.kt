package com.dns.sms.ui.settings

import androidx.core.net.toUri
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Edit
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dns.sms.BuildConfig
import com.dns.sms.R
import com.dns.sms.settings.AutoBackup
import com.dns.sms.settings.FontScale
import com.dns.sms.settings.LaunchFolder
import com.dns.sms.settings.Retention
import com.dns.sms.settings.SwipeAction
import com.dns.sms.ui.messages.ConfirmDialog
import com.dns.sms.ui.messages.swipeLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Icon colours from the reference list (original icons).
private val Green = Color(0xFF43A047)
private val Teal = Color(0xFF26A69A)
private val Purple = Color(0xFF9575CD)
private val Gray = Color(0xFF9E9E9E)
private val Yellow = Color(0xFFFFCA28)
private val Red = Color(0xFFEF5350)
private val Olive = Color(0xFF9E9D24)
private val Blue = Color(0xFF42A5F5)

@Composable
fun SettingsScreen(onBack: () -> Unit, open: (String) -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s = vm.settings
    val isDefault by vm.gate.isDefault.collectAsStateWithLifecycle()
    val ownNumber by s.ownNumber.flow.collectAsStateWithLifecycle()
    val security by s.financeSecurity.flow.collectAsStateWithLifecycle()
    val quick by s.showQuickReplies.flow.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf("") }

    SettingsScaffold(stringResource(R.string.settings), onBack) {
        SettingRow(stringResource(R.string.app_status), if (isDefault) stringResource(R.string.default_sms_app) else stringResource(R.string.not_default), icon = Icons.Filled.Flag, tint = Green) { open("status") }
        SettingRow(stringResource(R.string.general), icon = Icons.Filled.Settings, tint = Teal) { open("general") }
        // Language row hidden: the app is English (US) only until translations are added.
        SettingRow(stringResource(R.string.backup_restore), icon = Icons.Filled.Backup, tint = Purple) { open("backup") }
        SettingRow(stringResource(R.string.message_rules), icon = Icons.Filled.Delete, tint = Gray) { open("rules") }
        SettingRow(stringResource(R.string.notifications), icon = Icons.Filled.Notifications, tint = Yellow) { open("notifications") }
        SettingRow(stringResource(R.string.phone_number), ownNumber.ifBlank { stringResource(R.string.unknown_value) }, icon = Icons.Filled.PhoneAndroid, tint = Red) { dialog = "number" }
        SettingRow(stringResource(R.string.swipe_actions), icon = Icons.AutoMirrored.Filled.CompareArrows, tint = Olive) { open("swipe") }
        SwitchRow(stringResource(R.string.finance_security), security, icon = Icons.Filled.Shield, tint = Green) { s.financeSecurity.value = it }
        SwitchRow(stringResource(R.string.show_quick_replies), quick, icon = Icons.AutoMirrored.Filled.Chat, tint = Teal) { s.showQuickReplies.value = it }
        SettingRow(stringResource(R.string.edit_quick_replies), icon = Icons.Filled.Edit, tint = Teal) { open("quick_replies") }
        SettingRow(stringResource(R.string.about), icon = Icons.Filled.Info, tint = Blue) { open("about") }
    }
    when (dialog) {
        "number" -> TextDialog(stringResource(R.string.phone_number), ownNumber, stringResource(R.string.phone_number_hint),
            { s.ownNumber.value = it.filter { c -> c.isDigit() || c == '+' } }, { dialog = "" }, maxLength = 20)
    }
}

@Composable
fun GeneralSettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val s = vm.settings
    val signature by s.signature.flow.collectAsStateWithLifecycle()
    val launch by s.launchFolder.flow.collectAsStateWithLifecycle()
    val font by s.fontScale.flow.collectAsStateWithLifecycle()
    val use24 by s.use24h.flow.collectAsStateWithLifecycle()
    val follow by s.use24hFollowSystem.flow.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf("") }
    val effective24 = if (follow) android.text.format.DateFormat.is24HourFormat(ctx) else use24
    SettingsScaffold(stringResource(R.string.general), onBack) {
        SettingRow(stringResource(R.string.signature), signature.ifBlank { stringResource(R.string.signature_hint) }) { dialog = "sig" }
        SettingRow(stringResource(R.string.launch_folder), launchLabel(launch)) { dialog = "launch" }
        SettingRow(stringResource(R.string.font_size), fontLabel(font)) { dialog = "font" }
        SwitchRow(stringResource(R.string.use_24h), effective24, subtitle = if (effective24) "13:00" else "1:00 PM") {
            s.use24hFollowSystem.value = false
            s.use24h.value = it
        }
    }
    when (dialog) {
        "sig" -> TextDialog(stringResource(R.string.signature), signature, stringResource(R.string.signature_hint), { s.signature.value = it }, { dialog = "" }, maxLength = 100)
        "launch" -> ChoiceDialog(stringResource(R.string.launch_folder), LaunchFolder.entries, launch, { launchLabel(it) }, { s.launchFolder.value = it; dialog = "" }, { dialog = "" })
        "font" -> ChoiceDialog(stringResource(R.string.font_size), FontScale.entries, font, { fontLabel(it) }, { s.fontScale.value = it; dialog = "" }, { dialog = "" })
    }
}

@Composable
private fun launchLabel(l: LaunchFolder) = stringResource(
    when (l) {
        LaunchFolder.LAST_RECEIVED -> R.string.last_received
        LaunchFolder.PERSONAL -> R.string.folder_personal
        LaunchFolder.TRANSACTIONS -> R.string.folder_transactions
        LaunchFolder.PROMOTIONS -> R.string.folder_promotions
    },
)

@Composable
private fun fontLabel(f: FontScale) = stringResource(when (f) { FontScale.SMALL -> R.string.small; FontScale.NORMAL -> R.string.normal; FontScale.LARGE -> R.string.large })

@Composable
private fun retentionLabel(r: Retention) = stringResource(
    when (r) {
        Retention.NEVER -> R.string.never
        Retention.DAY_1 -> R.string.one_day
        Retention.DAYS_3 -> R.string.three_days
        Retention.WEEK_1 -> R.string.one_week
        Retention.MONTH_1 -> R.string.one_month
    },
)

@Composable
fun MessageRulesScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s = vm.settings
    val otp by s.deleteOtpAfter.flow.collectAsStateWithLifecycle()
    val promo by s.deletePromotionalAfter.flow.collectAsStateWithLifecycle()
    val blocked by s.deleteBlockedAfter.flow.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf("") }
    SettingsScaffold(stringResource(R.string.message_rules), onBack) {
        Text(stringResource(R.string.rules_note), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingRow(stringResource(R.string.rule_otp), retentionLabel(otp)) { dialog = "otp" }
        SettingRow(stringResource(R.string.rule_promo), retentionLabel(promo)) { dialog = "promo" }
        SettingRow(stringResource(R.string.rule_blocked), retentionLabel(blocked)) { dialog = "blocked" }
    }
    val setting = when (dialog) { "otp" -> s.deleteOtpAfter; "promo" -> s.deletePromotionalAfter; "blocked" -> s.deleteBlockedAfter; else -> null }
    setting?.let { st ->
        val opts = if (dialog == "otp") listOf(Retention.NEVER, Retention.DAY_1, Retention.DAYS_3, Retention.WEEK_1)
        else listOf(Retention.NEVER, Retention.WEEK_1, Retention.MONTH_1)
        ChoiceDialog(stringResource(R.string.delete_after), opts, st.value, { retentionLabel(it) }, { st.value = it; vm.rulesChanged(); dialog = "" }, { dialog = "" })
    }
}

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val s = vm.settings
    val def by s.notifyDefault.flow.collectAsStateWithLifecycle()
    val promo by s.notifyPromotional.flow.collectAsStateWithLifecycle()
    val otp by s.notifyOtp.flow.collectAsStateWithLifecycle()
    val rem by s.notifyReminders.flow.collectAsStateWithLifecycle()
    val otpDelete by s.deleteOtpAfterCopy.flow.collectAsStateWithLifecycle()
    SettingsScaffold(stringResource(R.string.notifications), onBack) {
        SwitchRow(stringResource(R.string.notify_default), def) { s.notifyDefault.value = it }
        SwitchRow(stringResource(R.string.notify_promo), promo) { s.notifyPromotional.value = it }
        SwitchRow(stringResource(R.string.notify_otp), otp) { s.notifyOtp.value = it }
        SwitchRow(stringResource(R.string.otp_delete_after_copy), otpDelete, subtitle = stringResource(R.string.otp_delete_after_copy_desc)) { s.deleteOtpAfterCopy.value = it }
        SwitchRow(stringResource(R.string.notify_reminders), rem) { s.notifyReminders.value = it }
        SettingRow(stringResource(R.string.system_notification_settings)) {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
    }
}

@Composable
fun SwipeSettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s = vm.settings
    val tabs by s.changeTabsOnSwipe.flow.collectAsStateWithLifecycle()
    val left by s.leftSwipe.flow.collectAsStateWithLifecycle()
    val right by s.rightSwipe.flow.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf("") }
    SettingsScaffold(stringResource(R.string.swipe_actions), onBack) {
        SwitchRow(stringResource(R.string.change_tabs_on_swipe), tabs, subtitle = stringResource(R.string.change_tabs_on_swipe_desc)) { s.changeTabsOnSwipe.value = it }
        SettingRow(stringResource(R.string.left_swipe), stringResource(swipeLabel(left))) { if (!tabs) dialog = "left" }
        SettingRow(stringResource(R.string.right_swipe), stringResource(swipeLabel(right))) { if (!tabs) dialog = "right" }
    }
    val st = when (dialog) { "left" -> s.leftSwipe; "right" -> s.rightSwipe; else -> null }
    st?.let { setting ->
        ChoiceDialog(stringResource(if (dialog == "left") R.string.left_swipe else R.string.right_swipe), SwipeAction.entries, setting.value,
            { stringResource(swipeLabel(it)) }, { setting.value = it; dialog = "" }, { dialog = "" })
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    com.dns.sms.ui.finance.TouchGuard()
    val ctx = LocalContext.current
    SettingsScaffold(stringResource(R.string.about), onBack) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_identity), Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_data), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Play requires the privacy policy to be reachable inside the app (Requirement.md section 8).
            TextButton(onClick = {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, ctx.getString(R.string.privacy_policy_url).toUri())) }
                catch (_: ActivityNotFoundException) { }
            }, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.privacy_policy)) }
            // Local crash log: only shared when the user chooses; contains no message content.
            val crashes = remember { com.dns.sms.util.CrashLog.read(ctx) }
            if (crashes.isNotEmpty()) Row {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.crash_log_subject, BuildConfig.VERSION_NAME))
                        .putExtra(Intent.EXTRA_TEXT, crashes)
                    try { ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_crash_log))) }
                    catch (_: ActivityNotFoundException) { }
                }) { Text(stringResource(R.string.share_crash_log)) }
                TextButton(onClick = { com.dns.sms.util.CrashLog.clear(ctx) }) { Text(stringResource(R.string.clear_crash_log)) }
            }
        }
    }
}

/** App status / health check: default SMS role, notifications, battery optimisation (OEM guidance). */
@Composable
fun AppStatusScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val isDefault by vm.gate.isDefault.collectAsStateWithLifecycle()
    var batteryOk by rememberSaveable { mutableStateOf(vm.isIgnoringBatteryOptimizations()) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.gate.refresh() }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { batteryOk = vm.isIgnoringBatteryOptimizations() }
    val notificationsOn = androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    SettingsScaffold(stringResource(R.string.app_status), onBack) {
        SettingRow(stringResource(R.string.default_sms_app), stringResource(if (isDefault) R.string.yes else R.string.no)) {
            if (!isDefault) roleLauncher.launch(vm.gate.requestIntent())
        }
        SettingRow(stringResource(R.string.notifications), stringResource(if (notificationsOn) R.string.on else R.string.off)) {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
        SettingRow(
            stringResource(R.string.battery_optimisation),
            stringResource(if (batteryOk) R.string.battery_unrestricted else R.string.battery_optimised),
            subtitle = stringResource(R.string.battery_desc),
        ) {
            // Opens the system list; no REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission (Play policy).
            batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val s = vm.settings
    val account by s.driveAccount.flow.collectAsStateWithLifecycle()
    val auto by s.autoBackup.flow.collectAsStateWithLifecycle()
    val lastAt by s.lastBackupAt.flow.collectAsStateWithLifecycle()
    val lastBytes by s.lastBackupBytes.flow.collectAsStateWithLifecycle()
    val lastError by s.lastBackupError.flow.collectAsStateWithLifecycle()
    val restoring by vm.restoreRunning.collectAsStateWithLifecycle()
    val busy = vm.busy.collectAsStateWithLifecycle().value || restoring
    val running by vm.backupRunning.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val consent by vm.consent.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf("") }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.onConsentResult(it.resultCode, it.data) }
    val accountLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onAccountChosen(it.data) }
    LaunchedEffect(consent) { consent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) } }
    LaunchedEffect(message) { message?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.message.value = null } }

    SettingsScaffold(stringResource(R.string.backup_restore), onBack) {
        Column(Modifier.padding(16.dp)) {
            if (lastAt > 0) Text(
                stringResource(R.string.last_backup, DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()).format(Instant.ofEpochMilli(lastAt).atZone(ZoneId.systemDefault())), sizeText(lastBytes)),
                style = MaterialTheme.typography.labelLarge,
            )
            if (lastError == "screen-lock") Text(stringResource(R.string.backup_needs_lock), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            else if (lastError.isNotBlank()) Text(stringResource(R.string.last_backup_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.backup_desc), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { vm.backupNow() }, enabled = account.isNotBlank() && !running && !busy,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp),
                ) { Text(stringResource(R.string.back_up).uppercase()) }
                Button(
                    onClick = { vm.restore() }, enabled = account.isNotBlank() && !running && !busy,
                    modifier = Modifier.weight(1f).padding(start = 12.dp), shape = RoundedCornerShape(4.dp),
                ) { Text(stringResource(R.string.restore).uppercase()) }
            }
            if (running || busy) Row(Modifier.padding(top = 12.dp)) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text(stringResource(if (busy) R.string.restoring else R.string.backing_up))
            }
        }
        SettingRow(stringResource(R.string.drive_account), subtitle = account.ifBlank { stringResource(R.string.drive_choose_account) }, icon = Icons.Filled.Cloud, tint = Gray) {
            accountLauncher.launch(vm.accountChooserIntent())
        }
        SettingRow(stringResource(R.string.auto_backup), subtitle = autoLabel(auto), icon = Icons.Filled.Timer, tint = Gray) { dialog = "auto" }
        if (lastAt > 0) SettingRow(stringResource(R.string.delete_drive_backup), subtitle = stringResource(R.string.delete_drive_backup_desc), icon = Icons.Filled.Delete, tint = Gray) {
            if (!busy && !running) dialog = "delete-remote"
        }
    }
    when (dialog) {
        "auto" -> ChoiceDialog(stringResource(R.string.auto_backup), AutoBackup.entries, auto, { autoLabel(it) }, { s.autoBackup.value = it; vm.autoBackupChanged(); dialog = "" }, { dialog = "" })
        "delete-remote" -> ConfirmDialog(
            title = stringResource(R.string.delete_drive_backup),
            body = stringResource(R.string.delete_drive_backup_body),
            confirm = stringResource(R.string.delete),
            onConfirm = { dialog = ""; vm.deleteDriveBackup() },
            onDismiss = { dialog = "" },
        )
    }
}

@Composable
private fun autoLabel(a: AutoBackup) = stringResource(when (a) { AutoBackup.MANUAL -> R.string.manual; AutoBackup.DAILY -> R.string.daily; AutoBackup.WEEKLY -> R.string.weekly; AutoBackup.MONTHLY -> R.string.monthly })

private fun sizeText(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0)
    else -> "${bytes / 1000} KB"
}

/** Settings > Quick replies: edit, add, remove, reset (shown as tiles above the composer). */
@Composable
fun QuickRepliesScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s = vm.settings
    val replies by s.quickReplies.flow.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Int?>(null) } // index, or -1 = new
    SettingsScaffold(stringResource(R.string.edit_quick_replies), onBack) {
        replies.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r, Modifier.weight(1f).clickable { editing = i }.padding(vertical = 14.dp))
                IconButton(onClick = { s.quickReplies.value = replies.filterIndexed { j, _ -> j != i } }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.delete_quick_reply, r))
                }
            }
        }
        if (replies.size < MAX_QUICK_REPLIES) TextButton(onClick = { editing = -1 }, Modifier.padding(start = 8.dp)) {
            Text(stringResource(R.string.add_quick_reply))
        }
        TextButton(onClick = { s.quickReplies.reset() }, Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.reset_quick_replies)) }
    }
    editing?.let { i ->
        TextDialog(
            stringResource(if (i < 0) R.string.add_quick_reply else R.string.edit_quick_reply),
            if (i < 0) "" else replies[i], stringResource(R.string.quick_reply_hint),
            { text ->
                val t = text.trim()
                if (t.isNotEmpty()) s.quickReplies.value = if (i < 0) replies + t else replies.mapIndexed { j, r -> if (j == i) t else r }
            },
            { editing = null }, maxLength = 120,
        )
    }
}

private const val MAX_QUICK_REPLIES = 12
