package com.dns.sms.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Floating "liquid glass" folder bar (owner screenshots 2026-11-21 18:27): big with labels when you
 * arrive or scroll up, shrinks to an icon-only pill when you scroll down. Springs give the morph its
 * soft, liquid feel; the selected folder sits in a pill that slides between items.
 */
@Stable
class FolderBarState {
    var collapsed by mutableStateOf(false)

    /**
     * Watches list scrolling: down (content moving up) collapses, up expands. Distance is summed with
     * hysteresis so small jitters or a fling settling never flip the bar back and forth (which looked
     * like it was vanishing). Only real list movement counts (onPostScroll's consumed), not overscroll.
     */
    private var travel = 0f
    val scrollConnection = object : NestedScrollConnection {
        @Suppress("SameReturnValue") // only observes scrolling; never consumes any
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = consumed.y
            if (dy == 0f) return Offset.Zero
            if ((dy < 0) != (travel < 0)) travel = 0f
            travel += dy
            if (travel < -THRESHOLD_PX) collapsed = true else if (travel > THRESHOLD_PX) collapsed = false
            return Offset.Zero
        }
    }

    fun expand() { collapsed = false; travel = 0f }

    companion object {
        /** Space to leave under lists so their last row isn't hidden behind the expanded bar. */
        val ContentInset: Dp = 104.dp
        private const val THRESHOLD_PX = 48f
    }
}

@Composable
fun rememberFolderBarState() = remember { FolderBarState() }

private val GlassTop = Color(0xF72E2F34)
private val GlassBottom = Color(0xFA1B1C20)
private val GlassEdgeTop = Color(0x55FFFFFF)
private val GlassEdgeBottom = Color(0x0FFFFFFF)
private val SelectedPill = Color(0xFF111215)

@Composable
fun FloatingFolderBar(
    selected: Int,
    collapsed: Boolean,
    dotOn: Folder?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val springy = spring<Dp>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val itemWidth by animateDpAsState(if (collapsed) 62.dp else 92.dp, springy, label = "itemWidth")
    val barHeight by animateDpAsState(if (collapsed) 52.dp else 68.dp, springy, label = "barHeight")
    val indicatorX by animateDpAsState(itemWidth * selected, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow), label = "indicator")
    val shape = RoundedCornerShape(50)
    val count = Folder.entries.size

    Box(
        modifier
            .zIndex(2f)
            .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Brush.verticalGradient(listOf(GlassTop, GlassBottom)), shape)
            .border(1.dp, Brush.verticalGradient(listOf(GlassEdgeTop, GlassEdgeBottom)), shape)
            .padding(6.dp)
            .width(itemWidth * count)
            .height(barHeight - 12.dp),
    ) {
        // Sliding selection pill.
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(indicatorX.roundToPx(), 0) }.width(itemWidth).fillMaxHeight()
                .background(SelectedPill, shape)
                .border(1.dp, Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color.Transparent)), shape),
        )
        Row(Modifier.fillMaxHeight().selectableGroup()) {
            Folder.entries.forEachIndexed { i, f ->
                val isSel = i == selected
                val tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.width(itemWidth).fillMaxHeight()
                        // No ripple/press highlight: the sliding pill is the only feedback (owner request).
                        .selectable(selected = isSel, interactionSource = null, indication = null, role = Role.Tab, onClick = { onSelect(i) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        // Label hidden when collapsed, so the icon carries the name for TalkBack.
                        Icon(f.icon, contentDescription = if (collapsed) f.label else null, tint = tint, modifier = Modifier.size(22.dp))
                        if (dotOn == f) Box(
                            Modifier.size(7.dp).offset(x = 17.dp, y = (-1).dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                    AnimatedVisibility(
                        visible = !collapsed,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                    ) {
                        Text(
                            f.label, Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
