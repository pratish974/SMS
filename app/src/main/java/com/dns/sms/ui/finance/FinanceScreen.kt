package com.dns.sms.ui.finance

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dns.sms.R
import com.dns.sms.parse.AccountGroups
import androidx.compose.ui.res.pluralStringResource
import com.dns.sms.parse.Money
import com.dns.sms.ui.messages.EmptyState
import com.dns.sms.ui.theme.Cards
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MASK = "*****"
private const val RECENT_MS = 3L * 24 * 60 * 60 * 1000
val RecentDot = Color(0xFFE57373)

@Composable
fun FinanceScreen(onOpenAccount: (AccountGroups.Merged) -> Unit, modifier: Modifier = Modifier, vm: FinanceViewModel = hiltViewModel()) {
    SecureScreen()
    val ctx = LocalContext.current
    val showBalance = stringResource(R.string.show_balance)
    val banks by vm.banks.collectAsStateWithLifecycle()
    val revealed by vm.revealed.collectAsStateWithLifecycle()
    var needsLock by remember { mutableStateOf(false) }
    val toggle: (Boolean) -> Unit = { on ->
        if (!on) vm.lock.hide()
        else (ctx as? FragmentActivity)?.let { a -> vm.lock.reveal(a) { needsLock = true } }
    }
    if (needsLock) AlertDialog(
        onDismissRequest = { needsLock = false },
        title = { Text(stringResource(R.string.finance_lock_needed_title)) },
        text = { Text(stringResource(R.string.finance_no_lock)) },
        confirmButton = {
            TextButton(onClick = {
                needsLock = false
                try { ctx.startActivity(FinanceLock.setupIntent()) }
                catch (_: ActivityNotFoundException) { ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            }) { Text(stringResource(R.string.set_up)) }
        },
        dismissButton = { TextButton(onClick = { needsLock = false }) { Text(stringResource(R.string.cancel)) } },
    )
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.finance_disclaimer), Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.show_balance), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp),
            )
            Switch(
                checked = revealed, onCheckedChange = toggle,
                modifier = Modifier.semantics { contentDescription = showBalance },
            )
        }
        val list = banks
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyState(stringResource(R.string.finance_empty))
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                item(key = "header") {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccountBalance, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.accounts).uppercase(), Modifier.padding(start = 8.dp).weight(1f),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        )
                        Box(Modifier.size(6.dp).background(RecentDot, CircleShape))
                        Text(
                            stringResource(R.string.recently_updated), Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // One card per bank; differently-printed endings of the same account are one row.
                list.forEach { b ->
                    item(key = "bank:" + b.bank) {
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(b.bank, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                pluralStringResource(R.plurals.bank_accounts, b.accounts.size, b.accounts.size),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(Modifier.background(Cards, RoundedCornerShape(6.dp))) {
                            b.accounts.forEachIndexed { i, a ->
                                AccountRow(a, revealed) { onOpenAccount(a) }
                                if (i < b.accounts.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.background)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(m: AccountGroups.Merged, revealed: Boolean, onClick: () -> Unit) {
    val a = m.latest
    val recent = System.currentTimeMillis() - a.balanceAt < RECENT_MS
    val amount = a.balance?.let { runCatching { Money.formatInr(BigDecimal(it)) }.getOrNull() } ?: "—"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(if (recent) RecentDot else Color.Transparent, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("${m.bank} ${m.mask}", style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.avbl_bal_on, longDate(a.balanceAt)),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (revealed) amount else MASK,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { if (!revealed) contentDescription = "Balance hidden" },
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun longDate(millis: Long): String =
    DateTimeFormatter.ofPattern("dd MMMM yy", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
