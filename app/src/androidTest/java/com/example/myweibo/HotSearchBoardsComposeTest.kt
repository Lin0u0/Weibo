package com.example.myweibo

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myweibo.data.HotSearchItem
import com.example.myweibo.ui.HotSearchBoards
import com.example.myweibo.ui.PersonalHotSearchState
import com.example.myweibo.ui.theme.MyWeiboTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HotSearchBoardsComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bothBoardsStartUnderTabsAndOnlyTheVisibleBoardDeterminesScrollHeight() {
        val standard = (1..45).map { HotSearchItem("#Hot topic $it#") }
        val personal = listOf(HotSearchItem("Personal topic"))
        val listState = LazyListState()
        var selectedWord: String? = null
        compose.setContent {
            // Shorter text lines expose the accumulated slack in a guessed per-row height.
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 0.8f)) {
                MyWeiboTheme(darkTheme = false) {
                    val pager = rememberPagerState(pageCount = { 2 })
                    val personalState = remember { PersonalHotSearchState() }
                    LazyColumn(state = listState, modifier = Modifier.height(320.dp)) {
                        item {
                            HotSearchBoards(
                                loadPersonal = { personal },
                                pager = pager,
                                personal = personalState,
                                standardItems = standard,
                                standardLoading = false,
                                standardError = null,
                                refreshGeneration = 1,
                                onRetryStandard = {},
                                onSelect = { selectedWord = it },
                            )
                        }
                    }
                }
            }
        }

        assertFirstTopicUnderTab("热搜", "Hot topic 1")
        compose.onNodeWithText("我的").performClick()
        assertFirstTopicUnderTab("我的", "Personal topic")
        compose.runOnIdle { assertFalse(listState.canScrollForward) }

        compose.onNodeWithText("热搜").performClick()
        assertFirstTopicUnderTab("热搜", "Hot topic 1")
        compose.runOnIdle { listState.requestScrollToItem(index = 0, scrollOffset = 10_000) }
        compose.onNodeWithText("Hot topic 45").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("#Hot topic 45#", selectedWord) }
    }

    private fun assertFirstTopicUnderTab(tab: String, firstTopic: String) {
        val tabLabelBottom = compose.onNodeWithText(tab).getUnclippedBoundsInRoot().bottom
        val topic = compose.onNodeWithText(firstTopic).assertIsDisplayed()
        val gap = topic.getUnclippedBoundsInRoot().top - tabLabelBottom
        // Allow for the tab underline and the row's normal text padding.
        assertTrue("Expected $firstTopic directly below $tab; actual gap: $gap", gap in 0.dp..24.dp)
    }
}
