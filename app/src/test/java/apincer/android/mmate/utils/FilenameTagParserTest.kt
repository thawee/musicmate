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

    @Test
    fun testParentFolderPattern() {
        val parsed = FilenameTagParser.parse(
            "/storage/emulated/0/Music/Queen/A Night at the Opera/11 - Bohemian Rhapsody.flac",
            "%artist%/%album%/%track% - %title%"
        )
        assertNotNull(parsed)
        assertEquals("Queen", parsed?.artist)
        assertEquals("A Night at the Opera", parsed?.album)
        assertEquals("11", parsed?.track)
        assertEquals("Bohemian Rhapsody", parsed?.title)
    }

    @Test
    fun testParentFolderPatternNeedsEnoughFolders() {
        assertNull(FilenameTagParser.parse("Queen/11 - Bohemian Rhapsody.flac", "%artist%/%album%/%track% - %title%"))
    }

    @Test
    fun testTrackMustBeNumeric() {
        assertNull(FilenameTagParser.parse("The Rolling Stones - Satisfaction.flac", "%track% - %title%"))
    }

    @Test
    fun testDetectArtistAlbumFolderWithArtistTitleFile() {
        val pattern = FilenameTagParser.detectPattern(listOf(
            "/Music/The Rolling Stones/In Mono/The Rolling Stones - Satisfaction.flac",
            "/Music/The Rolling Stones/In Mono/The Rolling Stones - Paint It Black.flac"
        ))
        assertEquals("%artist%/%album%/%artist% - %title%", pattern)
    }

    @Test
    fun testDetectPicksPatternMatchingMostTracks() {
        val pattern = FilenameTagParser.detectPattern(listOf(
            "/Music/Queen/A Night at the Opera/01. Death on Two Legs.flac",
            "/Music/Queen/A Night at the Opera/02. Lazing on a Sunday Afternoon.flac",
            "/Music/Queen/A Night at the Opera/Bonus Track.flac"
        ))
        assertEquals("%artist%/%album%/%track%. %title%", pattern)
    }

    @Test
    fun testDetectPrefersTrackOverArtist() {
        assertEquals(
            "%artist%/%album%/%track% - %title%",
            FilenameTagParser.detectPattern(listOf("/Music/Queen/A Night at the Opera/11 - Bohemian Rhapsody.flac"))
        )
    }

    @Test
    fun testDetectFallsBackToFilenamePresetWithoutFolders() {
        assertEquals("%track% - %title%", FilenameTagParser.detectPattern(listOf("11 - Bohemian Rhapsody.flac")))
    }
}
