package apincer.android.mmate.ui.compose

import apincer.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class VUMeterAndBadgeTest {

    private fun createMockTrack(
        encoding: String = "FLAC",
        bitDepth: Int = 16,
        sampleRate: Long = 44100,
        bitRate: Long = 0,
        fileType: String = "FLAC",
        qualityInd: String = ""
    ): Track {
        return Proxy.newProxyInstance(
            Track::class.java.classLoader,
            arrayOf(Track::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAudioEncoding" -> encoding
                "getAudioBitsDepth" -> bitDepth
                "getAudioSampleRate" -> sampleRate
                "getAudioBitRate" -> bitRate
                "getFileType" -> fileType
                "getQualityInd" -> qualityInd
                "getTitle" -> "Sample Track"
                "getArtist" -> "Sample Artist"
                "getPath" -> "/storage/emulated/0/Music/sample.$fileType"
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Double::class.javaPrimitiveType -> 0.0
                    else -> null
                }
            }
        } as Track
    }

    @Test
    fun testVUMeterThemesExistAndHaveProperNames() {
        val themes = VUMeterTheme.values()
        assertEquals(3, themes.size)
        assertTrue(themes.any { it.displayName.contains("ACCUPHASE") })
        assertTrue(themes.any { it.displayName.contains("MCINTOSH") })
        assertTrue(themes.any { it.displayName.contains("STUDIO") })
    }

    @Test
    fun testUnifiedBadgeTextHiResFLAC() {
        val track = createMockTrack(
            encoding = "FLAC",
            bitDepth = 24,
            sampleRate = 96000,
            fileType = "FLAC"
        )
        val badgeText = getUnifiedBadgeText(track)
        assertEquals("HI-RES 24/96", badgeText)
    }

    @Test
    fun testUnifiedBadgeTextCDLossless() {
        val track = createMockTrack(
            encoding = "FLAC",
            bitDepth = 16,
            sampleRate = 44100,
            fileType = "FLAC"
        )
        val badgeText = getUnifiedBadgeText(track)
        assertEquals("CD 16/44.1", badgeText)
    }

    @Test
    fun testUnifiedBadgeTextDSD() {
        val track = createMockTrack(
            encoding = "DSD",
            bitDepth = 1,
            sampleRate = 2822400,
            fileType = "DSF"
        )
        val badgeText = getUnifiedBadgeText(track)
        assertEquals("DSD 64", badgeText)
    }

    @Test
    fun testUnifiedBadgeTextMP3() {
        val track = createMockTrack(
            encoding = "MP3",
            bitDepth = 0,
            sampleRate = 44100,
            bitRate = 320000,
            fileType = "MP3"
        )
        val badgeText = getUnifiedBadgeText(track)
        assertEquals("320k", badgeText)
    }

    @Test
    fun testUnifiedBadgeTextNullTrack() {
        assertEquals("-", getUnifiedBadgeText(null))
    }
}
