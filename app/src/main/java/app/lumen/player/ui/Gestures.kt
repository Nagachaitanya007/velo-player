package app.lumen.player.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

fun Modifier.watchGestures(
    enabled: Boolean,
    onTap: () -> Unit,
    onDouble: (side: Int) -> Unit,
    onBoost: (Boolean) -> Unit,
    onSeek: (phase: Int, dx: Float, width: Float) -> Unit,
    onVertical: (left: Boolean, phase: Int, dy: Float, height: Float) -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    coroutineScope {
        var lastTap = 0L
        var lastSide = 0
        var pendingTap: Job? = null
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = down.position
            var axis = -1
            var totalX = 0f
            var totalY = 0f
            var longFired = false
            val width = size.width.toFloat().coerceAtLeast(1f)
            val height = size.height.toFloat().coerceAtLeast(1f)
            val job = launch {
                delay(340)
                if (axis == -1) {
                    longFired = true
                    pendingTap?.cancel()
                    onBoost(true)
                }
            }
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    totalX = change.position.x - start.x
                    totalY = change.position.y - start.y
                    if (axis == -1 && (abs(totalX) > slop || abs(totalY) > slop)) {
                        job.cancel()
                        pendingTap?.cancel()
                        axis = if (abs(totalX) > abs(totalY)) 0 else 1
                        if (axis == 0) onSeek(0, 0f, width) else {
                            onVertical(start.x < width * 0.5f, 0, 0f, height)
                        }
                    }
                    if (axis == 0) {
                        change.consume()
                        onSeek(1, totalX, width)
                    } else if (axis == 1) {
                        change.consume()
                        onVertical(start.x < width * 0.5f, 1, totalY, height)
                    }
                    if (change.positionChange() != androidx.compose.ui.geometry.Offset.Zero && axis != -1) {
                        change.consume()
                    }
                }
            } finally {
                job.cancel()
                if (longFired) {
                    onBoost(false)
                } else if (axis == 0) {
                    onSeek(2, totalX, width)
                } else if (axis == 1) {
                    onVertical(start.x < width * 0.5f, 2, totalY, height)
                } else {
                    val now = android.os.SystemClock.uptimeMillis()
                    val side = when {
                        start.x < width * 0.33f -> -1
                        start.x > width * 0.67f -> 1
                        else -> 0
                    }
                    if (now - lastTap < 280 && side == lastSide) {
                        pendingTap?.cancel()
                        lastTap = 0
                        onDouble(side)
                    } else {
                        lastTap = now
                        lastSide = side
                        pendingTap = launch {
                            delay(260)
                            onTap()
                        }
                    }
                }
            }
        }
    }
}
