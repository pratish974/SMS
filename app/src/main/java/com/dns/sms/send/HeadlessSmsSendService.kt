package com.dns.sms.send

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.dns.sms.di.entry
import com.dns.sms.intent.ComposeIntentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** "Respond via message" from the incoming-call screen. Required for a default SMS app. */
class HeadlessSmsSendService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) { stopSelf(startId); return START_NOT_STICKY }
        val draft = ComposeIntentParser.parse(intent.dataString)
        val text = ComposeIntentParser.cleanBody(intent.getStringExtra(Intent.EXTRA_TEXT))
        if (draft == null || text == null || draft.recipients.isEmpty()) { stopSelf(startId); return START_NOT_STICKY }
        scope.launch {
            try {
                val outbox = entry().outbox()
                draft.recipients.forEach { outbox.sendTo(listOf(it), text) }
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
