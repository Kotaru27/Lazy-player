package com.lazy.player.ui

import androidx.activity.compose.BackHandler
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.lazy.player.data.AudioTrack
import com.lazy.player.data.TrackFilters
import kotlin.math.roundToLong

private enum class Page { Listen, Liked }
private enum class Grouping { Songs, Albums, Artists }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LazyPlayerScreen(
    tracks: List<AudioTrack>,
    hasPermission: Boolean,
    libraryLoading: Boolean,
    folderName: String?,
    folderError: String?,
    usbDacConnected: Boolean,
    onChooseFolder: () -> Unit,
    onUseAllMusic: () -> Unit,
    onPermission: () -> Unit,
    currentTrack: AudioTrack?,
    playing: Boolean,
    positionState: State<Long>,
    duration: Long,
    shuffle: Boolean,
    repeatMode: Int,
    canNext: Boolean,
    canPrevious: Boolean,
    favorites: Set<Long>,
    onRefresh: () -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayCollection: (List<AudioTrack>, Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    queue: List<AudioTrack>,
    onQueueSelection: (Int) -> Unit,
    onToggleFavorite: (Long) -> Unit,
) {
    var page by remember { mutableStateOf(Page.Listen) }
    var grouping by remember { mutableStateOf(Grouping.Songs) }
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<Pair<Grouping, String>?>(null) }
    var fullPlayer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    val reduceMotion = rememberReducedMotion()

    BackHandler(enabled = fullPlayer && !showQueue) { fullPlayer = false }
    BackHandler(enabled = selectedGroup != null && !fullPlayer) { selectedGroup = null }

    val filtered = remember(tracks, query, page, favorites, grouping, selectedGroup) {
        var result = if (page == Page.Liked) tracks.filter { it.id in favorites } else tracks
        if (selectedGroup != null) {
            val (type, name) = selectedGroup!!
            result = if (type == Grouping.Albums) result.filter { albumKey(it) == name } else result.filter { it.artist == name }
        }
        result.filter { TrackFilters.matches(it.title, it.artist, it.album, query) }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Lavender, onSurface = MistWhite, surface = LazyNight)) {
        Box(Modifier.fillMaxSize().background(LazyNight)) {
            AmbientHaze()
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Column(Modifier.weight(1f)) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().height(55.dp).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (selectedGroup != null) {
                            PlainAction(Icons.Rounded.ArrowBack, "Back to library") { selectedGroup = null }
                            Text(
                                if (selectedGroup!!.first == Grouping.Albums)
                                    tracks.firstOrNull { albumKey(it) == selectedGroup!!.second }?.album ?: "Album"
                                else selectedGroup!!.second,
                                modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MistWhite, fontSize = 19.sp, fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text("lazy player", modifier = Modifier.weight(1f), fontSize = 23.sp,
                                fontWeight = FontWeight.SemiBold, color = MistWhite, letterSpacing = (-0.6).sp)
                        }
                        FolderMenu(folderName, onChooseFolder, onUseAllMusic)
                        PlainAction(if (search) Icons.Rounded.Close else Icons.Rounded.Search,
                            if (search) "Close search" else "Search music") {
                            search = !search
                            if (!search) query = ""
                        }
                        PlainAction(if (page == Page.Liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            if (page == Page.Liked) "Return to music library" else "Show favourites") {
                            page = if (page == Page.Liked) Page.Listen else Page.Liked
                            selectedGroup = null
                        }
                    }
                    if (search) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(45.dp)
                                .clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.07f))
                                .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Search, null, tint = SoftText, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(9.dp))
                            BasicTextField(
                                value = query, onValueChange = { query = it },
                                modifier = Modifier.weight(1f), singleLine = true,
                                cursorBrush = SolidColor(Lavender),
                                textStyle = TextStyle(fontSize = 15.sp, color = MistWhite),
                                decorationBox = { inner -> Box {
                                    if (query.isEmpty()) Text("Search music", fontSize = 14.sp, color = SoftText)
                                    inner()
                                }}
                            )
                        }
                    }
                    Spacer(Modifier.height(11.dp))
                    CollectionHeading(
                        name = when {
                            page == Page.Liked -> "Favourites"
                            selectedGroup != null -> "Songs"
                            else -> grouping.name
                        },
                        count = filtered.size,
                        enabled = hasPermission && !libraryLoading && filtered.isNotEmpty(),
                        onPlay = { onPlayCollection(filtered, false) },
                        onShuffle = { onPlayCollection(filtered, true) }
                    )
                    Spacer(Modifier.height(6.dp))
                    when {
                        !hasPermission -> IntroState(onPermission, onChooseFolder)
                        libraryLoading && tracks.isEmpty() -> LoadingLibrary()
                        folderError != null && tracks.isEmpty() -> FolderError(folderError, onChooseFolder)
                        tracks.isEmpty() -> EmptyLibrary(onRefresh, folderName, onChooseFolder)
                        filtered.isEmpty() -> NoResults(page == Page.Liked)
                        selectedGroup == null && page == Page.Listen && grouping != Grouping.Songs -> {
                            val groups = remember(filtered, grouping) {
                                if (grouping == Grouping.Albums) filtered.groupBy { albumKey(it) } else filtered.groupBy { it.artist }
                            }
                            LazyColumn(contentPadding = PaddingValues(bottom = 26.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                items(groups.entries.toList(), key = { it.key }) { entry ->
                                    GroupRow(title = if (grouping == Grouping.Albums) entry.value.first().album else entry.key, tracks = entry.value, onClick = { selectedGroup = grouping to entry.key })
                                }
                            }
                        }
                        else -> {
                            LazyColumn(contentPadding = PaddingValues(bottom = 26.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                items(filtered, key = { it.id }) { track ->
                                    TrackRow(track, selected = currentTrack?.id == track.id, playing = playing,
                                        liked = track.id in favorites, onFavorite = { onToggleFavorite(track.id) },
                                        onClick = { onPlayTrack(track, filtered) })
                                }
                            }
                        }
                    }
                }
                UnifiedDock(
                    track = currentTrack, playing = playing,
                    reduceMotion = reduceMotion,
                    positionState = positionState, duration = duration,
                    grouping = grouping,
                    onGroup = { grouping = it; page = Page.Listen; selectedGroup = null },
                    onOpen = { fullPlayer = true },
                    onPlay = onTogglePlay, onNext = onNext,
                    canNext = canNext, canPrevious = canPrevious,
                    onSwipeNext = onSwipeNext, onSwipePrevious = onSwipePrevious
                )
                Spacer(Modifier.height(8.dp))
            }
            AnimatedVisibility(
                visible = fullPlayer && currentTrack != null,
                enter = if (reduceMotion) fadeIn(tween(0))
                    else slideInVertically(initialOffsetY = { it / 6 }, animationSpec = tween(280)) +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(280)) + fadeIn(tween(240)),
                exit = if (reduceMotion) fadeOut(tween(0))
                    else slideOutVertically(targetOffsetY = { it / 6 }, animationSpec = tween(225)) +
                        scaleOut(targetScale = 0.97f, animationSpec = tween(225)) + fadeOut(tween(185))
            ) {
                currentTrack?.let { track ->
                    NowPlaying(
                        track, playing, positionState, duration, shuffle, repeatMode,
                        liked = track.id in favorites,
                        onDismiss = { fullPlayer = false }, onPlay = onTogglePlay, onNext = onNext, onPrevious = onPrevious,
                        onSeek = onSeek, onShuffle = onShuffle, onRepeat = onRepeat, onFavorite = { onToggleFavorite(track.id) },
                        onQueue = { showQueue = true }, usbDacConnected = usbDacConnected,
                        canNext = canNext, canPrevious = canPrevious,
                        onSwipeNext = onSwipeNext, onSwipePrevious = onSwipePrevious
                    )
                }
            }
            if (showQueue) {
                ModalBottomSheet(
                    onDismissRequest = { showQueue = false },
                    containerColor = Color(0xFF141E26),
                    contentColor = MistWhite,
                    dragHandle = { Box(Modifier.padding(top = 12.dp).size(width = 38.dp, height = 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.34f))) }
                ) {
                    Text("Up next", Modifier.padding(start = 26.dp, top = 25.dp, bottom = 18.dp), fontSize = 26.sp, fontWeight = FontWeight.Light)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 36.dp)) {
                        items(queue.size) { index ->
                            TrackRow(queue[index], selected = currentTrack?.id == queue[index].id, playing = playing,
                                liked = queue[index].id in favorites,
                                onFavorite = { onToggleFavorite(queue[index].id) },
                                onClick = { onQueueSelection(index); showQueue = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f
    }
}

@Composable
private fun PlainAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, tint = MistWhite, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun FolderMenu(folderName: String?, onChooseFolder: () -> Unit, onUseAllMusic: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    Box {
        PlainAction(Icons.Rounded.FolderOpen,
            if (folderName == null) "Select music folder" else "Folder: $folderName") { show = true }
        DropdownMenu(expanded = show, onDismissRequest = { show = false },
            modifier = Modifier.background(Color(0xFF17232B))) {
            DropdownMenuItem(enabled = false,
                text = { Text(folderName ?: "All device music", color = MistWhite, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) },
                onClick = {})
            DropdownMenuItem(text = { Text("Change folder") },
                leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
                onClick = { show = false; onChooseFolder() })
            if (folderName != null) {
                DropdownMenuItem(text = { Text("Use all music") },
                    leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null) },
                    onClick = { show = false; onUseAllMusic() })
            }
        }
    }
}

