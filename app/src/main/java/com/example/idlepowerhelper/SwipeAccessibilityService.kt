package com.example.idlepowerhelper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Minimal Accessibility Service whose only job is to inject swipe gestures.
 * It does not read any content from the screen.
 *
 * The active instance is exposed via [instance] so that [OverlayService] can
 * call [performSwipe] without binding to the service.
 */
class SwipeAccessibilityService : AccessibilityService() {

    companion object {
        /** Set when the system connects the service; null when disconnected. */
        var instance: SwipeAccessibilityService? = null
            private set

        private const val SWIPE_DURATION_MS = 180L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // We don't listen to any events — just here for gesture injection.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    /**
     * Performs a straight-line drag from ([fromX],[fromY]) to ([toX],[toY]).
     * [onDone] is called on the main thread when the gesture completes or is cancelled.
     *
     * MUST be called from the main thread.
     */
    fun performSwipe(
        fromX: Float, fromY: Float,
        toX: Float,   toY: Float,
        onDone: (() -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX,   toY)
        }
        val stroke  = GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { onDone?.invoke() }
            override fun onCancelled(g: GestureDescription?) { onDone?.invoke() }
        }, Handler(Looper.getMainLooper()))
    }
}
