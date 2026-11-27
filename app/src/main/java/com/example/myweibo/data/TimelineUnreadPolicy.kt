package com.example.myweibo.data

import java.security.MessageDigest

/** An account ID here must already have been matched to the authenticated session. */
internal data class TimelineUnreadScope private constructor(
    val accountId: String,
    val kind: TimelineKind,
    val groupId: String?,
) {
    val storageKey: String by lazy(LazyThreadSafetyMode.NONE) {
        val route = if (groupId == null) "kind:${kind.name}" else "group:$groupId"
        MessageDigest.getInstance("SHA-256").digest("$accountId/$route".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    // User groups are the same chronological feed family as the two built-in streams.
    val requiresOrderedResponse: Boolean get() = groupId == null && kind == TimelineKind.SpecialFollowing

    companion object {
        fun of(verifiedAccountId: String?, kind: TimelineKind, groupId: String?): TimelineUnreadScope? {
            val account = verifiedAccountId?.takeIf { id ->
                id.isNotEmpty() && id != "0" && id.all { it in '0'..'9' }
            } ?: return null
            if (groupId != null) {
                val group = groupId.trim().takeIf { it.isNotEmpty() && it != "0" } ?: return null
                return TimelineUnreadScope(account, TimelineKind.SpecialFollowing, group)
            }
            return when (kind) {
                TimelineKind.Following, TimelineKind.FriendsCircle, TimelineKind.SpecialFollowing ->
                    TimelineUnreadScope(account, kind, null)
                TimelineKind.Hot, TimelineKind.Nearby, TimelineKind.Favorites, TimelineKind.Liked -> null
            }
        }
    }
}

internal data class TimelineUnreadHead(val statusId: String, val sortKey: Long)

internal data class TimelineUnreadSnapshot(
    val head: TimelineUnreadHead? = null,
    val unread: Set<String> = emptySet(),
    val orderConfirmed: Boolean = true,
) {
    // No number is asserted while there is no defensible ordered boundary.
    val displayCount: Int get() = if (orderConfirmed && head != null) unread.size else 0

    fun markViewed(statusIds: Set<String>): TimelineUnreadSnapshot =
        if (statusIds.none { it in unread }) this else copy(unread = unread - statusIds)

    /** Call only for an accepted network first page, never for disk restore or cursor pages. */
    fun refreshed(
        scope: TimelineUnreadScope,
        page: List<FeedItem>,
        currentlyLoaded: List<FeedItem>,
    ): TimelineUnreadSnapshot {
        val posts = page.filterNot { it.isPinned }.map { it to feedItemSortKey(it) }
        if (posts.isEmpty()) return this // empty / pin-only is not a new boundary
        if (scope.requiresOrderedResponse &&
            (posts.any { it.second <= 0 } || posts.zipWithNext().any { (a, b) -> a.second < b.second })
        ) {
            // The standalone endpoint sometimes changes order. Preserve the high-water
            // mark and unread IDs, but don't compare across an untrusted ordering interval.
            return copy(orderConfirmed = false)
        }
        val seenStatusIds = mutableSetOf<String>()
        val seenRowIds = mutableSetOf<String>()
        val ordered = (if (scope.requiresOrderedResponse) posts else posts.sortedByDescending { it.second })
            .filter { (post, key) ->
                val valid = post.statusId.isNotBlank() && post.statusId != "0" && key > 0
                if (!valid || post.statusId in seenStatusIds || post.id in seenRowIds) false
                else {
                    seenStatusIds.add(post.statusId)
                    seenRowIds.add(post.id)
                    true
                }
            }
        val (newestPost, newestKey) = ordered.firstOrNull() ?: return this
        val newest = TimelineUnreadHead(newestPost.statusId, newestKey)
        val boundary = head ?: return copy(head = newest, orderConfirmed = true) // no initial backlog
        if (!orderConfirmed) {
            return copy(head = if (newestKey > boundary.sortKey) newest else boundary, orderConfirmed = true)
        }
        if (newestKey <= boundary.sortKey) return this // never roll the watermark back

        // Anchor on the same previous head when it is present. Across a disjoint
        // refresh only strictly newer post dates can establish freshness. Equal
        // or missing dates, changed pins, and backfilled older rows aren't proof.
        val prefix = ordered.takeWhile { (post, key) -> post.statusId != boundary.statusId && key > boundary.sortKey }
        val knownStatusIds = currentlyLoaded.mapTo(HashSet()) { it.statusId }
        val knownRowIds = currentlyLoaded.mapTo(HashSet()) { it.id }
        val additions = prefix.asSequence().map { it.first }
            .filterNot { it.statusId in knownStatusIds || it.id in knownRowIds }.map { it.statusId }.toSet()
        return TimelineUnreadSnapshot(newest, unread + additions)
    }
}

/** Lazy item coordinates and the usable viewport must be in the same coordinate space. */
internal fun isTimelineRowMeaningfullyVisible(
    rowStart: Int,
    rowHeight: Int,
    viewportStart: Int,
    viewportEnd: Int,
): Boolean {
    val viewportHeight = viewportEnd - viewportStart
    if (rowHeight <= 0 || viewportHeight <= 0) return false
    val visible = (minOf(rowStart + rowHeight, viewportEnd) - maxOf(rowStart, viewportStart)).coerceAtLeast(0)
    // Very tall posts can qualify once they occupy most of the usable screen.
    return visible.toLong() * 5 >= minOf(rowHeight, viewportHeight).toLong() * 3
}
