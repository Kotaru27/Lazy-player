package com.lazy.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazy.player.data.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CoverKey(val albumOrTrack: Long, val pixelSize: Int)
private sealed interface CoverResult {
    data object Loading : CoverResult
    data class Ready(val bitmap: Bitmap?) : CoverResult
}

/** Two decodable sizes, 32 MiB in-memory cache, and IO-thread metadata reads. */
private object CoverCache {
    private val missing = LinkedHashSet<CoverKey>()
    // Distinct keys may decode concurrently; equal keys share a stripe so a
    // full-size artwork is never decoded twice by the dock, player and tint.
    private val loadLocks = Array(16) { Any() }
    private val cache = object : LruCache<CoverKey, Bitmap>(32 * 1024) {
        override fun sizeOf(key: CoverKey, value: Bitmap) = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun read(context: Context, track: AudioTrack, cap: Int): Bitmap? {
        val size = if (cap <= 240) 192 else 640
        val id = if (track.albumId > 0) track.albumId else -track.id
        val key = CoverKey(id, size)
        synchronized(cache) {
            cache.get(key)?.let { return it }
            if (key in missing) return null
        }
        // Fast cache lookup above handles the usual path. Recheck after acquiring
        // the stripe because another composable may have finished this decode.
        val loadLock = loadLocks[(key.hashCode() and Int.MAX_VALUE) % loadLocks.size]
        synchronized(loadLock) {
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
}

/**
 * Keep outgoing cover visible until incoming metadata decode completes.
 * Transition applies only to the cover: navigation/transport stays sharp.
 * A genuine no-art result intentionally replaces the old cover with a fallback.
 */
@Composable
fun Artwork(
    track: AudioTrack?,
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    blurred: Boolean = false,
    targetPx: Int = 640,
    showPlaceholderCaption: Boolean = true
) {
    val context = LocalContext.current
    val outcome by produceState<CoverResult>(
        initialValue = CoverResult.Loading, track?.id, targetPx
    ) {
        value = CoverResult.Loading
        value = CoverResult.Ready(track?.let {
            withContext(Dispatchers.IO) { CoverCache.read(context, it, targetPx) }
        })
    }
    var previous by remember(targetPx) { mutableStateOf<Bitmap?>(null) }
    val display = when (val result = outcome) {
        is CoverResult.Ready -> result.bitmap
        CoverResult.Loading -> previous
    }
    LaunchedEffect(outcome) {
        if (outcome is CoverResult.Ready) previous = (outcome as CoverResult.Ready).bitmap
    }
    Box(
        modifier = modifier.then(if (blurred) Modifier.blur(22.dp) else Modifier)
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(listOf(Color(0xFF30343B),
                    Color(0xFF20252D), Color(0xFF11161C)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = display, animationSpec = tween(210), label = "album-cover") { bitmap ->
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(),
                    contentDescription = if (blurred) null else "Album artwork",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (!blurred) {
                MissingCover(track, targetPx, showPlaceholderCaption)
            }
        }
    }
}

/**
 * Warm only the neighboring full-size covers while the full player is visible.
 * Never changes the playback queue, never reads on the main thread. Under
 * shuffle the caller omits neighbors rather than guessing the queue order.
 */
@Composable
fun PrefetchSwipeArtwork(next: AudioTrack?, previous: AudioTrack?) {
    val context = LocalContext.current
    LaunchedEffect(next?.id, previous?.id) {
        withContext(Dispatchers.IO) {
            next?.let { CoverCache.read(context, it, 640) }
            if (previous?.id != next?.id) {
                previous?.let { CoverCache.read(context, it, 640) }
            }
        }
    }
}

@Composable
private fun MissingCover(track: AudioTrack?, targetPx: Int, showCaption: Boolean) {
    val large = targetPx > 240
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width * .51f, size.height * .48f)
            val unit = size.minDimension
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF424852), Color(0xFF1D252D)),
                    center = c, radius = unit * .52f),
                radius = unit * .43f, center = c
            )
            for (i in 0..3) {
                drawCircle(Color.White.copy(alpha = .035f),
                    radius = unit * (.22f + .05f * i), center = c,
                    style = Stroke(width = (if (large) 1.4.dp else .8.dp).toPx()))
            }
            drawCircle(Color(0xFFB9C2C7).copy(alpha = .24f),
                radius = unit * .09f, center = c)
            drawCircle(Color(0xFF20242B), radius = unit * .024f, center = c)
        }
        if (!large) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null,
                tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(18.dp))
        } else if (showCaption) {
            val label = track?.album?.takeUnless {
                it.isBlank() || it.equals("Unknown album", ignoreCase = true) ||
                    it.equals("Folder music", ignoreCase = true)
            } ?: track?.artist.orEmpty()
            if (label.isNotBlank()) {
                Text(label, Modifier.align(Alignment.BottomCenter)
                    .padding(start = 21.dp, end = 21.dp, bottom = 20.dp),
                    fontFamily = FontFamily.SansSerif,
                    color = Color.White.copy(alpha = .72f), fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun rememberArtworkTint(track: AudioTrack?): Color {
    val context = LocalContext.current
    val tint by produceState(initialValue = Color(0xFF343840), track?.id) {
        value = track?.let {
            withContext(Dispatchers.IO) {
                val bmp = CoverCache.read(context, it, 192)
                if (bmp != null) mutedColor(bmp) else Color(0xFF343840)
            }
        } ?: Color(0xFF343840)
    }
    return tint
}

private fun mutedColor(bitmap: Bitmap): Color {
    if (bitmap.width < 1 || bitmap.height < 1) return Color(0xFF343840)
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
    AndroidColor.RGBToHSV((r / samples).toInt(), (g / samples).toInt(),
        (b / samples).toInt(), hsv)
    if (hsv[2] < 0.1f) return Color(0xFF343840)
    hsv[1] = hsv[1].coerceIn(0.08f, 0.28f)
    hsv[2] = 0.34f
    return Color(AndroidColor.HSVToColor(hsv))
}
