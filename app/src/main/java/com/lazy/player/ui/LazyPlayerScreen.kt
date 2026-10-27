package com.lazy.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
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
    position: Long,
    duration: Long,
    shuffle: Boolean,
    repeatMode: Int,
    favorites: Set<Long>,
    onRefresh: () -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
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
                    Row(Modifier.fillMaxWidth().height(49.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selectedGroup != null) {
                            PlainAction(Icons.Rounded.ArrowBack, "Back to library") { selectedGroup = null }
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (selectedGroup!!.first == Grouping.Albums)
                                    tracks.firstOrNull { albumKey(it) == selectedGroup!!.second }?.album ?: "Album"
                                else selectedGroup!!.second,
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MistWhite, fontSize = 20.sp, fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text("lazy player", modifier = Modifier.weight(1f), fontSize = 23.sp,
                                fontWeight = FontWeight.Medium, color = MistWhite, letterSpacing = (-0.8).sp)
                        }
                        PlainAction(if (search) Icons.Rounded.Close else Icons.Rounded.Search,
                            if (search) "Close search" else "Search music") {
                            search = !search
                            if (!search) query = ""
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
                    Spacer(Modifier.height(8.dp))
                    if (selectedGroup == null && page == Page.Listen) {
                        FolderSourceRow(folderName, onChooseFolder, onUseAllMusic)
                        Spacer(Modifier.height(7.dp))
                        Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Grouping.entries.forEach { tab ->
                                val active = grouping == tab
                                Column(Modifier.height(42.dp).clickable { grouping = tab }.padding(horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom) {
                                    Text(tab.name, color = if (active) MistWhite else SoftText, fontSize = 14.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                                    Spacer(Modifier.height(8.dp))
                                    Box(Modifier.width(26.dp).height(2.dp).clip(CircleShape)
                                        .background(if (active) Lavender else Color.Transparent))
                                }
                            }
                        }
                    } else if (selectedGroup == null) {
                        Text("Favourites", Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                            color = MistWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Text("${filtered.size} songs", Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                            fontSize = 12.sp, color = SoftText)
                    }
                    Spacer(Modifier.height(7.dp))
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
                                item {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (page == Page.Liked) "FAVOURITES" else "YOUR TRACKS", color = SoftText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
                                        Spacer(Modifier.weight(1f))
                                        Text("${filtered.size} songs", fontSize = 11.sp, color = SoftText)
                                    }
                                }
                                items(filtered, key = { it.id }) { track ->
                                    TrackRow(track, selected = currentTrack?.id == track.id, onClick = { onPlayTrack(track, filtered) })
                                }
                            }
                        }
                    }
                }
                if (currentTrack != null) {
                    MiniPlayer(currentTrack, playing, onTogglePlay, onNext, onOpen = { fullPlayer = true })
                    Spacer(Modifier.height(7.dp))
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(56.dp).glass(28)
                        .padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavigationItem("Listen", Icons.Rounded.Headphones, page == Page.Listen, Modifier.weight(1f)) { page = Page.Listen; selectedGroup = null; query = "" }
                    NavigationItem("Liked", Icons.Rounded.FavoriteBorder, page == Page.Liked, Modifier.weight(1f)) { page = Page.Liked; selectedGroup = null; query = "" }
                }
                Spacer(Modifier.height(8.dp))
            }
            AnimatedVisibility(
                visible = fullPlayer && currentTrack != null,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(350)) + fadeIn(tween(350)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(tween(300))
            ) {
                currentTrack?.let { track ->
                    NowPlaying(
                        track, playing, position, duration, shuffle, repeatMode,
                        liked = track.id in favorites,
                        onDismiss = { fullPlayer = false }, onPlay = onTogglePlay, onNext = onNext, onPrevious = onPrevious,
                        onSeek = onSeek, onShuffle = onShuffle, onRepeat = onRepeat, onFavorite = { onToggleFavorite(track.id) },
                        onQueue = { showQueue = true }, usbDacConnected = usbDacConnected
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
                            TrackRow(queue[index], selected = currentTrack?.id == queue[index].id, onClick = { onQueueSelection(index); showQueue = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlainAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, tint = MistWhite, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun FolderSourceRow(folderName: String?, onChooseFolder: () -> Unit, onUseAllMusic: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(48.dp)
            .clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.055f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onChooseFolder).padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FolderOpen, null, tint = SoftText, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(11.dp))
            Text(folderName ?: "Choose music folder", modifier = Modifier.weight(1f),
                color = MistWhite, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.ChevronRight, "Change folder", tint = SoftText, modifier = Modifier.size(19.dp))
        }
        if (folderName != null) {
            IconButton(onClick = onUseAllMusic, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, "Use all music instead", tint = SoftText, modifier = Modifier.size(18.dp))
            }
        } else Spacer(Modifier.width(13.dp))
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
private fun TrackRow(track: AudioTrack, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(13.dp))
            .then(if (selected) Modifier.background(Color.White.copy(alpha = 0.055f)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track, Modifier.size(49.dp), 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = if (selected) MistWhite else MistWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(track.artist, color = SoftText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(durationString(track.durationMs), color = SoftText, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun NavigationItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxHeight().clip(CircleShape).background(if (active) Color.White.copy(alpha = 0.075f) else Color.Transparent).clickable(onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (active) MistWhite else SoftText)
        Spacer(Modifier.width(9.dp))
        Text(title, fontSize = 13.sp, color = if (active) MistWhite else SoftText, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun MiniPlayer(track: AudioTrack, playing: Boolean, onPlay: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit) {
    // Artwork is blurred *inside this single clipped surface*; list rows stay
    // unblurred and inexpensive. This is a frosted artwork treatment, not a
    // claim of arbitrary Android backdrop refraction.
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(65.dp)
        .clip(RoundedCornerShape(20.dp)).glass(20, bright = true)) {
        Artwork(track, Modifier.fillMaxSize(), corner = 20.dp, blurred = true)
        Box(Modifier.fillMaxSize().background(Color(0xD2121B24)))
        Row(Modifier.fillMaxSize().clickable(onClick = onOpen).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Artwork(track, Modifier.size(46.dp), 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = MistWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(track.artist, color = SoftText, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPlay, modifier = Modifier.size(44.dp)) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) "Pause" else "Play", tint = MistWhite, modifier = Modifier.size(25.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next track", tint = MistWhite, modifier = Modifier.size(23.dp))
            }
        }
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
    position: Long,
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
) {
    var draggedFraction by remember(track.id) { mutableStateOf<Float?>(null) }
    val fraction = (draggedFraction ?:
        (if (duration > 0L) position.toFloat() / duration else 0f)).coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxSize().background(LazyNight)) {
        val compact = maxHeight < 735.dp
        val narrow = maxWidth < 375.dp
        val coverSize = (maxHeight * if (compact) 0.34f else 0.40f)
            .coerceAtMost(maxWidth - 68.dp).coerceIn(160.dp, 358.dp)
        // One bounded album-art haze supplies depth behind the opaque content.
        Artwork(track, Modifier.fillMaxWidth().height(maxHeight * 0.50f), corner = 0.dp, blurred = true)
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xBB0B1016), Color(0xDF0B1016), LazyNight))
        ))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (narrow) 20.dp else 26.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                PlainAction(Icons.Rounded.KeyboardArrowDown, "Close Now Playing", onDismiss)
                Spacer(Modifier.weight(1f))
                Text("Now Playing", color = MistWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                PlainAction(Icons.Rounded.QueueMusic, "Open queue", onQueue)
            }
            Spacer(Modifier.height(if (compact) 10.dp else 22.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Artwork(track,
                    Modifier.size(coverSize).border(0.75.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp)),
                    22.dp
                )
            }
            Spacer(Modifier.height(if (compact) 18.dp else 28.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = MistWhite,
                        fontSize = if (compact) 21.sp else 24.sp, lineHeight = if (compact) 25.sp else 29.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Text(track.artist, color = SoftText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (liked) "Remove favourite" else "Add favourite", tint = if (liked) MistWhite else SoftText,
                        modifier = Modifier.size(23.dp))
                }
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val format = track.filename.substringAfterLast('.', "").uppercase().ifBlank { "AUDIO" }
                Text(format, color = MistWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(9.dp))
                Box(Modifier.size(3.dp).clip(CircleShape).background(SoftText))
                Spacer(Modifier.width(9.dp))
                Icon(Icons.Rounded.Headphones, null, tint = SoftText, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (usbDacConnected) "USB DAC detected" else "Android audio", color = SoftText, fontSize = 11.sp)
            }
            Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
            // Visually 3 dp, but Slider keeps a tall accessible interactive area.
            Slider(
                value = fraction,
                onValueChange = { draggedFraction = it },
                onValueChangeFinished = {
                    onSeek(((draggedFraction ?: fraction) * duration.coerceAtLeast(0L)).roundToLong())
                    draggedFraction = null
                },
                modifier = Modifier.fillMaxWidth().height(43.dp),
                thumb = { Box(Modifier.size(11.dp).clip(CircleShape).background(MistWhite)) },
                track = { state ->
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f))) {
                        Box(Modifier.fillMaxWidth(state.value.coerceIn(0f, 1f)).fillMaxHeight()
                            .background(MistWhite))
                    }
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(durationString((fraction * duration).roundToLong()), color = SoftText, fontSize = 11.sp)
                Text(durationString(duration), color = SoftText, fontSize = 11.sp)
            }
            Spacer(Modifier.height(if (compact) 10.dp else 17.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShuffle, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffle) MistWhite else SoftText,
                        modifier = Modifier.size(21.dp))
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = MistWhite, modifier = Modifier.size(32.dp))
                }
                Box(Modifier.size(if (narrow) 65.dp else 69.dp).clip(CircleShape)
                    .background(MistWhite).clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pause" else "Play", tint = LazyNight, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = MistWhite, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = onRepeat, modifier = Modifier.size(44.dp)) {
                    Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repeat", tint = if (repeatMode == Player.REPEAT_MODE_OFF) SoftText else MistWhite,
                        modifier = Modifier.size(21.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
