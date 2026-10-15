package apincer.android.mmate.ui.compose

import apincer.music.core.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPresentationLabelTest {

    private val expandedLabels = listOf(
        "DSD AUDIO", "MQA MASTER", "MQA STUDIO", "HI-RES LOSSLESS",
        "24-BIT LOSSLESS", "LOSSLESS", "LOSSY AUDIO"
    )

    @Test
    fun expandedLabels_mapBackToTheSameTier() {
        // Tiers are found by searching label text, so a rename must not move a label to another tier
        expandedLabels.forEach { assertEquals(it, AudioPresentation.qualityLabel(it, true)) }
    }

    @Test
    fun categoryTitles_useTheTierLabel() {
        assertEquals("24-BIT LOSSLESS", AudioPresentation.qualityLabel(Constants.TITLE_CD_EXT_QUALITY, true))
        assertEquals("HI-RES LOSSLESS", AudioPresentation.qualityLabel(Constants.TITLE_HIRES_QUALITY, true))
        assertEquals("LOSSLESS", AudioPresentation.qualityLabel(Constants.TITLE_CD_QUALITY, true))
    }

    @Test
    fun compactLegend_isTheLibraryPillLabel() {
        assertEquals("24-BIT LOSSLESS", AudioPresentation.qualityLabel(Constants.LEGEND_STUDIO, true))
    }

    @Test
    fun storedCdCode_showsAsLossless() {
        // The database keeps storing "CD"; only the label changes
        assertEquals("CD", Constants.LEGEND_CD)
        assertEquals("LOSSLESS", AudioPresentation.qualityLabel(Constants.LEGEND_CD, false))
        assertEquals("LOSSLESS", AudioPresentation.qualityLabel(Constants.LEGEND_CD, true))
        assertEquals("Lossless", Constants.TITLE_CD_QUALITY)
    }

    @Test
    fun lossyAndUnknownCodes_doNotDisplayAsLossless() {
        for (code in listOf("LOSSY", "Compressed", "LC", "STANDARD QUALITY")) {
            assertEquals("LOSSY AUDIO", AudioPresentation.qualityLabel(code, true))
            assertEquals("LOSSY", AudioPresentation.qualityLabel(code, false))
        }
        assertEquals("UNKNOWN QUALITY", AudioPresentation.qualityLabel("", true))
    }
}
