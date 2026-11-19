package com.dns.sms.work

/** Pure helpers for the message-rules cleanup (kept apart from the provider so they are unit tested). */
object RuleDeletion {
    const val DELETE_CHUNK = 500

    /**
     * Candidates minus starred messages, in chunks small enough for one `_id IN (...)` delete. Starred ids
     * are filtered here instead of in SQL: `_id NOT IN (<every star>)` exceeds SQLite's limits with lakhs
     * of stars and the whole delete silently failed.
     */
    fun deletionChunks(candidates: Collection<Long>, starred: Set<Long>, chunkSize: Int = DELETE_CHUNK): List<List<Long>> =
        candidates.filter { it !in starred }.distinct().chunked(chunkSize)
}
