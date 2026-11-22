package com.dns.sms.ui.reminders

import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dns.sms.R
import com.dns.sms.data.Reminder
import com.dns.sms.security.UrlRisk
import com.dns.sms.ui.SuspiciousAmber
import com.dns.sms.ui.finance.TouchGuard
import com.dns.sms.ui.textRes
import com.dns.sms.ui.titleRes
import com.dns.sms.data.ShopOrder
import com.dns.sms.ui.theme.Cards
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val Basket = Color(0xFFFFA000)
val BannerPurple = Color(0xFF5E35B1)

fun kindIcon(kind: String): ImageVector = when (kind) {
    "BILL" -> Icons.AutoMirrored.Filled.ReceiptLong
    "MOVIE" -> Icons.Filled.Movie
    "BUS" -> Icons.Filled.DirectionsBus
    "FLIGHT" -> Icons.Filled.Flight
    else -> Icons.Filled.Event
}

fun fmt(millis: Long, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
fun statusLabel(status: String): String = stringResource(
    when (status) {
        "DISPATCHED" -> R.string.status_dispatched
        "OUT_FOR_DELIVERY" -> R.string.status_out_for_delivery
        "ARRIVING" -> R.string.status_arriving
        "DELIVERED" -> R.string.status_delivered_order
        "CANCELLED" -> R.string.status_cancelled
        else -> R.string.status_placed
    },
)

@Composable
fun ReminderCard(r: Reminder, onClick: (() -> Unit)?, onDismiss: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(Cards, RoundedCornerShape(6.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = 64.dp).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kindIcon(r.kind), contentDescription = null, tint = Basket)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(fmt(r.dueAt, "EEE, d MMM yyyy · h:mm a"), r.detail).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDismiss != null) IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.dismiss)) }
    }
}

/** Merchant card like the reference: basket icon, merchant, item line, bold status, last updated, Track chip. */
@Composable
fun OrderCard(o: ShopOrder, onClick: () -> Unit) {
    var confirmUrl by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().background(Cards, RoundedCornerShape(6.dp)).clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ShoppingBasket, null, tint = Basket)
            Text(o.merchant, Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 0.dp).heightIn(min = 1.dp).background(MaterialTheme.colorScheme.background))
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(o.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(horizontalAlignment = Alignment.End) {
                val done = o.status == "DELIVERED"
                Text(
                    if (o.status == "ARRIVING" && o.expectedAt != null) stringResource(R.string.arriving_on, fmt(o.expectedAt, "d MMM")) else statusLabel(o.status),
                    fontWeight = FontWeight.Bold,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(stringResource(R.string.last_updated_on, fmt(o.statusAt, "dd MMM")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        safeUrl(o.url)?.let { url ->
            TrackChip { confirmUrl = url }
        }
    }
    confirmUrl?.let { url -> OpenLinkDialog(url) { confirmUrl = null } }
}

@Composable
fun TrackChip(onClick: () -> Unit) {
    Row(
        Modifier.padding(start = 12.dp, bottom = 10.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.LocationOn, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.track), Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}

/** Only http(s) links are ever offered (Requirement.md section 9). */
fun safeUrl(raw: String?): String? {
    val u = raw?.trim() ?: return null
    val scheme = runCatching { u.toUri().scheme?.lowercase() }.getOrNull()
    return u.takeIf { scheme == "http" || scheme == "https" }
}

/**
 * Shows the real address and asks before opening it in the browser, with the risk verdict. A dangerous
 * link needs a second tap ("Open anyway"). Callers without a sender or body get a link-only assessment.
 */
@Composable
fun OpenLinkDialog(url: String, assessment: UrlRisk.Assessment? = null, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val risk = assessment ?: remember(url) { UrlRisk.assess("", "", url) }
    val dangerous = risk.level == UrlRisk.Level.DANGEROUS
    var armed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.open_link_title)) },
        text = {
            Column {
                TouchGuard()
                Text(
                    stringResource(risk.level.titleRes()), fontWeight = FontWeight.SemiBold,
                    color = when (risk.level) {
                        UrlRisk.Level.DANGEROUS -> MaterialTheme.colorScheme.error
                        UrlRisk.Level.SUSPICIOUS -> SuspiciousAmber
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                risk.reasons.forEach { Text("\u2022 " + stringResource(it.textRes()), style = MaterialTheme.typography.bodySmall) }
                // UrlRisk strips hidden characters for its checks; show the address without them too.
                Text(stringResource(R.string.open_link_body, UrlRisk.stripHidden(url)), Modifier.padding(top = 8.dp))
                // S10: show where the link really goes; warn on look-alike (non-ASCII) domains.
                com.dns.sms.util.LinkFinder.asciiHost(url)?.let { host ->
                    Text(stringResource(R.string.link_real_host, host), Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
                }
                if (com.dns.sms.util.LinkFinder.isLookalike(url)) Text(
                    stringResource(R.string.link_lookalike_warning), Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (dangerous && !armed) { armed = true; return@TextButton }
                onDone()
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, UrlRisk.stripHidden(url).toUri()).addCategory(Intent.CATEGORY_BROWSABLE)) }
                catch (_: ActivityNotFoundException) { }
            }) {
                Text(
                    stringResource(if (!dangerous) R.string.open else if (armed) R.string.open_anyway_again else R.string.open_anyway),
                    color = if (dangerous) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun OrdersBanner() {
    Text(
        stringResource(R.string.orders_banner),
        Modifier.fillMaxWidth().padding(8.dp).background(BannerPurple, RoundedCornerShape(6.dp)).padding(12.dp),
        color = Color.White, style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(), Modifier.padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

val CardSpacing = Arrangement.spacedBy(8.dp)
