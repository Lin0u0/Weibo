package com.example.myweibo.data

import java.security.MessageDigest

/** An account ID here must already have been matched to the authenticated session. */
internal data class TimelineReadingScope private constructor(
    val accountId: String,
    val kind: TimelineKind,
    val groupId: String?,
) {
    val storageKey: String by lazy(LazyThreadSafetyMode.NONE) {
        val route = if (groupId == null) "kind:${kind.name}" else "group:$groupId"
        MessageDigest.getInstance("SHA-256").digest("$accountId/$route".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }


    companion object {
        fun of(verifiedAccountId: String?, kind: TimelineKind, groupId: String?): TimelineReadingScope? {
            val account = verifiedAccountId?.takeIf { id ->
                id.isNotEmpty() && id != "0" && id.all { it in '0'..'9' }
            } ?: return null
            if (groupId != null) {
                val group = groupId.trim().takeIf { it.isNotEmpty() && it != "0" } ?: return null
                return TimelineReadingScope(account, TimelineKind.SpecialFollowing, group)
            }
            return when (kind) {
                TimelineKind.Following, TimelineKind.FriendsCircle, TimelineKind.SpecialFollowing ->
                    TimelineReadingScope(account, kind, null)
                TimelineKind.Hot, TimelineKind.Nearby, TimelineKind.Favorites, TimelineKind.Liked -> null
            }
        }
    }
}

/** Row keys drive Compose; status IDs resolve the same post after aliases or a cold reload. */
internal data class TimelineReadingAnchor(val rowId: String, val statusId: String?) {
    fun matches(item: FeedItem): Boolean = !item.isPinned &&
        (rowId == item.id || statusId != null && statusId == item.statusId)

    companion object {
        fun from(item: FeedItem): TimelineReadingAnchor? = item.id.takeIf { it.isNotBlank() && it != "0" }
            ?.let { id -> TimelineReadingAnchor(id, item.statusId.takeIf { it.isNotBlank() && it != "0" }) }
    }
}

/** Standalone Special Following is not guaranteed to be chronological, unlike user groups. */
internal fun hasChronologicalResponse(items: List<FeedItem>): Boolean {
    val keys = items.asSequence().filterNot { it.isPinned }.map(::feedItemSortKey).toList()
    return keys.isNotEmpty() && keys.all { it > 0 } && keys.zipWithNext().all { (newer, older) -> newer >= older }
}

/**
 * The contiguous read frontier, walking towards the newest post. Consult the full list so a
 * folded or flung-past post remains a barrier. A missing saved anchor never seeds a replacement.
 * Only this observer, not a refresh or a resume jump, may move the frontier.
 */
internal fun advanceTimelineReadingAnchor(
    anchor: TimelineReadingAnchor?,
    items: List<FeedItem>,
    qualifiedRowIds: Set<String>,
    disconnectedAt: Set<TimelineReadingAnchor> = emptySet(),
): TimelineReadingAnchor? {
    if (qualifiedRowIds.isEmpty()) return anchor
    val ordinary = items.filterNot { it.isPinned }
    if (ordinary.isEmpty()) return anchor
    if (anchor == null) return ordinary.first().takeIf { it.id in qualifiedRowIds }?.let(TimelineReadingAnchor::from)
    val index = ordinary.indexOfFirst(anchor::matches)
    if (index < 0) return anchor
    var frontier = anchor
    for (candidate in index - 1 downTo 0) {
        if (disconnectedAt.any { it.matches(ordinary[candidate + 1]) }) break
        val item = ordinary[candidate]
        if (item.id !in qualifiedRowIds) break
        frontier = TimelineReadingAnchor.from(item) ?: break
    }
    return frontier
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

/** Null means unavailable or inconclusive; zero is a confirmed nonempty no-new result. */
internal fun countNewTimelinePosts(previous: List<FeedItem>, page: List<FeedItem>, ordered: Boolean): Int? {
    val head = previous.firstOrNull { !it.isPinned } ?: return null
    if (page.none { !it.isPinned }) return null
    val rowIds = previous.mapTo(HashSet()) { it.id }
    val statusIds = previous.mapNotNullTo(HashSet()) { TimelineReadingAnchor.from(it)?.statusId }
    val headIdentity = TimelineReadingAnchor.from(head)
    val headIndex = page.indexOfFirst { headIdentity?.matches(it) == true }
    val headTime = if (ordered) feedItemSortKey(head) else 0L
    var count = 0
    for ((index, item) in page.withIndex()) {
        val status = TimelineReadingAnchor.from(item)?.statusId
        if (item.isPinned || item.id in rowIds || status?.let { it in statusIds } == true) continue
        rowIds += item.id
        if (status != null) statusIds += status
        val time = if (ordered) feedItemSortKey(item) else 0L
        val newer = when {
            !ordered -> true
            time > 0 && headTime > 0 && time != headTime -> time > headTime
            headIndex >= 0 -> index < headIndex
            // An unfamiliar, tied/undated row on a disjoint page proves neither that
            // the response added news nor that it had none. Do not display a zero.
            else -> return null
        }
        if (newer) count++
    }
    return count
}
