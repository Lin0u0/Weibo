package com.example.myweibo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myweibo.data.TimelineGroup
import com.example.myweibo.data.TimelineKind
import com.example.myweibo.data.ViboDockEntry
import com.example.myweibo.data.ViboDockPreferences
import com.example.myweibo.data.viboDockCatalogue
import com.example.myweibo.ui.MainTab
import com.example.myweibo.ui.ViboDockRuntime
import com.example.myweibo.ui.WeiboLiquidBottomBar
import com.kyant.backdrop.backdrops.emptyBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViboDockRuntimeComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overflowingShortcutsKeepEndcapsFixedAndSelectOnlyOneEntry() {
        val groups = listOf(TimelineGroup("a", "读书"), TimelineGroup("b", "旅行"))
        val catalogue = viboDockCatalogue("fixture", groups)
        val group = catalogue.single { it.group?.id == "b" }
        val hidden = catalogue.single { it.timelineKind == TimelineKind.Liked }
        val near = catalogue.single { it.timelineKind == TimelineKind.Nearby }
        val preferences = ViboDockPreferences(
            visibleEntryIds = listOf(ViboDockEntry.Home.id, ViboDockEntry.Messages.id, near.id, group.id),
        )
        var selectedTab by mutableStateOf(MainTab.Feed)
        var selectedEntryId by mutableStateOf(hidden.id)
        var menuExpanded by mutableStateOf(false)
        var clickedEntry: ViboDockEntry? = null
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 320.dp, height = 220.dp)) {
                    WeiboLiquidBottomBar(
                        selectedTab = selectedTab,
                        onTabChange = { selectedTab = it },
                        backdrop = emptyBackdrop(),
                        timelineMenuExpanded = menuExpanded,
                        onTimelineMenuExpandedChange = { menuExpanded = it },
                        timelineMenuContent = { Text("Timeline picker") },
                        modifier = Modifier.align(Alignment.BottomCenter),
                        dockPreferences = preferences,
                        glassAmount = 0f,
                        applyNavigationBarInsets = false,
                        dockRuntime = ViboDockRuntime(
                            catalogue = catalogue,
                            selectedEntryId = selectedEntryId,
                            onEntrySelected = {
                                clickedEntry = it
                                selectedEntryId = it.id
                            },
                            profileAvatarUrl = null,
                        ),
                    )
                }
            }
        }

        val home = entry(ViboDockEntry.Home)
        val search = entry(ViboDockEntry.Search)
        val mine = entry(ViboDockEntry.Profile)
        val searchBounds = search.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val mineBounds = mine.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(searchBounds.right < home.fetchSemanticsNode().boundsInRoot.left)
        compose.onNodeWithTag("vibo_dock_avatar_fallback", useUnmergedTree = true).assertIsDisplayed()
        entry(hidden).assertDoesNotExist()
        home.assertIsSelected().performTouchInput { longClick() }
        compose.onNodeWithText("Timeline picker").assertIsDisplayed()
        compose.runOnIdle { menuExpanded = false }

        compose.onNodeWithTag("vibo_dock_capsule").performTouchInput { swipeLeft() }
        assertEquals(searchBounds, search.fetchSemanticsNode().boundsInRoot)
        assertEquals(mineBounds, mine.fetchSemanticsNode().boundsInRoot)

        // A selection from the timeline picker should reveal the pinned shortcut.
        compose.runOnIdle { selectedEntryId = group.id }
        val shortcut = entry(group).assertIsDisplayed().assertIsSelected()
        home.assert(notSelected)
        shortcut.performClick()
        compose.runOnIdle { assertEquals(group, clickedEntry) }
        mine.performClick().assertIsSelected()
        shortcut.assert(notSelected)
    }

    @Test fun timelineLongPressStillOpensPickerWhenHomeIsHidden() {
        val special = ViboDockEntry.forTimeline(TimelineKind.SpecialFollowing)
        val favorites = ViboDockEntry.forTimeline(TimelineKind.Favorites)
        val unpinned = ViboDockEntry.forTimeline(TimelineKind.Hot)
        val catalogue = listOf(special, favorites, unpinned)
        var selectedEntryId by mutableStateOf(special.id)
        var menuExpanded by mutableStateOf(false)
        var selections = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 320.dp, height = 220.dp)) {
                    WeiboLiquidBottomBar(
                        selectedTab = MainTab.Feed,
                        onTabChange = {},
                        backdrop = emptyBackdrop(),
                        timelineMenuExpanded = menuExpanded,
                        onTimelineMenuExpandedChange = { menuExpanded = it },
                        timelineMenuContent = { Text("Timeline picker") },
                        modifier = Modifier.align(Alignment.BottomCenter),
                        dockPreferences = ViboDockPreferences(visibleEntryIds = listOf(special.id, favorites.id)),
                        glassAmount = 0f,
                        applyNavigationBarInsets = false,
                        dockRuntime = ViboDockRuntime(
                            catalogue = catalogue,
                            selectedEntryId = selectedEntryId,
                            onEntrySelected = { selections++ },
                            profileAvatarUrl = null,
                        ),
                    )
                }
            }
        }

        entry(ViboDockEntry.Home).assertDoesNotExist()
        entry(favorites).assert(notSelected).performTouchInput { longClick() }
        compose.onNodeWithText("Timeline picker").assertIsDisplayed()
        entry(special).assertIsSelected()
        compose.runOnIdle {
            assertEquals(0, selections)
            menuExpanded = false
            selectedEntryId = unpinned.id
        }

        // An unpinned route neither selects a different shortcut nor removes access to the picker.
        entry(unpinned).assertDoesNotExist()
        entry(special).assert(notSelected).performTouchInput { longClick() }
        compose.onNodeWithText("Timeline picker").assertIsDisplayed()
        entry(special).assert(notSelected)
        entry(favorites).assert(notSelected)
        compose.runOnIdle { assertEquals(0, selections) }
    }

    private fun entry(entry: ViboDockEntry) = compose.onNodeWithContentDescription(entry.title)
    private val notSelected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
}
