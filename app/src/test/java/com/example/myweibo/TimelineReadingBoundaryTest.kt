package com.example.myweibo

import com.example.myweibo.data.FeedItem
import com.example.myweibo.data.TimelineKind
import com.example.myweibo.data.TimelineReadingAnchor
import com.example.myweibo.data.TimelineReadingScope
import com.example.myweibo.data.advanceTimelineReadingAnchor
import com.example.myweibo.data.countNewTimelinePosts
import com.example.myweibo.data.hasChronologicalResponse
import com.example.myweibo.data.isTimelineRowMeaningfullyVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineReadingBoundaryTest {
    @Test fun flingAndCollapsedRowsCannotSkipReadFrontier() {
        val pin = post(90).copy(isPinned = true)
        val rows = listOf(post(5), post(4), pin, post(3), post(2), post(1))
        val read = anchor(2)
        assertEquals(read, advanceTimelineReadingAnchor(read, rows, setOf("row-5", "row-4", pin.id)))
        assertEquals(anchor(3), advanceTimelineReadingAnchor(read, rows, setOf("row-5", "row-3")))
        assertEquals(anchor(4), advanceTimelineReadingAnchor(read, rows, setOf("row-4", "row-3")))
        assertEquals(read, advanceTimelineReadingAnchor(read, rows, setOf("row-1")))
    }

    @Test fun firstObservationMustQualifyOrdinaryHeadAndDisjointRefreshIsNotContinuity() {
        val rows = listOf(post(90).copy(isPinned = true), post(5), post(4), post(3), post(2))
        assertNull(advanceTimelineReadingAnchor(null, rows, setOf("row-90", "row-4")))
        assertEquals(anchor(5), advanceTimelineReadingAnchor(null, rows, setOf("row-5")))
        assertEquals(anchor(3), advanceTimelineReadingAnchor(anchor(2), rows, setOf("row-3", "row-4"), setOf(anchor(3))))
    }

    @Test fun missingAnchorIsRetainedUntilExactIdentityReappearsUnpinned() {
        val read = anchor(2)
        val rows = listOf(post(4), post(3))
        assertEquals(read, advanceTimelineReadingAnchor(read, rows, setOf("row-4", "row-3")))
        val alias = post(2).copy(id = "cold-alias")
        assertEquals(read, advanceTimelineReadingAnchor(read, rows + alias.copy(isPinned = true), setOf("row-3")))
        assertEquals(anchor(3), advanceTimelineReadingAnchor(read, rows + alias, setOf("row-3")))
    }

    @Test fun routeKeysSeparateAccountsGroupsAndBuiltInSpecialFollowing() {
        fun key(account: String, kind: TimelineKind, group: String? = null) =
            requireNotNull(TimelineReadingScope.of(account, kind, group)).storageKey
        val following = key("123", TimelineKind.Following)
        assertNotEquals(following, key("124", TimelineKind.Following))
        assertNotEquals(following, key("123", TimelineKind.FriendsCircle))
        assertNotEquals(key("123", TimelineKind.SpecialFollowing), key("123", TimelineKind.SpecialFollowing, "one"))
        assertNotEquals(key("123", TimelineKind.SpecialFollowing, "one"), key("123", TimelineKind.SpecialFollowing, "two"))
        assertNull(TimelineReadingScope.of(null, TimelineKind.Following, null))
        assertNull(TimelineReadingScope.of("123", TimelineKind.Hot, null))
    }

    @Test fun standaloneSpecialNeedsARealChronologicalResponse() {
        val pin = post(90).copy(isPinned = true)
        assertFalse(hasChronologicalResponse(listOf(pin)))
        assertFalse(hasChronologicalResponse(listOf(post(2), post(3))))
        assertFalse(hasChronologicalResponse(listOf(post(2).copy(createdAt = null))))
        assertTrue(hasChronologicalResponse(listOf(post(3), pin, post(2))))
    }

    @Test fun successfulNoNewIsDifferentFromEmptyAndPinChangesOrBackfillsAreNotNew() {
        val previous = listOf(post(4), post(2))
        val reorderedPin = post(9).copy(isPinned = true)
        assertNull(countNewTimelinePosts(previous, emptyList(), ordered = true))
        assertNull(countNewTimelinePosts(emptyList(), previous, ordered = true))
        assertNull(countNewTimelinePosts(previous, listOf(reorderedPin), ordered = true))
        assertEquals(0, countNewTimelinePosts(previous, listOf(reorderedPin, post(4), post(3)), ordered = true))
        val prefix = post(6)
        assertEquals(1, countNewTimelinePosts(previous, listOf(prefix, prefix.copy(id = "alias"), post(4)), ordered = true))
    }

    @Test fun tiedOrUndatedDisjointPostsAreInconclusiveUntilThereIsOrderingEvidence() {
        val head = post(4)
        val previous = listOf(head, post(2))
        val tied = post(5).copy(createdAt = head.createdAt)
        val undated = post(6).copy(createdAt = null)
        assertNull(countNewTimelinePosts(previous, listOf(tied), ordered = true))
        assertNull(countNewTimelinePosts(previous, listOf(undated), ordered = true))
        assertEquals(1, countNewTimelinePosts(previous, listOf(tied, head), ordered = true))
        assertEquals(0, countNewTimelinePosts(previous, listOf(head, undated), ordered = true))
    }

    @Test fun exposedRowUsesUsableViewportForTallAndClippedRows() {
        assertFalse(isTimelineRowMeaningfullyVisible(rowStart = -80, rowHeight = 120, viewportStart = 0, viewportEnd = 300))
        assertTrue(isTimelineRowMeaningfullyVisible(rowStart = -80, rowHeight = 640, viewportStart = 0, viewportEnd = 300))
    }

    private fun anchor(number: Int) = requireNotNull(TimelineReadingAnchor.from(post(number)))

    private fun post(number: Int) = FeedItem(
        id = "row-$number", statusId = "status-$number", authorId = "author", authorName = "Author",
        authorAvatarUrl = null, createdAt = (1_700_000_000L + number).toString(), source = null,
        ipLocation = null, text = "Post $number", repostsCount = "0", commentsCount = "0", likesCount = "0",
        images = emptyList(),
    )
}
