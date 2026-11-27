package com.example.myweibo.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.myweibo.R
import com.example.myweibo.data.TimelineKind
import com.example.myweibo.data.ViboDockItem
import com.example.myweibo.data.ViboDockPreferences
import com.example.myweibo.ui.liquidglass.LiquidBottomTab
import com.example.myweibo.ui.liquidglass.LiquidBottomTabs
import com.example.myweibo.ui.liquidglass.LiquidDockSearchButton
import com.example.myweibo.ui.liquidglass.LocalLiquidBottomTabCoverage
import com.example.myweibo.ui.liquidglass.LocalLiquidBottomTabPressProgress
import com.example.myweibo.ui.theme.isAppLightTheme
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

private val DockHeight = 56.dp
private val DockGap = 8.dp
private val DockTabMinWidth = 48.dp
private val DockCapsulePadding = 4.dp

@Composable
internal fun WeiboLiquidBottomBar(
    selectedTab: MainTab,
    darkSurfaceOverride: Boolean = false,
    onTabChange: (MainTab) -> Unit,
    backdrop: Backdrop,
    frostAmount: Float = 0f,
    glassAmount: Float = 1f,
    timelineMenuExpanded: Boolean,
    onTimelineMenuExpandedChange: (Boolean) -> Unit,
    selectedTimelineKind: TimelineKind = TimelineKind.Following,
    selectedTimelineLabel: String? = null,
    onTimelineKindChange: (TimelineKind) -> Unit = {},
    timelineMenuContent: @Composable (dismiss: () -> Unit) -> Unit,
    timelineMenuWidth: Dp = 152.dp,
    timelineMenuHeight: Dp = 89.dp,
    timelineMenuGap: Dp = 4.dp,
    onBarBoundsChanged: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
    dockPreferences: ViboDockPreferences = ViboDockPreferences(),
    unreadMessagesCount: Int = 0,
    applyNavigationBarInsets: Boolean = true,
) {
    val tabs = dockPreferences.visibleItems.distinct().map(ViboDockItem::toMainTab)
    val feedIndex = tabs.indexOf(MainTab.Feed)
    val timelineLabel = selectedTimelineLabel ?: selectedTimelineKind.label

    LaunchedEffect(feedIndex, timelineMenuExpanded) {
        if (feedIndex < 0 && timelineMenuExpanded) onTimelineMenuExpandedChange(false)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp)
            .then(if (applyNavigationBarInsets) Modifier.navigationBarsPadding() else Modifier),
        contentAlignment = Alignment.BottomStart,
    ) {
        val density = LocalDensity.current
        val dockWidth = maxWidth
        val searchSize = DockHeight.coerceAtMost(dockWidth)
        val gap = DockGap.coerceAtMost((dockWidth - searchSize).coerceAtLeast(0.dp))
        val availableCapsuleWidth = (dockWidth - searchSize - gap).coerceAtLeast(0.dp)
        val minimumCapsuleWidth = with(density) {
            (DockTabMinWidth.roundToPx() * tabs.size + DockCapsulePadding.roundToPx() * 2).toDp()
        }
        val capsuleWidth = maxOf(minimumCapsuleWidth, availableCapsuleWidth)
        val scrollable = capsuleWidth > availableCapsuleWidth
        val capsuleScroll = rememberScrollState()

        ViboDock(
            tabs = tabs,
            selectedTab = selectedTab,
            timelineLabel = timelineLabel,
            onTabChange = { tab ->
                if (tab != MainTab.Feed) onTimelineMenuExpandedChange(false)
                onTabChange(tab)
            },
            onHomeLongPress = { onTimelineMenuExpandedChange(true) },
            showLabels = dockPreferences.showLabels,
            unreadMessagesCount = unreadMessagesCount,
            searchSize = searchSize,
            capsuleGap = gap,
            availableCapsuleWidth = availableCapsuleWidth,
            capsuleWidth = capsuleWidth,
            capsuleScroll = capsuleScroll,
            backdrop = backdrop,
            frostAmount = frostAmount,
            glassAmount = glassAmount,
            darkSurfaceOverride = darkSurfaceOverride,
            modifier = Modifier.onGloballyPositioned { onBarBoundsChanged?.invoke(it.boundsInRoot()) },
        )

        val menuWidth = timelineMenuWidth.coerceAtMost(dockWidth)
        val homeCenterInDock = with(density) {
            val inset = DockCapsulePadding.roundToPx()
            val itemWidth = (capsuleWidth.roundToPx() - 2f * inset) / tabs.size.coerceAtLeast(1)
            val scrollOffset = if (scrollable) capsuleScroll.value else 0
            searchSize.roundToPx() + gap.roundToPx() + inset + itemWidth * (feedIndex + 0.5f) - scrollOffset
        }
        val timelineMenuOffsetX = with(density) {
            (homeCenterInDock - menuWidth.toPx() / 2f)
                .roundToInt()
                .coerceIn(0, (dockWidth - menuWidth).roundToPx().coerceAtLeast(0))
        }
        val layoutDirection = LocalLayoutDirection.current
        val timelineMenuOrigin = with(density) {
            val startRelativeX = homeCenterInDock - timelineMenuOffsetX
            val physicalX = if (layoutDirection == LayoutDirection.Ltr) startRelativeX else menuWidth.toPx() - startRelativeX
            Offset(physicalX, timelineMenuHeight.toPx())
        }
        ActionMenuReveal(
            visible = timelineMenuExpanded && feedIndex >= 0 && availableCapsuleWidth > 0.dp,
            menuWidth = menuWidth,
            menuHeight = timelineMenuHeight,
            originInMenu = timelineMenuOrigin,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(timelineMenuOffsetX, -(DockHeight + timelineMenuGap).roundToPx()) }
                .zIndex(50f)
                .graphicsLayer { clip = false },
        ) {
            timelineMenuContent { onTimelineMenuExpandedChange(false) }
        }
    }
}

