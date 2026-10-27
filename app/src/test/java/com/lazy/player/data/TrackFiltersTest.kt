package com.lazy.player.data

import org.junit.Assert.*
import org.junit.Test

class TrackFiltersTest {
    @Test fun emptySearchShowsEverything() {
        assertTrue(TrackFilters.matches("Midnight", "Nova", "Glass", "  "))
    }
    @Test fun titleArtistAndAlbumSearchAreCaseInsensitive() {
        assertTrue(TrackFilters.matches("Midnight", "Nova", "Glass", "NIGHT"))
        assertTrue(TrackFilters.matches("Midnight", "Nova", "Glass", "nova"))
        assertTrue(TrackFilters.matches("Midnight", "Nova", "Glass", "gLaSs"))
    }
    @Test fun unrelatedSearchDoesNotMatch() {
        assertFalse(TrackFilters.matches("Midnight", "Nova", "Glass", "SUNRISE"))
    }
    @Test fun missingGroupGetsFallback() {
        assertEquals("Unknown artist", TrackFilters.normalizedGroup("  ", "Unknown artist"))
    }
}
