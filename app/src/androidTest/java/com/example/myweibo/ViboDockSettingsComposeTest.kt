package com.example.myweibo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myweibo.data.ViboDockItem.HOME
import com.example.myweibo.data.ViboDockItem.MESSAGES
import com.example.myweibo.data.ViboDockItem.PROFILE
import com.example.myweibo.data.ViboDockPreferences
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

        compose.onNodeWithTag("vibo_dock_visibility_HOME").assertDoesNotExist()
        compose.onNodeWithTag("vibo_dock_visibility_PROFILE").assertDoesNotExist()
        compose.onNodeWithTag("vibo_dock_visibility_SEARCH").assertDoesNotExist()
        assertPreviewSearchIsLeftOfItems()
        compose.onNodeWithTag("vibo_dock_up_HOME").assertIsNotEnabled()
        compose.onNodeWithContentDescription("上移消息").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(MESSAGES, HOME), preferences.value.visibleItems.take(2)) }

        compose.onNodeWithTag("vibo_dock_visibility_MESSAGES").assertIsOn().performScrollTo().performClick()
        compose.onNodeWithTag("vibo_dock_visibility_MESSAGES").assertIsOff()
        compose.onNodeWithTag("vibo_dock_visibility_COMPOSE").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(HOME, PROFILE), preferences.value.visibleItems) }

        compose.onNodeWithTag("vibo_dock_labels").assertIsOn().performScrollTo().performClick()
        compose.onNodeWithTag("vibo_dock_labels").assertIsOff()
        assertPreviewSearchIsLeftOfItems()
        compose.onNodeWithTag("vibo_dock_restore_defaults").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ViboDockPreferences(), preferences.value) }
        compose.onNodeWithTag("vibo_dock_labels").assertIsOn()
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

    private fun assertPreviewSearchIsLeftOfItems() {
        val search = compose.onNodeWithTag("vibo_dock_preview_search", useUnmergedTree = true)
        search.performScrollTo()
        val items = compose.onNodeWithTag("vibo_dock_preview_items", useUnmergedTree = true)
        assertTrue(
            "The permanent Search button should be to the left of the configurable dock items",
            search.fetchSemanticsNode().boundsInRoot.right < items.fetchSemanticsNode().boundsInRoot.left,
        )
    }
}
