package com.dns.sms.ui.reminders

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dns.sms.R

@Composable
fun RemindersScreen(
    onPastReminders: () -> Unit,
    onPastOrders: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenThread: (Long) -> Unit,
    modifier: Modifier = Modifier,
    vm: RemindersViewModel = hiltViewModel(),
) {
    val upcoming by vm.upcoming.collectAsStateWithLifecycle()
    val orders by vm.activeOrders.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.reminders_caption), Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val list = upcoming.orEmpty()
        when {
            !ready && list.isEmpty() && orders.isEmpty() -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Image(painterResource(R.drawable.ic_empty_tray), null, Modifier.size(96.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.reminders_setting_up), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.reminders_setting_up_body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            else -> LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = CardSpacing) {
                if (list.isEmpty()) item(key = "none") {
                    Text(
                        stringResource(R.string.no_upcoming), Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    item(key = "h-up") { SectionTitle(stringResource(R.string.upcoming)) }
                    items(list, key = { "r-${it.id}" }) { r ->
                        ReminderCard(r, onClick = r.threadId?.let { t -> { onOpenThread(t) } }, onDismiss = { vm.dismiss(r) })
                    }
                }
                if (orders.isNotEmpty()) {
                    item(key = "h-orders") { SectionTitle(stringResource(R.string.active_orders)) }
                    items(orders, key = { "o-${it.key}" }) { o -> OrderCard(o) { onOpenOrder(o.key) } }
                }
                item(key = "buttons") {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onPastReminders, Modifier.weight(1f), shape = RoundedCornerShape(4.dp)) { Text(stringResource(R.string.past_reminders).uppercase()) }
                        Button(onClick = onPastOrders, Modifier.weight(1f), shape = RoundedCornerShape(4.dp)) { Text(stringResource(R.string.past_orders).uppercase()) }
                    }
                }
            }
        }
        // End padding keeps the text clear of the FAB.
        Row(Modifier.padding(start = 16.dp, end = 88.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.VerifiedUser, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.privacy_footer), Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
