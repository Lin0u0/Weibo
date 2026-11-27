package com.example.myweibo

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myweibo.data.FeedItem
import com.example.myweibo.data.FeedMedia
import com.example.myweibo.data.MediaType
import com.example.myweibo.data.ReadingPreferences
import com.example.myweibo.data.ReadingPreferencesStore
import com.example.myweibo.data.TimelineCacheStore
import com.example.myweibo.data.TimelineKind
import com.example.myweibo.data.TimelinePage
import com.example.myweibo.data.TimelineReadingBoundaryStore
import com.example.myweibo.data.ViboDockEntry
import com.example.myweibo.data.ViboDockPreferences
import com.example.myweibo.data.mergeFeedTimelinePages
import com.example.myweibo.ui.FollowFeedScreen
import com.example.myweibo.ui.HomeTimelineReadingState
import com.example.myweibo.ui.MainTab
import com.example.myweibo.ui.ViboDockRuntime
import com.example.myweibo.ui.WeiboLiquidBottomBar
import com.kyant.backdrop.backdrops.emptyBackdrop
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real feed rows and liquid tab gestures with local data; no production account or network. */
@RunWith(AndroidJUnit4::class)
class ReverseHomeReselectComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedHomeTouchJumpsToOldHeadAtZeroOffsetWithStaticMarkerAtTheLimit() {
        val fixture = mount()
        fixture.publish(newCount = 2)
        fixture.scrollTo(index = 12, offset = 19)

        fixture.touchReselect(home())
        fixture.assertResumedAtOldHead(index = 3) // Two new posts, then the static marker.

