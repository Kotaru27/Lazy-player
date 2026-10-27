package com.lazy.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderLibraryTest {
    @Test fun commonLosslessFormatsAreIncluded() {
        assertTrue(FolderLibrary.isSupported("My Album - Track.FLAC"))
        assertTrue(FolderLibrary.isSupported("live.session.WAV"))
        assertTrue(FolderLibrary.isSupported("lossless.wave"))
    }

    @Test fun mixedCaseAndLongFilenamesWork() {
        assertTrue(FolderLibrary.isSupported("Song.OPUS"))
        assertTrue(FolderLibrary.isSupported("A very long song name with numbers 2048.FlAc"))
    }

    @Test fun unrelatedDocumentsAreExcluded() {
        assertFalse(FolderLibrary.isSupported("cover.jpg"))
        assertFalse(FolderLibrary.isSupported("playlist.m3u8"))
        assertFalse(FolderLibrary.isSupported("album.flac.txt"))
        assertFalse(FolderLibrary.isSupported(""))
    }
}
