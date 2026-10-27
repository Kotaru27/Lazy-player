package com.lazy.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lazy.player.data.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded bitmap cache; playback never waits for artwork decoding. */
private object CoverCache {
    private val missing = LinkedHashSet<Long>()
    private val cache = object : LruCache<Long, Bitmap>(24 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
    }

    fun read(context: Context, track: AudioTrack): Bitmap? {
        val key = if (track.id < 0) track.id else if (track.albumId > 0) track.albumId else -track.id
        synchronized(cache) {
            cache.get(key)?.let { return it }
            if (key in missing) return null
        }
        val image = try {
            val bytes = MediaMetadataRetriever().run {
                try {
                    setDataSource(context, track.uri)
                    embeddedPicture
                } finally { release() }
            } ?: ByteArray(0)
            if (bytes.isEmpty()) null else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 700 || bounds.outHeight / sample > 700) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
            }
        } catch (_: Exception) {
            null
        }
        synchronized(cache) {
            if (image == null) {
                if (missing.size >= 2048) missing.remove(missing.first())
                missing.add(key)
            } else cache.put(key, image)
        }
        return image
    }
}

@Composable
fun Artwork(track: AudioTrack?, modifier: Modifier = Modifier, corner: Dp = 18.dp, blurred: Boolean = false) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, track?.id) {
        value = track?.let { withContext(Dispatchers.IO) { CoverCache.read(context, it) } }
    }
    val tint = accentFor(track?.id ?: 42)
    Box(
        modifier = modifier
            .then(if (blurred) Modifier.blur(26.dp) else Modifier)
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.82f), Color(0xFF293A46), Color(0xFF0F1921)))),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = if (blurred) null else "Album artwork", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (!blurred) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.fillMaxSize(0.30f))
        }
    }
}

private fun accentFor(id: Long): Color = when ((id and Long.MAX_VALUE) % 5L) {
    0L -> Color(0xFF7B94A1)
    1L -> Color(0xFF66899B)
    2L -> Color(0xFF8997A5)
    3L -> Color(0xFF68999D)
    else -> Color(0xFF8C97A2)
}
