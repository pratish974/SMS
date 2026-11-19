package com.dns.sms.ui.compose

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dns.sms.R
import com.dns.sms.data.ScheduledMessage
import com.dns.sms.scheduled.ScheduleLogic
import com.dns.sms.ui.theme.BubbleOut
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun scheduleLabel(ctx: Context, millis: Long): String {
    val pattern = if (DateFormat.is24HourFormat(ctx)) "EEE, d MMM, HH:mm" else "EEE, d MMM, h:mm a"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}

/** Date picker, then time picker. Past times (less than a minute ahead) are refused with a hint. */
fun pickDateTime(ctx: Context, initial: Long?, onPicked: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(initial ?: (System.currentTimeMillis() + 60 * 60 * 1000)).atZone(zone).toLocalDateTime()
    DatePickerDialog(ctx, { _, y, m, d ->
        fun pickTime(hour: Int, minute: Int) {
            TimePickerDialog(ctx, { _, h, min ->
                val at = LocalDateTime.of(y, m + 1, d, h, min).atZone(zone).toInstant().toEpochMilli()
                if (ScheduleLogic.isValidChoice(at, System.currentTimeMillis())) onPicked(at)
                else {
                    // Ask again instead of silently dropping the choice (Send would then go out now).
                    Toast.makeText(ctx, R.string.schedule_past, Toast.LENGTH_SHORT).show()
                    pickTime(h, min)
                }
            }, hour, minute, DateFormat.is24HourFormat(ctx)).show()
        }
        pickTime(start.hour, start.minute)
    }, start.year, start.monthValue - 1, start.dayOfMonth).apply {
        datePicker.minDate = System.currentTimeMillis() - 1000
    }.show()
}

/** Asks once to allow exact alarms so scheduled messages go out on the minute. */
@Composable
fun ExactTimingDialog(intent: Intent, onDone: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.exact_timing_title)) },
        text = { Text(stringResource(R.string.exact_timing_body)) },
        confirmButton = {
            TextButton(onClick = {
                onDone()
                try { ctx.startActivity(intent) } catch (_: ActivityNotFoundException) { }
            }) { Text(stringResource(R.string.allow)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.not_now)) } },
    )
}

/** A pending (or failed) scheduled message, styled like an outgoing bubble with its actions. */
@Composable
fun ScheduledItem(
    m: ScheduledMessage,
    heading: String?,
    onSendNow: () -> Unit,
    onChangeTime: () -> Unit,
    onEditText: (() -> Unit)?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val failed = m.state == ScheduledMessage.FAILED
    Row(
        modifier.fillMaxWidth().background(BubbleOut, RoundedCornerShape(8.dp)).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (failed) Icons.Filled.ErrorOutline else Icons.Filled.Schedule, null, Modifier.size(18.dp),
            tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            heading?.let { Text(it, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(
                if (failed) stringResource(R.string.scheduled_failed_short) + " · " + scheduleLabel(ctx, m.sendAt)
                else stringResource(R.string.scheduled_for, scheduleLabel(ctx, m.sendAt)),
                style = MaterialTheme.typography.labelMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(m.body, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
        androidx.compose.foundation.layout.Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.send_now)) }, onClick = { menu = false; onSendNow() })
                DropdownMenuItem(text = { Text(stringResource(R.string.change_time)) }, onClick = { menu = false; onChangeTime() })
                onEditText?.let { edit -> DropdownMenuItem(text = { Text(stringResource(R.string.edit_text)) }, onClick = { menu = false; edit() }) }
                DropdownMenuItem(text = { Text(stringResource(R.string.cancel_schedule)) }, onClick = { menu = false; onCancel() })
            }
        }
    }
}
