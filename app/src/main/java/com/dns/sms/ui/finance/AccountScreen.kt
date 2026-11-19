package com.dns.sms.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dns.sms.R
import com.dns.sms.parse.Money
import com.dns.sms.ui.messages.CenteredText
import com.dns.sms.ui.theme.Cards
import java.math.BigDecimal

/** One account: latest balance and the debits/credits we parsed (masked like the list). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit, vm: AccountViewModel = hiltViewModel()) {
    SecureScreen()
    val account by vm.account.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val revealed by vm.revealed.collectAsStateWithLifecycle()
    fun amount(s: String?) = if (!revealed) "*****" else s?.let { runCatching { Money.formatInr(BigDecimal(it)) }.getOrNull() } ?: "—"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("${vm.bank} ${vm.mask}") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            account?.let { a ->
                Column(Modifier.fillMaxWidth().background(Cards, RoundedCornerShape(6.dp)).padding(16.dp)) {
                    Text(stringResource(R.string.avbl_bal_on, longDate(a.balanceAt)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(amount(a.balance), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.finance_disclaimer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                stringResource(R.string.transactions_from_sms), Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (txns.isEmpty()) {
                CenteredText(stringResource(R.string.no_transactions))
            } else LazyColumn(Modifier.background(Cards, RoundedCornerShape(6.dp))) {
                items(txns, key = { it.messageId }) { t ->
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            // Words, not just colour, say which way the money moved.
                            Text(stringResource(if (t.type == "CREDIT") R.string.credited else R.string.debited))
                            Text(longDate(t.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            (if (t.type == "CREDIT") "+ " else "− ") + amount(t.amount),
                            fontWeight = FontWeight.SemiBold,
                            color = if (t.type == "CREDIT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                }
            }
        }
    }
}
