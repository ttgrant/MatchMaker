package org.grant.matchmaker.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import org.grant.matchmaker.domain.Player

internal class DragDropState {
    var isDragging by mutableStateOf(false)
    var dragPosition by mutableStateOf(Offset.Zero)
    var dragOffset by mutableStateOf(Offset.Zero)
    var draggedItem by mutableStateOf<Player?>(null)
    
    val dropTargets = mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>()
    var currentDropTarget by mutableStateOf<String?>(null)
    val dropCallbacks = mutableMapOf<String, (Player) -> Unit>()
    
    fun onDrop() {
        val target = currentDropTarget
        val item = draggedItem
        if (target != null && item != null) {
            dropCallbacks[target]?.invoke(item)
        }
        isDragging = false
        draggedItem = null
        dragPosition = Offset.Zero
        dragOffset = Offset.Zero
        currentDropTarget = null
    }
}

internal val LocalDragDropState = compositionLocalOf { DragDropState() }

@Composable
fun DragDropContainer(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state = remember { DragDropState() }
    CompositionLocalProvider(LocalDragDropState provides state) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
            
            if (state.isDragging && state.draggedItem != null) {
                Box(modifier = Modifier
                    .graphicsLayer {
                        translationX = state.dragPosition.x + state.dragOffset.x - 75f
                        translationY = state.dragPosition.y + state.dragOffset.y - 30f
                        alpha = 0.9f
                    }
                    .zIndex(100f)
                ) {
                    PlayerCard(state.draggedItem!!)
                }
            }
        }
    }
}

@Composable
fun DraggablePlayer(player: Player, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = LocalDragDropState.current
    var globalPosition by remember { mutableStateOf(Offset.Zero) }
    
    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                globalPosition = coordinates.localToWindow(Offset.Zero)
            }
            .pointerInput(player) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        state.isDragging = true
                        state.draggedItem = player
                        state.dragPosition = globalPosition + offset
                        state.dragOffset = Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.dragOffset += dragAmount
                        
                        val currentGlobalPos = state.dragPosition + state.dragOffset
                        
                        var found: String? = null
                        for ((id, bounds) in state.dropTargets) {
                            if (bounds.contains(currentGlobalPos)) {
                                found = id
                                break
                            }
                        }
                        state.currentDropTarget = found
                    },
                    onDragEnd = { state.onDrop() },
                    onDragCancel = {
                        state.isDragging = false
                        state.draggedItem = null
                        state.currentDropTarget = null
                    }
                )
            }
    ) {
        val isDragged = state.isDragging && state.draggedItem == player
        Box(modifier = Modifier.graphicsLayer { alpha = if (isDragged) 0.3f else 1f }) {
            content()
        }
    }
}

@Composable
fun DropTarget(
    id: String,
    onDrop: (Player) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (isHovered: Boolean) -> Unit
) {
    val state = LocalDragDropState.current
    
    DisposableEffect(id) {
        state.dropCallbacks[id] = onDrop
        onDispose {
            state.dropCallbacks.remove(id)
            state.dropTargets.remove(id)
        }
    }
    
    Box(modifier = modifier.onGloballyPositioned { coordinates ->
        state.dropTargets[id] = coordinates.boundsInWindow()
    }) {
        val isHovered = state.currentDropTarget == id
        content(isHovered)
    }
}