@Composable
private fun CollectionHeading(name: String, count: Int, enabled: Boolean,
    onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(59.dp).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 20.sp, color = MistWhite, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$"+"{count} songs", color = SoftText, fontSize = 11.sp)
        }
        SmallAction(Icons.Rounded.PlayArrow, "Play all $"+"{count} songs", enabled, onPlay)
        Spacer(Modifier.width(5.dp))
        SmallAction(Icons.Rounded.Shuffle, "Shuffle all $"+"{count} songs", enabled, onShuffle)
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp),
        interactionSource = source) {
        Box(Modifier.size(39.dp).clip(RoundedCornerShape(13.dp))
            .background(if (enabled) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.025f))
            .border(0.6.dp, Color.White.copy(alpha = if (enabled) 0.11f else 0.025f),
                RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = title,
                tint = if (enabled) MistWhite else SoftText.copy(alpha = 0.42f),
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun IntroState(onPermission: () -> Unit, onChooseFolder: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(62.dp), tint = Lavender)
        Spacer(Modifier.height(28.dp))
        Text("Make room for music.", color = MistWhite, fontSize = 25.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(10.dp))
        Text("Select a music folder or grant access to your complete on-device library. Nothing gets uploaded.", color = SoftText, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(29.dp))
        Box(Modifier.clip(CircleShape).background(MistWhite).clickable(onClick = onPermission).padding(horizontal = 26.dp, vertical = 16.dp)) {
            Text("All songs on this phone", color = LazyNight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.glass(24, bright = true).clickable(onClick = onChooseFolder).padding(horizontal = 24.dp, vertical = 15.dp)) {
            Text("Choose my FLAC folder", color = MistWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoadingLibrary() {
    Column(Modifier.fillMaxWidth().fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(modifier = Modifier.size(27.dp), color = Lavender, strokeWidth = 2.dp)
        Spacer(Modifier.height(18.dp))
        Text("Finding your music…", color = SoftText, fontSize = 14.sp)
    }
}

@Composable
private fun FolderError(message: String, onChooseFolder: () -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.FolderOff, null, tint = Lavender, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(15.dp))
        Text(message, color = SoftText, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.glass(24).clickable(onClick = onChooseFolder).padding(16.dp)) {
            Text("Choose music folder", color = MistWhite, fontSize = 14.sp)
        }
    }
}

@Composable
private fun EmptyLibrary(onRefresh: () -> Unit, folderName: String?, onChooseFolder: () -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.MusicOff, null, tint = Lavender, modifier = Modifier.size(46.dp))
        Spacer(Modifier.height(20.dp))
        Text("Nothing here yet", color = MistWhite, fontSize = 22.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text(if (folderName != null) "No supported audio files in this folder. Try a different folder." else "Add music to your phone or select a folder.", color = SoftText, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.glass(24).clickable(onClick = onRefresh).padding(horizontal = 24.dp, vertical = 13.dp)) {
            Text("Refresh", color = MistWhite, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.clickable(onClick = onChooseFolder).padding(14.dp)) {
            Text("Choose another folder", color = Lavender, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NoResults(liked: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(if (liked) "Songs you love will appear here." else "No matching songs found.", color = SoftText, fontSize = 14.sp)
    }
}

@Composable
private fun SoftIcon(image: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(image, contentDescription = label, tint = MistWhite, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun GroupRow(title: String, tracks: List<AudioTrack>, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(tracks.firstOrNull(), Modifier.size(54.dp), 13.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MistWhite, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text("${tracks.size} songs", color = SoftText, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, "Open collection", tint = SoftText, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun TrackRow(
    track: AudioTrack, selected: Boolean, playing: Boolean,
    liked: Boolean, onFavorite: () -> Unit, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 67.dp).clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(2.dp).height(26.dp).clip(CircleShape)
            .background(if (selected) MistWhite else Color.Transparent))
        Spacer(Modifier.width(7.dp))
        Artwork(track, Modifier.size(49.dp), 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = MistWhite, fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(track.artist, color = SoftText, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Icon(if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.PauseCircleOutline,
                if (playing) "Playing" else "Paused", tint = MistWhite,
                modifier = Modifier.size(19.dp))
        } else {
            Text(durationString(track.durationMs), color = SoftText, fontSize = 11.sp)
        }
        IconButton(onClick = onFavorite, modifier = Modifier.size(44.dp)) {
            Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (liked) "Remove from favourites" else "Add to favourites",
                tint = if (liked) MistWhite else SoftText, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun UnifiedDock(
    track: AudioTrack?, playing: Boolean, positionState: State<Long>, duration: Long,
    grouping: Grouping, onGroup: (Grouping) -> Unit,
    reduceMotion: Boolean,
    onOpen: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit,
    canNext: Boolean, canPrevious: Boolean,
    onSwipeNext: () -> Unit, onSwipePrevious: () -> Unit
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 17.dp)
        .shadow(13.dp, RoundedCornerShape(25.dp), clip = false)
        .clip(RoundedCornerShape(25.dp))) {
        if (track != null) {
            // A single artwork-backed layer, blurred only in this bounded dock.
            Artwork(track, Modifier.matchParentSize(), 25.dp, blurred = true)
            Box(Modifier.matchParentSize().background(Color(0xD319232D)))
        } else {
            Box(Modifier.matchParentSize().background(Color(0xE518222C)))
        }
        Box(Modifier.matchParentSize().glass(25, bright = true))
        Column(Modifier.fillMaxWidth()) {
            if (track != null) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 9.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).fillMaxHeight()
                        .trackSwipe(track.id, canNext, canPrevious, onSwipeNext, onSwipePrevious)
                        .clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(track, Modifier.size(46.dp), 11.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, fontSize = 13.sp, color = MistWhite,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Text(track.artist, color = SoftText, fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Pause" else "Play", tint = MistWhite,
                            modifier = Modifier.size(25.dp))
                    }
                    IconButton(onClick = onNext, enabled = canNext, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.SkipNext, "Next track",
                            tint = if (canNext) MistWhite else SoftText.copy(alpha = 0.45f),
                            modifier = Modifier.size(24.dp))
                    }
                }
                DockProgress(positionState, duration, playing)
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.6.dp)
                    .background(Color.White.copy(alpha = 0.11f)))
            }
            DockTabs(grouping, onGroup, reduceMotion)
        }
    }
}

@Composable
private fun DockTabs(grouping: Grouping, onGroup: (Grouping) -> Unit, reduceMotion: Boolean) {
    Row(Modifier.fillMaxWidth().height(57.dp).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(83.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LibraryMusic, null, tint = SoftText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("Library", color = SoftText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        BoxWithConstraints(Modifier.weight(1f).height(49.dp)) {
            val segment = maxWidth / 3
            val selected = Grouping.entries.indexOf(grouping).coerceAtLeast(0)
            val x by animateDpAsState(targetValue = segment * selected,
                animationSpec = if (reduceMotion) snap() else spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                ), label = "moving-dock-highlight")
            Box(Modifier.offset(x = x).width(segment).height(45.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.13f))
                .border(0.65.dp, Color.White.copy(alpha = 0.23f), RoundedCornerShape(16.dp)))
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Grouping.entries.forEach { tab ->
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .clickable(onClick = { onGroup(tab) }),
                        contentAlignment = Alignment.Center) {
                        Text(tab.name, color = if (grouping == tab) MistWhite else SoftText,
                            fontSize = 12.sp,
                            fontWeight = if (grouping == tab) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DockProgress(positionState: State<Long>, duration: Long, playing: Boolean) {
    val progress = if (duration > 0L) positionState.value.toFloat() / duration else 0f
    val fraction by animateFloatAsState(progress.coerceIn(0f, 1f),
        animationSpec = tween(if (playing) 490 else 0, easing = LinearEasing), label = "dock-progress")
    Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MistWhite.copy(alpha = 0.82f)))
    }
}


/**
 * Touch slop and directional arbitration come from Compose's horizontal detector.
 * Only artwork and the non-button mini-player region use this modifier. No
 * gesture is handled by the seek bar, transport controls, or vertical song list.
 */
private fun Modifier.trackSwipe(
    trackId: Long,
    canNext: Boolean, canPrevious: Boolean,
    onNext: () -> Unit, onPrevious: () -> Unit
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val threshold = with(density) { 72.dp.toPx() }
    val displacement = remember(trackId) { Animatable(0f) }
    val nextState = rememberUpdatedState(onNext)
    val previousState = rememberUpdatedState(onPrevious)
    this.offset { IntOffset(displacement.value.roundToInt(), 0) }
        .pointerInput(trackId, canNext, canPrevious) {
            var drag = 0f
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onHorizontalDrag = { event, amount ->
                    drag += amount
                    event.consume()
                    scope.launch { displacement.snapTo(drag.coerceIn(-94f, 94f)) }
                },
                onDragEnd = {
                    when (TrackSwipe.decide(drag, 0f, threshold, canNext, canPrevious)) {
                        SwipeTrackAction.NEXT -> nextState.value()
                        SwipeTrackAction.PREVIOUS -> previousState.value()
                        SwipeTrackAction.NONE -> Unit
                    }
                    scope.launch {
                        displacement.animateTo(0f,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium))
                    }
                },
                onDragCancel = {
                    scope.launch { displacement.animateTo(0f, spring()) }
                }
            )
        }
}


private fun albumKey(track: AudioTrack): String = if (track.albumId > 0) "album:${track.albumId}" else "${track.album}|${track.artist}"

private fun durationString(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlaying(
    track: AudioTrack,
    playing: Boolean,
    positionState: State<Long>,
    duration: Long,
    shuffle: Boolean,
    repeatMode: Int,
    liked: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit,
    onQueue: () -> Unit,
    usbDacConnected: Boolean,
    canNext: Boolean,
    canPrevious: Boolean,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
) {
    var draggedFraction by remember(track.id) { mutableStateOf<Float?>(null) }
    val sampledFraction = (if (duration > 0L) positionState.value.toFloat() / duration else 0f).coerceIn(0f, 1f)
    val smoothFraction by animateFloatAsState(
        targetValue = sampledFraction,
        animationSpec = tween(if (playing) 490 else 0, easing = LinearEasing),
        label = "now-playing-progress"
    )
    val fraction = (draggedFraction ?: smoothFraction).coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxSize().background(LazyNight)) {
        val compact = maxHeight < 740.dp
        val narrow = maxWidth < 375.dp
        val air = ((maxHeight - 755.dp) / 3f).coerceIn(0.dp, 38.dp)
        val coverSize = (maxHeight * if (compact) 0.34f else 0.39f)
            .coerceAtMost(maxWidth - 58.dp).coerceIn(162.dp, 356.dp)
        Artwork(track, Modifier.fillMaxWidth().height(maxHeight * 0.49f), corner = 0.dp, blurred = true)
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xAF0B1016), Color(0xE50B1016), LazyNight))
        ))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (narrow) 18.dp else 24.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                PlainAction(Icons.Rounded.KeyboardArrowDown, "Close Now Playing", onDismiss)
                Spacer(Modifier.weight(1f))
                Text("Now Playing", color = MistWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(if (compact) 8.dp else 17.dp).heightIn(min = air))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Artwork(track,
                    Modifier.size(coverSize)
                        .trackSwipe(track.id, canNext, canPrevious,
                            onSwipeNext, onSwipePrevious)
                        .border(0.7.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(22.dp)),
                    22.dp)
            }
            Spacer(Modifier.height((if (compact) 16.dp else 23.dp) + air))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = MistWhite,
                        fontSize = if (compact) 21.sp else 24.sp,
                        lineHeight = if (compact) 26.sp else 29.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Text(track.artist, color = SoftText, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (liked) "Remove from favourites" else "Add to favourites",
                        tint = if (liked) MistWhite else SoftText, modifier = Modifier.size(23.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val format = track.filename.substringAfterLast('.', "").uppercase().ifBlank { "AUDIO" }
                Text(format, color = MistWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(9.dp))
                Box(Modifier.size(3.dp).clip(CircleShape).background(SoftText))
                Spacer(Modifier.width(9.dp))
                Icon(Icons.Rounded.Headphones, null, tint = SoftText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (usbDacConnected) "USB DAC detected" else "Android audio",
                    color = SoftText, fontSize = 11.sp)
            }
            Spacer(Modifier.height((if (compact) 8.dp else 14.dp) + air))
            // A thin rail and small thumb inside the normal 48dp Material3 touch area.
            Slider(
                value = fraction,
                onValueChange = { draggedFraction = it },
                onValueChangeFinished = {
                    if (duration > 0L) {
                        onSeek(((draggedFraction ?: fraction) * duration).roundToLong())
                    }
                    draggedFraction = null
                },
                enabled = duration > 0L,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                thumb = { Box(Modifier.size(11.dp).clip(CircleShape).background(MistWhite)) },
                track = { state ->
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.23f))) {
                        Box(Modifier.fillMaxWidth(state.value.coerceIn(0f, 1f)).fillMaxHeight()
                            .background(MistWhite))
                    }
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(durationString((fraction * duration).roundToLong()), color = SoftText, fontSize = 11.sp)
                Text(durationString(duration), color = SoftText, fontSize = 11.sp)
            }
            Spacer(Modifier.height(if (compact) 14.dp else 20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                val accent = Color(0xFFD2E9F4)
                IconButton(onClick = onShuffle,
                    modifier = Modifier.size(47.dp).clip(CircleShape)
                        .background(if (shuffle) accent.copy(alpha = 0.20f) else Color.Transparent)) {
                    Icon(Icons.Rounded.Shuffle, if (shuffle) "Shuffle on" else "Shuffle off",
                        tint = if (shuffle) accent else SoftText, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = MistWhite,
                        modifier = Modifier.size(31.dp))
                }
                Box(Modifier.size(if (narrow) 64.dp else 68.dp).clip(CircleShape)
                    .background(MistWhite).clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pause" else "Play", tint = LazyNight, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = MistWhite,
                        modifier = Modifier.size(31.dp))
                }
                val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
                IconButton(onClick = onRepeat,
                    modifier = Modifier.size(47.dp).clip(CircleShape)
                        .background(if (repeatActive) accent.copy(alpha = 0.20f) else Color.Transparent)) {
                    Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                        else Icons.Rounded.Repeat,
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat one"
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            else -> "Repeat off"
                        }, tint = if (repeatActive) accent else SoftText, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            Row(Modifier.fillMaxWidth().height(47.dp).clickable(onClick = onQueue),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.QueueMusic, null, tint = MistWhite, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text("Queue", color = MistWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
