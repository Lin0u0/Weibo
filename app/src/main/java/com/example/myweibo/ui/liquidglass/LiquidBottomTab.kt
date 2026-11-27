package com.example.myweibo.ui.liquidglass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val LocalLiquidBottomTabCoverage =
    staticCompositionLocalOf<(Int) -> Float> { { 0f } }

internal val LocalLiquidBottomTabGlassMotionProgress =
    staticCompositionLocalOf { 0f }

internal val LocalLiquidBottomTabPressProgress =
    staticCompositionLocalOf { 0f }

internal val LocalLiquidBottomTabIsSample = staticCompositionLocalOf { false }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val tabModifier = if (LocalLiquidBottomTabIsSample.current) {
        Modifier
    } else {
        val action = if (onLongClick == null) {
            Modifier.selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
        } else {
            Modifier
                .semantics { this.selected = selected }
                .combinedClickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
        }
        modifier.clip(RoundedCornerShape(percent = 50)).then(action)
    }
    Column(
        tabModifier.fillMaxHeight().weight(1f),
        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}
