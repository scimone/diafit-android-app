package uk.scimone.diafit.home.presentation.utils

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Makes a hand-drawn (Canvas) panel pan and zoom the shared chart [scrollState]/[zoomState] like the Vico
 * panels do: horizontal drag with fling, and two-finger pinch zoom around the fingers. Single-finger taps and
 * long-press scrubbing are left to the parent's inspect gestures (which consume the scrub's events first).
 */
fun Modifier.chartPanZoom(
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    geometry: State<ChartGeometry?>
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val decay = remember(density) { splineBasedDecay<Float>(density) }
    val flingJob = remember { mutableStateOf<Job?>(null) }
    val currentGeometry by rememberUpdatedState(geometry)

    this
        .draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { dx ->
                flingJob.value?.cancel()
                scope.launch { scrollState.scroll(Scroll.Absolute.pixels(scrollState.value - dx)) }
            },
            onDragStarted = { flingJob.value?.cancel() },
            onDragStopped = { velocity ->
                flingJob.value = scope.launch {
                    AnimationState(scrollState.value, -velocity).animateDecay(decay) {
                        val target = value
                        scope.launch { scrollState.scroll(Scroll.Absolute.pixels(target)) }
                    }
                }
            }
        )
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2 && event.changes.none { it.isConsumed }) {
                        val factor = event.calculateZoom()
                        if (factor != 1f) {
                            val centroidX = event.calculateCentroid().x
                            val g = currentGeometry.value
                            val range = zoomState.valueRange
                            val newZoom = (zoomState.value * factor).coerceIn(range.start, range.endInclusive)
                            val applied = newZoom / zoomState.value
                            scope.launch {
                                // Keep the time under the fingers where it is: rescale around the centroid.
                                val anchor = g?.timeAt(centroidX)
                                zoomState.zoom(Zoom.fixed(newZoom))
                                if (g != null && anchor != null) {
                                    val newOrigin = centroidX - ((anchor - g.minX) * g.pxPerMs * applied).toFloat()
                                    scrollState.scroll(Scroll.Absolute.pixels(scrollState.value + (g.originPx - newOrigin)))
                                }
                            }
                            pressed.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
}
