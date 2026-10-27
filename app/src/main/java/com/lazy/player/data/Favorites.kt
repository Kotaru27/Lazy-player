package com.lazy.player.data

import android.content.Context

/** Persist local MediaStore ids. A media rescan can change ids; only existing ids are shown. */
class Favorites(context: Context) {
    private val prefs = context.getSharedPreferences("lazy_player_favorites", Context.MODE_PRIVATE)

    fun ids(): Set<Long> = prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun toggle(id: Long): Set<Long> {
        val updated = ids().toMutableSet()
        if (!updated.add(id)) updated.remove(id)
        prefs.edit().putStringSet("ids", updated.map { it.toString() }.toSet()).apply()
        return updated
    }
}
