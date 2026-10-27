package com.lazy.player.data

/** Pure functions intentionally isolated for JVM unit testing. */
object TrackFilters {
    fun matches(title: String, artist: String, album: String, query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || title.contains(q, true) || artist.contains(q, true) || album.contains(q, true)
    }
    fun normalizedGroup(value: String, fallback: String): String = value.trim().ifBlank { fallback }
}
