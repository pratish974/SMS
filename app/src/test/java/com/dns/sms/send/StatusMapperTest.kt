package com.dns.sms.send

import com.dns.sms.send.StatusMapper as S
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusMapperTest {
    @Test fun sms() {
        assertEquals(MessageStatus.RECEIVED, S.sms(S.SMS_INBOX, -1, false))
        assertEquals(MessageStatus.SENDING, S.sms(S.SMS_OUTBOX, -1, false))
        assertEquals(MessageStatus.SENDING, S.sms(S.SMS_QUEUED, -1, false))
        assertEquals(MessageStatus.SENT, S.sms(S.SMS_SENT, 32, false))
        assertEquals(MessageStatus.DELIVERED, S.sms(S.SMS_SENT, S.SMS_STATUS_COMPLETE, false))
        assertEquals(MessageStatus.DELIVERED, S.sms(S.SMS_SENT, -1, true))
        assertEquals(MessageStatus.FAILED, S.sms(S.SMS_SENT, S.SMS_STATUS_FAILED, false))
        assertEquals(MessageStatus.FAILED, S.sms(S.SMS_FAILED, -1, false))
    }

}
