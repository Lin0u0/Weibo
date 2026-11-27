package com.example.myweibo

import com.example.myweibo.data.TimelineGroup
import com.example.myweibo.data.TimelineKind
import com.example.myweibo.data.ViboDockEntry
import com.example.myweibo.data.ViboDockPreferences
import com.example.myweibo.data.decodeViboDockPreferences
import com.example.myweibo.data.viboDockCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViboDockPreferencesTest {
    private val defaults = ViboDockPreferences()
    private val home = ViboDockEntry.Home.id
    private val messages = ViboDockEntry.Messages.id
    private val compose = ViboDockEntry.Compose.id
    private val profile = ViboDockEntry.Profile.id
    private val search = ViboDockEntry.Search.id
    private val builtIns = viboDockCatalogue(null, emptyList())

    @Test fun defaultGroupedEntriesOnlyHaveHomeMessagesAndCompose() {
        assertEquals(listOf(home, messages, compose), defaults.orderedVisibleEntries(builtIns).ids())
        assertTrue(defaults.showLabels)
        assertEquals(defaults, decodeViboDockPreferences(null, null))
    }

    @Test fun everyHomeMenuTimelineHasOneStableShortcut() {
        val expected = mapOf(
            TimelineKind.Following to home,
            TimelineKind.FriendsCircle to "timeline:friends-circle",
            TimelineKind.SpecialFollowing to "timeline:special-following",
            TimelineKind.Favorites to "timeline:favorites",
            TimelineKind.Liked to "timeline:liked",
            TimelineKind.Hot to "timeline:hot",
            TimelineKind.Nearby to "timeline:nearby",
        )
        assertEquals(TimelineKind.entries.toSet(), expected.keys)
        assertEquals(expected, builtIns.associate { it.timelineKind to it.id })
        assertEquals(builtIns.size, builtIns.map(ViboDockEntry::iconRes).distinct().size)
        for (shortcut in builtIns.filterNot { it.id == home }) {
            val enabled = defaults.withEntryVisibility(shortcut.id, true, builtIns)
            assertEquals(listOf(home, messages, compose, shortcut.id), enabled.orderedVisibleEntries(builtIns).ids())
            val moved = enabled.moveEntry(shortcut.id, 0, builtIns)
            assertEquals(listOf(shortcut.id, home, messages, compose), moved.orderedVisibleEntries(builtIns).ids())
            val hidden = moved.withEntryVisibility(shortcut.id, false, builtIns)
            assertEquals(defaults.orderedVisibleEntries(builtIns), hidden.orderedVisibleEntries(builtIns))
            assertEquals(moved, hidden.withEntryVisibility(shortcut.id, true, builtIns))
        }
    }

    @Test fun theLastTimelineCannotBeHiddenAndTheEndcapsCannotEnterTheGroup() {
        val onlyHome = defaults.withEntryVisibility(messages, false, builtIns)
            .withEntryVisibility(compose, false, builtIns)
        val withEndcaps = builtIns + ViboDockEntry.Profile + ViboDockEntry.Search
        assertEquals(listOf(home), onlyHome.orderedVisibleEntries(withEndcaps).ids())
        assertEquals(onlyHome, onlyHome.withEntryVisibility(home, false, withEndcaps))
        assertEquals(onlyHome, onlyHome.withEntryVisibility(profile, true, withEndcaps))
        assertEquals(onlyHome, onlyHome.withEntryVisibility(search, true, withEndcaps))
        assertEquals(onlyHome, onlyHome.moveEntry(profile, 0, withEndcaps))
        assertEquals(onlyHome, onlyHome.moveEntry(home, 1, withEndcaps))
        assertEquals(listOf(messages, home, compose), defaults.moveEntry(home, 1, withEndcaps).visibleEntryIds)
    }

    @Test fun fourAvailableCenterEntriesBlockAFifthUntilOneIsHidden() {
        val group = TimelineGroup("42", "活动组")
        val catalogue = viboDockCatalogue("owner", listOf(group)) + ViboDockEntry.Search + ViboDockEntry.Profile
        val groupId = catalogue.single { it.group == group }.id
        val friends = ViboDockEntry.forTimeline(TimelineKind.FriendsCircle).id
        val full = defaults.withEntryVisibility(friends, true, catalogue)

        assertEquals(listOf(home, messages, compose, friends), full.orderedVisibleEntries(catalogue).ids())
        assertFalse(full.canShowEntry(groupId, catalogue))
        assertEquals(full, full.withEntryVisibility(groupId, true, catalogue))

        val freed = full.withEntryVisibility(messages, false, catalogue)
        assertTrue(freed.canShowEntry(groupId, catalogue))
        assertEquals(
            listOf(home, compose, friends, groupId),
            freed.withEntryVisibility(groupId, true, catalogue).orderedVisibleEntries(catalogue).ids(),
        )
    }

    @Test fun oversizedLegacySelectionIsCappedWithoutLosingDormantChoices() {
        val group = TimelineGroup("42", "活动组")
        val owner = viboDockCatalogue("owner", listOf(group))
        val groupId = owner.single { it.group == group }.id
        val friends = ViboDockEntry.forTimeline(TimelineKind.FriendsCircle).id
        val hot = ViboDockEntry.forTimeline(TimelineKind.Hot).id
        val legacy = decodeViboDockPreferences("$search,$messages,$groupId,$compose,$friends,$profile,$hot,$hot", false)

        assertEquals(listOf(messages, groupId, compose, friends), legacy.orderedVisibleEntries(owner).ids())
        assertEquals(listOf(messages, compose, friends, hot), legacy.orderedVisibleEntries(builtIns).ids())
        assertEquals(legacy, legacy.moveEntry(hot, 0, owner))
        assertEquals(legacy, legacy.moveEntry(friends, 4, owner))
        assertEquals(
            listOf(friends, messages, groupId, compose),
            legacy.moveEntry(friends, 0, owner).orderedVisibleEntries(owner).ids(),
        )
        assertTrue(groupId in legacy.visibleEntryIds)
        assertTrue(hot in legacy.visibleEntryIds)
    }

    @Test fun unavailableGroupsDoNotUseSlotsAndFallbackHomeIsTemporary() {
        val group = TimelineGroup("42", "活动组")
        val owner = viboDockCatalogue("owner", listOf(group))
        val groupId = owner.single { it.group == group }.id
        val friends = ViboDockEntry.forTimeline(TimelineKind.FriendsCircle).id
        val hot = ViboDockEntry.forTimeline(TimelineKind.Hot).id
        val saved = ViboDockPreferences(visibleEntryIds = listOf(messages, compose, groupId))

        assertEquals(listOf(home, messages, compose), saved.orderedVisibleEntries(builtIns).ids())
        assertFalse(saved.canHideEntry(home, builtIns))
        val withFriends = saved.withEntryVisibility(friends, true, builtIns)
        assertEquals(listOf(messages, compose, friends), withFriends.orderedVisibleEntries(builtIns).ids())
        assertTrue(withFriends.canShowEntry(hot, builtIns))
        assertFalse(withFriends.canShowEntry(hot, owner))
        val withHot = withFriends.withEntryVisibility(hot, true, builtIns)
        assertEquals(listOf(messages, compose, friends, hot), withHot.orderedVisibleEntries(builtIns).ids())
        assertEquals(listOf(messages, compose, groupId, friends), withHot.orderedVisibleEntries(owner).ids())
        assertFalse(home in withHot.visibleEntryIds)
    }

    @Test fun capReservesTheLastSlotForASelectedTimelineOrFallbackHome() {
        val friends = ViboDockEntry.forTimeline(TimelineKind.FriendsCircle)
        val malformedEntries = listOf("group:not-a-timeline", "timeline:not-a-timeline").map {
            ViboDockEntry(it, it, ViboDockEntry.Messages.iconRes)
        }
        val nonTimelines = listOf(messages, compose) + malformedEntries.ids()
        val catalogue = malformedEntries + friends
        val saved = ViboDockPreferences(
            visibleEntryIds = nonTimelines + friends.id,
            entryOrder = nonTimelines + friends.id + home,
        )

        assertEquals(nonTimelines.take(3) + friends.id, saved.orderedVisibleEntries(catalogue).ids())
        assertEquals(nonTimelines.take(3) + home, saved.orderedVisibleEntries(malformedEntries).ids())
        assertEquals(nonTimelines.take(3) + home, saved.copy(visibleEntryIds = nonTimelines).orderedVisibleEntries(catalogue).ids())
    }

    @Test fun migrationDropsProfileButKeepsRelativeOrderVisibilityAndLabelPreference() {
        assertEquals(defaults, decodeViboDockPreferences(123, "false", "UNKNOWN"))
        val profileOnly = decodeViboDockPreferences("PROFILE,SEARCH,PROFILE", false)
        assertEquals(listOf(home), profileOnly.visibleEntryIds)
        assertFalse(profileOnly.showLabels)

        val migrated = decodeViboDockPreferences(
            "MESSAGES,PROFILE,HOME", false, "COMPOSE,MESSAGES,PROFILE,HOME",
        )
        assertEquals(listOf(messages, home), migrated.visibleEntryIds)
        assertEquals(listOf(compose, messages, home), migrated.entryOrder)
        assertFalse(migrated.showLabels)
        assertEquals(
            listOf(compose, messages, home),
            migrated.withEntryVisibility(compose, true, builtIns).visibleEntryIds,
        )
    }

    @Test fun homeCanBeHiddenWithAnotherTimelineButFallbackHomeDoesNotChangeStorage() {
        val group = TimelineGroup("42", "活动组")
        val owner = viboDockCatalogue("account-a", listOf(group))
        val otherAccount = viboDockCatalogue("account-b", listOf(group))
        val groupId = owner.single { it.group != null }.id
        assertFalse(defaults.canHideEntry(home, owner))
        val withGroup = defaults.withEntryVisibility(groupId, true, owner)
        assertTrue(withGroup.canHideEntry(home, owner))
        val noHome = withGroup.withEntryVisibility(home, false, owner)
        assertFalse(noHome.canHideEntry(groupId, owner))
        assertEquals(noHome, noHome.withEntryVisibility(groupId, false, owner))
        assertEquals(listOf(messages, compose, groupId), noHome.orderedVisibleEntries(owner).ids())
        assertEquals(listOf(home, messages, compose), noHome.orderedVisibleEntries(otherAccount).ids())
        assertEquals(listOf(home, messages, compose), noHome.orderedVisibleEntries(builtIns).ids())
        assertFalse(home in noHome.visibleEntryIds)
        assertFalse(home in noHome.withEntryVisibility(messages, false, otherAccount).visibleEntryIds)
        assertEquals(noHome, decodeViboDockPreferences(
            noHome.visibleEntryIds.joinToString(","), noHome.showLabels, noHome.entryOrder.joinToString(","),
        ))
        val friends = ViboDockEntry.forTimeline(TimelineKind.FriendsCircle).id
        val builtInOnly = defaults.withEntryVisibility(friends, true, builtIns).withEntryVisibility(home, false, builtIns)
        assertEquals(listOf(messages, compose, friends), builtInOnly.orderedVisibleEntries(builtIns).ids())
    }

    @Test fun migrationFoldsAnExplicitFollowingShortcutIntoHomeWithoutDuplicateRows() {
        val migrated = decodeViboDockPreferences("timeline:following,MESSAGES,HOME", false, "timeline:following,MESSAGES,HOME,COMPOSE")
        assertEquals(listOf(home, messages), migrated.orderedVisibleEntries(builtIns).ids())
        assertEquals(listOf(home, messages, compose), migrated.entryOrder)
        assertFalse(migrated.showLabels)
    }

    @Test fun groupBadgesPreserveExtendedGraphemesAndResolveCollisionsStably() {
        val scientist = "👩🏽‍🔬"
        val groups = listOf(
            TimelineGroup("a", "${scientist}宇宙"),
            TimelineGroup("b", "${scientist}工作"),
            TimelineGroup("c", "${scientist}工作同行"),
            TimelineGroup("d", "🇨🇳读书"),
            TimelineGroup("e", "e\u0301lan"),
            TimelineGroup("f", "éducation"),
        )
        fun badges(input: List<TimelineGroup>) = viboDockCatalogue("owner", input)
            .filter { it.group != null }.associate { it.group!!.id to it.groupBadge }
        val result = badges(groups)
        assertEquals("${scientist}宇", result["a"])
        assertEquals("${scientist}工", result["b"])
        assertEquals("${scientist}2", result["c"])
        assertEquals("🇨🇳", result["d"])
        assertEquals("e\u0301l", result["e"])
        assertEquals("éd", result["f"])
        assertEquals(result, badges(groups.reversed()))
        assertEquals(groups.size, result.values.distinct().size)
    }

    @Test fun groupChoicesUseAccountAndGroupIdNotTitleOrListPosition() {
        val rawGroup = TimelineGroup("42,:% +", "旧名称")
        val accountOne = viboDockCatalogue("account:one", listOf(rawGroup, rawGroup.copy(name = "Duplicate")))
        val accountTwo = viboDockCatalogue("account:two", listOf(rawGroup))
        val oldEntry = accountOne.single { it.group != null }
        val otherEntry = accountTwo.single { it.group != null }
        val renamed = viboDockCatalogue("account:one", listOf(rawGroup.copy(name = "新名称")))
            .single { it.group != null }
        assertEquals(oldEntry.id, renamed.id)
        assertNotEquals(oldEntry.id, otherEntry.id)
        assertNotEquals(
            ViboDockEntry.forGroup("a:b", TimelineGroup("c", "first")).id,
            ViboDockEntry.forGroup("a", TimelineGroup("b:c", "second")).id,
        )
        assertFalse(oldEntry.id.contains(','))
        assertEquals("新名称", renamed.title)
        assertEquals(TimelineKind.SpecialFollowing, accountOne[accountOne.indexOf(oldEntry) - 1].timelineKind)
        assertEquals(builtIns, viboDockCatalogue(null, listOf(rawGroup)))
    }

    @Test fun unavailableShortcutsSurviveEditingResetAndStorageRoundTrip() {
        val first = TimelineGroup("1", "第一组")
        val second = TimelineGroup("2", "第二组")
        val catalogue = viboDockCatalogue("account-a", listOf(first, second))
        val otherAccount = viboDockCatalogue("account-b", listOf(first, second))
        val firstId = catalogue.single { it.group == first }.id
        val secondId = catalogue.single { it.group == second }.id
        val unknownTimelineId = "timeline:future-mode"
        val visible = "$home,$firstId,$messages,$unknownTimelineId,$compose"
        val ordered = "$home,$firstId,$secondId,$messages,$unknownTimelineId,$compose"
        val preferences = decodeViboDockPreferences(visible, false, ordered)
        assertEquals(listOf(home, messages, compose), preferences.orderedVisibleEntries(otherAccount).ids())

        val edited = preferences.moveEntry(compose, 0, otherAccount)
            .withEntryVisibility(messages, false, otherAccount)
        val readBack = decodeViboDockPreferences(
            edited.visibleEntryIds.joinToString(","), edited.showLabels, edited.entryOrder.joinToString(","),
        )
        assertEquals(edited, readBack)
        assertEquals(listOf(compose, firstId, home), readBack.orderedVisibleEntries(catalogue).ids())
        assertTrue(secondId in readBack.entryOrder)
        assertFalse(secondId in readBack.visibleEntryIds)
        assertTrue(unknownTimelineId in readBack.visibleEntryIds)

        val resetOnAnotherAccount = readBack.restoringDefaults(otherAccount)
        assertEquals(listOf(home, firstId, messages, compose), resetOnAnotherAccount.orderedVisibleEntries(catalogue).ids())
        assertTrue(secondId in resetOnAnotherAccount.entryOrder)
        assertTrue(unknownTimelineId in resetOnAnotherAccount.visibleEntryIds)
        val resetOnOwner = resetOnAnotherAccount.restoringDefaults(catalogue)
        assertFalse(firstId in resetOnOwner.visibleEntryIds)
        assertFalse(secondId in resetOnOwner.entryOrder)
    }

    private fun List<ViboDockEntry>.ids() = map(ViboDockEntry::id)
}
