package apincer.android.mmate.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegPlaybackTest {
    @Test
    fun alacAiffAndDsdGoThroughFfmpeg() {
        assertTrue(FfmpegPlayback.needsFfmpeg("/Music/a.m4a", alacOrAiff = true, dsd = false))
        assertTrue(FfmpegPlayback.needsFfmpeg("/Music/a.dsf", alacOrAiff = false, dsd = true))
    }

    @Test
    fun aiffAndDsdAreRecognisedByExtensionWhenTheScanMissedThem() {
        listOf("a.aif", "a.AIFF", "a.aifc", "a.dsf", "a.DFF").forEach {
            assertTrue(it, FfmpegPlayback.needsFfmpeg("/Music/$it", alacOrAiff = false, dsd = false))
        }
    }

    @Test
    fun everythingElseKeepsTheNormalPath() {
        listOf("a.flac", "a.m4a", "a.mp3", "a.wav", "noextension").forEach {
            assertFalse(it, FfmpegPlayback.needsFfmpeg("/Music/$it", alacOrAiff = false, dsd = false))
        }
        assertFalse(FfmpegPlayback.needsFfmpeg(null, alacOrAiff = false, dsd = false))
    }
}