        fixture.scrollTo(index = 2)
        val marker = label(R.string.feed_reverse_browse_new_posts_marker)
        compose.onNodeWithText(marker).assertIsDisplayed().assert(!hasClickAction())
        compose.runOnIdle {
            assertEquals("以上是新微博", marker)
            assertEquals(post(24).id, fixture.timeline.readingAnchor?.rowId)
            assertEquals((26 downTo 1).map(::post), fixture.timeline.items)
            assertEquals(0, fixture.refreshes)
        }
    }

    @Test fun foldedDividerTogglesCachedRowsAndBothProjectionsJumpToTheSameOldHead() {
        val fixture = mount()
        fixture.publish(newCount = 4)
        fixture.scrollTo(index = 12, offset = 19)
        home().activateThroughSemantics()
        fixture.assertResumedAtOldHead(index = 3)

        fixture.scrollTo(index = 2)
        val folded = compose.onNodeWithText(label(R.string.feed_reverse_browse_gap, 2))
        val expanded = compose.onNodeWithText(label(R.string.feed_reverse_browse_gap_open, 2))
        compose.onNodeWithText(label(R.string.feed_reverse_browse_new_posts_marker)).assertDoesNotExist()
        folded.assertIsDisplayed().performClick()
        // Expansion preserves the first visible post; the collapse control can now be above it.
        compose.runOnIdle {
            assertTrue(fixture.timeline.reverseBrowseGapExpanded)
            assertEquals((28 downTo 1).map(::post), fixture.timeline.feedProjection.visibleItems)
            assertEquals(0, fixture.refreshes)
            assertEquals(0, fixture.pages)
        }

        fixture.scrollTo(index = 12, offset = 19)
        fixture.touchReselect(home())
        fixture.assertResumedAtOldHead(index = 5)
        fixture.scrollTo(index = 2)
        expanded.assertIsDisplayed().performClick()
        folded.assertIsDisplayed()
        compose.runOnIdle {
            assertEquals((28 downTo 27).map(::post) + (24 downTo 1).map(::post), fixture.timeline.feedProjection.visibleItems)
            assertEquals((28 downTo 1).map(::post), fixture.timeline.items)
            assertEquals(post(24).id, fixture.timeline.readingAnchor?.rowId)
            assertFalse(fixture.timeline.reverseBrowseGapExpanded)
            assertEquals(0, fixture.refreshes)
            assertEquals(0, fixture.pages)
        }
    }

    @Test fun openingFourFoldedPostsKeepsVisibleOldVideoAtSameScreenY() {
        val coverPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val fixture = mount(row = { number -> fourFoldedPostsFixture(number, coverPackage) })
        fixture.publish(newCount = 6) // Keep 30 and 29 visible; fold 28 through 25 before old head 24.
        val folded = compose.onNodeWithText(label(R.string.feed_reverse_browse_gap, 4))
        val expanded = compose.onNodeWithText(label(R.string.feed_reverse_browse_gap_open, 4))
        val oldVideo = compose.onNodeWithText("Old post 24: portrait video")
        val oldestHidden = compose.onNodeWithText("New post 25: oldest folded reply", substring = true)

        fun openFromMidScreen(record: Boolean = false) {
            fixture.positionFoldUnderPartiallyVisiblePost()
            folded.assertIsDisplayed()
            oldVideo.assertIsDisplayed()
            val oldScreenY = compose.runOnIdle {
                val layout = fixture.listState.layoutInfo
                val projection = fixture.timeline.feedProjection
                assertEquals(4, projection.gap?.hiddenPostCount)
                val divider = layout.visibleItemsInfo.first { it.index == projection.dividerIndex }
                val newer = layout.visibleItemsInfo.first { it.key == post(29).id }
                val old = layout.visibleItemsInfo.first { it.key == post(24).id }
                val newerTop = newer.offset + layout.beforeContentPadding
                val dividerCenter = divider.offset + layout.beforeContentPadding + divider.size / 2
                val viewportHeight = layout.viewportSize.height
                assertEquals(newer.index, fixture.listState.firstVisibleItemIndex)
                assertTrue("Newer card must straddle the viewport top", newerTop < 0 && newerTop + newer.size > 0)
                assertTrue("Fold divider must be mid-screen", dividerCenter in viewportHeight / 3..viewportHeight * 2 / 3)
                assertEquals(divider.index + 1, old.index)
                assertEquals(divider.offset + divider.size, old.offset)
                assertTrue("Fixture must use a tall old card", old.size > divider.size * 3)
                assertTrue((25..28).none { number -> projection.visibleItems.any { it.id == post(number).id } })
                assertTrue("Feed screen origin was not measured", fixture.feedTopOnScreen.isFinite())
                fixture.feedTopOnScreen + layout.beforeContentPadding + old.offset
            }
            if (record) pauseForFoldWalkthrough()
            folded.performTouchInput { click() }
            oldVideo.assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(fixture.timeline.reverseBrowseGapExpanded)
                val layout = fixture.listState.layoutInfo
                val old = layout.visibleItemsInfo.first { it.key == post(24).id }
                assertEquals(
                    "Opening the fold must not move the already-visible old video card on screen",
                    oldScreenY, fixture.feedTopOnScreen + layout.beforeContentPadding + old.offset, 0.5f,
                )
                val oldest = layout.visibleItemsInfo.first { it.key == post(25).id }
                assertEquals(old.index - 1, oldest.index)
                assertTrue("Oldest folded card must be visible immediately above old head", oldest.offset < old.offset)
                assertEquals(old.offset, oldest.offset + oldest.size)
                assertEquals((30 downTo 1).map { post(it).id }, fixture.timeline.feedProjection.visibleItems.map { it.id })
                assertEquals(0, fixture.refreshes)
                assertEquals(0, fixture.pages)
            }
            oldestHidden.assertIsDisplayed()
            if (record) pauseForFoldWalkthrough()
        }

        openFromMidScreen(record = true)
        // With the old card pinned in place, the expanded divider can move above the viewport.
        val expandedDividerIndex = compose.runOnIdle { requireNotNull(fixture.timeline.feedProjection.dividerIndex) }
        fixture.scrollTo(index = expandedDividerIndex)
        expanded.assertIsDisplayed().performTouchInput { click() }
        folded.assertIsDisplayed()
        compose.runOnIdle { assertFalse(fixture.timeline.reverseBrowseGapExpanded) }
        openFromMidScreen()
    }

    @Test fun selectedSingleMissingAnchorDoesNotRefreshAndOtherTabOnlySwitches() {
        val fixture = mount(seedAnchor = false)
        fixture.scrollTo(index = 10, offset = 19)
        val mine = compose.onNodeWithContentDescription(MainTab.Mine.label)
        mine.performTouchInput { click() }.assertIsSelected()
        home().performTouchInput { click() }
        compose.waitUntil(timeoutMillis = 2_000) { fixture.homeSwitches.get() == 1 }
        fixture.assertAtRow(index = 10, postNumber = 14, offset = 19)
        home().assertIsSelected().performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, fixture.menuOpens)
            assertEquals(0, fixture.resumeActivations.get())
        }
        fixture.touchReselect(home())
        fixture.assertAtRow(index = 10, postNumber = 14, offset = 19)
        compose.runOnIdle { assertEquals(0, fixture.refreshes) }
    }

    @Test fun reversePreferenceOffDoesNotDisableResumeAndHomeDoubleRefreshesWithoutJumping() {
        val fixture = mount(reverseReading = false, kind = TimelineKind.FriendsCircle)
        fixture.scrollTo(index = 10, offset = 19)
        home().performTouchInput { doubleClick() }
        fixture.assertAtRow(index = 10, postNumber = 14, offset = 19)
        compose.runOnIdle {
            assertEquals(1, fixture.refreshes)
            assertEquals(0, fixture.resumeActivations.get())
            assertEquals(TimelineKind.FriendsCircle, fixture.timeline.timelineKind)
        }
        fixture.publish(newCount = 2)
        fixture.assertAtRow(index = 13, postNumber = 14, offset = 19)
        fixture.touchReselect(home())
        fixture.assertResumedAtOldHead(index = 3)
        compose.runOnIdle { assertEquals(1, fixture.refreshes) }
    }

    @Test fun selectedShortcutResumesButUnselectedShortcutOnlySwitches() {
        val fixture = mount(withShortcut = true)
        fixture.scrollTo(index = 10, offset = 19)
        val shortcut = compose.onNodeWithContentDescription(TimelineKind.FriendsCircle.label)
        shortcut.performTouchInput { doubleClick() }
        fixture.assertAtRow(index = 10, postNumber = 14, offset = 19)
        compose.runOnIdle {
            assertEquals(TimelineKind.Following, fixture.timeline.timelineKind)
            assertEquals(0, fixture.switches.get())
            assertEquals(0, fixture.refreshes)
        }
        shortcut.performTouchInput { click() }
        compose.waitUntil(timeoutMillis = 2_000) { fixture.switches.get() == 1 }
        shortcut.assertIsSelected()
        fixture.assertAtRow(index = 10, postNumber = 14, offset = 19)
        compose.runOnIdle {
            assertEquals(TimelineKind.FriendsCircle, fixture.timeline.timelineKind)
            assertEquals(0, fixture.resumeActivations.get())
            assertEquals(0, fixture.refreshes)
        }
        fixture.touchReselect(shortcut)
        fixture.assertResumedAtRow(index = 2, postNumber = 22)
        fixture.scrollTo(index = 10, offset = 19)
        shortcut.performTouchInput { doubleClick() }
        fixture.assertAtRow(index = 10, postNumber = 14, offset = 19)
        compose.runOnIdle {
            assertEquals(1, fixture.refreshes)
            assertEquals(1, fixture.switches.get())
            assertEquals(1, fixture.resumeActivations.get())
        }
    }

    @Test fun coldMissingAnchorRemainsUntilTheExactPostIsLoaded() {
        val fixture = mount(coldReopen = true)
        compose.runOnIdle {
            assertEquals(post(24).id, fixture.timeline.readingAnchor?.rowId)
            assertNull(fixture.timeline.readingBoundaryLazyIndex)
        }
        // Stay early enough in the short cold page that LazyColumn can align the row.
        fixture.scrollTo(index = 2, offset = 17)
        home().activateThroughSemantics()
        fixture.assertAtRow(index = 2, postNumber = 34, offset = 17)
        compose.runOnIdle {
            assertEquals(post(24).id, fixture.timeline.readingAnchor?.rowId)
            fixture.timeline.items = fixture.timeline.items + (24 downTo 1).map(::post)
        }
        home().activateThroughSemantics()
        fixture.assertResumedAtOldHead(index = 12)
        compose.runOnIdle { assertEquals(0, fixture.refreshes) }
    }

    @Test fun successfulTopRefreshHidesOnlyHistoricalMarkerAndKeepsReadIdentityAndGap() {
        val fixture = mount()
        fixture.publish(newCount = 2)
        fixture.scrollTo(index = 0)
        fixture.publish(newCount = 2, topNoNew = true)
        fixture.scrollTo(index = 10)
        home().activateThroughSemantics()
        fixture.assertResumedAtOldHead(index = 2)
        compose.onNodeWithText(label(R.string.feed_reverse_browse_new_posts_marker)).assertDoesNotExist()

        fixture.publish(newCount = 4)
        fixture.scrollTo(index = 0)
        fixture.publish(newCount = 4, topNoNew = true)
        val collapsed = compose.onNodeWithText(label(R.string.feed_reverse_browse_gap, 2))
        fixture.scrollTo(index = 2)
        collapsed.assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(post(24).id, fixture.timeline.readingAnchor?.rowId)
            assertEquals((28 downTo 1).map(::post), fixture.timeline.feedProjection.visibleItems)
        }
    }

    @Test fun confirmedDockRefreshResumesAtOldestNewPostEvenWhenThresholdFoldsIt() {
        val fixture = mount(newPostLimit = 1, row = ::rolloverPost)
        fixture.publish(newCount = 2) // Local A/B arrive without an explicit rollover; R is still the read frontier.
        fixture.scrollTo(index = 10, offset = 19)
        fixture.touchReselect(home())
        fixture.assertResumedAtOldHead(index = 2)
        compose.onNodeWithText(rolloverPost(24).text).assertIsDisplayed()
        pauseForRolloverWalkthrough()

        fixture.scrollTo(index = 10, offset = 19)
        val beforeRefresh = fixture.firstVisibleRow()
        fixture.dockRefresh(listOf(rolloverPost(28), rolloverPost(27), rolloverPost(26), rolloverPost(25)))
        assertEquals("An explicit refresh should preserve the keyed viewport", beforeRefresh, fixture.firstVisibleRow())
        compose.runOnIdle {
            assertEquals(post(24).id, fixture.timeline.readingAnchor?.rowId)
            assertEquals((28 downTo 1).map(::rolloverPost), fixture.timeline.items)
            assertEquals(1, fixture.timeline.feedProjection.gap?.hiddenPostCount)
            assertFalse(fixture.timeline.feedProjection.visibleItems.any { it.id == post(27).id })
        }
        fixture.scrollTo(index = 0)
        compose.onNodeWithText(label(R.string.feed_reverse_browse_gap, 1)).assertIsDisplayed()
        pauseForRolloverWalkthrough()

        fixture.touchReselect(home())
        fixture.assertResumedAtPost(27)
        compose.onNodeWithText(rolloverPost(27).text).assertIsDisplayed()
        pauseForRolloverWalkthrough()
        // The rail still counts posts above the viewport, not the committed reading batch.
        fixture.scrollPastPaddingAtPost(27)
        fixture.assertRailCount(1)
        fixture.scrollPastPaddingAtPost(24)
        fixture.assertRailCount(4)
        compose.runOnIdle {
            assertEquals(post(24).id, fixture.timeline.readingAnchor?.rowId)
            assertEquals(1, fixture.refreshes)
            assertEquals(0, fixture.pages)
        }
    }

    @Test fun explicitNonDockRefreshResumesAtCFromMidTimeline() {
        val fixture = mount()
        fixture.publish(newCount = 2)
        fixture.scrollTo(index = 10, offset = 19)
        val beforeRefresh = fixture.firstVisibleRow()
        fixture.deliverRefresh(Result.success(TimelinePage(listOf(post(28), post(27), post(26)))), explicit = true)
        assertEquals(beforeRefresh, fixture.firstVisibleRow())
        fixture.touchReselect(home())
        fixture.assertResumedAtPost(27)
        compose.runOnIdle { assertEquals(post(24).id, fixture.timeline.readingAnchor?.rowId) }
    }

    @Test fun confirmedZeroAtTopShowsNoNewHintAndDisablesResumeWithoutRemovingHistory() {
        val fixture = mount(showResultHint = true, persistReading = true, row = ::rolloverPost)
        fixture.publish(newCount = 2)
        fixture.scrollPastPaddingAtPost(14)
        fixture.assertRailCount(12)
        fixture.touchReselect(home())
        fixture.assertResumedAtOldHead(index = 3)
        fixture.scrollTo(index = 0)
        fixture.dockRefresh(listOf(rolloverPost(26), rolloverPost(25), rolloverPost(24)))
        compose.onNodeWithText("没有新微博了").assertIsDisplayed()
        pauseForRolloverWalkthrough()
        compose.runOnIdle {
            assertEquals(post(24).id, fixture.timeline.readingAnchor?.rowId)
            assertEquals((26 downTo 1).map(::rolloverPost), fixture.timeline.items)
            assertNull(fixture.timeline.readingBoundaryLazyIndex)
            assertNull(fixture.timeline.feedProjection.dividerIndex)
        }
        fixture.scrollPastPaddingAtPost(14)
        fixture.assertRailCount(12)
        val beforeReselect = fixture.firstVisibleRow()
        fixture.touchReselect(home())
        assertEquals(beforeReselect, fixture.firstVisibleRow())

        fixture.reopen()
        fixture.scrollPastPaddingAtPost(14)
        val beforeColdReselect = fixture.firstVisibleRow()
        fixture.touchReselect(home())
        assertEquals(beforeColdReselect, fixture.firstVisibleRow())
        compose.runOnIdle {
            assertEquals(post(24).id, fixture.timeline.readingAnchor?.rowId)
            assertNull(fixture.timeline.readingBoundaryLazyIndex)
            assertNull(fixture.timeline.feedRefreshHint) // Do not persist a transient hint.
            assertEquals(1, fixture.refreshes)
            assertEquals(0, fixture.pages)
        }
    }

    @Test fun unsuccessfulAndUnconfirmedExplicitRefreshesKeepPreviousBatchAndNeverHintNoNew() {
        val fixture = mount()
        fixture.publish(newCount = 2)
        val cases = listOf(
            "network failure" to Result.failure<TimelinePage>(IllegalStateException("fixture offline")),
            "empty success" to Result.success(TimelinePage(emptyList())),
            "reordered response" to Result.success(TimelinePage(listOf(post(26), post(24), post(25)))),
            "disjoint response" to Result.success(TimelinePage(listOf(post(28), post(27)))),
            // Disjoint rows may have been merged for display, but seeing them again without the
            // connecting seam does not establish a confirmed zero against the older batch.
            "repeat disjoint response" to Result.success(TimelinePage(listOf(post(28), post(27)))),
        )
        cases.forEach { (scenario, result) ->
            fixture.scrollTo(index = 0)
            fixture.dockRefresh(result)
            compose.runOnIdle {
                assertNull(scenario, fixture.timeline.feedRefreshHint)
                assertEquals(scenario, post(24).id, fixture.timeline.readingAnchor?.rowId)
                assertTrue(scenario, fixture.timeline.items.containsAll((26 downTo 1).map(::post)))
            }
            fixture.scrollTo(index = 10, offset = 19)
            fixture.touchReselect(home())
            fixture.assertResumedAtPost(24)
        }
        compose.runOnIdle { assertEquals(cases.size, fixture.refreshes) }
    }

    @Test fun aResponseFromAnEarlierVisitCannotReplaceTheCurrentResume() {
        val fixture = mount()
        fixture.publish(newCount = 2)
        compose.runOnIdle {
            val timeline = fixture.timeline
            val oldVisit = requireNotNull(timeline.captureExplicitRefreshBaseline(fixture.accountId))
            timeline.leaveExplicitRefreshVisit()
            assertNull(
                timeline.publishRefresh(
                    TimelinePage(listOf(post(28), post(27), post(26))), fixture.listState,
                    protectViewport = true, keepReading = true, dockRefresh = true, explicitBaseline = oldVisit,
                ),
            )
            assertEquals(post(24).id, timeline.readingAnchor?.rowId)
        }
        fixture.scrollTo(index = 10, offset = 19)
        fixture.touchReselect(home())
        fixture.assertResumedAtPost(24)
    }

    @Test fun automaticRefreshAndPaginationDoNotCommitAnExplicitReadingBatch() {
        val fixture = mount()
        fixture.publish(newCount = 2)
        fixture.dockRefresh(listOf(post(28), post(27), post(26)))
        fixture.scrollTo(index = 0)
        fixture.deliverRefresh(Result.success(TimelinePage(listOf(post(30), post(29), post(28)))), explicit = false)
        fixture.deliverRefresh(Result.success(TimelinePage(listOf(post(30), post(29), post(28)))), explicit = false)
        compose.runOnIdle {
            assertNull(fixture.timeline.feedRefreshHint)
            assertEquals((30 downTo 1).map(::post), fixture.timeline.items)
            // This is the real merge used by loadMore; its publish path only assigns the rows.
            val (paged, appended) = mergeFeedTimelinePages(fixture.timeline.items, listOf(post(2), post(1), post(0)))
            assertEquals(1, appended)
            fixture.timeline.items = paged
            assertEquals((30 downTo 0).map(::post), fixture.timeline.items)
            assertNull(fixture.timeline.feedRefreshHint)
            assertEquals(post(24).id, fixture.timeline.readingAnchor?.rowId)
        }
        fixture.scrollTo(index = 10, offset = 19)
        fixture.touchReselect(home())
        fixture.assertResumedAtPost(27)
    }

    @Test fun coldReopenRestoresExplicitResumeOnlyForItsAccountAndRoute() {
        val fixture = mount(persistReading = true)
        fixture.publish(newCount = 2)
        fixture.dockRefresh(listOf(post(28), post(27), post(26)))
        val refreshedRows = compose.runOnIdle { fixture.timeline.items }
        fixture.reopen(rows = refreshedRows)
        fixture.scrollTo(index = 10, offset = 17)
        fixture.touchReselect(home())
        fixture.assertResumedAtPost(27)
        compose.runOnIdle { assertEquals(post(24).id, fixture.timeline.readingAnchor?.rowId) }

        for ((account, kind, group) in listOf(
            Triple("${fixture.accountId}1", TimelineKind.Following, null),
            Triple(fixture.accountId, TimelineKind.FriendsCircle, null),
            Triple(fixture.accountId, TimelineKind.SpecialFollowing, "fixture-group"),
        )) {
            fixture.reopen(ownerId = account, kind = kind, groupId = group, rows = refreshedRows)
            fixture.scrollTo(index = 10, offset = 17)
            val beforeReselect = fixture.firstVisibleRow()
            fixture.touchReselect(home())
            assertEquals(beforeReselect, fixture.firstVisibleRow())
            compose.runOnIdle {
                assertNull(fixture.timeline.readingAnchor)
                assertNull(fixture.timeline.readingBoundaryLazyIndex)
                assertNull(fixture.timeline.feedRefreshHint)
            }
        }
        fixture.reopen(rows = refreshedRows)
        fixture.touchReselect(home())
        fixture.assertResumedAtPost(27)
    }

    @Test fun settledExposureAdvancesOnlyToAnAdjacentPost() {
        val fixture = mount(observe = true)
        fixture.publish(newCount = 2)
        fixture.scrollTo(index = 0) // Both adjacent newer rows can qualify in the usable viewport.
        compose.waitUntil(timeoutMillis = 4_000) { fixture.timeline.readingAnchor?.rowId == post(26).id }
        fixture.scrollTo(index = 10)
        home().activateThroughSemantics()
        fixture.assertResumedAtRow(index = 0, postNumber = 26)
    }

    private fun mount(
        reverseReading: Boolean = true,
        kind: TimelineKind = TimelineKind.Following,
        seedAnchor: Boolean = true,
        coldReopen: Boolean = false,
        withShortcut: Boolean = false,
        observe: Boolean = false,
        newPostLimit: Int = 2,
        persistReading: Boolean = coldReopen,
        showResultHint: Boolean = false,
        row: (Int) -> FeedItem = ::post,
    ): Fixture {
        val readingPreferences = ReadingPreferences(reverseReading = reverseReading, newPostLimit = newPostLimit)
        val fixture = Fixture(row, readingPreferences, persistReading)
        compose.setContent {
            val context = LocalContext.current
            val listState = rememberLazyListState()
            val resumeScope = rememberCoroutineScope()
            val (homeTimeline, shortcutTimeline) = remember {
                fun session(route: TimelineKind, rows: List<FeedItem>) =
                    fixture.openTimeline(context, route, rows).apply { feedInitialScrollReconciled = observe }
                val rows = (24 downTo 1).map(row)
                val first = session(kind, rows)
                if (seedAnchor) first.markReadingRowsViewed(requireNotNull(first.readingScope), setOf(post(24).id))
                val home = if (coldReopen) session(kind, (36 downTo 25).map(row)) else first
                // Keep the list keys stable across the synthetic switch, but give the other
                // route a distinct boundary to catch accidental resume of the Home route.
                val pinned = if (withShortcut) session(TimelineKind.FriendsCircle, rows.drop(2)).apply {
                    markReadingRowsViewed(requireNotNull(readingScope), setOf(post(22).id))
                    items = rows
                } else null
                home to pinned
            }
            val shortcut = remember { ViboDockEntry.forTimeline(TimelineKind.FriendsCircle) }
            var selectedTab by remember { mutableStateOf(MainTab.Feed) }
            var activeShortcut by remember { mutableStateOf(false) }
            val timeline = if (activeShortcut) requireNotNull(shortcutTimeline) else fixture.reopenedTimeline ?: homeTimeline
            fun selectTab(tab: MainTab) {
                if (tab == MainTab.Feed && selectedTab != MainTab.Feed) fixture.homeSwitches.incrementAndGet()
                selectedTab = tab
            }
            SideEffect { fixture.timeline = timeline; fixture.listState = listState }
            MaterialTheme {
                Box(Modifier.fillMaxSize().onGloballyPositioned { fixture.feedTopOnScreen = it.positionOnScreen().y }) {
                    FollowFeedScreen(
                        listState = listState,
                        homeTimeline = timeline,
                        hasLoginCookie = true,
                        onRefresh = { fixture.refresh(timeline, dockGesture = false) },
                        onLoadMore = { fixture.pages++ },
                        onOpenLoginSettings = {},
                        onItemClick = { _, _ -> },
                        onCommentClick = { _, _ -> },
                        onCommentLongClick = {},
                        onRepostClick = { _, _ -> },
                        onMediaClick = { _, _ -> },
                        feedUiOnTop = selectedTab == MainTab.Feed,
                    )
                    // The app-level capsule lives outside FollowFeedScreen. Mirror the real result
                    // text here for the offline recording, not the refresh decision itself.
                    if (showResultHint) timeline.feedRefreshHint?.let { Text(it, Modifier.align(Alignment.TopCenter)) }
                    WeiboLiquidBottomBar(
                        selectedTab = selectedTab,
                        backdrop = emptyBackdrop(),
                        timelineMenuExpanded = false,
                        onTimelineMenuExpandedChange = { if (it) fixture.menuOpens++ },
                        timelineMenuContent = {},
                        onTabChange = ::selectTab,
                        selectedTimelineKind = timeline.timelineKind,
                        dockPreferences = if (withShortcut) {
                            ViboDockPreferences(visibleEntryIds = listOf(ViboDockEntry.Home.id, shortcut.id))
                        } else ViboDockPreferences(),
                        dockRuntime = ViboDockRuntime(
                            catalogue = if (withShortcut) listOf(shortcut) else emptyList(),
                            selectedEntryId = shortcut.id.takeIf { activeShortcut },
                            onEntrySelected = { entry ->
                                // Home also has a timeline kind and uses this callback when unselected.
                                activeShortcut = entry.id == shortcut.id
                                selectTab(MainTab.Feed)
                                if (activeShortcut) fixture.switches.incrementAndGet()
                            },
                            onSelectedTimelineClick = {
                                timeline.onHomeTabReselected(listState, resumeScope)
                                fixture.resumeActivations.incrementAndGet()
                            },
                            onEntryDoubleClick = { fixture.refresh(timeline, dockGesture = true) },
                            profileAvatar = null,
                        ),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        return fixture
    }

    private fun home() = compose.onNodeWithContentDescription(MainTab.Feed.label)

    private fun SemanticsNodeInteraction.activateThroughSemantics() =
        performSemanticsAction(SemanticsActions.OnClick) { click -> assertTrue(click()) }

    private fun label(resource: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *args)

    private inner class Fixture(
        private val row: (Int) -> FeedItem,
        private val readingPreferences: ReadingPreferences,
        private val persistReading: Boolean,
    ) {
        lateinit var timeline: HomeTimelineReadingState
        lateinit var listState: LazyListState
        var reopenedTimeline by mutableStateOf<HomeTimelineReadingState?>(null)
        var onRefreshResult: ((HomeTimelineReadingState, Boolean) -> Unit)? = null
        var feedTopOnScreen = Float.NaN
        var refreshes = 0
        var pages = 0
        var menuOpens = 0
        val switches = AtomicInteger()
        val homeSwitches = AtomicInteger()
        val resumeActivations = AtomicInteger()
        private val refreshDeliveries = AtomicInteger()
        val accountId = (System.nanoTime() and Long.MAX_VALUE).toString()

        fun openTimeline(
            context: Context,
            kind: TimelineKind,
            rows: List<FeedItem>,
            ownerId: String = accountId,
            groupId: String? = null,
        ) = HomeTimelineReadingState(
            ReadingPreferencesStore(context), TimelineCacheStore(context), kind, readingPreferences,
            TimelineReadingBoundaryStore(context).takeIf { persistReading },
        ).apply {
            selectedTimelineGroupId = groupId
            feedCacheLoaded = true
            timelineVerifiedFromNetwork = true
            items = rows
            timelineCacheAccountId = ownerId
            acceptReadingRows(ownerId, chronological = true)
        }

        fun refresh(timeline: HomeTimelineReadingState, dockGesture: Boolean) {
            refreshes++
            val complete = onRefreshResult
            onRefreshResult = null
            if (complete != null) {
                complete(timeline, dockGesture)
                refreshDeliveries.incrementAndGet()
            }
        }

        fun assertRailCount(count: Int) {
            compose.onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label(R.string.feed_scroll_posts_above, count)),
                useUnmergedTree = true,
            ).assertExists()
        }

        fun reopen(
            ownerId: String = accountId,
            kind: TimelineKind = TimelineKind.Following,
            groupId: String? = null,
            rows: List<FeedItem>? = null,
        ) = compose.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            reopenedTimeline = openTimeline(context, kind, rows ?: timeline.items, ownerId, groupId)
        }

        fun dockRefresh(rows: List<FeedItem>) = dockRefresh(Result.success(TimelinePage(rows)))

        fun dockRefresh(result: Result<TimelinePage>) {
            val expected = compose.runOnIdle {
                onRefreshResult = { timeline, dockGesture ->
                    assertTrue(dockGesture)
                    processRefresh(timeline, result, explicit = true, dockGesture = dockGesture)
                }
                refreshDeliveries.get() + 1
            }
            home().assertIsSelected().performTouchInput { doubleClick() }
            compose.waitUntil(timeoutMillis = 2_000) { refreshDeliveries.get() == expected }
        }

        fun deliverRefresh(result: Result<TimelinePage>, explicit: Boolean) = compose.runOnIdle {
            processRefresh(timeline, result, explicit, dockGesture = false)
        }

        private fun processRefresh(
            timeline: HomeTimelineReadingState,
            result: Result<TimelinePage>,
            explicit: Boolean,
            dockGesture: Boolean,
        ) {
            timeline.timelineRequestGeneration++
            timeline.feedRefreshHint = null
            timeline.cancelReadingResume()
            val owner = requireNotNull(timeline.readingScope).accountId
            val baseline = if (explicit) timeline.captureExplicitRefreshBaseline(owner) else null
            val startedAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            val page = result.getOrNull() ?: return
            val count = timeline.publishRefresh(
                page, listState, protectViewport = true, keepReading = true,
                dockRefresh = dockGesture, explicitBaseline = baseline,
            )
            // This fixture has no app-level network or status-capsule coordinator. The data
            // decision comes from the production publisher; display only a confirmed zero.
            if (count == 0 && startedAtTop) timeline.feedRefreshHint = "没有新微博了"
        }

        fun publish(newCount: Int, topNoNew: Boolean = false) = compose.runOnIdle {
            timeline.publishRefresh(
                TimelinePage((24 + newCount downTo 24).map(row)), listState,
                protectViewport = true, keepReading = true, dockRefresh = true, successfulNoNewAtTop = topNoNew,
            )
        }

        fun scrollTo(index: Int, offset: Int = 0) = compose.runOnIdle {
            listState.requestScrollToItem(index, offset)
        }

        fun positionFoldUnderPartiallyVisiblePost() {
            val dividerIndex = compose.runOnIdle { requireNotNull(timeline.feedProjection.dividerIndex) }
            scrollTo(index = dividerIndex)
            val dividerHeight = compose.runOnIdle {
                listState.layoutInfo.visibleItemsInfo.first { it.index == dividerIndex }.size
            }
            scrollTo(index = dividerIndex - 1)
            val offset = compose.runOnIdle {
                val layout = listState.layoutInfo
                val newer = layout.visibleItemsInfo.first { it.index == dividerIndex - 1 }
                // Derive the scroll amount from measured rows and viewport, not emulator density.
                val centerOffset = newer.size + layout.beforeContentPadding + dividerHeight / 2 - layout.viewportSize.height / 2
                centerOffset.coerceIn(layout.beforeContentPadding + 1, newer.size - 1)
            }
            scrollTo(index = dividerIndex - 1, offset = offset)
        }

        fun touchReselect(tab: SemanticsNodeInteraction) {
            val expected = resumeActivations.get() + 1
            tab.assertIsSelected().performTouchInput { click() }
            // Compose can report idle before the pointer recognizer's double-tap window ends.
            compose.waitUntil(timeoutMillis = 2_000) { resumeActivations.get() == expected }
        }

        private fun indexOfPost(number: Int): Int {
            val projection = timeline.feedProjection
            val index = projection.visibleItems.indexOfFirst { it.id == post(number).id }
            assertTrue("Expected post $number in the rendered projection", index >= 0)
            return projection.lazyIndexOfPost(index)
        }

        fun firstVisibleRow(): Pair<Any, Int> = compose.runOnIdle {
            val row = listState.layoutInfo.visibleItemsInfo.first { it.index == listState.firstVisibleItemIndex }
            row.key to listState.firstVisibleItemScrollOffset
        }

        fun scrollPastPaddingAtPost(number: Int) = compose.runOnIdle {
            val layout = listState.layoutInfo
            listState.requestScrollToItem(indexOfPost(number), layout.beforeContentPadding + 1)
        }

        fun assertResumedAtPost(number: Int) = assertResumedAtRow(compose.runOnIdle { indexOfPost(number) }, number)

        fun assertResumedAtOldHead(index: Int) = assertResumedAtRow(index, 24)

        fun assertResumedAtRow(index: Int, postNumber: Int) {
            compose.runOnIdle { assertEquals(index, timeline.readingBoundaryLazyIndex) }
            compose.waitUntil(timeoutMillis = 2_000) {
                !listState.isScrollInProgress && listState.firstVisibleItemIndex == index &&
                    listState.firstVisibleItemScrollOffset == 0
            }
            assertAtRow(index, postNumber)
        }

        fun assertAtRow(index: Int, postNumber: Int, offset: Int = 0) = compose.runOnIdle {
            assertEquals(index, listState.firstVisibleItemIndex)
            assertEquals(offset, listState.firstVisibleItemScrollOffset)
            val row = listState.layoutInfo.visibleItemsInfo.first { it.index == index }
            assertEquals(post(postNumber).id, row.key)
            assertEquals(-offset, row.offset)
        }
    }

    private fun pauseForRolloverWalkthrough() {
        if (InstrumentationRegistry.getArguments().getString("viboRolloverWalkthrough") == "true") {
            SystemClock.sleep(1_500)
        }
    }

    private fun pauseForFoldWalkthrough() {
        if (InstrumentationRegistry.getArguments().getString("viboFoldWalkthrough") == "true") {
            SystemClock.sleep(1_500)
        }
    }

    private fun fourFoldedPostsFixture(number: Int, coverPackage: String): FeedItem = post(number).let { row ->
        fun portraitVideo(title: String) = FeedMedia(
            type = MediaType.Video,
            title = title,
            coverUrl = "android.resource://$coverPackage/${R.drawable.ic_vibo_foreground}",
            streamUrl = "file:///android_asset/live_photo_retry_fixture.mp4", // Preview only; never played.
            durationSeconds = 12,
            coverWidth = 360,
            coverHeight = 640,
        )
        when (number) {
            29 -> row.copy(text = "New post 29: portrait clip", medias = listOf(portraitVideo("Newer clip")))
            28 -> row.copy(text = "New post 28: one line")
            27 -> row.copy(text = "New post 27: two lines\nSecond line")
            26 -> row.copy(text = "New post 26: three lines\nSecond line\nThird line")
            25 -> row.copy(text = "New post 25: oldest folded reply\nSecond line\nThird line\nFourth line")
            24 -> row.copy(text = "Old post 24: portrait video", medias = listOf(portraitVideo("Old clip")))
            else -> row
        }
    }

    private fun rolloverPost(number: Int) = post(number).let { row ->
        val title = when (number) {
            28 -> "D · Newest post from this refresh"
            27 -> "C · First unread post from this refresh"
            26 -> "B · Previous unread batch"
            25 -> "A · Previous unread batch"
            24 -> "R · Last read post"
            else -> row.text
        }
        row.copy(text = title)
    }

    private fun post(number: Int) = FeedItem(
        id = "reverse-post-$number",
        statusId = "reverse-status-$number",
        authorId = "fixture-author",
        authorName = "Fixture author",
        authorAvatarUrl = null,
        createdAt = (1_700_000_000L + number).toString(),
        source = null,
        ipLocation = null,
        text = if (number > 24) "New post $number" else "Earlier post $number",
        repostsCount = "0",
        commentsCount = "0",
        likesCount = "0",
        images = emptyList(),
    )
}
