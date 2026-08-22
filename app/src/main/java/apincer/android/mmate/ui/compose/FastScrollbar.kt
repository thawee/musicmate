package apincer.android.mmate.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Fast scroll overlay for LazyColumn.
 *
 * Draws a thin gold scroll thumb on the right edge. While dragging:
 * - The thumb follows the finger
 * - A bubble pops out showing the first letter of the track at that scroll position
 *
 * Usage: wrap your LazyColumn content in a Box and put FastScrollbar on top.
 */
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun FastScrollbar(
    listState: LazyListState,
    totalItems: Int,
    modifier: Modifier = Modifier,
    /** Map index → letter for the bubble label (usually track.title[0]) */
    itemLabel: (index: Int) -> String = { "" }
) {
    if (totalItems == 0) return

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var bubbleLabel by remember { mutableStateOf("") }
    var lastHapticLabel by remember { mutableStateOf("") }

    // Current scroll fraction [0..1] — derived from LazyListState
    val scrollFraction by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val visibleItems = info.visibleItemsInfo
            if (visibleItems.isEmpty() || totalItems == 0) return@derivedStateOf 0f
            val firstVisible = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset
            val avgItemSize = visibleItems.sumOf { it.size } / visibleItems.size.toFloat()
            val totalScrollable = (totalItems - visibleItems.size).coerceAtLeast(1)
            ((firstVisible + firstVisibleOffset / avgItemSize) / totalScrollable)
                .coerceIn(0f, 1f)
        }
    }

    // Thumb alpha — fade out when not dragging
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.45f,
        animationSpec = tween(durationMillis = 300),
        label = "thumbAlpha"
    )

    BoxWithConstraints(modifier = modifier) {
        val totalHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeightDp = 48.dp
        val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
        val scrollRangePx = (totalHeightPx - thumbHeightPx).coerceAtLeast(1f)

        // Thumb Y offset
        val thumbOffsetPx = (scrollFraction * scrollRangePx).coerceIn(0f, scrollRangePx)

        Box(modifier = Modifier.fillMaxSize()) {

            // ── Bubble label (shown while dragging) ─────────────────────────
            if (isDragging && bubbleLabel.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset {
                            IntOffset(
                                x = with(density) { (-48).dp.roundToPx() },
                                y = thumbOffsetPx.roundToInt()
                            )
                        }
                        .size(48.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 2.dp))
                        .background(Color(0xF0141414))
                        .border(1.25.dp, Color(0xFFFFD700), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = bubbleLabel,
                        color = Color(0xFFFFD700),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Scroll thumb with expanded 24dp touch target ────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(x = 0, y = thumbOffsetPx.roundToInt()) }
                    .size(width = 24.dp, height = thumbHeightDp)
                    .pointerInput(totalItems) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newFraction = (thumbOffsetPx + dragAmount.y)
                                    .coerceIn(0f, scrollRangePx) / scrollRangePx
                                val targetIndex = (newFraction * (totalItems - 1))
                                    .roundToInt()
                                    .coerceIn(0, totalItems - 1)
                                val currentLabel = itemLabel(targetIndex)
                                if (currentLabel.isNotEmpty() && currentLabel != lastHapticLabel) {
                                    lastHapticLabel = currentLabel
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                bubbleLabel = currentLabel
                                coroutineScope.launch {
                                    listState.scrollToItem(targetIndex)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                // Visual slim gold pill
                Box(
                    modifier = Modifier
                        .size(width = 4.5.dp, height = thumbHeightDp)
                        .padding(end = 1.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD700).copy(alpha = thumbAlpha))
                        .alpha(thumbAlpha)
                )
            }
        }
    }
}
