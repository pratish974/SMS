package com.dns.sms.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleDeletionTest {
    @Test fun starredAreNeverDeleted() {
        val chunks = RuleDeletion.deletionChunks(listOf(1, 2, 3, 4, 5), setOf(2L, 4L))
        assertEquals(listOf(listOf(1L, 3L, 5L)), chunks)
    }

    @Test fun chunksAreBounded() {
        val chunks = RuleDeletion.deletionChunks((1L..1203L).toList(), emptySet())
        assertEquals(listOf(500, 500, 203), chunks.map { it.size })
    }

    @Test fun lakhsOfStarsAreFine() {
        val stars = (1L..300_000L).toSet()
        val chunks = RuleDeletion.deletionChunks(listOf(5L, 300_001L, 300_002L), stars, chunkSize = 1)
        assertEquals(listOf(listOf(300_001L), listOf(300_002L)), chunks)
    }

    @Test fun nothingToDelete() {
        assertTrue(RuleDeletion.deletionChunks(emptyList(), setOf(1L)).isEmpty())
        assertTrue(RuleDeletion.deletionChunks(listOf(1L, 1L), setOf(1L)).isEmpty())
    }
}