@Composable
private fun ViboDock(
    tabs: List<MainTab>,
    selectedTab: MainTab,
    timelineLabel: String,
    onTabChange: (MainTab) -> Unit,
    onHomeLongPress: () -> Unit,
    showLabels: Boolean,
    unreadMessagesCount: Int,
    searchSize: Dp,
    capsuleGap: Dp,
    availableCapsuleWidth: Dp,
    capsuleWidth: Dp,
    capsuleScroll: ScrollState,
    backdrop: Backdrop,
    frostAmount: Float,
    glassAmount: Float,
    darkSurfaceOverride: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val contentColor = if (isAppLightTheme() && !darkSurfaceOverride) Color.Black else Color(0xFFD0D0D0)
    val selectedIndex = tabs.indexOf(selectedTab)
    val feedIndex = tabs.indexOf(MainTab.Feed)
    val scrollable = capsuleWidth > availableCapsuleWidth

    Row(
        modifier = modifier.height(DockHeight).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(capsuleGap),
    ) {
        LiquidDockSearchButton(
            selected = selectedTab == MainTab.Search,
            onClick = { onTabChange(MainTab.Search) },
            backdrop = backdrop,
            frostAmount = frostAmount,
            glassAmount = glassAmount,
            forceDarkSurface = darkSurfaceOverride,
            modifier = Modifier.size(searchSize).semantics { contentDescription = MainTab.Search.label },
        ) {
            WeiboTabIcon(MainTab.Search, contentColor)
        }

        if (tabs.isNotEmpty() && availableCapsuleWidth > 0.dp) {
            Box(
                Modifier.width(availableCapsuleWidth)
                    .then(if (scrollable) Modifier.horizontalScroll(capsuleScroll) else Modifier),
            ) {
                // Cancel positional gestures if the user changes the item order while touching.
                key(tabs) {
                    LiquidBottomTabs(
                        selectedTabIndex = { selectedIndex },
                        onTabSelected = { index -> onTabChange(tabs[index]) },
                        backdrop = backdrop,
                        accentColor = accentColor,
                        frostAmount = frostAmount,
                        glassAmount = glassAmount,
                        forceDarkSurface = darkSurfaceOverride,
                        tabsCount = tabs.size,
                        feedTabIndex = feedIndex,
                        onTabLongPress = { onHomeLongPress() },
                        height = DockHeight,
                        dragSelectionEnabled = !scrollable,
                        modifier = Modifier.width(capsuleWidth),
                    ) {
                        val tabCoverage = LocalLiquidBottomTabCoverage.current
                        val pressProgress = LocalLiquidBottomTabPressProgress.current
                        tabs.forEachIndexed { index, tab ->
                            val coverage = tabCoverage(index)
                            val isHome = tab == MainTab.Feed
                            val unread = if (tab == MainTab.Messages) unreadMessagesCount else 0
                            LiquidBottomTab(
                                selected = index == selectedIndex,
                                onClick = { onTabChange(tab) },
                                onLongClick = if (isHome) onHomeLongPress else null,
                                modifier = Modifier.semantics {
                                    contentDescription = tab.label
                                    if (isHome && timelineLabel != tab.label) stateDescription = timelineLabel
                                    if (unread > 0) stateDescription = "$unread 条未读消息"
                                },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (showLabels) 32.dp else 36.dp)
                                        .graphicsLayer {
                                            val scale = 1f + 0.2f * pressProgress * coverage
                                            scaleX = scale
                                            scaleY = scale
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (unread > 0) {
                                        BadgedBox(badge = {
                                            Badge(modifier = Modifier.clearAndSetSemantics { }) {
                                                Text(if (unread > 99) "99+" else unread.toString())
                                            }
                                        }) {
                                            WeiboTabIcon(tab, contentColor)
                                        }
                                    } else {
                                        WeiboTabIcon(tab, contentColor)
                                    }
                                }
                                if (showLabels) {
                                    DockLabel(
                                        label = if (isHome) timelineLabel else tab.label,
                                        fallback = if (isHome) tab.label else null,
                                        color = contentColor,
                                        emphasized = coverage > 0.5f,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockLabel(label: String, fallback: String?, color: Color, emphasized: Boolean) {
    val style = LocalTextStyle.current.copy(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
    )
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        val measurer = rememberTextMeasurer()
        val displayLabel = if (fallback != null && label != fallback && measurer.measure(
                text = label,
                style = style,
                maxLines = 1,
                softWrap = false,
                constraints = Constraints(maxWidth = constraints.maxWidth),
            ).hasVisualOverflow
        ) fallback else label
        Text(
            text = displayLabel,
            modifier = Modifier.align(Alignment.Center).clearAndSetSemantics { },
            color = color,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun ViboDockItem.toMainTab(): MainTab = when (this) {
    ViboDockItem.HOME -> MainTab.Feed
    ViboDockItem.MESSAGES -> MainTab.Messages
    ViboDockItem.COMPOSE -> MainTab.Compose
    ViboDockItem.PROFILE -> MainTab.Mine
}

@Composable
private fun WeiboTabIcon(tab: MainTab, color: Color) {
    Icon(
        painter = painterResource(
            when (tab) {
                MainTab.Feed -> R.drawable.ic_tab_home
                MainTab.Search -> R.drawable.ic_tab_search
                MainTab.Messages -> R.drawable.ic_tab_messages
                MainTab.Mine -> R.drawable.ic_tab_mine
                MainTab.Compose -> R.drawable.ic_tab_compose
            },
        ),
        contentDescription = null,
        modifier = Modifier.size(22.dp),
        tint = color,
    )
}
