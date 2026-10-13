package apincer.android.mmate.ui.compose

import apincer.music.core.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPresentationLabelTest {

    private val expandedLabels = listOf(
        "DSD AUDIO", "MQA MASTER", "MQA STUDIO", "HI-RES LOSSLESS",
        "24-BIT LOSSLESS", "CD QUALITY", "STANDARD QUALITY"
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
        assertEquals("CD QUALITY", AudioPresentation.qualityLabel(Constants.TITLE_CD_QUALITY, true))
    }

    @Test
    fun compactLegend_isTheLibraryPillLabel() {
        assertEquals("24-BIT LOSSLESS", AudioPresentation.qualityLabel(Constants.LEGEND_STUDIO, true))
    }
}
