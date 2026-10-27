package com.lazy.player.ui

import kotlin.math.abs

enum class SwipeTrackAction { NEXT, PREVIOUS, NONE }

/** Pure gesture decision: horizontal intent, minimum committed distance, queue boundaries. */
object TrackSwipe {
    fun decide(
        deltaX: Float, deltaY: Float, threshold: Float,
        canNext: Boolean, canPrevious: Boolean
    ): SwipeTrackAction {
        if (!deltaX.isFinite() || !deltaY.isFinite() || threshold <= 0f) return SwipeTrackAction.NONE
        if (abs(deltaX) < threshold || abs(deltaX) < abs(deltaY) * 1.25f) return SwipeTrackAction.NONE
        return if (deltaX < 0f) {
            if (canNext) SwipeTrackAction.NEXT else SwipeTrackAction.NONE
        } else {
            if (canPrevious) SwipeTrackAction.PREVIOUS else SwipeTrackAction.NONE
        }
    }
}
