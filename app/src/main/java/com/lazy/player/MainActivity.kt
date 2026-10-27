package com.lazy.player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lazy.player.data.AudioTrack
import com.lazy.player.data.Favorites
import com.lazy.player.data.MusicLibrary
import com.lazy.player.ui.LazyPlayerScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        setContent { LazyPlayerApplication() }
    }
}

private data class PlayerView(
    val mediaId: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
)

private fun observe(controller: MediaController): PlayerView = PlayerView(
    mediaId = controller.currentMediaItem?.mediaId,
    playing = controller.isPlaying,
    positionMs = controller.currentPosition.coerceAtLeast(0),
    durationMs = controller.duration.coerceAtLeast(0),
    repeatMode = controller.repeatMode,
    shuffle = controller.shuffleModeEnabled,
)

@Composable
private fun LazyPlayerApplication() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var libraryLoading by remember { mutableStateOf(granted) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var state by remember { mutableStateOf(PlayerView()) }
    var queueRevision by remember { mutableIntStateOf(0) }
    var favorites by remember { mutableStateOf(Favorites(context).ids()) }

    LaunchedEffect(granted, refreshTick) {
        if (!granted) {
            tracks = emptyList()
            libraryLoading = false
        } else {
            if (tracks.isEmpty()) libraryLoading = true
            try { tracks = MusicLibrary.read(context) }
            finally { libraryLoading = false }
        }
    }
    DisposableEffect(lifecycleOwner, granted) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasPermission = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                if (granted != hasPermission) granted = hasPermission
                else if (hasPermission) refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, com.lazy.player.playback.PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { controller = it }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    DisposableEffect(controller) {
        val c = controller
        if (c == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    state = observe(c)
                    if (events.contains(Player.EVENT_TIMELINE_CHANGED)) queueRevision++
                }
            }
            c.addListener(listener)
            state = observe(c)
            onDispose { c.removeListener(listener) }
        }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            state = observe(c)
            delay(500)
        }
    }

    val currentTrack = tracks.firstOrNull { it.id.toString() == state.mediaId }
    // Preserve real Media3 timeline indices even when removed files are hidden.
    val queueEntries = remember(state.mediaId, tracks, queueRevision, controller) {
        controller?.let { c ->
            val byId = tracks.associateBy { it.id.toString() }
            (0 until c.mediaItemCount).mapNotNull { index ->
                byId[c.getMediaItemAt(index).mediaId]?.let { index to it }
            }
        }.orEmpty()
    }
    LazyPlayerScreen(
        tracks = tracks,
        hasPermission = granted,
        libraryLoading = libraryLoading,
        onPermission = { permissionLauncher.launch(permission) },
        currentTrack = currentTrack,
        playing = state.playing,
        position = state.positionMs,
        duration = state.durationMs,
        shuffle = state.shuffle,
        repeatMode = state.repeatMode,
        favorites = favorites,
        onRefresh = { refreshTick++ },
        onPlayTrack = { track, queue ->
            controller?.let { c ->
                val index = queue.indexOfFirst { it.id == track.id }
                if (index >= 0) {
                    c.setMediaItems(queue.map { it.toMediaItem() }, index, 0L)
                    c.prepare()
                    c.play()
                }
            }
        },
        onTogglePlay = {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        },
        onNext = { controller?.seekToNextMediaItem() },
        onPrevious = {
            controller?.let { if (it.currentPosition > 3_000) it.seekTo(0) else it.seekToPreviousMediaItem() }
        },
        onSeek = { controller?.seekTo(it) },
        onShuffle = { controller?.let { c -> c.shuffleModeEnabled = !c.shuffleModeEnabled } },
        onRepeat = {
            controller?.let { c ->
                c.repeatMode = when (c.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        },
        queue = queueEntries.map { it.second },
        onQueueSelection = { index ->
            queueEntries.getOrNull(index)?.let { item -> controller?.seekToDefaultPosition(item.first) }
        },
        onToggleFavorite = { id -> favorites = Favorites(context).toggle(id) }
    )
}
