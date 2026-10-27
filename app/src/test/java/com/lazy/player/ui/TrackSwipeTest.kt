package com.lazy.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackSwipeTest {
    @Test fun validHorizontalSwipesPickExactlyOneQueueDirection() {
        assertEquals(SwipeTrackAction.NEXT, TrackSwipe.decide(-110f, 6f, 72f, true, true))
        assertEquals(SwipeTrackAction.PREVIOUS, TrackSwipe.decide(110f, -8f, 72f, true, true))
    }
    @Test fun shortOrVerticalSwipesDoNothing() {
        assertEquals(SwipeTrackAction.NONE, TrackSwipe.decide(44f, 0f, 72f, true, true))
        assertEquals(SwipeTrackAction.NONE, TrackSwipe.decide(110f, 130f, 72f, true, true))
        assertEquals(SwipeTrackAction.NONE, TrackSwipe.decide(0f, 0f, 72f, true, true))
    }
    @Test fun boundaryAndOneSongQueueAreSafe() {
        assertEquals(SwipeTrackAction.NONE, TrackSwipe.decide(110f, 0f, 72f, true, false))
        assertEquals(SwipeTrackAction.NONE, TrackSwipe.decide(-110f, 0f, 72f, false, true))
        assertEquals(SwipeTrackAction.NONE, TrackSwipe.decide(-110f, 0f, 72f, false, false))
        assertEquals(SwipeTrackAction.NONE, TrackSwipe.decide(110f, 0f, 72f, false, false))
    }
    @Test fun malformedGestureIsIgnored() {
        assertEquals(SwipeTrackAction.NONE, TrackSwipe.decide(Float.NaN, 0f, 72f, true, true))
        assertEquals(SwipeTrackAction.NONE, TrackSwipe.decide(-100f, 0f, -1f, true, true))
    }
}