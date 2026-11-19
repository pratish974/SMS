package com.dns.sms.parse

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderOrderParserTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val received = ZonedDateTime.of(2026, 11, 19, 10, 0, 0, 0, ist).toInstant().toEpochMilli()
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, ist).toInstant().toEpochMilli()

    @Test fun billDue() {
        val r = ReminderParser.parse("VM-HDFCBK", "Your HDFC Bank Credit Card statement: total due Rs 12,340.00. Payment due date 05-Dec-26.", received, ist)!!
        assertEquals("BILL", r.kind)
        assertEquals(at(2026, 12, 5, 9, 0), r.dueAt)
        assertEquals("HDFC bill due · ₹ 12,340", r.title)
    }

    @Test fun flight() {
        val r = ReminderParser.parse("AD-INDGOI", "Booking confirmed! Flight 6E 2134 BOM-DEL, departure 25 Nov 2026 07:05. Check-in opens 48h before.", received, ist)!!
        assertEquals("FLIGHT", r.kind)
        assertEquals("Flight 6E 2134", r.title)
        assertEquals(at(2026, 11, 25, 7, 5), r.dueAt)
    }

    @Test fun movieAndBus() {
        assertEquals("MOVIE", ReminderParser.parse("BMSHOW", "Booking ID ABC123: 2 tickets for your movie at PVR Screen 3 on 22 Nov 2026 9:45 PM", received, ist)!!.kind)
        assertEquals("BUS", ReminderParser.parse("REDBUS", "Ticket booked: bus from Pune to Goa, seat 12, on 30/11/2026 10:30 PM", received, ist)!!.kind)
    }

    @Test fun skipsPastTrainsAndPaid() {
        assertNull(ReminderParser.parse("VM-HDFCBK", "Bill due date 01-Oct-26", received, ist))
        assertNull(ReminderParser.parse("IRCTCI", "Train 12345 coach B2 berth 33 on 25 Nov 2026 07:05", received, ist))
        assertNull(ReminderParser.parse("VM-BESCOM", "Thank you for your payment of Rs 500 for electricity bill due on 30 Nov 2026", received, ist))
    }

    @Test fun orderStatuses() {
        val shipped = OrderParser.parse("VM-AMAZON", "Your Amazon order for Google 30W charger has been shipped. Track: https://amzn.example/t/abc123", received, ist)!!
        assertEquals("Amazon", shipped.merchant)
        assertEquals("Google 30W charger", shipped.title)
        assertEquals("DISPATCHED", shipped.status)
        assertEquals("https://amzn.example/t/abc123", shipped.url)

        val out = OrderParser.parse("JD-EKARTL", "Out for delivery: Order #OD4357713 will reach you today. OTP 4421", received, ist)!!
        assertEquals("OUT_FOR_DELIVERY", out.status)
        assertEquals("OD4357713", out.orderId)
        assertEquals("Ekart", out.merchant)

        assertEquals("DELIVERED", OrderParser.parse("BLNKIT", "Your order ORD043561341 has been delivered.", received, ist)!!.status)
        assertEquals("CANCELLED", OrderParser.parse("BLNKIT", "Order ORD043561341 is cancelled. Refund initiated.", received, ist)!!.status)
        val arriving = OrderParser.parse("VM-AMAZON", "Your order for headphones is arriving 21 Nov 2026.", received, ist)!!
        assertEquals("ARRIVING", arriving.status)
        assertNotNull(arriving.expectedAt)
    }

    @Test fun notOrders() {
        assertNull(OrderParser.parse("VM-ICICIT", "Rs 500 debited from a/c XX439", received, ist))
        assertNull(OrderParser.parse("AMAZON", "Big sale! Order now", received, ist)) // no status, no id
    }
}
