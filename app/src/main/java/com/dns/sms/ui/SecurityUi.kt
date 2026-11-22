package com.dns.sms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dns.sms.R
import com.dns.sms.security.SensitiveText
import com.dns.sms.security.UrlRisk
import com.dns.sms.ui.messages.ConfirmDialog

/** Amber for suspicious links (red is reserved for dangerous ones). */
val SuspiciousAmber = Color(0xFFFFB300)

fun UrlRisk.Level.titleRes(): Int = when (this) {
    UrlRisk.Level.SAFE -> R.string.risk_title_safe
    UrlRisk.Level.UNKNOWN -> R.string.risk_title_unknown
    UrlRisk.Level.SUSPICIOUS -> R.string.risk_title_suspicious
    UrlRisk.Level.DANGEROUS -> R.string.risk_title_dangerous
}

fun UrlRisk.Reason.textRes(): Int = when (this) {
    UrlRisk.Reason.BRAND_IMPERSONATION -> R.string.risk_brand_impersonation
    UrlRisk.Reason.SENDER_DOMAIN_MISMATCH -> R.string.risk_sender_domain_mismatch
    UrlRisk.Reason.SHORTENER -> R.string.risk_shortener
    UrlRisk.Reason.RISKY_TLD -> R.string.risk_risky_tld
    UrlRisk.Reason.IP_OR_PORT -> R.string.risk_ip_or_port
    UrlRisk.Reason.USERINFO_TRICK -> R.string.risk_userinfo_trick
    UrlRisk.Reason.LOOKALIKE -> R.string.risk_lookalike
    UrlRisk.Reason.HIDDEN_CHARS -> R.string.risk_hidden_chars
    UrlRisk.Reason.NOT_HTTPS -> R.string.risk_not_https
    UrlRisk.Reason.UNUSUAL_SHAPE -> R.string.risk_unusual_shape
    UrlRisk.Reason.SCAM_WORDING -> R.string.risk_scam_wording
    UrlRisk.Reason.UNVERIFIED_SENDER -> R.string.risk_unverified_sender
}

private fun SensitiveText.Kind.textRes(): Int = when (this) {
    SensitiveText.Kind.OTP -> R.string.sensitive_otp
    SensitiveText.Kind.CARD_NUMBER -> R.string.sensitive_card
    SensitiveText.Kind.CVV -> R.string.sensitive_cvv
    SensitiveText.Kind.AADHAAR -> R.string.sensitive_aadhaar
    SensitiveText.Kind.PAN -> R.string.sensitive_pan
    SensitiveText.Kind.UPI_PIN -> R.string.sensitive_upi_pin
}

/** Small red warning strip shown above the text of a suspicious received message. */
@Composable
fun ScamWarningRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f), RoundedCornerShape(6.dp)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        Text(text, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
    }
}

/** "Sharing sensitive information?" shown before a message with secrets (OTP, card, CVV, ...) is sent. */
@Composable
fun SensitiveSendDialog(kinds: Set<SensitiveText.Kind>, onSend: () -> Unit, onCancel: () -> Unit) {
    val found = kinds.map { stringResource(it.textRes()) }.joinToString(", ")
    ConfirmDialog(
        title = stringResource(R.string.sensitive_title),
        body = stringResource(R.string.sensitive_body, found),
        confirm = stringResource(R.string.send_anyway),
        onConfirm = onSend, onDismiss = onCancel,
    )
}

/** Asked before a message holding a one-time code is copied, shared or forwarded. */
@Composable
fun ShareOtpDialog(onContinue: () -> Unit, onCancel: () -> Unit) = ConfirmDialog(
    title = stringResource(R.string.share_otp_title),
    body = stringResource(R.string.share_otp_body),
    confirm = stringResource(R.string.continue_action),
    onConfirm = onContinue, onDismiss = onCancel,
)
