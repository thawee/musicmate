package apincer.android.mmate.ui.compose

import androidx.compose.ui.graphics.Color
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
        qualityInd: String = "",
        originalRate: Long = 0
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
                "getMqaSampleRate" -> originalRate
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
        assertEquals("DSD64", badgeText)
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
    fun mqaIdentitySurvivesGenericPcmTiers() {
        for (quality in listOf("MQA", "MQA Studio")) {
            for ((bits, rate) in listOf(16 to 44100L, 24 to 44100L, 24 to 96000L)) {
                val track = createMockTrack(bitDepth = bits, sampleRate = rate, qualityInd = quality)
                val resolution = if (rate == 96000L) "96" else "44.1"
                assertEquals("$quality at $bits/$rate", "MQA $bits/$resolution", getUnifiedBadgeText(track))
                val expanded = if (quality == "MQA Studio") "MQA STUDIO" else "MQA MASTER"
                assertEquals(expanded, AudioPresentation.qualityLabel(track, true))
                assertEquals("MQA", AudioPresentation.qualityLabel(track, false))
                assertEquals(Color(0xFFE040FB), AudioPresentation.accent(track))
            }
        }
    }

    @Test
    fun mqaFallbackLabelsPreserveStudioIdentityAndAccent() {
        assertEquals("MQA STUDIO", AudioPresentation.qualityLabel("MQA Studio", true))
        assertEquals("MQA MASTER", AudioPresentation.qualityLabel("MQA", true))
        assertEquals("MQA", AudioPresentation.qualityLabel("MQA STUDIO", false))
        assertEquals(Color(0xFFE040FB), AudioPresentation.accent("MQA STUDIO"))
        assertEquals(Color(0xFF64B5F6), AudioPresentation.accent(null as String?))
        assertEquals(Color(0xFFFFD700), AudioPresentation.accent("HR"))
    }

    @Test
    fun originalMqaRateIsLabeledAndDoesNotReplaceEncodedRate() {
        for (quality in listOf("MQA", "MQA Studio")) {
            for (originalRate in listOf(0L, 44100L, 88200L, 192000L)) {
                val track = createMockTrack(bitDepth = 24, qualityInd = quality, originalRate = originalRate)
                assertEquals("MQA 24/44.1", getUnifiedBadgeText(track))
                assertEquals("24/44.1", AudioPresentation.compactResolution(track))
                val original = when (originalRate) {
                    44100L -> "44.1 kHz"
                    88200L -> "88.2 kHz"
                    192000L -> "192 kHz"
                    else -> ""
                }
                assertEquals(original, AudioPresentation.originalRate(track))
                assertEquals(
                    "Encoded: 24-bit / 44.1 kHz" + if (original.isEmpty()) "" else "; Original: $original",
                    AudioPresentation.resolutionDescription(track)
                )
            }
        }
    }

    @Test
    fun originalRateRequiresMqaAndValidMetadata() {
        assertEquals("", AudioPresentation.originalRate(createMockTrack(originalRate = 192000)))
        assertEquals("", AudioPresentation.originalRate(createMockTrack(qualityInd = "MQA", originalRate = -1)))
        assertEquals("", AudioPresentation.originalRate(null))
        assertEquals("", AudioPresentation.encodedResolution(null))
        assertEquals("-", AudioPresentation.qualityLabel(null as Track?, true))
        val incomplete = createMockTrack(bitDepth = 0, sampleRate = 0, bitRate = 320000, qualityInd = "MQA")
        assertEquals("MQA 320k", getUnifiedBadgeText(incomplete))
        assertEquals("320k", AudioPresentation.resolutionDescription(incomplete))
    }

    @Test
    fun genericQualityAndDsdPrecedenceArePreserved() {
        val pcm = createMockTrack(bitDepth = 24)
        assertEquals("24-BIT 24/44.1", getUnifiedBadgeText(pcm))
        assertEquals("24-BIT STUDIO", AudioPresentation.qualityLabel(pcm, true))
        assertEquals(Color(0xFFFFD700), AudioPresentation.accent(pcm))
        val dsd = createMockTrack(bitDepth = 1, sampleRate = 5644800, qualityInd = "MQA Studio")
        assertEquals("DSD128", getUnifiedBadgeText(dsd))
        assertEquals("DSD AUDIO", AudioPresentation.qualityLabel(dsd, true))
        assertEquals("DSD128", AudioPresentation.encodedResolution(dsd))
        val unknownQuality = createMockTrack(qualityInd = "-")
        assertEquals("CD", AudioPresentation.qualityLabel(unknownQuality, false))
    }

    @Test
    fun testUnifiedBadgeTextNullTrack() {
        assertEquals("-", getUnifiedBadgeText(null))
    }
}
