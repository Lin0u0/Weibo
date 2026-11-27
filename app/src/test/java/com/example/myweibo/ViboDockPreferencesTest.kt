package com.example.myweibo

import com.example.myweibo.data.ViboDockItem.COMPOSE
import com.example.myweibo.data.ViboDockItem.HOME
import com.example.myweibo.data.ViboDockItem.MESSAGES
import com.example.myweibo.data.ViboDockItem.PROFILE
import com.example.myweibo.data.ViboDockPreferences
import com.example.myweibo.data.decodeViboDockPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViboDockPreferencesTest {
    private val defaults = ViboDockPreferences()

    @Test fun defaultsHaveFourItemsAndLabels() {
        assertEquals(listOf(HOME, MESSAGES, COMPOSE, PROFILE), defaults.visibleItems)
        assertTrue(defaults.showLabels)
        assertEquals(defaults, decodeViboDockPreferences(null, null))
    }

    @Test fun homeAndProfileCannotBeHiddenButCanBeReordered() {
        val two = defaults.withItemVisibility(MESSAGES, false).withItemVisibility(COMPOSE, false)
        assertEquals(listOf(HOME, PROFILE), two.visibleItems)
        assertEquals(two, two.withItemVisibility(HOME, false))
        assertEquals(two, two.withItemVisibility(PROFILE, false))
        assertEquals(two, two.moveItem(MESSAGES, 0))
        assertEquals(two, two.moveItem(HOME, 3))
        assertEquals(listOf(PROFILE, HOME), two.moveItem(PROFILE, 0).visibleItems)
    }

    @Test fun hidingAndShowingKeepsTheCustomRowOrder() {
        val reordered = defaults.moveItem(MESSAGES, 0)
        val hidden = reordered.withItemVisibility(MESSAGES, false)
        assertEquals(reordered.itemOrder, hidden.itemOrder)
        assertEquals(listOf(HOME, COMPOSE, PROFILE), hidden.visibleItems)
        assertEquals(reordered, hidden.withItemVisibility(MESSAGES, true))

        val movedWhileHidden = hidden.moveItem(PROFILE, 0)
        assertEquals(listOf(MESSAGES, PROFILE, HOME, COMPOSE), movedWhileHidden.itemOrder)
        assertEquals(movedWhileHidden.itemOrder, movedWhileHidden.withItemVisibility(MESSAGES, true).visibleItems)
    }

    @Test fun decodingPreservesValidOptionsAndRepairsInvalidValues() {
        assertEquals(defaults, decodeViboDockPreferences(123, "false", "UNKNOWN"))
        val repaired = decodeViboDockPreferences("PROFILE,SEARCH,PROFILE", false)
        assertEquals(listOf(PROFILE, HOME), repaired.visibleItems)
        assertFalse(repaired.showLabels)
        val restored = decodeViboDockPreferences("PROFILE,HOME", false, "COMPOSE,PROFILE,HOME,MESSAGES")
        assertEquals(listOf(PROFILE, HOME), restored.visibleItems)
        assertEquals(listOf(COMPOSE, PROFILE, HOME, MESSAGES), restored.itemOrder)
    }
}
