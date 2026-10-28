package com.lazy.player.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lazy.player.data.AudioTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Common swipe physics; Media3 playback is requested at gesture completion. */
@Composable
internal fun TrackMotionSurface(
    track: AudioTrack, previous: AudioTrack?, next: AudioTrack?,
    canNext: Boolean, canPrevious: Boolean, reduceMotion: Boolean,
    onNext: () -> Unit, onPrevious: () -> Unit,
    modifier: Modifier = Modifier, tag: String,
    content: @Composable (AudioTrack) -> Unit
) {
    var shown by remember { mutableStateOf(track) }
    var displacement by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var toNext by remember { mutableStateOf(false) }
    var latched by remember { mutableStateOf<AudioTrack?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val actual = rememberUpdatedState(track)
    val nextAction = rememberUpdatedState(onNext)
    val prevAction = rememberUpdatedState(onPrevious)
    LaunchedEffect(track.id, settling) {
        if (!settling) shown = track
    }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    BoxWithConstraints(modifier.testTag(tag)) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val threshold = with(LocalDensity.current) { 62.dp.toPx() }
        val incoming = if (settling) latched
            else if (displacement < 0f) next else previous
        val towardsNext = if (settling) toNext else displacement < 0f
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
            .pointerInput(track.id, next?.id, previous?.id, canNext, canPrevious, widthPx, reduceMotion) {
                var delta = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (!settling) { job?.cancel(); delta = 0f }
                    },
                    onHorizontalDrag = { event, amount ->
                        if (!settling) {
                            delta += amount
                            val d = if ((delta < 0 && !canNext) ||
                                (delta > 0 && !canPrevious)) delta * 0.12f else delta
                            // When no real visual neighbour is known (notably
                            // shuffle), retain the current cover and only hint at
                            // the gesture; never reveal a blank artwork viewport.
                            val hasVisualNeighbour = if (delta < 0f) next != null else previous != null
                            val limit = if (hasVisualNeighbour) widthPx else widthPx * 0.16f
                            displacement = d.coerceIn(-limit, limit)
                            event.consume()
                        }
                    },
                    onDragEnd = {
                        if (!settling) {
                            val choice = TrackSwipe.decide(
                                delta, 0f, threshold, canNext, canPrevious)
                            job?.cancel()
                            if (choice == SwipeTrackAction.NONE) {
                                job = scope.launch {
                                    if (reduceMotion) displacement = 0f
                                    else Animatable(displacement).animateTo(0f,
                                        spring(dampingRatio = Spring.DampingRatioNoBouncy)
                                    ) { displacement = value }
                                }
                            } else {
                                settling = true
                                toNext = choice == SwipeTrackAction.NEXT
                                latched = if (toNext) next else previous
                                val outgoingId = shown.id
                                if (toNext) nextAction.value() else prevAction.value()
                                job = scope.launch {
                                    if (latched != null && !reduceMotion) {
                                        Animatable(displacement).animateTo(
                                            if (toNext) -widthPx else widthPx,
                                            tween(165, easing = FastOutSlowInEasing)
                                        ) { displacement = value }
                                    } else {
                                        // Fallback: no adjacent artwork is known, so
                                        // don't push the only cover out of the screen.
                                        displacement = 0f
                                    }
                                    val changed = withTimeoutOrNull(650L) {
                                        snapshotFlow { actual.value.id }
                                            .first { it != outgoingId }
                                    }
                                    Snapshot.withMutableSnapshot {
                                        if (changed != null) shown = actual.value
                                        displacement = 0f
                                        settling = false
                                        latched = null
                                    }
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        if (!settling) {
                            job?.cancel()
                            job = scope.launch {
                                if (reduceMotion) displacement = 0f
                                else Animatable(displacement).animateTo(0f, spring()) {
                                    displacement = value
                                }
                            }
                        }
                    }
                )
            }
        ) {
            if (incoming != null && displacement != 0f) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    translationX = displacement +
                        (if (towardsNext) widthPx else -widthPx)
                }) { content(incoming) }
            }
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX = displacement
            }) {
                Crossfade(targetState = shown,
                    animationSpec = tween(if (reduceMotion) 0 else 145),
                    label = "track-content") { item -> content(item) }
            }
        }
    }
}
