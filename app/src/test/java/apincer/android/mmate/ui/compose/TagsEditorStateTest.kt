package apincer.android.mmate.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagsEditorStateTest {
    @Test
    fun snapshotRestoresEveryDraftFieldAndItsModifiedFlag() {
        val original = TagsEditorState().apply {
            title = "Time"
            artist = "Pink Floyd"
            album = "The Dark Side of the Moon"
            albumArtist = "Pink Floyd"
            track = "4"
            year = "1973"
            genre = "Progressive Rock"
            style = "Art Rock"
            mood = "Reflective"
            origin = "UK"
            publisher = "Harvest"
            titleModified = true
            artistModified = true
            albumModified = true
            albumArtistModified = true
            trackModified = true
            yearModified = true
            genreModified = true
            styleModified = true
            moodModified = true
            originModified = true
            publisherModified = true
        }

        val restored = TagsEditorState().apply { restore(original.snapshot()) }

        assertEquals("Time", restored.title)
        assertEquals("Pink Floyd", restored.artist)
        assertEquals("The Dark Side of the Moon", restored.album)
        assertEquals("Pink Floyd", restored.albumArtist)
        assertEquals("4", restored.track)
        assertEquals("1973", restored.year)
        assertEquals("Progressive Rock", restored.genre)
        assertEquals("Art Rock", restored.style)
        assertEquals("Reflective", restored.mood)
        assertEquals("UK", restored.origin)
        assertEquals("Harvest", restored.publisher)
        assertTrue(restored.titleModified)
        assertTrue(restored.artistModified)
        assertTrue(restored.albumModified)
        assertTrue(restored.albumArtistModified)
        assertTrue(restored.trackModified)
        assertTrue(restored.yearModified)
        assertTrue(restored.genreModified)
        assertTrue(restored.styleModified)
        assertTrue(restored.moodModified)
        assertTrue(restored.originModified)
        assertTrue(restored.publisherModified)
    }

    @Test
    fun snapshotDistinguishesUntouchedMixedValuesFromExplicitlyClearedFields() {
        val original = TagsEditorState().apply {
            title = TagsEditorState.MULTI_VALUES_MARKER
            titleModified = false
            artist = ""
            artistModified = true
            album = "Shared album"
            albumModified = false
        }
        val snapshot = original.snapshot()
        original.artist = "Changed after snapshot"

        val restored = TagsEditorState().apply { restore(snapshot) }

        assertEquals(TagsEditorState.MULTI_VALUES_MARKER, restored.title)
        assertFalse(restored.titleModified)
        assertEquals("", restored.artist)
        assertTrue(restored.artistModified)
        assertEquals("Shared album", restored.album)
        assertFalse(restored.albumModified)
    }
    @Test
    fun blankBatchReplacementPreservesIndividualValuesButSingleTrackCanBeCleared() {
        assertEquals("Original A", TagsEditorState.valueForSave("", "Original A", true, true))
        assertEquals("", TagsEditorState.valueForSave("", "Original A", true, false))
        assertEquals("Original A", TagsEditorState.valueForSave("New", "Original A", false, true))
    }
}
