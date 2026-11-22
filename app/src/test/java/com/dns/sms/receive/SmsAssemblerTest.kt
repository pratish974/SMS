package com.dns.sms.receive

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsAssemblerTest {
    @Test fun joinsMultipartInOrder() {
        val out = SmsAssembler.assemble(listOf(
            SmsPart("VM-BANK", "Part one, ", 100),
            SmsPart("VM-BANK", "part two.", 101),
        ))
        assertEquals(listOf(IncomingMessage("VM-BANK", "Part one, part two.", 100)), out)
    }

    @Test fun separatesSenders() {
        val out = SmsAssembler.assemble(listOf(SmsPart("A", "x", 1), SmsPart("B", "y", 2)))
        assertEquals(2, out.size)
    }

    @Test fun emptyInput() {
        assertEquals(emptyList<IncomingMessage>(), SmsAssembler.assemble(emptyList()))
    }
}
