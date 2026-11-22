package com.dns.sms.classify

/**
 * Decides which folders a conversation shows in (Requirement.md section 7 order):
 * user override -> saved contact -> rules. Groups default to Personal.
 * A conversation also appears in any folder that one of its messages was moved to.
 */
object FolderResolver {

    fun threadCategory(
        threadId: Long,
        addresses: List<String>,
        snippet: String,
        overrides: Map<String, Category>,
        isContact: (String) -> Boolean,
    ): Category {
        if (addresses.size > 1) return overrides[SenderKey.ofGroup(threadId)] ?: Category.PERSONAL
        val address = addresses.firstOrNull().orEmpty()
        return RuleClassifier.classify(
            sender = address,
            body = snippet,
            isSavedContact = isContact(address),
            override = overrides[SenderKey.of(address)],
        )
    }

    fun folders(threadCategory: Category, movedMessageCategories: Collection<Category>): Set<Category> =
        linkedSetOf(threadCategory) + movedMessageCategories
}
