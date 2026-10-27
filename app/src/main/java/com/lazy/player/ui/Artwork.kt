package com.lazy.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.DrawScope
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

private data class CoverKey(val albumOrTrack: Long, val pixelSize: Int)

/**
 * Two bounded decoding sizes: row thumbnails and now-playing art.
 * Retrieval/decoding always occurs on Dispatchers.IO, never in list composition.
 */
private object CoverCache {
    private val missing = LinkedHashSet<CoverKey>()
    private val cache = object : LruCache<CoverKey, Bitmap>(32 * 1024) {
        override fun sizeOf(key: CoverKey, value: Bitmap) = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun read(context: Context, track: AudioTrack, cap: Int): Bitmap? {
        val size = if (cap <= 240) 192 else 640
        val id = if (track.id < 0) track.id else if (track.albumId > 0) track.albumId else -track.id
        val key = CoverKey(id, size)
        synchronized(cache) {
            cache.get(key)?.let { return it }
            if (key in missing) return null
        }
        val image = try {
            val retriever = MediaMetadataRetriever()
            val raw = try {
                retriever.setDataSource(context, track.uri)
                retriever.embeddedPicture
            } finally { retriever.release() }
            if (raw == null || raw.isEmpty()) null else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
                    var sample = 1
                    while (bounds.outWidth / sample > size * 1.2f ||
                        bounds.outHeight / sample > size * 1.2f) sample *= 2
                    BitmapFactory.decodeByteArray(raw, 0, raw.size,
                        BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        })
                }
            }
        } catch (_: Exception) { null }
        synchronized(cache) {
            if (image == null) {
                if (missing.size >= 512) missing.remove(missing.first())
                missing.add(key)
            } else cache.put(key, image)
        }
        return image
    }
}

@Composable
fun Artwork(
    track: AudioTrack?,
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    blurred: Boolean = false,
    targetPx: Int = 640
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, track?.id, targetPx) {
        value = track?.let { withContext(Dispatchers.IO) { CoverCache.read(context, it, targetPx) } }
    }
    Box(
        modifier = modifier
            .then(if (blurred) Modifier.blur(22.dp) else Modifier)
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(
                listOf(Color(0xFF292D34), Color(0xFF1D2229), Color(0xFF151A20)))),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = if (blurred) null else "Album artwork",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (!blurred) {
            val caption = track?.album?.takeUnless {
                it.isBlank() || it.equals("Folder music", ignoreCase = true) ||
                    it.equals("Unknown album", ignoreCase = true)
            } ?: track?.artist?.takeUnless {
                it.isBlank() || it.equals("Unknown artist", ignoreCase = true)
            } ?: track?.title.orEmpty()
            val initials = caption.trim().split(Regex("\\s+"))
                .filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.take(1).uppercase() }.ifBlank { "LP" }
            Column(Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(initials, color = Color(0xFFDEE2E6).copy(alpha = 0.84f),
                    fontSize = if (targetPx <= 240) 17.sp else 44.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1)
                if (targetPx > 240) {
                    Spacer(Modifier.height(10.dp))
                    Icon(Icons.Rounded.MusicNote, contentDescription = null,
                        tint = Color(0xFFDEE2E6).copy(alpha = 0.36f),
                        modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

/** Muted palette sampled from album artwork; falls back to graphite when unavailable. */
@Composable
fun rememberArtworkTint(track: AudioTrack?): Color {
    val context = LocalContext.current
    val tint by produceState(initialValue = Color(0xFF343840), track?.id) {
        value = track?.let {
            withContext(Dispatchers.IO) {
                val bmp = CoverCache.read(context, it, 192)
                if (bmp != null) mutedColor(bmp) else Color(0xFF354955)
            }
        } ?: Color(0xFF354955)
    }
    return tint
}

private fun mutedColor(bitmap: Bitmap): Color {
    if (bitmap.width < 1 || bitmap.height < 1) return Color(0xFF354955)
    var r = 0L; var g = 0L; var b = 0L
    var samples = 0
    for (row in 1..4) for (col in 1..4) {
        val pixel = bitmap.getPixel(
            ((bitmap.width - 1) * col / 5).coerceAtLeast(0),
            ((bitmap.height - 1) * row / 5).coerceAtLeast(0))
        r += AndroidColor.red(pixel)
        g += AndroidColor.green(pixel)
        b += AndroidColor.blue(pixel)
        samples++
    }
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV((r / samples).toInt(), (g / samples).toInt(), (b / samples).toInt(), hsv)
    if (hsv[2] < 0.1f) return Color(0xFF354955)
    hsv[1] = hsv[1].coerceIn(0.08f, 0.28f)
    hsv[2] = 0.34f
    return Color(AndroidColor.HSVToColor(hsv))
}
