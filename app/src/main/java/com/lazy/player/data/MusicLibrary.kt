package com.lazy.player.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MusicLibrary {
    suspend fun read(context: Context): List<AudioTrack> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val tracks = mutableListOf<AudioTrack>()
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0",
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val date = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val trackId = cursor.getLong(id)
                    tracks += AudioTrack(
                        id = trackId,
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId),
                        title = cursor.getString(title).orEmpty().ifBlank { "Untitled" },
                        artist = cursor.getString(artist).orEmpty().takeUnless { it == "<unknown>" }.orEmpty().ifBlank { "Unknown artist" },
                        album = cursor.getString(album).orEmpty().takeUnless { it == "<unknown>" }.orEmpty().ifBlank { "Unknown album" },
                        albumId = cursor.getLong(albumId),
                        durationMs = cursor.getLong(duration),
                        dateAdded = cursor.getLong(date),
                        filename = cursor.getString(name).orEmpty(),
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permissions can be revoked at any time; surface an empty library instead of crashing.
        }
        tracks
    }
}
