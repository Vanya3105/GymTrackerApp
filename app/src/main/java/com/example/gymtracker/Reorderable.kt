package com.example.gymtracker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Drag-to-reorder for a LazyColumn. The dragged item follows the finger exactly; when its edge
 * passes the middle of a neighbour the two swap (via [onMove]) and the neighbour animates into the
 * freed slot. Near the top/bottom edge the list scrolls on its own.
 *
 * Items are identified by their LazyColumn key; [canReorder] limits which keys take part.
 *
 * [isReordering] stays true from pick-up until the list has settled after the drop, so items can
 * collapse to a compact row meanwhile (tall cards are hard to move past each other). While they
 * collapse or expand, the list is scrolled so the held item stays under the finger.
 */
class DragDropState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val autoScrollEdgePx: Float,
    private val canReorder: (Any) -> Boolean,
    private val onMove: (fromKey: Any, toKey: Any) -> Unit,
    private val onSwap: () -> Unit,
    private val onDrop: () -> Unit,
    /** Items change size while reordering ([isReordering]); keep the held one under the finger. */
    private val collapsesOnDrag: Boolean
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    private var draggedDistance by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    /** Item that was just dropped and is gliding back into its slot. */
    var settlingKey by mutableStateOf<Any?>(null)
        private set
    val settleOffset = Animatable(0f)

    private val draggingItem: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    /** Visual translation for the dragged item, relative to where the list currently places it. */
    val draggingOffset: Float
        get() = draggingItem?.let { initialOffset + draggedDistance - it.offset } ?: 0f

    val isDragging get() = draggingKey != null

    var isReordering by mutableStateOf(false)
        private set
    /** Layout is still collapsing/expanding: no swaps or auto-scroll until it settles. */
    private var pinning = false
    private var pinJob: Job? = null

    fun onDragStart(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        scope.launch { settleOffset.snapTo(0f) }
        settlingKey = null
        draggingKey = key
        initialOffset = item.offset
        draggedDistance = 0f
        if (!isReordering) {
            isReordering = true
            // The item may move if it can't be scrolled into place (near the top): follow it.
            if (collapsesOnDrag) pinWhileLayoutChanges(key, { initialOffset + draggedDistance }) { left -> initialOffset += left.toInt() }
        }
        startAutoScroll()
    }

    fun onDrag(deltaY: Float) {
        draggedDistance += deltaY
        checkForSwap(deltaY)
    }

    fun onDragEnd() {
        val key = draggingKey ?: return
        val remaining = draggingOffset
        draggingKey = null
        draggedDistance = 0f
        settlingKey = key
        scope.launch {
            onDrop()
            settleOffset.snapTo(remaining)
            settleOffset.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
            if (settlingKey == key) settlingKey = null
            if (draggingKey == null) {
                // Expand everything again, keeping the dropped item where it landed.
                val y = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset?.toFloat()
                isReordering = false
                if (y != null && collapsesOnDrag) pinWhileLayoutChanges(key, { y }) {}
            }
        }
    }

    /**
     * For a moment after the items change size, scroll so [key] stays at [targetY].
     * [onUnreachable] gets whatever the list couldn't scroll (at its very top or bottom).
     */
    private fun pinWhileLayoutChanges(key: Any, targetY: () -> Float, onUnreachable: (Float) -> Unit) {
        pinJob?.cancel()
        pinJob = scope.launch {
            pinning = true
            try {
                val start = System.nanoTime()
                var lastSizes = -1
                var stableFrames = 0
                // Until the sizes stop changing for a few frames (the springs have settled), or a cap.
                while (isActive && System.nanoTime() - start < PIN_MAX_NANOS && stableFrames < 6) {
                    withFrameNanos {}
                    val visible = listState.layoutInfo.visibleItemsInfo
                    val sizes = visible.sumOf { it.size } + visible.size
                    stableFrames = if (sizes == lastSizes) stableFrames + 1 else 0
                    lastSizes = sizes
                    val item = visible.firstOrNull { it.key == key } ?: continue
                    val diff = item.offset - targetY()
                    if (abs(diff) < 0.5f) continue
                    // Raw delta: synchronous and outside the scroll mutex, so it never cancels auto-scroll.
                    val consumed = listState.dispatchRawDelta(diff)
                    val left = diff - consumed
                    if (abs(left) >= 0.5f) onUnreachable(left)
                }
            } finally {
                pinning = false
            }
        }
    }

    private fun checkForSwap(direction: Float) {
        if (pinning) return
        val current = draggingItem ?: return
        val top = initialOffset + draggedDistance
        val bottom = top + current.size
        val visible = listState.layoutInfo.visibleItemsInfo

        val target = if (direction > 0) {
            visible.firstOrNull { it.index == current.index + 1 }
                ?.takeIf { canReorder(it.key) && bottom > it.offset + it.size / 2f }
        } else {
            visible.firstOrNull { it.index == current.index - 1 }
                ?.takeIf { canReorder(it.key) && top < it.offset + it.size / 2f }
        } ?: return

        // Moving the first visible item would make the list jump; pin the scroll position instead.
        if (current.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
            scope.launch { listState.scrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
        }
        onMove(current.key, target.key)
        onSwap()
    }

    private fun startAutoScroll() {
        scope.launch {
            val edge = autoScrollEdgePx
            while (isActive && draggingKey != null) {
                val item = draggingItem
                if (item != null && !pinning) {
                    val top = initialOffset + draggedDistance
                    val bottom = top + item.size
                    val viewportStart = listState.layoutInfo.viewportStartOffset
                    val viewportEnd = listState.layoutInfo.viewportEndOffset
                    val speed = when {
                        bottom > viewportEnd - edge && item.size < (viewportEnd - viewportStart) -> ((bottom - (viewportEnd - edge)) / edge).coerceIn(0f, 1f) * 18f
                        top < viewportStart + edge -> -((viewportStart + edge - top) / edge).coerceIn(0f, 1f) * 18f
                        else -> 0f
                    }
                    if (speed != 0f) {
                        val consumed = listState.scrollBy(speed)
                        // The finger hasn't moved, but the list has: keep the item under it.
                        draggedDistance += consumed
                        checkForSwap(speed)
                    }
                }
                delay(16)
            }
        }
    }
}

