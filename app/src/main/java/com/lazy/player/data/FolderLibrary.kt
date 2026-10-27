package com.lazy.player.data

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException

/** Read-only scan of a user-selected Storage Access Framework tree, including subfolders. */
object FolderLibrary {
    private val extensions = setOf("flac", "wav", "wave", "mp3", "m4a", "aac", "ogg", "opus")
    data class Scan(val tracks: List<AudioTrack>, val error: String? = null)
    fun isSupported(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions

    /** SAF entries use negative IDs, avoiding collisions with MediaStore positive IDs. */
    fun documentId(uri: Uri): Long =
        UUID.nameUUIDFromBytes(uri.toString().toByteArray(Charsets.UTF_8)).leastSignificantBits or Long.MIN_VALUE

    suspend fun read(context: Context, folderUri: Uri): Scan = withContext(Dispatchers.IO) {
        try {
            val root = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext Scan(emptyList(), "That music folder is unavailable.")
            if (!root.exists() || !root.isDirectory) {
                return@withContext Scan(emptyList(), "The music folder is no longer accessible. Choose it again.")
            }
            val pending = ArrayDeque<DocumentFile>()
            val results = ArrayList<AudioTrack>()
            pending.add(root)
            var visited = 0
            while (pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                if (++visited > 1200 || results.size >= 20000) {
                    return@withContext Scan(results, "Folder scan reached its safety limit. Choose a smaller music folder.")
                }
                val node = pending.removeFirst()
                for (file in node.listFiles()) {
                    currentCoroutineContext().ensureActive()
                    if (file.isDirectory) pending.add(file)
                    else if (file.isFile && isSupported(file.name.orEmpty())) {
                        val name = file.name.orEmpty()
                        val base = name.substringBeforeLast('.', name)
                        var title = base
                        var artist = "Unknown artist"
                        var album = "Folder music"
                        var duration = 0L
                        try {
                            val mmr = MediaMetadataRetriever()
                            try {
                                mmr.setDataSource(context, file.uri)
                                title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().ifBlank { base }
                                artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().ifBlank { artist }
                                album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().ifBlank { album }
                                duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            } finally { mmr.release() }
                        } catch (_: Exception) {
                            // Retain readable files without embedded metadata.
                        }
                        results.add(AudioTrack(
                            id = documentId(file.uri), uri = file.uri, title = title,
                            artist = artist, album = album, albumId = -1L,
                            durationMs = duration, dateAdded = file.lastModified(),
                            filename = name
                        ))
                    }
                }
            }
            Scan(results.sortedWith(compareBy<AudioTrack> { it.album.lowercase(Locale.ROOT) }.thenBy { it.title.lowercase(Locale.ROOT) }))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            Scan(emptyList(), "Folder access was revoked. Choose the folder again.")
        } catch (_: Exception) {
            Scan(emptyList(), "Could not read this folder. Choose another folder or retry.")
        }
    }
}
