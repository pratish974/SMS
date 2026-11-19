package com.dns.sms.ui.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dns.sms.R
import com.dns.sms.ui.messages.CenteredText
import com.dns.sms.ui.theme.Cards
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
fun PastRemindersScreen(onBack: () -> Unit, vm: RemindersViewModel = hiltViewModel()) {
    val past by vm.past.collectAsStateWithLifecycle()
    SubScreen(stringResource(R.string.past_reminders), onBack) { m ->
        if (past.isEmpty()) CenteredText(stringResource(R.string.no_past_reminders), m)
        else LazyColumn(m.fillMaxSize().padding(12.dp), verticalArrangement = CardSpacing) {
            items(past, key = { it.id }) { r -> ReminderCard(r, onClick = null, onDismiss = { vm.delete(r) }) }
        }
    }
}

@Composable
fun PastOrdersScreen(onBack: () -> Unit, onOpenOrder: (String) -> Unit, vm: RemindersViewModel = hiltViewModel()) {
    val orders by vm.allOrders.collectAsStateWithLifecycle()
    SubScreen(stringResource(R.string.past_orders), onBack) { m ->
        Column(m.fillMaxSize()) {
            OrdersBanner()
            if (orders.isEmpty()) CenteredText(stringResource(R.string.no_orders))
            else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = CardSpacing) {
                items(orders, key = { it.key }) { o -> OrderCard(o) { onOpenOrder(o.key) } }
            }
        }
    }
}

@Composable
fun OrderDetailScreen(onBack: () -> Unit, vm: OrderViewModel = hiltViewModel()) {
    val order by vm.order.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    var confirmUrl by remember { mutableStateOf<String?>(null) }
    SubScreen(stringResource(R.string.order_details), onBack) { m ->
        Column(m.fillMaxSize()) {
            OrdersBanner()
            order?.let { o ->
                Column(Modifier.fillMaxWidth().background(Cards).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ShoppingBasket, null, tint = Basket)
                        Text(o.merchant, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(o.title, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium)
                    Text(statusLabel(o.status), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    safeUrl(o.url)?.let { url -> Row(Modifier.padding(top = 8.dp)) { TrackChip { confirmUrl = url } } }
                }
            }
            Text(
                stringResource(R.string.delivery_details), Modifier.padding(12.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.padding(horizontal = 8.dp).background(Cards, RoundedCornerShape(6.dp))) {
                events.forEachIndexed { i, e ->
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(fmt(e.at, "EEE, dd MMM"), Modifier.weight(0.4f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Text(statusLabel(e.status), Modifier.weight(0.6f))
                    }
                    if (i < events.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.background)
                }
            }
        }
    }
    confirmUrl?.let { OpenLinkDialog(it) { confirmUrl = null } }
}

@Composable
fun AddReminderScreen(onBack: () -> Unit, vm: RemindersViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var time by rememberSaveable { mutableStateOf(LocalTime.of(9, 0).toString()) }
    val d = LocalDate.parse(date)
    val t = LocalTime.parse(time)
    val dueAt = d.atTime(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    SubScreen(stringResource(R.string.add_reminder), onBack) { m ->
        Column(m.fillMaxSize().imePadding().padding(16.dp)) {
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(120) },
                label = { Text(stringResource(R.string.reminder_title_hint)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                OutlinedButton(onClick = {
                    DatePickerDialog(ctx, { _, y, mo, day -> date = LocalDate.of(y, mo + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                }, Modifier.weight(1f)) { Text(fmt(dueAt, "EEE, d MMM yyyy")) }
                OutlinedButton(onClick = {
                    TimePickerDialog(ctx, { _, h, min -> time = LocalTime.of(h, min).toString() }, t.hour, t.minute, DateFormat.is24HourFormat(ctx)).show()
                }, Modifier.weight(1f).padding(start = 12.dp)) { Text(fmt(dueAt, if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a")) }
            }
            Button(
                onClick = { vm.addManual(title, dueAt); onBack() },
                enabled = title.isNotBlank() && dueAt > System.currentTimeMillis(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold) }
        }
    }
}