private const val PIN_MAX_NANOS = 1_500_000_000L

@Composable
fun rememberDragDropState(
    listState: LazyListState,
    canReorder: (Any) -> Boolean,
    onMove: (Any, Any) -> Unit,
    onSwap: () -> Unit = {},
    onDrop: () -> Unit = {},
    collapsesOnDrag: Boolean = false
): DragDropState {
    val scope = rememberCoroutineScope()
    val edgePx = with(LocalDensity.current) { 80.dp.toPx() }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentCanReorder by rememberUpdatedState(canReorder)
    val currentOnSwap by rememberUpdatedState(onSwap)
    val currentOnDrop by rememberUpdatedState(onDrop)
    return remember(listState) {
        DragDropState(listState, scope, edgePx, { currentCanReorder(it) }, { a, b -> currentOnMove(a, b) }, { currentOnSwap() }, { currentOnDrop() }, collapsesOnDrag)
    }
}

/**
 * Apply to each reorderable LazyColumn item. Neighbours glide to their new place; the dragged item
 * floats above with a slight lift.
 */
@Composable
fun LazyItemScope.DraggableItem(
    state: DragDropState,
    key: Any,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit
) {
    val dragging = state.draggingKey == key
    val settling = state.settlingKey == key
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "drag_lift"
    )
    val itemModifier = if (dragging || settling) {
        Modifier.zIndex(1f).graphicsLayer {
            translationY = if (dragging) state.draggingOffset else state.settleOffset.value
            val scale = 1f + 0.035f * lift
            scaleX = scale
            scaleY = scale
            shadowElevation = 24.dp.toPx() * lift
            shape = CardShape
            clip = false
        }
    } else {
        Modifier.animateItem(placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    androidx.compose.foundation.layout.Box(modifier.then(itemModifier)) { content(dragging) }
}

/** Grip gesture: dragging the handle picks the item up straight away. */
fun Modifier.dragHandle(state: DragDropState, key: Any, enabled: Boolean = true, onStart: () -> Unit = {}): Modifier =
    if (!enabled) this else pointerInput(state, key) {
        detectDragGestures(
            onDragStart = { onStart(); state.onDragStart(key) },
            onDrag = { change, amount -> change.consume(); state.onDrag(amount.y) },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragEnd() }
        )
    }

/** Long-press anywhere on it, then drag: for rows whose whole surface can pick the item up. */
fun Modifier.longPressDragHandle(state: DragDropState, key: Any, enabled: Boolean = true, onStart: () -> Unit = {}): Modifier =
    if (!enabled) this else pointerInput(state, key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { onStart(); state.onDragStart(key) },
            onDrag = { change, amount -> change.consume(); state.onDrag(amount.y) },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragEnd() }
        )
    }
