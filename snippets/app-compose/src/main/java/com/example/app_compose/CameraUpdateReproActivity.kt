/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.app_compose

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.children
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "CameraUpdateRepro"

private const val SWEEP_DURATION_MS = 2_000
private const val SUMMARY_INTERVAL_NANOS = 1_000_000_000L

private val START = LatLng(-34.0, 151.0)
private const val ZOOM = 10f

/**
 * Reproducer for https://issuetracker.google.com/issues/419217088.
 *
 * One infinite transition drives both the map camera and an on-screen box. The camera is moved
 * back and forth the way a finger does it: by a synthetic drag on the [MapView], one touch move
 * per frame. One frame after every touch move the camera position is read back, and a position
 * that is still the same although the camera was expected to have moved on is logged as a
 * duplicate.
 *
 * The drag is needed because `GoogleMap.moveCamera` updates the reported camera position
 * synchronously and therefore never goes stale. A gesture moves the camera on the SDK's GL
 * thread, which reports the new position to the main thread with a plain `Handler.post`.
 *
 * Why the box matters: Compose runs layout inside `dispatchDraw`. When that layout moves a layer
 * (which is what animating `Modifier.offset { }` does), the invalidation reaches `ViewRootImpl`
 * in the middle of the draw, which schedules the next traversal right away and posts a sync
 * barrier. The camera message from the GL thread is held back until after the next frame, by
 * which time a newer touch move has been handled and the SDK drops the stale update. Animating
 * the same box with `graphicsLayer { }` does not invalidate during the draw and serves as the
 * control.
 *
 * Watch the result with: `adb logcat -s CameraUpdateRepro`
 */
class CameraUpdateReproActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CameraUpdateRepro() }
    }
}

private enum class BoxAnimation(val label: String) {
    /** Moves the box in the layout phase. Expected to reproduce the issue. */
    LAYOUT_OFFSET("Modifier.offset { } (layout)"),

    /** Moves the box in the draw phase only. Control: expected not to reproduce. */
    GRAPHICS_LAYER("Modifier.graphicsLayer { } (draw only)"),

    /** Static box. Control: expected not to reproduce. */
    NONE("none"),
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun CameraUpdateRepro() {
    var boxAnimation by remember { mutableStateOf(BoxAnimation.LAYOUT_OFFSET) }

    val transition = rememberInfiniteTransition(label = "sweep")
    val fraction = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SWEEP_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fraction",
    )

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(START, ZOOM)
    }

    val hostView = LocalView.current
    val dragPx = with(LocalDensity.current) { 200.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = cameraPositionState,
        ) {
            // Restarts (and resets its counters) whenever the box animation is switched.
            MapEffect(boxAnimation) { map ->
                val mapView = checkNotNull(hostView.findMapView()) { "MapView not found" }
                val drag = SyntheticDrag(mapView)
                val mapPosition = DuplicateDetector("GoogleMap.cameraPosition", boxAnimation)
                val statePosition = DuplicateDetector("CameraPositionState.position", boxAnimation)
                val heldBackMessages = HeldBackMessageCounter()
                var movedLastFrame = false
                var moves = 0
                var lastSummaryNanos = 0L

                try {
                    while (true) {
                        // The block runs synchronously inside the Choreographer animation
                        // callback, so the touch move is handled within the frame like a real
                        // one, which is delivered in the input callback of the same frame.
                        withFrameNanos { frameTimeNanos ->
                            heldBackMessages.onFrame()

                            // Read back the result of the previous frame's touch move. The SDK
                            // has had a whole frame to report it.
                            mapPosition.check(map.cameraPosition, movedLastFrame, frameTimeNanos)
                            statePosition.check(
                                cameraPositionState.position,
                                movedLastFrame,
                                frameTimeNanos,
                            )

                            movedLastFrame = drag.moveTo(
                                x = mapView.width / 2f + (fraction.value - 0.5f) * dragPx,
                                y = mapView.height / 2f,
                            )
                            if (movedLastFrame) moves++

                            if (frameTimeNanos - lastSummaryNanos >= SUMMARY_INTERVAL_NANOS) {
                                if (lastSummaryNanos != 0L) {
                                    Log.i(
                                        TAG,
                                        "box animation: ${boxAnimation.label}; camera moves: $moves; " +
                                            "${mapPosition.summary()}; ${statePosition.summary()}; " +
                                            heldBackMessages.summary(),
                                    )
                                }
                                lastSummaryNanos = frameTimeNanos
                                moves = 0
                                mapPosition.resetCount()
                                statePosition.resetCount()
                                heldBackMessages.resetCount()
                            }
                        }
                    }
                } finally {
                    drag.release()
                }
            }
        }

        val boxModifier = when (boxAnimation) {
            // offset { } places its content with a layer and reads the animated value during
            // placement, so every frame moves a layer from inside the layout pass.
            BoxAnimation.LAYOUT_OFFSET ->
                Modifier.offset { IntOffset((fraction.value * dragPx).roundToInt(), 0) }

            BoxAnimation.GRAPHICS_LAYER ->
                Modifier.graphicsLayer { translationX = fraction.value * dragPx }

            BoxAnimation.NONE -> Modifier
        }
        Box(
            Modifier
                .safeDrawingPadding()
                .padding(16.dp)
                .then(boxModifier)
                .size(48.dp)
                .background(Color.Red),
        )

        Button(
            onClick = {
                boxAnimation = BoxAnimation.entries[(boxAnimation.ordinal + 1) % BoxAnimation.entries.size]
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Text("Box animation: ${boxAnimation.label}")
        }
    }
}

