package apincer.android.mmate.ui

import apincer.music.core.model.AudioTag
import apincer.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionModelTest {

    private fun track(title: String, path: String = "/music/$title.flac"): Track =
        AudioTag().apply {
            setTitle(title)
            setPath(path)
        }

    private fun tracks(vararg titles: String): List<Track> = titles.map { track(it) }

    @Test
    fun switchingLibraryDestinationClearsSelection() {
        val model = LibrarySelectionModel()
        model.setTracks(tracks("One", "Two", "Three"))
        model.select(0)
        model.select(1)

        model.setTracks(tracks("Four", "Five"))

        assertFalse(model.hasSelection())
        assertEquals(emptyList<Track>(), model.getSelectedTracks())
    }

    @Test
    fun explicitClearNotifiesListenerWithZeroCount() {
        val observed = mutableListOf<Int>()
        val model = LibrarySelectionModel { count, _ -> observed.add(count) }
        model.setTracks(tracks("One", "Two"))
        model.select(0)

        model.clear()

        assertEquals(listOf(1, 0), observed)
    }

    @Test
    fun paginationKeepsSelectionBoundToTheSameTracks() {
        val model = LibrarySelectionModel()
        val firstPage = tracks("One", "Two")
        model.setTracks(firstPage)
        model.select(1)
        val selectedBefore = model.getSelectedTracks()

        model.setTracks(firstPage + tracks("Three", "Four"))

        assertTrue(model.hasSelection())
        assertEquals(selectedBefore, model.getSelectedTracks())
    }

    @Test
    fun vanishedTrackIsDroppedInsteadOfRetargetingAnotherRow() {
        val model = LibrarySelectionModel()
        model.setTracks(tracks("One", "Two", "Three", "Four"))
        model.select(3)

        model.setTracks(tracks("One", "Two"))

        assertFalse(model.hasSelection())
        assertEquals(emptyList<Track>(), model.getSelectedTracks())
    }

    @Test
    fun samePositionShowingADifferentTrackIsNotSelected() {
        val model = LibrarySelectionModel()
        model.setTracks(tracks("One", "Two"))
        model.select(0)
        model.select(1)

        model.setTracks(listOf(track("Uno", "/other/Uno.flac"), track("Dos", "/other/Dos.flac")))

        assertFalse(model.hasSelection())
        assertEquals(emptyList<Track>(), model.getSelectedTracks())
    }

    @Test
    fun reorderingKeepsTheSameTracksSelected() {
        val model = LibrarySelectionModel()
        val one = track("One")
        val two = track("Two")
        val three = track("Three")
        model.setTracks(listOf(one, two, three))
        model.select(0)
        model.select(2)

        model.setTracks(listOf(three, one, two))

        assertEquals(listOf("Three", "One"), model.getSelectedTracks().map { it.title })
    }

    @Test
    fun reIndexedTrackStaysSelectedAtItsNewPosition() {
        val model = LibrarySelectionModel()
        val two = track("Two")
        model.setTracks(tracks("One", "Two", "Three"))
        model.select(1)

        model.setTracks(listOf(track("Zero"), two, track("Three")))

        assertTrue(model.isSelected(1))
        assertEquals(listOf("Two"), model.getSelectedTracks().map { it.title })
    }

    @Test
    fun toggleAddsThenRemovesPosition() {
        val model = LibrarySelectionModel()
        model.setTracks(tracks("One", "Two"))

        model.toggle(0)
        assertEquals(listOf("One"), model.getSelectedTracks().map { it.title })

        model.toggle(0)
        assertFalse(model.hasSelection())
    }

    @Test
    fun selectAllSelectsEveryCurrentTrackAndClearsWhenAlreadyComplete() {
        val model = LibrarySelectionModel()
        model.setTracks(tracks("One", "Two", "Three"))

        model.selectAll()
        assertEquals(3, model.getSelectedTracks().size)

        model.selectAll()
        assertFalse(model.hasSelection())
    }

    @Test
    fun outOfRangePositionsAreIgnored() {
        val model = LibrarySelectionModel()
        model.setTracks(tracks("One", "Two"))

        model.select(5)
        model.toggle(-1)

        assertFalse(model.hasSelection())
    }
}
