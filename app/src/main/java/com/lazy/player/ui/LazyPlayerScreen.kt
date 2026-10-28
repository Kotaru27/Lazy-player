package com.lazy.player.ui

import androidx.activity.compose.BackHandler
import android.provider.Settings
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    BackHandler(enabled = showQueue) { showQueue = false }
    BackHandler(enabled = selectedGroup != null && !fullPlayer) { selectedGroup = null }

    val filtered = remember(tracks, query, page, favorites, selectedGroup) {
        var result = if (page == Page.Liked) tracks.filter { it.id in favorites } else tracks
        if (selectedGroup != null) {
            val (type, name) = selectedGroup!!
            result = if (type == Grouping.Albums) result.filter { albumKey(it) == name } else result.filter { it.artist == name }
        }
        result.filter { TrackFilters.matches(it.title, it.artist, it.album, query) }
    }

    val visualIndex = remember(queue, currentTrack?.id) {
        queue.indexOfFirst { it.id == currentTrack?.id }
    }
    // A shuffle sequence is owned by Media3, not the displayed queue order.
    val previousVisual = if (!shuffle && canPrevious && visualIndex > 0)
        queue[visualIndex - 1] else null
    val nextVisual = if (!shuffle && canNext && visualIndex >= 0)
        queue.getOrNull(visualIndex + 1) else null

    val contentTransition = remember { Animatable(1f) }
    LaunchedEffect(grouping, page, selectedGroup, reduceMotion) {
        if (reduceMotion) contentTransition.snapTo(1f)
        else {
            contentTransition.snapTo(0.82f)
            contentTransition.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Lavender, onSurface = MistWhite, surface = LazyNight)) {
        // One app-owned type system: device display-font substitutions do not affect
        // metadata and navigation, while Android still provides script fallbacks.
        ProvideTextStyle(value = TextStyle(fontFamily = FontFamily.SansSerif)) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            val sharedScope = this@SharedTransitionLayout
            Box(Modifier.fillMaxSize().background(LazyNight)) {
            AmbientHaze()
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
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
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Search local music" }, singleLine = true,
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
                        collectionCaption = when {
                            selectedGroup != null || page == Page.Liked || grouping == Grouping.Songs -> null
                            grouping == Grouping.Albums -> {
                                val count = filtered.map { albumKey(it) }.distinct().size
                                count.toString() + if (count == 1) " album" else " albums"
                            }
                            else -> {
                                val count = filtered.map { it.artist }.distinct().size
                                count.toString() + if (count == 1) " artist" else " artists"
                            }
                        },
                        allowFavouriteFilter = selectedGroup == null && grouping == Grouping.Songs,
                        showingFavourites = page == Page.Liked,
                        onChangeFavourites = { show ->
                            page = if (show) Page.Liked else Page.Listen
                            selectedGroup = null
                        },
                        enabled = hasPermission && !libraryLoading && filtered.isNotEmpty(),
                        onPlay = { onPlayCollection(filtered, false) },
                        onShuffle = { onPlayCollection(filtered, true) }
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        alpha = contentTransition.value
                    }) {
                    when {
                        !hasPermission -> IntroState(onPermission, onChooseFolder)
                        libraryLoading && tracks.isEmpty() -> LoadingLibrary()
                        folderError != null && tracks.isEmpty() -> FolderError(folderError, onChooseFolder)
                        tracks.isEmpty() -> EmptyLibrary(onRefresh, folderName, onChooseFolder)
                        filtered.isEmpty() -> NoResults(page == Page.Liked, query.isNotBlank())
                        selectedGroup == null && page == Page.Listen && grouping != Grouping.Songs -> {
                            val groups = remember(filtered, grouping) {
                                if (grouping == Grouping.Albums) filtered.groupBy { albumKey(it) }
                                else filtered.groupBy { it.artist }
                            }
                            val entries = remember(groups) { groups.entries.toList() }
                            if (grouping == Grouping.Albums) {
                                BoxWithConstraints(Modifier.fillMaxSize()) {
                                    val largeText = LocalDensity.current.fontScale >= 1.65f
                                    val columns = if (maxWidth < 340.dp || largeText) 1 else 2
                                    LazyVerticalGrid(columns = GridCells.Fixed(columns),
                                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 5.dp, bottom = 28.dp),
                                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                                        verticalArrangement = Arrangement.spacedBy(22.dp)) {
                                        gridItems(entries, key = { it.key }) { entry ->
                                            AlbumTile(entry.value.first().album, entry.value) {
                                                selectedGroup = Grouping.Albums to entry.key
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    items(entries, key = { it.key }) { entry ->
                                        GroupRow(title = entry.key, tracks = entry.value,
                                            artist = true, onClick = { selectedGroup = Grouping.Artists to entry.key })
                                    }
                                }
                            }
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.testTag("track-list"),
                                contentPadding = PaddingValues(bottom = 30.dp, start = 20.dp, end = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                items(filtered, key = { it.id }) { track ->
                                    TrackRow(track, selected = currentTrack?.id == track.id, playing = playing,
                                        liked = track.id in favorites, onFavorite = { onToggleFavorite(track.id) },
                                        onClick = { onPlayTrack(track, filtered) })
                                }
                            }
                        }
                    }
                    }
                }
                UnifiedDock(
                    track = currentTrack, playing = playing,
                    previous = previousVisual, next = nextVisual,
                    reduceMotion = reduceMotion,
                    positionState = positionState, duration = duration,
                    grouping = grouping,
                    sharedScope = sharedScope, expanded = fullPlayer,
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
                // Only translate a short distance and fade: avoid scaling the full-screen
                // blurred artwork, which allocates an expensive composited render layer.
                enter = if (reduceMotion) fadeIn(tween(0))
                    else fadeIn(tween(280)),
                exit = if (reduceMotion) fadeOut(tween(0))
                    else fadeOut(tween(320))
            ) {
                currentTrack?.let { track ->
                    // Media3's queue order is only a neighbor hint when shuffle is off.
                    // Do not prefetch the wrong cover for an arbitrary shuffle order.
                    NowPlaying(
                        track, playing, positionState, duration, shuffle, repeatMode,
                        previousArtworkTrack = previousVisual,
                        nextArtworkTrack = nextVisual,
                        expanded = fullPlayer, sharedScope = sharedScope, reduceMotion = reduceMotion,
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
                    containerColor = Color(0xFF171816),
                    contentColor = MistWhite,
                    dragHandle = { Box(Modifier.padding(top = 12.dp).size(width = 38.dp, height = 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.34f))) }
                ) {
                    Text("Up next", Modifier.padding(start = 24.dp, top = 19.dp, bottom = 15.dp), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
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
    }
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    fun read(): Boolean = Settings.Global.getFloat(context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f
    var reduced by remember(context) { mutableStateOf(read()) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) reduced = read()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return reduced
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
            modifier = Modifier.background(Color(0xFF222320))) {
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
private fun CollectionHeading(
    name: String, count: Int, collectionCaption: String?, allowFavouriteFilter: Boolean,
    showingFavourites: Boolean, onChangeFavourites: (Boolean) -> Unit,
    enabled: Boolean, onPlay: () -> Unit, onShuffle: () -> Unit
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 74.dp)
        .padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (allowFavouriteFilter) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    Row(Modifier.heightIn(min = 48.dp).clickable { menu = true },
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (showingFavourites) "Favourites" else "Songs",
                            fontSize = 25.sp, color = MistWhite,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.KeyboardArrowDown,
                            "Choose All songs or Favourites", tint = SoftText,
                            modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                        modifier = Modifier.background(Color(0xFF222320))) {
                        DropdownMenuItem(text = { Text("All songs") }, onClick = {
                            menu = false; onChangeFavourites(false)
                        }, leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null) })
                        DropdownMenuItem(text = { Text("Favourites") }, onClick = {
                            menu = false; onChangeFavourites(true)
                        }, leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null) })
                    }
                }
            } else {
                Text(name, color = MistWhite, fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(collectionCaption ?: "$count ${if (count == 1) "song" else "songs"}",
                color = SoftText, fontSize = 12.sp)
        }
        val songCountLabel = "$count ${if (count == 1) "song" else "songs"}"
        SmallAction(Icons.Rounded.PlayArrow, "Play all $songCountLabel", enabled, onPlay)
        SmallAction(Icons.Rounded.Shuffle, "Shuffle all $songCountLabel", enabled, onShuffle)
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = title },
        interactionSource = source) {
        Box(Modifier.size(39.dp).clip(RoundedCornerShape(13.dp))
            .background(if (enabled) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.025f))
            .border(0.6.dp, Color.White.copy(alpha = if (enabled) 0.11f else 0.025f),
                RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null,
                tint = if (enabled) MistWhite else SoftText.copy(alpha = 0.42f),
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun IntroState(onPermission: () -> Unit, onChooseFolder: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.LibraryMusic, null, tint = MistWhite, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(28.dp))
        Text("Your music.\nOnly yours.", color = MistWhite, fontSize = 31.sp,
            lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Text("Listen to audio saved on this phone. No account, upload or streaming needed.",
            color = SoftText, fontSize = 15.sp, lineHeight = 23.sp)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(15.dp))
            .background(MistWhite).clickable(onClick = onPermission)
            .padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            Text("Allow device music", color = LazyNight, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.075f)).clickable(onClick = onChooseFolder)
            .padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            Text("Choose a music folder", color = MistWhite, fontSize = 15.sp)
        }
    }
}