private fun View.findMapView(): MapView? {
    if (this is MapView) return this
    if (this is ViewGroup) {
        for (child in children) {
            child.findMapView()?.let { return it }
        }
    }
    return null
}

/** A one finger drag made of synthetic touch events that are dispatched straight to [view]. */
private class SyntheticDrag(private val view: View) {
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    private var downTime = 0L
    private var downX = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var panning = false

    /**
     * Moves the finger to the given position, putting it down first if needed.
     *
     * @return whether this is expected to move the camera, which is the case for every move once
     *   the finger has travelled far enough for the map to treat the gesture as a pan.
     */
    fun moveTo(x: Float, y: Float): Boolean {
        if (downTime == 0L) {
            downTime = SystemClock.uptimeMillis()
            downX = x
            dispatch(MotionEvent.ACTION_DOWN, x, y)
        } else if (x != lastX || y != lastY) {
            dispatch(MotionEvent.ACTION_MOVE, x, y)
        } else {
            return false
        }
        val wasPanning = panning
        panning = panning || abs(x - downX) > 2 * touchSlop
        lastX = x
        lastY = y
        return wasPanning
    }

    fun release() {
        if (downTime != 0L) {
            dispatch(MotionEvent.ACTION_UP, lastX, lastY)
            downTime = 0L
        }
    }

    private fun dispatch(action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        view.dispatchTouchEvent(event)
        event.recycle()
    }
}

/**
 * Logs every camera position that is a duplicate of the previous one although the camera was
 * expected to have moved on.
 *
 * The map may render at a lower rate than the display (60 Hz on a 120 Hz display, for example),
 * so a position is only expected to have changed once it has been the same for longer than
 * [TOLERANCE_NANOS] while the finger kept moving.
 */
private class DuplicateDetector(
    private val source: String,
    private val boxAnimation: BoxAnimation,
) {
    private var lastPosition: CameraPosition? = null
    private var unchangedSinceNanos = 0L
    private var reads = 0
    private var duplicates = 0
    private var longestFreezeNanos = 0L

    fun check(position: CameraPosition, cameraMoved: Boolean, frameTimeNanos: Long) {
        if (!cameraMoved || position != lastPosition) {
            lastPosition = position
            unchangedSinceNanos = frameTimeNanos
        }
        if (!cameraMoved) return

        reads++
        val frozenNanos = frameTimeNanos - unchangedSinceNanos
        if (frozenNanos > TOLERANCE_NANOS) {
            duplicates++
            longestFreezeNanos = maxOf(longestFreezeNanos, frozenNanos)
            Log.w(
                TAG,
                "duplicated $source (box animation: ${boxAnimation.label}): the camera has been " +
                    "moving for ${frozenNanos / NANOS_PER_MILLI} ms but the position is still " +
                    "${position.target}",
            )
        }
    }

    fun summary(): String = "duplicated $source: $duplicates of $reads reads " +
        "(longest freeze ${longestFreezeNanos / NANOS_PER_MILLI} ms)"

    fun resetCount() {
        reads = 0
        duplicates = 0
        longestFreezeNanos = 0L
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val TOLERANCE_NANOS = 50 * NANOS_PER_MILLI
    }
}

/**
 * Shows the cause without involving the map: every frame it posts a plain message to the main
 * thread from between two frames, like the SDK's GL thread does, and counts the messages that
 * were not delivered before the next frame started.
 */
private class HeldBackMessageCounter {
    private val handler = Handler(Looper.getMainLooper())
    private var frame = 0
    private var posted = 0
    private var heldBack = 0

    fun onFrame() {
        frame++
        val postedInFrame = frame
        posted++
        handler.postDelayed({ if (frame > postedInFrame) heldBack++ }, POST_DELAY_MS)
    }

    fun summary(): String = "main thread messages held back past the next frame: $heldBack of $posted"

    fun resetCount() {
        posted = 0
        heldBack = 0
    }

    private companion object {
        // Late enough to be enqueued after the frame has been drawn, early enough to be due
        // before the next frame starts.
        const val POST_DELAY_MS = 4L
    }
}
