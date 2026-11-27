package com.example.myweibo

import com.example.myweibo.data.FeedItem
import com.example.myweibo.data.TimelineKind
import com.example.myweibo.data.TimelineUnreadScope
import com.example.myweibo.data.TimelineUnreadSnapshot
import com.example.myweibo.data.isTimelineRowMeaningfullyVisible
import com.example.myweibo.data.projectFeedTimelineRefresh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineUnreadPolicyTest {
    private val following = scope(TimelineKind.Following)

    @Test fun firstVerifiedPageIsBaselineNotBacklogAndEmptySnapshotsDoNotMoveIt() {
        val empty = TimelineUnreadSnapshot()
        assertEquals(empty, empty.refreshed(following, emptyList(), emptyList()))
        assertEquals(empty, empty.refreshed(following, listOf(post(7, pinned = true)), emptyList()))
        val baseline = empty.refreshed(following, listOf(post(5), post(4), post(99, pinned = true)), emptyList())
        assertEquals("s5", baseline.head?.statusId)
        assertEquals(0, baseline.displayCount)
        assertEquals(baseline, baseline.refreshed(following, emptyList(), emptyList()))
        assertEquals(baseline, baseline.refreshed(following, listOf(post(4), post(3)), emptyList()))
    }

    @Test fun refreshCountsOnlyStrictlyNewLoadedStatusesAndDeduplicatesAliasesAndPins() {
        val previous = listOf(post(5), post(4))
        val fresh = listOf(
            post(99, pinned = true), post(7), post(7).copy(id = "another-row-key"), post(6),
            post(3).copy(createdAt = post(5).createdAt), post(21).copy(createdAt = null), post(5), post(2),
        )
        val baseline = TimelineUnreadSnapshot().refreshed(following, previous, emptyList())
        val loaded = baseline.refreshed(following, fresh, previous)
        assertEquals(setOf("s7", "s6"), loaded.unread)
        assertEquals(2, loaded.displayCount)
        assertEquals(loaded, loaded.refreshed(following, fresh, previous + fresh))
    }

    @Test fun persistedHeadWorksWithNoOverlapAndAlreadyReadPostsDoNotReappear() {
        val before = TimelineUnreadSnapshot().refreshed(following, listOf(post(3)), emptyList())
        val refreshed = before.refreshed(following, listOf(post(8), post(7), post(1)), emptyList())
        assertEquals(setOf("s8", "s7"), refreshed.unread)
        val saved = refreshed.markViewed(setOf("s8"))
        val reopened = TimelineUnreadSnapshot(saved.head?.copy(), saved.unread.toSet())
        assertEquals(setOf("s7"), reopened.unread)
        val next = reopened.refreshed(following, listOf(post(9), post(8), post(7), post(2)), emptyList())
        assertEquals(setOf("s9", "s7"), next.unread)
        assertEquals(0, next.markViewed(setOf("s9", "s7", "not-unread")).displayCount)
    }

    @Test fun foldedAndPaginatedItemsAreNotAcknowledgedByTheirPresence() {
        val old = listOf(post(5), post(4))
        val incoming = listOf(post(8), post(7), post(6), post(5))
        val unread = TimelineUnreadSnapshot().refreshed(following, old, emptyList())
            .refreshed(following, incoming, old)
        val fullWithOlderPage = incoming + listOf(post(4), post(3), post(2))
        val folded = projectFeedTimelineRefresh(fullWithOlderPage, old.first().id, 1)
        assertEquals(2, folded.gap?.hiddenPostCount)
        // Simulate only the qualifying lazy row being exposed, not every loaded row.
        val afterView = unread.markViewed(setOf(folded.visibleItems.first().statusId, "s2"))
        assertEquals(setOf("s6", "s7"), afterView.unread)
        assertEquals(afterView, afterView.refreshed(following, incoming, fullWithOlderPage))
    }

    @Test fun rankedSpecialResponseHidesPriorCountAndReestablishesBaselineWithoutBackfill() {
        val special = scope(TimelineKind.SpecialFollowing)
        val first = TimelineUnreadSnapshot().refreshed(special, listOf(post(3), post(2)), emptyList())
        val second = first.refreshed(special, listOf(post(4), post(3)), emptyList())
        assertEquals(1, second.displayCount)
        val ranked = second.refreshed(special, listOf(post(2), post(8)), emptyList())
        assertEquals(0, ranked.displayCount)
        assertEquals(setOf("s4"), ranked.unread)
        val next = ranked.refreshed(special, listOf(post(9), post(2)), emptyList())
        assertEquals(setOf("s4"), next.unread)
        assertEquals(1, next.displayCount)

        val rankedAfterRead = ranked.markViewed(setOf("s4"))
        val staleRecovery = rankedAfterRead.refreshed(special, listOf(post(3), post(2)), emptyList())
        assertEquals("s4", staleRecovery.head?.statusId)
        assertEquals(0, staleRecovery.refreshed(special, listOf(post(4), post(3)), emptyList()).displayCount)
    }

    @Test fun accountsBuiltinRoutesAndGroupRoutesHaveDifferentPersistentKeys() {
        assertEquals(following.storageKey, scope(TimelineKind.Following).storageKey)
        assertTrue(following.storageKey.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(following.storageKey, scope(TimelineKind.Following, account = "760000002").storageKey)
        assertNotEquals(following.storageKey, scope(TimelineKind.FriendsCircle).storageKey)
        assertNotEquals(scope(TimelineKind.SpecialFollowing).storageKey, scope(TimelineKind.SpecialFollowing, "1").storageKey)
        assertNotEquals(scope(TimelineKind.SpecialFollowing, "1").storageKey, scope(TimelineKind.SpecialFollowing, "2").storageKey)
        assertNull(TimelineUnreadScope.of(null, TimelineKind.Following, null))
        assertNull(TimelineUnreadScope.of("wrong", TimelineKind.Following, null))
        assertNull(TimelineUnreadScope.of("760000001", TimelineKind.Following, " "))
        for (kind in listOf(TimelineKind.Hot, TimelineKind.Nearby, TimelineKind.Favorites, TimelineKind.Liked)) {
            assertNull(TimelineUnreadScope.of("760000001", kind, null))
        }
    }

    @Test fun exposureRequiresSubstantialOverlapInsideStatusAndDockInsets() {
        assertFalse(isTimelineRowMeaningfullyVisible(-40, 100, 24, 400))
        assertTrue(isTimelineRowMeaningfullyVisible(-16, 100, 24, 400))
        assertFalse(isTimelineRowMeaningfullyVisible(341, 100, 24, 400))
        assertTrue(isTimelineRowMeaningfullyVisible(340, 100, 24, 400))
        assertTrue(isTimelineRowMeaningfullyVisible(0, 1200, 24, 400))
        assertFalse(isTimelineRowMeaningfullyVisible(0, 1200, 24, 24))
        assertFalse(isTimelineRowMeaningfullyVisible(24, 0, 24, 400))
    }

    private fun scope(kind: TimelineKind, group: String? = null, account: String = "760000001") =
        requireNotNull(TimelineUnreadScope.of(account, kind, group))

    private fun post(number: Int, pinned: Boolean = false) = FeedItem(
        id = "row-$number",
        statusId = "s$number",
        authorId = "a",
        authorName = "Author",
        authorAvatarUrl = null,
        createdAt = (1_700_000_000L + number).toString(),
        source = null,
        ipLocation = null,
        text = "post $number",
        repostsCount = "0",
        commentsCount = "0",
        likesCount = "0",
        images = emptyList(),
        isPinned = pinned,
    )
}
