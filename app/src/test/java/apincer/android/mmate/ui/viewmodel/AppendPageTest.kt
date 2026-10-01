package apincer.android.mmate.ui.viewmodel

import apincer.music.core.model.AudioTag
import org.junit.Assert.assertEquals
import org.junit.Test

class AppendPageTest {

    private fun track(id: Long) = AudioTag().apply { this.id = id }

    @Test
    fun appendPage_skipsIdsAlreadyShown() {
        val previous = listOf(track(1), track(2), track(3))
        // A track inserted before the page boundary shifts track 3 into the next page
        val page = listOf(track(3), track(4), track(5))

        val result = appendPage(previous, page)

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), result.map { it.id })
    }

    @Test
    fun appendPage_skipsDuplicatesWithinPage() {
        val result = appendPage(listOf(track(1)), listOf(track(2), track(2)))

        assertEquals(listOf(1L, 2L), result.map { it.id })
    }
}
