package com.example.myweibo

import com.example.myweibo.data.TimelineGroup
import com.example.myweibo.data.TimelineKind
import com.example.myweibo.data.ViboDockEntry
import com.example.myweibo.data.viboDockCatalogue
import com.example.myweibo.ui.selectedViboTimelineShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViboDockTimelineSelectionTest {
    private val group = TimelineGroup(id = "test-group", name = "同事")

    @Test fun builtInTimelineSelectsItsOwnShortcut() {
        val entries = viboDockCatalogue("123", listOf(group))
        for (kind in TimelineKind.entries) {
            assertEquals(ViboDockEntry.forTimeline(kind).id, selectedViboTimelineShortcut(entries, kind, null))
        }
    }

    @Test fun customGroupDoesNotHighlightTheBuiltInSpecialFollowingShortcut() {
        val entries = viboDockCatalogue("123", listOf(group))
        assertEquals(
            ViboDockEntry.forGroup("123", group).id,
            selectedViboTimelineShortcut(entries, TimelineKind.SpecialFollowing, group.id),
        )
        assertNull(selectedViboTimelineShortcut(viboDockCatalogue(null, emptyList()), TimelineKind.SpecialFollowing, group.id))
    }
}
