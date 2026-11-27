package com.example.myweibo.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState

/** Move toward the exact keyed row without animating through an arbitrarily long timeline. */
internal suspend fun LazyListState.resumeReadingAt(index: Int, key: String, isCurrent: () -> Boolean) {
    fun targetOffset() = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index && it.key == key }?.offset
    if (!isCurrent()) return
    if (targetOffset() == null) {
        // For a far jump, approach the target from the original scroll direction. Compose
        // clamps this offset at the ends of the list, and the final scroll remains keyed.
        val step = (layoutInfo.viewportSize.height / 8).coerceIn(1, 96)
        scrollToItem(index, if (index < firstVisibleItemIndex) step else -step)
    }
    if (!isCurrent()) return
    val distance = targetOffset()
    if (distance != null && distance != 0) {
        scroll {
            var moved = 0f
            // Native Compose animation uses the system motion-duration scale. User input
            // interrupts the scroll mutation; cancellation must not reach the final snap.
            animate(0f, distance.toFloat(), animationSpec = tween(200, easing = FastOutSlowInEasing)) { value, _ ->
                if (isCurrent()) moved += scrollBy(value - moved)
            }
        }
    }
    if (isCurrent() && (firstVisibleItemIndex != index || firstVisibleItemScrollOffset != 0)) scrollToItem(index)
}
