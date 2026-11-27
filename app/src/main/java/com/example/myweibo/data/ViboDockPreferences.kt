package com.example.myweibo.data

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.myweibo.R
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/** A dock destination. Search and Profile are permanent endcaps, not grouped entries. */
data class ViboDockEntry(
    val id: String,
    val title: String,
    @DrawableRes val iconRes: Int,
    val timelineKind: TimelineKind? = null,
    val group: TimelineGroup? = null,
    @StringRes val titleRes: Int? = null,
    val groupBadge: String? = null,
) {
    val isTimeline: Boolean get() = timelineKind != null || group != null

    companion object {
        val Home = ViboDockEntry(
            "HOME", "首页", R.drawable.ic_tab_home, timelineKind = TimelineKind.Following,
            titleRes = R.string.vibo_dock_home,
        )
        val Messages = ViboDockEntry("MESSAGES", "消息", R.drawable.ic_tab_messages, titleRes = R.string.vibo_dock_messages)
        val Compose = ViboDockEntry("COMPOSE", "写微博", R.drawable.ic_tab_compose, titleRes = R.string.vibo_dock_compose)
        val Search = ViboDockEntry("SEARCH", "搜索", R.drawable.ic_tab_search, titleRes = R.string.vibo_dock_search)
        val Profile = ViboDockEntry("PROFILE", "我的", R.drawable.ic_tab_mine, titleRes = R.string.vibo_dock_profile)

        fun forTimeline(kind: TimelineKind): ViboDockEntry {
            // Persist explicit tokens instead of enum names, ordinals, or display labels.
            val (token, icon) = when (kind) {
                TimelineKind.Following -> return Home
                TimelineKind.FriendsCircle -> "friends-circle" to R.drawable.ic_dock_friends
                TimelineKind.SpecialFollowing -> "special-following" to R.drawable.ic_dock_special
                TimelineKind.Favorites -> "favorites" to R.drawable.ic_dock_favorites
                TimelineKind.Liked -> "liked" to R.drawable.ic_status_like
                TimelineKind.Hot -> "hot" to R.drawable.ic_dock_hot
                TimelineKind.Nearby -> "nearby" to R.drawable.ic_feed_location
            }
            return ViboDockEntry("$TIMELINE_PREFIX$token", kind.label, icon, timelineKind = kind)
        }

        fun forGroup(
            accountId: String,
            group: TimelineGroup,
            badge: String = dockGraphemes(group.name).first(),
        ): ViboDockEntry = ViboDockEntry(
            id = "$GROUP_PREFIX${encodeDockKeyPart(accountId)}:${encodeDockKeyPart(group.id)}",
            title = group.name,
            iconRes = R.drawable.ic_dock_group,
            group = group,
            groupBadge = badge,
        )
    }
}

internal const val VIBO_DOCK_MAX_CENTER_SLOTS = 4

private const val TIMELINE_PREFIX = "timeline:"
private const val LEGACY_FOLLOWING_ID = "timeline:following"
private const val GROUP_PREFIX = "group:"
private val defaultEntries = listOf(ViboDockEntry.Home, ViboDockEntry.Messages, ViboDockEntry.Compose)
private val defaultIds = defaultEntries.map(ViboDockEntry::id)

// Encode each component independently so even embedded separators are unambiguous.
private fun encodeDockKeyPart(value: String): String = URLEncoder.encode(value, "UTF-8")

/** Build from the same account and group snapshot as the Home long-press menu. */
fun viboDockCatalogue(accountId: String?, groups: List<TimelineGroup>): List<ViboDockEntry> = buildList {
    val kinds = TimelineKind.entries
    val primaryCount = kinds.indexOf(TimelineKind.SpecialFollowing) + 1
    addAll(kinds.take(primaryCount).map { ViboDockEntry.forTimeline(it) })
    if (!accountId.isNullOrBlank()) {
        val accountGroups = groups.distinctBy(TimelineGroup::id).filter { it.id.isNotBlank() }
        val badges = dockGroupBadges(accountGroups)
        accountGroups.forEach { group -> add(ViboDockEntry.forGroup(accountId, group, badges.getValue(group.id))) }
    }
    addAll(kinds.drop(primaryCount).map { ViboDockEntry.forTimeline(it) })
}

private val extendedGrapheme = Regex("\\X")
private fun dockGraphemes(name: String): List<String> = extendedGrapheme.findAll(name.trim())
    .take(2).map { it.value }.toList().ifEmpty { listOf("组") }

private fun badgeKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC).uppercase(Locale.ROOT)

