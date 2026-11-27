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

/** The local disposition and qualified progress of the last committed explicit refresh.
 * An empty [remaining] list is meaningful: do not fall back to the old read frontier. */
internal data class TimelineRefreshReadingBatch(
    val baseline: TimelineReadingAnchor,
    val remaining: List<TimelineReadingAnchor>,
    val viewedThrough: TimelineReadingAnchor? = null,
    val forceProjection: Boolean = false,
) {
    fun afterViewing(items: List<FeedItem>, qualified: Set<String>): TimelineRefreshReadingBatch {
        var size = remaining.size
        while (size > 0 && items.any { it.id in qualified && remaining[size - 1].matches(it) }) size--
        return if (size == remaining.size) this else copy(
            remaining = remaining.take(size), viewedThrough = remaining[size],
        )
    }
}

/** A first page must actually reconnect to the request-time head; dates do not bridge a gap. */
internal fun classifyExplicitTimelineRefresh(
    previous: List<FeedItem>,
    page: List<FeedItem>,
    requiredSeams: List<Pair<TimelineReadingAnchor, TimelineReadingAnchor>> = emptyList(),
): TimelineRefreshReadingBatch? {
    val old = previous.filterNot { it.isPinned }
    val head = old.firstOrNull() ?: return null
    val baseline = TimelineReadingAnchor.from(head) ?: return null
    val rowPositions = old.mapIndexed { index, item -> item.id to index }.toMap()
    val statusPositions = old.mapIndexedNotNull { index, item ->
        TimelineReadingAnchor.from(item)?.statusId?.let { it to index }
    }.toMap()
    val knownRows = previous.mapTo(HashSet()) { it.id }
    val knownStatuses = previous.mapNotNullTo(HashSet()) { TimelineReadingAnchor.from(it)?.statusId }
    val seenRows = HashSet<String>()
    val seenStatuses = HashSet<String>()
    val incoming = page.filter { item ->
        if (item.isPinned) return@filter false
        val status = TimelineReadingAnchor.from(item)?.statusId
        if (item.id in seenRows || status != null && status in seenStatuses) return@filter false
        seenRows += item.id
        if (status != null) seenStatuses += status
        true
    }
    val headIndex = incoming.indexOfFirst(baseline::matches)
    if (headIndex < 0) return null
    val headTime = feedItemSortKey(head)
    val batch = mutableListOf<TimelineReadingAnchor>()
    var lastKnown = -1
    var lastTime = 0L
    for ((index, item) in incoming.withIndex()) {
        val identity = TimelineReadingAnchor.from(item) ?: return null
        val rowPosition = rowPositions[item.id]
        val statusPosition = identity.statusId?.let(statusPositions::get)
        if (rowPosition != null && statusPosition != null && rowPosition != statusPosition) return null
        val known = rowPosition ?: statusPosition
        if (known != null) {
            if (known <= lastKnown || index < headIndex) return null
            lastKnown = known
        } else if (item.id in knownRows || identity.statusId?.let(knownStatuses::contains) == true) {
            // A known pin changing treatment doesn't become a fresh ordinary post.
            if (index < headIndex) return null
            continue
        }
        val time = feedItemSortKey(item)
        if (time > 0) {
            if (lastTime > 0 && time > lastTime) return null
            lastTime = time
        }
        if (index < headIndex) {
            if (time > 0 && headTime > 0 && time <= headTime) return null
            batch += identity
        } else if (index > headIndex && known == null && time > 0 && headTime > 0 && time > headTime) {
            return null
        }
    }
    if (requiredSeams.any { (newer, older) ->
            val newerIndex = incoming.indexOfFirst(newer::matches)
            newerIndex < 0 || incoming.indexOfFirst(older::matches) <= newerIndex
        }) return null
    return TimelineRefreshReadingBatch(baseline, batch)
}