@Composable
private fun LoadingLibrary() {
    Column(Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp),
            color = MistWhite, strokeWidth = 2.dp)
        Spacer(Modifier.height(18.dp))
        Text("Reading your music", color = MistWhite, fontSize = 17.sp,
            fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Text("Building your local library", color = SoftText, fontSize = 13.sp)
    }
}

@Composable
private fun FolderError(message: String, onChooseFolder: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
        Icon(Icons.Rounded.FolderOff, null, tint = SoftText, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(20.dp))
        Text("Can't read this folder", color = MistWhite, fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(9.dp))
        Text(message, color = SoftText, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(23.dp))
        Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
            .background(MistWhite).clickable(onClick = onChooseFolder)
            .padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text("Choose another folder", color = LazyNight, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyLibrary(onRefresh: () -> Unit, folderName: String?, onChooseFolder: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
        Icon(Icons.Rounded.MusicOff, null, tint = SoftText, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(20.dp))
        Text("No music found", color = MistWhite, fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(if (folderName != null) "This folder contains no supported audio files."
            else "Add audio files to your phone or choose a music folder.",
            color = SoftText, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(23.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                .background(MistWhite).clickable(onClick = onChooseFolder)
                .padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text("Choose folder", color = LazyNight, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.075f)).clickable(onClick = onRefresh)
                .padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text("Refresh", color = MistWhite, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun NoResults(liked: Boolean, searching: Boolean = false) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (searching) Icons.Rounded.SearchOff else Icons.Rounded.FavoriteBorder,
            contentDescription = null, tint = SoftText,
            modifier = Modifier.size(29.dp))
        Spacer(Modifier.height(16.dp))
        Text(when {
            searching -> "Nothing matches that search"
            liked -> "No favourites yet"
            else -> "Nothing found"
        }, color = MistWhite, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(when {
            searching -> "Try another title, artist or album."
            liked -> "Songs you save will appear here."
            else -> "Choose another folder or refresh your library."
        }, color = SoftText, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun SoftIcon(image: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(image, contentDescription = label, tint = MistWhite, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AlbumTile(title: String, tracks: List<AudioTrack>, onClick: () -> Unit) {
    // The collection identity is the cover itself. Metadata stays outside it.
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Artwork(tracks.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f),
            12.dp, targetPx = 640, showPlaceholderCaption = false)
        Spacer(Modifier.height(10.dp))
        Text(title, color = MistWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
        Spacer(Modifier.height(5.dp))
        Text(tracks.firstOrNull()?.artist.orEmpty(), color = SoftText, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text("${tracks.size} ${if (tracks.size == 1) "song" else "songs"}",
            color = SoftText.copy(alpha = 0.82f), fontSize = 11.sp)
    }
}

@Composable
private fun GroupRow(title: String, tracks: List<AudioTrack>, artist: Boolean = false, onClick: () -> Unit) {
    // Do not misrepresent an album cover as the artist's portrait.
    Row(Modifier.fillMaxWidth().heightIn(min = 80.dp)
        .clickable(onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (artist) {
            Box(Modifier.size(52.dp).clip(CircleShape)
                .background(Color(0xFF262825)), contentAlignment = Alignment.Center) {
                Text(title.trim().take(1).uppercase().ifEmpty { "•" },
                    fontSize = 23.sp, color = MistWhite,
                    fontWeight = FontWeight.Light)
            }
        } else Artwork(tracks.firstOrNull(), Modifier.size(52.dp), 12.dp, targetPx = 192)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MistWhite, fontSize = 16.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            val caption = if (artist) {
                val albums = tracks.map { albumKey(it) }.distinct().size
                "$albums ${if (albums == 1) "album" else "albums"} · ${tracks.size} songs"
            } else "${tracks.size} songs"
            Text(caption, color = SoftText, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, "Open $title", tint = SoftText,
            modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun TrackRow(track: AudioTrack, selected: Boolean, playing: Boolean,
    liked: Boolean, onFavorite: () -> Unit, onClick: () -> Unit
) {
    var menu by remember(track.id) { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale >= 1.45f
    Row(Modifier.fillMaxWidth().heightIn(min = if (largeText) 96.dp else 68.dp)
        .clip(RoundedCornerShape(11.dp))
        .background(if (selected) Color.White.copy(alpha = 0.065f) else Color.Transparent)
        .clickable(onClick = onClick)
        .padding(start = 3.dp, end = 0.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(23.dp).clip(CircleShape)
            .background(if (selected) MistWhite else Color.Transparent))
        Spacer(Modifier.width(9.dp))
        Artwork(track, Modifier.size(49.dp), 9.dp,
            targetPx = 192, showPlaceholderCaption = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = MistWhite, fontSize = 15.sp, lineHeight = 20.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = if (largeText) 2 else 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(if (largeText) "${track.artist} · ${durationString(track.durationMs)}"
                else track.artist, color = SoftText, fontSize = 12.sp,
                maxLines = if (largeText) 2 else 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Icon(if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.PauseCircleOutline,
            if (playing) "Track playing" else "Track paused", tint = MistWhite,
            modifier = Modifier.size(18.dp))
        else if (!largeText) Text(durationString(track.durationMs),
            color = SoftText, fontSize = 11.sp)
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, "Options for ${track.title}",
                    tint = SoftText, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                modifier = Modifier.background(Color(0xFF20211F))) {
                DropdownMenuItem(
                    text = { Text(if (liked) "Remove from favourites" else "Add to favourites") },
                    onClick = { menu = false; onFavorite() },
                    leadingIcon = {
                        Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null)
                    }
                )
            }
        }
    }
}

@Composable
private fun UnifiedDock(
    track: AudioTrack?, playing: Boolean, positionState: State<Long>, duration: Long,
    previous: AudioTrack?, next: AudioTrack?,
    grouping: Grouping, onGroup: (Grouping) -> Unit,
    sharedScope: SharedTransitionScope, expanded: Boolean,
    reduceMotion: Boolean,
    onOpen: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit,
    canNext: Boolean, canPrevious: Boolean,
    onSwipeNext: () -> Unit, onSwipePrevious: () -> Unit
) {
    // Opal is ONE object with two internal zones, not stacked floating capsules.
    // The actual content remains grounded in the Media3 state passed by the caller.
    if (track != null) PrefetchSwipeArtwork(next, previous, targetPx = 192)
    val fontScale = LocalDensity.current.fontScale
    val expandedType = fontScale >= 1.45f
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .testTag("unified-dock")
            .clip(shape)
            .background(Color(0xFFBFC1BE).copy(alpha = 0.095f))
            .background(Brush.verticalGradient(listOf(
                Color.White.copy(alpha = 0.052f),
                Color(0xFF77746F).copy(alpha = 0.018f)
            )))
    ) {
        if (track != null) {
            Box(Modifier.fillMaxWidth().height(when {
                fontScale >= 1.85f -> 112.dp
                expandedType -> 92.dp
                else -> 72.dp
            })) {
                Row(
                    Modifier.fillMaxSize().padding(start = 9.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackMotionSurface(
                        track = track, previous = previous, next = next,
                        canNext = canNext, canPrevious = canPrevious,
                        reduceMotion = reduceMotion,
                        onNext = onSwipeNext, onPrevious = onSwipePrevious,
                        modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onOpen),
                        tag = "mini-swipe-zone"
                    ) { shown ->
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            with(sharedScope) {
                                val art = if (reduceMotion) Modifier.size(50.dp)
                                    else Modifier.sharedElementWithCallerManagedVisibility(
                                        rememberSharedContentState(key = "quiet-art-${shown.id}"),
                                        !expanded
                                    ).size(50.dp)
                                Artwork(shown, art, 11.dp,
                                    targetPx = 640, showPlaceholderCaption = false)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(shown.title, color = MistWhite,
                                    fontSize = 14.sp, lineHeight = 19.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = if (expandedType) 2 else 1,
                                    overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text(shown.artist, color = SoftText, fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    IconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Pause" else "Play", tint = MistWhite,
                            modifier = Modifier.size(26.dp))
                    }
                    IconButton(onClick = onNext, enabled = canNext,
                        modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.SkipNext, "Next track",
                            tint = if (canNext) MistWhite else SoftText.copy(alpha = 0.45f),
                            modifier = Modifier.size(23.dp))
                    }
                }
                DockProgress(positionState, duration, playing,
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 15.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp)
                .background(Color.White.copy(alpha = 0.09f)))
        }
        DockTabs(grouping, onGroup, reduceMotion)
    }
}

@Composable
private fun DockTabs(grouping: Grouping, onGroup: (Grouping) -> Unit, reduceMotion: Boolean) {
    // Selection is a fine baseline travelling under the text, not a nested pill.
    BoxWithConstraints(Modifier.fillMaxWidth().height(54.dp).testTag("dock-tabs")) {
        val segment = maxWidth / 3
        val target = segment * Grouping.entries.indexOf(grouping)
        val position = animateDpAsState(
            targetValue = target,
            animationSpec = if (reduceMotion) snap() else spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "dock-tab-position"
        )
        Box(Modifier.width(segment).align(Alignment.BottomStart)
            .graphicsLayer { translationX = position.value.toPx() }
            .padding(horizontal = 25.dp, vertical = 5.dp)
            .height(2.dp)
            .clip(CircleShape)
            .background(MistWhite.copy(alpha = 0.80f)))
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Grouping.entries.forEach { tab ->
                Box(Modifier.weight(1f).fillMaxHeight()
                    .testTag("dock-tab-${tab.name.lowercase()}")
                    .clickable(onClick = { if (grouping != tab) onGroup(tab) }),
                    contentAlignment = Alignment.Center) {
                    Text(tab.name,
                        color = if (grouping == tab) MistWhite else SoftText,
                        fontSize = 13.sp, fontWeight = if (grouping == tab)
                            FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun DockProgress(
    positionState: State<Long>, duration: Long, playing: Boolean,
    modifier: Modifier = Modifier
) {
    val reduced = rememberReducedMotion()
    // State updates are read in this tiny composable only, not in the list or tabs.
    val current = if (duration > 0L) positionState.value.toFloat() / duration else 0f
    val animated = animateFloatAsState(
        targetValue = current.coerceIn(0f, 1f),
        animationSpec = tween(if (playing && !reduced) 525 else 0, easing = LinearEasing),
        label = "mini-track-progress"
    )
    Box(modifier.fillMaxWidth().height(1.5.dp).drawBehind {
        drawRect(Color.White.copy(alpha = 0.095f))
        drawRect(MistWhite.copy(alpha = 0.68f),
            size = androidx.compose.ui.geometry.Size(
                width = size.width * animated.value.coerceIn(0f, 1f),
                height = size.height
            ))
    })
}


/**
 * Touch slop and directional arbitration come from Compose's horizontal detector.
 * Only artwork and the non-button mini-player region use this modifier. No
 * gesture is handled by the seek bar, transport controls, or vertical song list.
 */
private fun albumKey(track: AudioTrack): String = if (track.albumId > 0) "album:${track.albumId}" else "${track.album}|${track.artist}"

private fun durationString(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTimeline(
    trackId: Long,
    positionState: State<Long>,
    duration: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit
) {
    // Playback ticks invalidate only this seekbar, not the artwork, background,
    // transport controls or Now Playing's root layout.
    val reduced = rememberReducedMotion()
    var draggedFraction by remember(trackId) { mutableStateOf<Float?>(null) }
    val sample = if (duration > 0L)
        (positionState.value.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val animated = animateFloatAsState(
        targetValue = sample,
        animationSpec = tween(if (playing && !reduced) 525 else 0, easing = LinearEasing),
        label = "isolated-seek-progress"
    )
    val fraction = (draggedFraction ?: animated.value).coerceIn(0f, 1f)
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
        thumb = { Box(Modifier.size(10.dp).clip(CircleShape).background(MistWhite)) },
        track = { state ->
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.21f))) {
                Box(Modifier.fillMaxWidth(state.value.coerceIn(0f, 1f))
                    .fillMaxHeight().background(MistWhite))
            }
        }
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(durationString((fraction * duration).roundToLong()),
            color = SoftText, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
        Text(durationString(duration),
            color = SoftText, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
    }
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
    previousArtworkTrack: AudioTrack?,
    nextArtworkTrack: AudioTrack?,
    expanded: Boolean,
    sharedScope: SharedTransitionScope,
    reduceMotion: Boolean,
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
    PrefetchSwipeArtwork(nextArtworkTrack, previousArtworkTrack)
    var dragY by remember { mutableFloatStateOf(0f) }
    val dragScope = rememberCoroutineScope()
    val dismissThreshold = with(LocalDensity.current) { 100.dp.toPx() }
    val settleDrag: () -> Unit = {
        dragScope.launch {
            if (reduceMotion) dragY = 0f
            else Animatable(dragY).animateTo(0f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy)) { dragY = value }
        }
    }
    val tint by animateColorAsState(
        targetValue = rememberArtworkTint(track),
        animationSpec = tween(if (reduceMotion) 0 else 240),
        label = "quiet-light"
    )
    BoxWithConstraints(Modifier.fillMaxSize().offset { IntOffset(0, dragY.roundToInt()) }
        .background(LazyNight)) {
        val fontScale = LocalDensity.current.fontScale
        val narrow = maxWidth < 360.dp
        val compact = maxHeight < 720.dp || fontScale >= 1.45f
        // The artwork stays a constant size when metadata changes. On short
        // screens the vertical scroller reveals controls without clipping them.
        val coverSize = (maxHeight * if (compact) 0.31f else 0.36f)
            .coerceAtMost(maxWidth - 52.dp)
            .coerceIn(150.dp, 344.dp)
        Box(Modifier.fillMaxWidth().height(maxHeight * 0.62f).background(
            Brush.verticalGradient(listOf(
                tint.copy(alpha = 0.48f),
                tint.copy(alpha = 0.19f),
                LazyNight
            ))
        ))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (narrow) 18.dp else 24.dp)) {
            Row(Modifier.fillMaxWidth().height(62.dp)
                .pointerInput(reduceMotion, dismissThreshold) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            dragY = (dragY + amount).coerceIn(0f, 800f)
                            change.consume()
                        },
                        onDragEnd = {
                            if (dragY >= dismissThreshold) onDismiss() else settleDrag()
                        },
                        onDragCancel = settleDrag
                    )
                }, verticalAlignment = Alignment.CenterVertically) {
                PlainAction(Icons.Rounded.KeyboardArrowDown, "Close Now Playing", onDismiss)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text("FROM YOUR LIBRARY", color = SoftText,
                        fontSize = 10.sp, letterSpacing = 0.9.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(track.album.ifBlank { "Local music" }, color = MistWhite,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PlainAction(Icons.Rounded.QueueMusic, "Open queue", onQueue)
            }
            Spacer(Modifier.height(if (compact) 10.dp else 22.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // No decorative frame or live blur. Existing swipe code owns
                // interactive neighbours and remains Media3-authoritative.
                TrackMotionSurface(
                    track = track,
                    previous = previousArtworkTrack,
                    next = nextArtworkTrack,
                    reduceMotion = reduceMotion,
                    canNext = canNext,
                    canPrevious = canPrevious,
                    onNext = onSwipeNext,
                    onPrevious = onSwipePrevious,
                    modifier = Modifier.size(coverSize),
                    tag = "now-artwork-swipe-zone"
                ) { shown ->
                    with(sharedScope) {
                        val art = if (reduceMotion) Modifier.fillMaxSize()
                            else Modifier.sharedElementWithCallerManagedVisibility(
                                rememberSharedContentState(key = "quiet-art-${shown.id}"),
                                expanded
                            ).fillMaxSize()
                        Artwork(shown, art, 12.dp,
                            targetPx = 640, showPlaceholderCaption = false)
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 18.dp else 29.dp))
            Row(Modifier.fillMaxWidth().heightIn(min = 80.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Crossfade(targetState = track,
                    animationSpec = tween(if (reduceMotion) 0 else 160),
                    modifier = Modifier.weight(1f).testTag("now-track-details"),
                    label = "metadata-change") { item ->
                    Column {
                        Text(item.title, color = MistWhite,
                            fontSize = if (compact) 23.sp else 27.sp,
                            lineHeight = if (compact) 29.sp else 34.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = if (fontScale >= 1.55f) 3 else 2,
                            overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Text(item.artist, color = SoftText, fontSize = 15.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (liked) "Remove from favourites" else "Add to favourites",
                        tint = if (liked) MistWhite else SoftText,
                        modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(5.dp))
            val extension = track.filename.substringAfterLast('.', "")
                .uppercase().ifBlank { "UNKNOWN" }
            Text("FILENAME FORMAT · .$extension", color = SoftText,
                fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(if (usbDacConnected) "USB DAC detected · output not verified"
                else "No USB DAC detected · output not verified",
                color = SoftText, fontSize = 11.sp)
            Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
            PlayerTimeline(track.id, positionState, duration, playing, onSeek)
            Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                val activeSurface = Color.White.copy(alpha = 0.105f)
                IconButton(onClick = onShuffle, modifier = Modifier.size(48.dp)
                    .clip(CircleShape)
                    .background(if (shuffle) activeSurface else Color.Transparent)) {
                    Icon(Icons.Rounded.Shuffle, if (shuffle) "Shuffle on" else "Shuffle off",
                        tint = if (shuffle) MistWhite else SoftText,
                        modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onPrevious, enabled = canPrevious,
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous track",
                        tint = if (canPrevious) MistWhite else SoftText,
                        modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = onPlay,
                    modifier = Modifier.size(if (narrow) 64.dp else 70.dp)
                        .clip(CircleShape).background(MistWhite)) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pause" else "Play", tint = LazyNight,
                        modifier = Modifier.size(33.dp))
                }
                IconButton(onClick = onNext, enabled = canNext,
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next track",
                        tint = if (canNext) MistWhite else SoftText,
                        modifier = Modifier.size(30.dp))
                }
                val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
                IconButton(onClick = onRepeat, modifier = Modifier.size(48.dp)
                    .clip(CircleShape)
                    .background(if (repeatActive) activeSurface else Color.Transparent)) {
                    Icon(if (repeatMode == Player.REPEAT_MODE_ONE)
                        Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat one"
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            else -> "Repeat off"
                        }, tint = if (repeatActive) MistWhite else SoftText,
                        modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}