/** Use one grapheme when possible, two when helpful, then append a stable collision index. */
private fun dockGroupBadges(groups: List<TimelineGroup>): Map<String, String> {
    val parts = groups.associate { it.id to dockGraphemes(it.name) }
    val byInitial = groups.groupBy { badgeKey(parts.getValue(it.id).first()) }
    val candidates = groups.associate { group ->
        val graphemes = parts.getValue(group.id)
        val initial = graphemes.first()
        group.id to if (byInitial.getValue(badgeKey(initial)).size == 1) initial else graphemes.joinToString("")
    }
    val used = candidates.values.map(::badgeKey).toMutableSet()
    return groups.groupBy { badgeKey(candidates.getValue(it.id)) }.toSortedMap().values.flatMap { matching ->
        matching.sortedBy(TimelineGroup::id).mapIndexed { index, group ->
            val candidate = candidates.getValue(group.id)
            if (index == 0) group.id to candidate else {
                val initial = parts.getValue(group.id).first()
                var suffix = index + 1
                while (!used.add(badgeKey("$initial$suffix"))) suffix++
                group.id to "$initial$suffix"
            }
        }
    }.toMap()
}

private fun groupedEntries(catalogue: List<ViboDockEntry>): Map<String, ViboDockEntry> =
    (catalogue.filter { isGroupedId(it.id) } + defaultEntries).associateBy(ViboDockEntry::id)

/** Compatibility for callers that check visibility of the original grouped tabs. */
enum class ViboDockItem { HOME, MESSAGES, COMPOSE }

data class ViboDockPreferences(
    val visibleEntryIds: List<String> = defaultIds,
    val showLabels: Boolean = true,
    internal val entryOrder: List<String> = defaultIds,
) {
    val visibleItems: List<ViboDockItem>
        get() = normalized().visibleEntryIds.filter { it in defaultIds }.map { ViboDockItem.valueOf(it) }

    /** Cap the current catalogue's projection without discarding stored or unavailable choices. */
    fun orderedVisibleEntries(catalogue: List<ViboDockEntry> = emptyList()): List<ViboDockEntry> {
        val current = normalized()
        val available = groupedEntries(catalogue)
        val selected = current.visibleEntryIds.toSet()
        val renderedIds = if (hasSelectedTimeline(available, selected)) selected else selected + ViboDockEntry.Home.id
        val ordered = current.entryOrder.filter(renderedIds::contains).mapNotNull(available::get)
        val capped = ordered.take(VIBO_DOCK_MAX_CENTER_SLOTS)
        // Reserve a slot for the first timeline if it would otherwise be cut off.
        return if (capped.any(ViboDockEntry::isTimeline)) capped
        else capped.take(VIBO_DOCK_MAX_CENTER_SLOTS - 1) + ordered.first(ViboDockEntry::isTimeline)
    }

    internal fun canShowEntry(id: String, catalogue: List<ViboDockEntry>): Boolean {
        val available = groupedEntries(catalogue)
        return canShowDockEntry(id, available, normalized().visibleEntryIds.toSet())
    }

    fun canHideEntry(id: String, catalogue: List<ViboDockEntry>): Boolean {
        val available = groupedEntries(catalogue)
        val selected = normalized().visibleEntryIds.toSet()
        val entry = available[id] ?: return false
        return id in selected && (!entry.isTimeline || hasSelectedTimeline(available, selected - id))
    }

    internal fun orderedEntries(catalogue: List<ViboDockEntry>): List<ViboDockEntry> {
        val available = groupedEntries(catalogue)
        return (normalized().entryOrder + available.keys).distinct().mapNotNull(available::get)
    }

    /** Normalize stored IDs, never dropping those missing from the current catalogue. */
    internal fun normalized(): ViboDockPreferences {
        val visible = visibleEntryIds.map(::migrateEntryId).filter(::isGroupedId).distinct()
        val completeOrder = (entryOrder + defaultIds + visible).map(::migrateEntryId).filter(::isGroupedId).distinct()
        val visibleSet = visible.toSet()
        val nextVisible = visible.iterator()
        return copy(visibleEntryIds = visible, entryOrder = completeOrder.map {
            if (it in visibleSet) nextVisible.next() else it
        })
    }

    fun withEntryVisibility(
        id: String,
        visible: Boolean,
        catalogue: List<ViboDockEntry>,
    ): ViboDockPreferences {
        val current = normalized()
        val available = groupedEntries(catalogue)
        if (if (visible) !canShowDockEntry(id, available, current.visibleEntryIds.toSet())
            else !current.canHideEntry(id, catalogue)
        ) return current
        val order = (current.entryOrder + current.orderedEntries(catalogue).map(ViboDockEntry::id)).distinct()
        val nextVisible = if (visible) current.visibleEntryIds + id else current.visibleEntryIds - id
        return current.copy(visibleEntryIds = order.filter(nextVisible.toSet()::contains), entryOrder = order)
    }

    /** Reorder the displayed selections, leaving fallback Home and non-displayed IDs in place. */
    fun moveEntry(id: String, targetIndex: Int, catalogue: List<ViboDockEntry>): ViboDockPreferences {
        val current = normalized()
        val selected = current.visibleEntryIds.toSet()
        val availableVisible = current.orderedVisibleEntries(catalogue).map(ViboDockEntry::id).filter(selected::contains)
        val from = availableVisible.indexOf(id)
        if (from == -1 || from == targetIndex || targetIndex !in availableVisible.indices) return current
        val moved = availableVisible.toMutableList().apply { add(targetIndex, removeAt(from)) }.iterator()
        val movableIds = availableVisible.toSet()
        return current.copy(visibleEntryIds = current.visibleEntryIds.map {
            if (it in movableIds) moved.next() else it
        }).normalized()
    }

    /** Reset options for the current catalogue without erasing offline or other-account shortcuts. */
    internal fun restoringDefaults(catalogue: List<ViboDockEntry>): ViboDockPreferences {
        val current = normalized()
        val available = groupedEntries(catalogue)
        val defaults = defaultIds.iterator()
        val order = current.entryOrder.mapNotNull { id ->
            when {
                id !in available -> id
                id in defaultIds -> defaults.next()
                else -> null
            }
        }
        val visible = current.visibleEntryIds.toSet()
        return ViboDockPreferences(
            visibleEntryIds = order.filter { it in defaultIds || it !in available && it in visible },
            entryOrder = order,
        )
    }
}

class ViboDockPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "vibo_dock_prefs",
        Context.MODE_PRIVATE,
    )

    fun read(): ViboDockPreferences {
        val saved = prefs.all
        return decodeViboDockPreferences(
            saved[KEY_VISIBLE_ITEMS], saved[KEY_SHOW_LABELS], saved[KEY_ITEM_ORDER],
        )
    }

    fun write(preferences: ViboDockPreferences) {
        val config = preferences.normalized()
        prefs.edit()
            .putString(KEY_VISIBLE_ITEMS, config.visibleEntryIds.joinToString(","))
            .putString(KEY_ITEM_ORDER, config.entryOrder.joinToString(","))
            .putBoolean(KEY_SHOW_LABELS, config.showLabels)
            .apply()
    }

    private companion object {
        const val KEY_VISIBLE_ITEMS = "visible_items"
        const val KEY_SHOW_LABELS = "show_labels"
        const val KEY_ITEM_ORDER = "item_order"
    }
}

internal fun decodeViboDockPreferences(
    items: Any?,
    showLabels: Any?,
    itemOrder: Any? = null,
): ViboDockPreferences {
    // PROFILE alone was a valid legacy value: it meant both optional main tabs were hidden.
    val savedVisible = decodeEntryIds(items)
    val visible = savedVisible.filter(::isGroupedId)
    val hadLegacyProfile = ViboDockEntry.Profile.id in savedVisible &&
        visible.none { it !in defaultIds }
    return ViboDockPreferences(
        visibleEntryIds = when {
            hadLegacyProfile && ViboDockEntry.Home.id !in visible -> visible + ViboDockEntry.Home.id
            visible.isNotEmpty() || items == "" -> visible
            else -> defaultIds
        },
        showLabels = showLabels as? Boolean ?: true,
        entryOrder = decodeEntryIds(itemOrder).filter(::isGroupedId),
    ).normalized()
}

private fun decodeEntryIds(value: Any?): List<String> = (value as? String)
    ?.split(',')?.map(String::trim).orEmpty()

private fun migrateEntryId(id: String): String = if (id == LEGACY_FOLLOWING_ID) ViboDockEntry.Home.id else id

private fun canShowDockEntry(
    id: String,
    available: Map<String, ViboDockEntry>,
    selected: Set<String>,
): Boolean {
    if (id !in available || id in selected) return false
    val next = selected + id
    val fallbackSlots = if (hasSelectedTimeline(available, next)) 0 else 1
    return next.count(available::containsKey) + fallbackSlots <= VIBO_DOCK_MAX_CENTER_SLOTS
}

private fun hasSelectedTimeline(available: Map<String, ViboDockEntry>, selected: Set<String>): Boolean =
    selected.any { available[it]?.isTimeline == true }

private fun isGroupedId(id: String): Boolean = id in defaultIds ||
    id.startsWith(TIMELINE_PREFIX) && id.length > TIMELINE_PREFIX.length ||
    id.startsWith(GROUP_PREFIX) && id.length > GROUP_PREFIX.length

/** Hoist once above both the dock and settings so changes are immediately observable in both. */
@Stable
class ViboDockPreferencesState internal constructor(private val store: ViboDockPreferencesStore) {
    var preferences: ViboDockPreferences by mutableStateOf(store.read())
        private set

    val config: ViboDockPreferences get() = preferences

    fun update(preferences: ViboDockPreferences) {
        val next = preferences.normalized()
        if (this.preferences == next) return
        store.write(next)
        this.preferences = next
    }
}

val LocalViboDockState = staticCompositionLocalOf<ViboDockPreferencesState> {
    error("ViboDockPreferencesState was not provided")
}

@Composable
fun rememberViboDockPreferencesState(): ViboDockPreferencesState {
    val context = LocalContext.current.applicationContext
    return remember(context) { ViboDockPreferencesState(ViboDockPreferencesStore(context)) }
}
