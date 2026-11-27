package com.example.myweibo.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Search is a separate, permanent control and is never part of this list. */
enum class ViboDockItem {
    HOME,
    MESSAGES,
    COMPOSE,
    PROFILE,
    ;

    val isPinned: Boolean
        get() = this == HOME || this == PROFILE
}

data class ViboDockPreferences(
    val visibleItems: List<ViboDockItem> = ViboDockItem.entries,
    val showLabels: Boolean = true,
    internal val itemOrder: List<ViboDockItem> = ViboDockItem.entries,
) {
    /** Preserve the requested visible order and the remembered positions of hidden rows. */
    internal fun normalized(): ViboDockPreferences {
        val visible = buildList {
            addAll(visibleItems.distinct())
            ViboDockItem.entries.filter { it.isPinned && it !in this }.forEach { add(it) }
        }
        val completeOrder = (itemOrder + ViboDockItem.entries).distinct()
        val nextVisible = visible.iterator()
        return copy(visibleItems = visible, itemOrder = completeOrder.map {
            if (it in visible) nextVisible.next() else it
        })
    }

    fun withItemVisibility(item: ViboDockItem, visible: Boolean): ViboDockPreferences {
        val current = normalized()
        if (item.isPinned || (item in current.visibleItems) == visible) return current
        val nextItems = if (visible) current.visibleItems + item else current.visibleItems - item
        return current.copy(visibleItems = current.itemOrder.filter { it in nextItems })
    }

    /** Move a visible item to its final index, leaving hidden items untouched. */
    fun moveItem(item: ViboDockItem, targetIndex: Int): ViboDockPreferences {
        val current = normalized()
        val from = current.visibleItems.indexOf(item)
        if (from == -1 || from == targetIndex || targetIndex !in current.visibleItems.indices) {
            return current
        }
        return current.copy(visibleItems = buildList {
            addAll(current.visibleItems)
            removeAt(from)
            add(targetIndex, item)
        }).normalized()
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
            .putString(KEY_VISIBLE_ITEMS, config.visibleItems.joinToString(",") { it.name })
            .putString(KEY_ITEM_ORDER, config.itemOrder.joinToString(",") { it.name })
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
): ViboDockPreferences = ViboDockPreferences(
    visibleItems = decodeItemList(items).ifEmpty { ViboDockItem.entries },
    showLabels = showLabels as? Boolean ?: true,
    itemOrder = decodeItemList(itemOrder).ifEmpty { ViboDockItem.entries },
).normalized()

private fun decodeItemList(value: Any?): List<ViboDockItem> = (value as? String)
    ?.split(',')
    ?.mapNotNull { saved -> ViboDockItem.entries.firstOrNull { it.name == saved.trim() } }
    .orEmpty()

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
