package com.lazy.player.data

import kotlin.random.Random

/** Collection-wide queue entry selection, shared by songs, albums, artists and favourites. */
object CollectionPlayback {
    fun startIndex(size: Int, shuffle: Boolean, random: Random = Random.Default): Int {
        if (size <= 0) return -1
        return if (shuffle) random.nextInt(size) else 0
    }
}
