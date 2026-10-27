package com.lazy.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CollectionPlaybackTest {
    @Test fun emptyCollectionDoesNotQueueAnything() {
        assertEquals(-1, CollectionPlayback.startIndex(0, false))
        assertEquals(-1, CollectionPlayback.startIndex(0, true))
    }

    @Test fun playAllAlwaysBeginsAtTheFirstVisibleSong() {
        for (size in 1..50) assertEquals(0, CollectionPlayback.startIndex(size, false))
    }

    @Test fun shuffledCollectionStaysWithinRangeAndVaries() {
        val rng = Random(9817)
        val observed = (0 until 128).map { CollectionPlayback.startIndex(15, true, rng) }
        assertTrue(observed.all { it in 0..14 })
        assertTrue(observed.distinct().size > 5)
    }

    @Test fun oneSongShuffleHasAValidStartingIndex() {
        assertEquals(0, CollectionPlayback.startIndex(1, true, Random(7)))
    }
}
