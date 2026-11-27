package com.example.myweibo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myweibo.data.TimelineGroup
import com.example.myweibo.data.TimelineKind
import com.example.myweibo.data.ViboDockEntry
import com.example.myweibo.data.ViboDockPreferences
import com.example.myweibo.data.viboDockCatalogue
import com.example.myweibo.ui.ViboDockSettings
import com.example.myweibo.ui.liquidglass.SettingsLiquidSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViboDockSettingsComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun buttonsSwitchesAndResetUpdateThePreviewConfiguration() {
        val preferences = mutableStateOf(ViboDockPreferences())
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ViboDockSettings(preferences.value, { preferences.value = it })
                }
            }
        }

        compose.onNodeWithTag("vibo_dock_visibility_HOME").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("vibo_dock_visibility_PROFILE").assertDoesNotExist()
        compose.onNodeWithTag("vibo_dock_visibility_SEARCH").assertDoesNotExist()
        compose.onNodeWithTag("vibo_dock_up_PROFILE").assertDoesNotExist()
        assertPreviewEndcaps()
        compose.onNodeWithTag("vibo_dock_up_HOME").assertIsNotEnabled()
        compose.onNodeWithContentDescription("上移消息").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(ViboDockEntry.Messages.id, ViboDockEntry.Home.id), preferences.value.visibleEntryIds.take(2))
        }

        compose.onNodeWithTag("vibo_dock_visibility_MESSAGES").assertIsOn().performScrollTo().performClick()
        compose.onNodeWithTag("vibo_dock_visibility_MESSAGES").assertIsOff()
        compose.onNodeWithTag("vibo_dock_visibility_COMPOSE").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(ViboDockEntry.Home.id), preferences.value.visibleEntryIds) }

        compose.onNodeWithTag("vibo_dock_labels").assertIsOn().performScrollTo().performClick()
        compose.onNodeWithTag("vibo_dock_labels").assertIsOff()
        assertPreviewEndcaps()
        compose.onNodeWithTag("vibo_dock_restore_defaults").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ViboDockPreferences(), preferences.value) }
        compose.onNodeWithTag("vibo_dock_labels").assertIsOn()
    }

    @Test fun timelineAndGroupRowsChangeTheirOwnPreviewEntries() {
        val preferences = mutableStateOf(ViboDockPreferences())
        val group = TimelineGroup("42", "活动组")
        val catalogue = viboDockCatalogue("account", listOf(group))
        val groupId = catalogue.single { it.group == group }.id
        val specialId = catalogue.single { it.timelineKind == TimelineKind.SpecialFollowing }.id
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ViboDockSettings(preferences.value, { preferences.value = it }, catalogue = catalogue)
                }
            }
        }

        compose.onNodeWithText("常用项目").assertExists()
        compose.onNodeWithText("时间线").assertExists()
        compose.onNodeWithText("我的分组").assertExists()
        compose.onNodeWithTag("vibo_dock_visibility_MESSAGES").performScrollTo().performClick()
        compose.onNodeWithTag("vibo_dock_visibility_COMPOSE").performScrollTo().performClick()
        val specialToggle = compose.onNodeWithTag("vibo_dock_visibility_$specialId")
        val groupToggle = compose.onNodeWithTag("vibo_dock_visibility_$groupId")
        specialToggle.assertIsOff().performScrollTo().performClick().assertIsOn()
        groupToggle.assertIsOff().performScrollTo().performClick().assertIsOn()
        compose.onNodeWithTag("vibo_dock_up_$groupId").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(ViboDockEntry.Home.id, groupId, specialId), preferences.value.visibleEntryIds)
        }
        assertPreviewEndcaps()
        compose.onNodeWithTag("vibo_dock_preview_$groupId", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("vibo_dock_preview_$specialId", useUnmergedTree = true).assertExists()

        groupToggle.performScrollTo().performClick().assertIsOff()
        specialToggle.assertIsOn()
        compose.onNodeWithTag("vibo_dock_preview_$groupId", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("vibo_dock_visibility_HOME").performScrollTo().performClick().assertIsOff()
        specialToggle.assertIsOn().assertIsNotEnabled()
    }

    @Test fun fourthCenterSlotDisablesOtherChoicesUntilOneIsUnchecked() {
        val preferences = mutableStateOf(ViboDockPreferences())
        val group = TimelineGroup("42", "活动组")
        val catalogue = viboDockCatalogue("account", listOf(group))
        val groupId = catalogue.single { it.group == group }.id
        val friendsId = ViboDockEntry.forTimeline(TimelineKind.FriendsCircle).id
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ViboDockSettings(preferences.value, { preferences.value = it }, catalogue = catalogue)
                }
            }
        }

        assertPreviewEndcaps()
        compose.onNodeWithText("中间 3/4 项").assertExists()
        compose.onNodeWithTag("vibo_dock_visibility_$friendsId").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("中间 4/4 项").assertExists()
        compose.onNodeWithText("中间已满，请先取消一项。").assertExists()
        val groupToggle = compose.onNodeWithTag("vibo_dock_visibility_$groupId")
        groupToggle.assertIsOff().assertIsNotEnabled().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "中间已满，请先取消一项。"),
        )
        compose.onNodeWithTag("vibo_dock_preview_$groupId", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag("vibo_dock_visibility_MESSAGES").performScrollTo().performClick().assertIsOff()
        groupToggle.assertIsEnabled().performScrollTo().performClick().assertIsOn()
        compose.runOnIdle {
            assertEquals(listOf("HOME", "COMPOSE", friendsId, groupId), preferences.value.visibleEntryIds)
        }
        compose.onNodeWithText("中间 4/4 项").assertExists()
        assertPreviewEndcaps()
    }

    @Test fun cancelledSwitchDragRestoresCommittedIndexWithoutCallbacks() {
        val selections = mutableListOf<Int>()
        var settled = 0
        compose.setContent {
            MaterialTheme {
                SettingsLiquidSwitch(
                    selectedIndex = 0,
                    onSelected = { selections.add(it) },
                    onSelectionSettled = { settled++ },
                    modifier = Modifier.testTag("settings_switch"),
                    optionCount = 3,
                    trackWidth = 172.dp,
                    thumbWidth = 56.dp,
                )
            }
        }

        val switch = compose.onNodeWithTag("settings_switch")
        switch.performTouchInput {
            down(Offset(width / 6f, center.y))
            moveTo(Offset(width * 5f / 6f, center.y))
            cancel()
        }
        compose.runOnIdle {
            assertTrue(selections.isEmpty())
            assertEquals(0, settled)
        }

        // This drag stays on index 0 only if the cancelled drag's offset was reset.
        switch.performTouchInput {
            down(Offset(width / 6f, center.y))
            moveTo(Offset(width / 4f, center.y))
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(0), selections)
        }
    }

    private fun assertPreviewEndcaps() {
        val search = compose.onNodeWithTag("vibo_dock_preview_search", useUnmergedTree = true)
        search.performScrollTo()
        val items = compose.onNodeWithTag("vibo_dock_preview_items", useUnmergedTree = true)
        val profile = compose.onNodeWithTag("vibo_dock_preview_profile", useUnmergedTree = true)
        assertTrue(
            "The permanent Search button should be to the left of the configurable dock items",
            search.fetchSemanticsNode().boundsInRoot.right < items.fetchSemanticsNode().boundsInRoot.left,
        )
        assertTrue(
            "The permanent Profile button should be to the right of the configurable dock items",
            items.fetchSemanticsNode().boundsInRoot.right < profile.fetchSemanticsNode().boundsInRoot.left,
        )
    }
}
