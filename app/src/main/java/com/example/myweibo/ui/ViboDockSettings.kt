package com.example.myweibo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myweibo.R
import com.example.myweibo.data.ViboDockItem
import com.example.myweibo.data.ViboDockPreferences

/** A non-scrolling settings section; the parent owns scrolling and app-wide state. */
@Composable
fun ViboDockSettings(
    preferences: ViboDockPreferences,
    onPreferencesChange: (ViboDockPreferences) -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable (ViboDockPreferences, Modifier) -> Unit = { config, previewModifier ->
        DockPreview(config, previewModifier)
    },
) {
    val config = remember(preferences) { preferences.normalized() }
    val items = config.visibleItems
    val rowOrder = config.itemOrder
    val currentUpdate by rememberUpdatedState(onPreferencesChange)
    val currentConfig by rememberUpdatedState(config)
    var drag by remember(rowOrder, items) { mutableStateOf<DockDrag?>(null) }
    val rowHeights = remember { mutableMapOf<ViboDockItem, Int>() }
    val move: (ViboDockItem, Int) -> Unit = { item, index ->
        currentUpdate(currentConfig.moveItem(item, index))
    }

    Column(
        modifier.fillMaxWidth().testTag("vibo_dock_settings"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.vibo_dock_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                DockHeading(R.string.vibo_dock_preview)
                preview(config, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("vibo_dock_labels")
                        .toggleable(config.showLabels, role = Role.Switch) {
                            currentUpdate(currentConfig.copy(showLabels = it))
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.vibo_dock_show_labels), Modifier.weight(1f))
                    Switch(checked = config.showLabels, onCheckedChange = null)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        DockHeading(R.string.vibo_dock_items)
                        Text(
                            text = stringResource(R.string.vibo_dock_visible_count, items.size),
                            modifier = Modifier.padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { currentUpdate(ViboDockPreferences()) },
                        enabled = config != ViboDockPreferences(),
                        modifier = Modifier.testTag("vibo_dock_restore_defaults"),
                    ) {
                        Text(stringResource(R.string.vibo_dock_restore_defaults))
                    }
                }
                Text(
                    text = stringResource(R.string.vibo_dock_items_hint),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rowOrder.forEach { item ->
                    key(item) {
                        val index = items.indexOf(item)
                        val visible = index >= 0
                        val label = stringResource(item.labelRes)
                        val currentMove by rememberUpdatedState(move)
                        val handle = if (!visible) Modifier else Modifier.pointerInput(rowOrder, items, item) {
                            var distance = 0f
                            var target = index
                            var centers = emptyList<Float>()
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    distance = 0f
                                    target = index
                                    val allCenters = dockRowCenters(rowOrder.map { rowHeights[it] ?: 0 })
                                    centers = items.map { allCenters[rowOrder.indexOf(it)] }
                                    drag = DockDrag(index, target)
                                },
                                onDragEnd = {
                                    drag = null
                                    if (target != index) currentMove(item, target)
                                },
                                onDragCancel = { drag = null },
                                onDrag = { change, amount ->
                                    change.consume()
                                    distance += amount.y
                                    target = dockDragTarget(centers, index, distance)
                                    drag = DockDrag(index, target)
                                },
                            )
                        }
                        val dragState = drag
                        Box(Modifier.onSizeChanged { rowHeights[item] = it.height }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .then(
                                        if (dragState?.from == index) {
                                            Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                                        } else Modifier,
                                    )
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(48.dp).then(handle), contentAlignment = Alignment.Center) {
                                    if (visible) Icon(Icons.Default.DragHandle, contentDescription = null)
                                }
                                DockItemName(item, label, Modifier.weight(1f))
                                IconButton(
                                    onClick = { move(item, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(48.dp).testTag("vibo_dock_up_${item.name}"),
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.vibo_dock_move_up, label))
                                }
                                IconButton(
                                    onClick = { move(item, index + 1) },
                                    enabled = visible && index < items.lastIndex,
                                    modifier = Modifier.size(48.dp).testTag("vibo_dock_down_${item.name}"),
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.vibo_dock_move_down, label))
                                }
                                if (item.isPinned) {
                                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = stringResource(R.string.vibo_dock_pinned_item, label),
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    val toggleLabel = stringResource(R.string.vibo_dock_show_item, label)
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("vibo_dock_visibility_${item.name}")
                                            .toggleable(visible, role = Role.Checkbox) { show ->
                                                currentUpdate(currentConfig.withItemVisibility(item, show))
                                            }
                                            .semantics { contentDescription = toggleLabel },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Checkbox(checked = visible, onCheckedChange = null)
                                    }
                                }
                            }
                            if (dragState != null && dragState.to == index && dragState.from != index) {
                                HorizontalDivider(
                                    modifier = Modifier.align(
                                        if (index < dragState.from) Alignment.TopCenter else Alignment.BottomCenter,
                                    ),
                                    thickness = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockHeading(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun DockItemName(item: ViboDockItem, label: String, modifier: Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(item.iconRes), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DockPreview(config: ViboDockPreferences, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(modifier = Modifier.width(56.dp), shape = shape, color = color) {
            PreviewItem(
                label = stringResource(R.string.vibo_dock_search),
                iconRes = R.drawable.ic_tab_search,
                showLabel = config.showLabels,
                modifier = Modifier.testTag("vibo_dock_preview_search"),
            )
        }
        Surface(
            modifier = Modifier.weight(1f).testTag("vibo_dock_preview_items"),
            shape = shape,
            color = color,
        ) {
            Row(Modifier.padding(horizontal = 4.dp)) {
                config.visibleItems.forEach { item ->
                    PreviewItem(
                        label = stringResource(item.labelRes),
                        iconRes = item.iconRes,
                        showLabel = config.showLabels,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewItem(label: String, iconRes: Int, showLabel: Boolean, modifier: Modifier) {
    Column(
        modifier = modifier.heightIn(min = if (showLabel) 56.dp else 44.dp).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(iconRes), contentDescription = if (showLabel) null else label, modifier = Modifier.size(20.dp))
        if (showLabel) Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    }
}

private data class DockDrag(val from: Int, val to: Int)

private fun dockRowCenters(heights: List<Int>): List<Float> {
    var top = 0f
    return heights.map { height ->
        val center = top + height / 2f
        top += height
        center
    }
}

/** Cross a neighbouring row's center before marking an insertion on its far side. */
private fun dockDragTarget(centers: List<Float>, from: Int, distance: Float): Int {
    val position = centers[from] + distance
    var target = from
    if (distance < 0f) {
        while (target > 0 && position <= centers[target - 1]) target--
    } else if (distance > 0f) {
        while (target < centers.lastIndex && position >= centers[target + 1]) target++
    }
    return target
}

private val ViboDockItem.labelRes: Int
    get() = when (this) {
        ViboDockItem.HOME -> R.string.vibo_dock_home
        ViboDockItem.MESSAGES -> R.string.vibo_dock_messages
        ViboDockItem.COMPOSE -> R.string.vibo_dock_compose
        ViboDockItem.PROFILE -> R.string.vibo_dock_profile
    }

private val ViboDockItem.iconRes: Int
    get() = when (this) {
        ViboDockItem.HOME -> R.drawable.ic_tab_home
        ViboDockItem.MESSAGES -> R.drawable.ic_tab_messages
        ViboDockItem.COMPOSE -> R.drawable.ic_tab_compose
        ViboDockItem.PROFILE -> R.drawable.ic_tab_mine
    }
