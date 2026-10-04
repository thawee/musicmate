package apincer.android.mmate.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FilenameTagParserTest {

    @Test
    fun testArtistTitlePattern() {
        val parsed = FilenameTagParser.parse("Queen - Bohemian Rhapsody.flac", "%artist% - %title%")
        assertNotNull(parsed)
        assertEquals("Queen", parsed?.artist)
        assertEquals("Bohemian Rhapsody", parsed?.title)
        assertNull(parsed?.track)
    }

    @Test
    fun testTrackArtistTitlePattern() {
        val parsed = FilenameTagParser.parse("/storage/emulated/0/Music/01 - Queen - Bohemian Rhapsody.mp3", "%track% - %artist% - %title%")
        assertNotNull(parsed)
        assertEquals("01", parsed?.track)
        assertEquals("Queen", parsed?.artist)
        assertEquals("Bohemian Rhapsody", parsed?.title)
    }

    @Test
    fun testTrackDotTitlePattern() {
        val parsed = FilenameTagParser.parse("05. Hotel California.wav", "%track%. %title%")
        assertNotNull(parsed)
        assertEquals("05", parsed?.track)
        assertEquals("Hotel California", parsed?.title)
    }

    @Test
    fun testArtistAlbumTrackTitlePattern() {
        val parsed = FilenameTagParser.parse("Pink Floyd - The Dark Side of the Moon - 02 - Time.dsf", "%artist% - %album% - %track% - %title%")
        assertNotNull(parsed)
        assertEquals("Pink Floyd", parsed?.artist)
        assertEquals("The Dark Side of the Moon", parsed?.album)
        assertEquals("02", parsed?.track)
        assertEquals("Time", parsed?.title)
    }

    @Test
    fun testNonMatchingPatternReturnsNull() {
        val parsed = FilenameTagParser.parse("InvalidFilenamePattern.flac", "%artist% - %album% - %track% - %title%")
        assertNull(parsed)
    }
}
