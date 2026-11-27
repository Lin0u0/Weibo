package com.example.myweibo.ui.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.myweibo.ui.theme.isAppLightTheme
import com.kyant.backdrop.Backdrop

/** A separate glass surface and input target; never shares the capsule's drag gestures. */
@Composable
internal fun LiquidDockSearchButton(
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop,
    frostAmount: Float,
    glassAmount: Float,
    forceDarkSurface: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isLightTheme = isAppLightTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "dock-search-press",
    )
    val selectionAlpha by animateFloatAsState(
        targetValue = if (selected) 0.14f else 0f,
        label = "dock-search-selection",
    )
    val selectionColor = if (isLightTheme && !forceDarkSurface) Color.Black else Color.White

    Box(
        modifier.selectable(
            selected = selected,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Tab,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer {
                    val scale = 1f + 0.06f * pressProgress
                    scaleX = scale
                    scaleY = scale
                }
                .drawBottomBarGlassBackdrop(backdrop, frostAmount, glassAmount, isLightTheme),
        )
        Box(
            Modifier.matchParentSize()
                .padding(4.dp)
                .background(selectionColor.copy(alpha = selectionAlpha), CircleShape)
                .background(Color.White.copy(alpha = 0.12f * pressProgress.coerceIn(0f, 1f)), CircleShape),
        )
        Box(
            Modifier.graphicsLayer {
                val scale = 1f + 0.16f * pressProgress
                scaleX = scale
                scaleY = scale
            },
            content = content,
        )
    }
}
