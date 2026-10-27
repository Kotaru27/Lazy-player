package com.lazy.player.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lazy.player.data.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class LazyPlayerUiTest {
    @get:Rule val rule = createComposeRule()

    private val songs = listOf(
        audio(1, "Stage Light", "First Artist", "Album One"),
        audio(2, "Second Track", "First Artist", "Album One"),
        audio(3, "A Very Long Example FLAC Title for Layout Verification", "Second Artist", "Album Two")
    )
    private var selected = emptyList<Long>()
    private var shuffled = false
    private var previousSwipes = 0
    private var nextSwipes = 0
    private var toggles = 0
    private var folderSelections = 0
    private var allMusicSelections = 0

    private fun audio(id: Long, title: String, artist: String, album: String) =
        AudioTrack(
            id = id,
            uri = Uri.parse("content://com.lazy.player.qa/music/$id"),
            title = title,
            artist = artist,
            album = album,
            albumId = when (album) {
                "Album One" -> 42L
                "Album Two" -> 43L
                else -> 100L + ((id - 1000L).coerceAtLeast(0L) / 10L)
            },
            durationMs = 35000L,
            dateAdded = 1L,
            filename = "$title.flac"
        )

    private fun show(
        active: AudioTrack? = songs[1],
        canNext: Boolean = true,
        canPrevious: Boolean = true,
        folderName: String? = "FLAC Collection",
        collection: List<AudioTrack> = songs
    ) {
        rule.setContent {
            var liked by remember { mutableStateOf(setOf<Long>()) }
            val progress = remember { mutableLongStateOf(7000L) }
            LazyPlayerScreen(
                tracks = collection,
                hasPermission = true,
                libraryLoading = false,
                folderName = folderName,
                folderError = null,
                usbDacConnected = true,
                onChooseFolder = { folderSelections++ },
                onUseAllMusic = { allMusicSelections++ },
                onPermission = {},
                currentTrack = active,
                playing = false,
                positionState = progress,
                duration = 35000L,
                shuffle = false,
                repeatMode = Player.REPEAT_MODE_OFF,
                canNext = canNext,
                canPrevious = canPrevious,
                favorites = liked,
                onRefresh = {},
                onPlayTrack = { _, _ -> },
                onPlayCollection = { collection, isShuffled ->
                    selected = collection.map { it.id }
                    shuffled = isShuffled
                },
                onTogglePlay = { toggles++ },
                onNext = {},
                onPrevious = {},
                onSwipeNext = { nextSwipes++ },
                onSwipePrevious = { previousSwipes++ },
                onSeek = {},
                onShuffle = {},
                onRepeat = {},
                queue = collection,
                onQueueSelection = {},
                onToggleFavorite = { id ->
                    liked = if (id in liked) liked - id else liked + id
                }
            )
        }
        rule.waitForIdle()
    }

    private fun save(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun libraryActionsRespectAlbumAndSearchAndFavourites() {
        show()
        rule.onNodeWithText("lazy player").assertExists()
        rule.onNodeWithText("Library").assertExists()
        rule.onNodeWithTag("dock-tabs").assertExists()
        rule.onNodeWithContentDescription("Play all 3 songs").performClick()
        rule.runOnIdle {
            assertEquals(listOf(1L, 2L, 3L), selected)
            assertEquals(false, shuffled)
        }
        save("01-library")
        rule.onNodeWithTag("dock-tab-albums").performClick()
        rule.onNodeWithText("Album One").performClick()
        rule.onNodeWithContentDescription("Shuffle all 2 songs").performClick()
        rule.runOnIdle {
            assertEquals(listOf(1L, 2L), selected)
            assertTrue(shuffled)
        }
        rule.onNodeWithTag("dock-tab-songs").performClick()
        rule.onNodeWithContentDescription("Options for Stage Light").performClick()
        rule.onNodeWithText("Add to favourites").performClick()
        rule.onNodeWithContentDescription("Choose All songs or Favourites").performClick()
        rule.onNodeWithText("Favourites").performClick()
        rule.onNodeWithContentDescription("Play all 1 song").performClick()
        rule.runOnIdle { assertEquals(1, selected.size) }
    }

    @Test fun folderMenuAndSearchRemainReachable() {
        show()
        rule.onNodeWithContentDescription("Folder: FLAC Collection").performClick()
        rule.onNodeWithText("FLAC Collection").assertExists()
        rule.onNodeWithText("Change folder").performClick()
        rule.runOnIdle { assertEquals(1, folderSelections) }
        rule.onNodeWithContentDescription("Folder: FLAC Collection").performClick()
        rule.onNodeWithText("Use all music").performClick()
        rule.runOnIdle { assertEquals(1, allMusicSelections) }
        rule.onNodeWithContentDescription("Search music").performClick()
        rule.onNodeWithText("Search music").assertExists()
    }

    @Test fun artworkAndMiniPlayerSwipesDoNotTogglePlayback() {
        show()
        rule.onNodeWithTag("mini-swipe-zone").performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(1, previousSwipes)
            assertEquals(0, toggles)
        }
        rule.onNodeWithTag("mini-swipe-zone").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, nextSwipes) }
        rule.onNodeWithTag("mini-swipe-zone").performClick()
        rule.onNodeWithTag("now-artwork-swipe-zone").assertExists()
        save("02-now-playing")
        rule.onNodeWithTag("now-artwork-swipe-zone").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(2, nextSwipes)
            assertEquals(0, toggles)
        }
    }

    @Test fun hundredTrackLibraryKeepsDockAndAccessibleLastSong() {
        val tracks = (0 until 120).map { i ->
            audio(1000L + i, "Track " + i.toString().padStart(3, '0') +
                if (i % 5 == 0) " తెలుగు సంగీతం" else "",
                if (i % 2 == 0) "Artist One" else "Artist Two",
                "Album " + (i / 10).toString())
        }
        show(active = tracks[0], collection = tracks)
        rule.onNodeWithContentDescription("Play all 120 songs").assertExists()
        rule.onNodeWithTag("dock-tabs").assertExists()
        rule.onNodeWithTag("track-list").performScrollToNode(hasText("Track 119", substring = true))
        rule.onNode(hasText("Track 119", substring = true)).assertExists()
        save("03-populated-library")
        rule.onNodeWithTag("dock-tab-albums").performClick()
        rule.onNodeWithText("Album 0").assertExists()
        save("04-albums")
        rule.onNodeWithTag("dock-tab-artists").performClick()
        rule.onNodeWithText("Artist One").assertExists()
    }

    @Test fun queueBoundariesIgnoreGestures() {
        show(active = songs[0], canNext = false, canPrevious = false)
        rule.onNodeWithTag("mini-swipe-zone").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("mini-swipe-zone").performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(0, previousSwipes)
            assertEquals(0, nextSwipes)
            assertEquals(0, toggles)
        }
    }
}
