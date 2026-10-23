// Adapted from Square's Backdrop catalog DragGestureInspector, Apache-2.0.
// Original source and upstream pin are retained in minimal/vendor/square/.
// Local changes: horizontal touch slop, consumption, no pointer substitution,
// cancellation in finally, and no committing a cancelled gesture.
package com.fuhbe.ytdlpg.simple.squareglass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange

internal suspend fun PointerInputScope.inspectDragGestures(
    onPress: (PointerInputChange) -> Unit,
    onPressEnd: () -> Unit,
    onDragStart: (PointerInputChange) -> Unit,
    onDragEnd: (PointerInputChange) -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(false)
        // Pressure/light starts at contact without consuming taps or vertical
        // scroll. Only horizontal motion beyond slop owns the gesture.
        onPress(down)
        var started=false
        try {
          val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
          if (start != null) {
            started=true
            var ended = false
            try {
                onDragStart(down)
                onDrag(start, start.position - down.position)
                while (true) {
                    // The horizontal recognizer already owns this pointer. Consume
                    // at Initial before scroll/click handlers run at Main, even
                    // when the same finger travels outside the visual bounds.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.any { it.id != down.id && it.pressed }) {
                        // This recognizer already owns a horizontal drag. Do
                        // not let a second pointer turn cancellation into a
                        // child's click when its finger is later released.
                        event.changes.forEach { it.consume() }
                        while(currentEvent.changes.any { it.pressed }) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                        break
                    }
                    if (change.isConsumed) break
                    if (change.changedToUpIgnoreConsumed()) {
                        change.consume()
                        onDragEnd(change)
                        ended = true
                        break
                    }
                    if (!change.pressed) break
                    onDrag(change, change.positionChange())
                    change.consume()
                }
            } finally {
                if (!ended) onDragCancel()
            }
          }
        } finally {
            if(!started) onPressEnd()
        }
    }
}
