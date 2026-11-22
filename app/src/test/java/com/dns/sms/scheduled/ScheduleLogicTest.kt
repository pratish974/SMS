package com.dns.sms.scheduled

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleLogicTest {
    private val min = 60_000L

    @Test fun nextAlarmIsEarliestEvenIfMissed() {
        assertEquals(5 * min, ScheduleLogic.nextAlarm(listOf(30 * min, 5 * min, 10 * min)))
        assertNull(ScheduleLogic.nextAlarm(emptyList()))
    }

    @Test fun dueIncludesMissedWhileOff() {
        val now = 100 * min
        val due = ScheduleLogic.due(listOf(10 * min, 100 * min, 101 * min), { it }, now)
        assertEquals(listOf(10 * min, 100 * min), due)
    }

    @Test fun lateFlag() {
        assertFalse(ScheduleLogic.isLate(0, 4 * min))
        assertTrue(ScheduleLogic.isLate(0, 6 * min))
    }

    @Test fun choiceMustBeInFuture() {
        val now = 1_000_000L
        assertFalse(ScheduleLogic.isValidChoice(now, now))
        assertFalse(ScheduleLogic.isValidChoice(now - 1, now))
        // Next whole minute picked 20 s before it starts must be accepted (was rejected -> sent now).
        assertTrue(ScheduleLogic.isValidChoice(now + 20_000, now))
        assertTrue(ScheduleLogic.isValidChoice(now + 60_000, now))
    }

    @Test fun sendWithoutTimeSendsNow() {
        assertEquals(ScheduleLogic.SendMode.NOW, ScheduleLogic.sendMode(null, 1_000))
    }

    @Test fun sendWithFutureTimeSchedulesNeverSendsNow() {
        assertEquals(ScheduleLogic.SendMode.SCHEDULE, ScheduleLogic.sendMode(1_001, 1_000))
        assertEquals(ScheduleLogic.SendMode.SCHEDULE, ScheduleLogic.sendMode(1_000 + 7L * 24 * 3600_000, 1_000))
    }

    @Test fun sendAfterPickedTimePassedDoesNotSend() {
        assertEquals(ScheduleLogic.SendMode.TIME_PASSED, ScheduleLogic.sendMode(1_000, 1_000))
        assertEquals(ScheduleLogic.SendMode.TIME_PASSED, ScheduleLogic.sendMode(500, 1_000))
    }

    @Test fun scheduledMessageIsNotDueBeforeItsTime() {
        val at = 10_000L
        assertTrue(ScheduleLogic.due(listOf(at), { it }, at - 1).isEmpty())
        assertEquals(listOf(at), ScheduleLogic.due(listOf(at), { it }, at))
        assertEquals(at, ScheduleLogic.nextAlarm(listOf(at, at + 5)))
    }
}
