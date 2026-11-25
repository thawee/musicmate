package apincer.android.mmate.audio.dsp

import apincer.android.mmate.audio.headphones.HeadphoneBinding
import apincer.android.mmate.audio.headphones.HeadphoneDsp
import apincer.android.mmate.audio.headphones.HeadphoneProfile
import apincer.android.mmate.audio.headphones.HeadphoneTone
import apincer.android.mmate.audio.headphones.ManualHeadphoneTuning
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class QuietListeningTest {
    @Test fun offIsAnExactIdentityAndInvalidStrengthsAreBounded() {
        for (strength in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertTrue(QuietListening.bands(strength).isEmpty())
        }
        assertEquals(QuietListening.bands(1.0), QuietListening.bands(8.0))
        val original = HeadphoneDsp.settingsFor(null, HeadphoneBinding(soundMode = SoundMode.LIVELY))
        assertEquals(SoundMode.LIVELY.bands(), original.bands)
        assertFalse(original.compress)
        assertFalse(original.crossfeed)
    }

    @Test fun strengthScalesGentleShelvesWithoutChangingTheMidrange() {
        val half = DspChain(QuietListening.bands(0.5), 48_000, 2)
        val full = DspChain(QuietListening.bands(1.0), 48_000, 2)
        assertEquals(2.0, half.responseDb(0.0), 0.001)
        assertEquals(4.0, full.responseDb(0.0), 0.001)
        assertEquals(0.5, half.responseDb(24_000.0), 0.001)
        assertEquals(1.0, full.responseDb(24_000.0), 0.001)
        assertEquals(0.0, full.responseDb(1_000.0), 0.01)
        assertEquals(2.0, full.responseDb(100.0), 0.01)
        assertEquals(0.5, full.responseDb(6_000.0), 0.01)
    }

    @Test fun compositionProtectsCombinedEarBoostsAndKeepsNightAndCrossfeedIndependent() {
        val profile = HeadphoneProfile.createManual("Listening", ManualHeadphoneTuning(
            left = HeadphoneTone(bassDb = 6.0), right = HeadphoneTone(trebleDb = 6.0)))
        for (mode in SoundMode.selectable) {
            val binding = HeadphoneBinding(soundMode = mode, quietListening = 1.0,
                bassDb = 4.0, trebleDb = 4.0, nightListening = true, crossfeed = true)
            val settings = HeadphoneDsp.settingsFor(profile, binding)
            assertTrue(settings.compress)
            assertTrue(settings.crossfeed)
            assertEquals(QuietListening.bands(1.0), settings.bands.takeLast(2))
            for (sampleRate in listOf(44_100, 48_000, 96_000)) {
                val chain = DspChain(settings.bands, sampleRate, 2, settings.requestedPreampDb,
                    settings.leftBands, settings.rightBands, settings.balanceDb, settings.monoBands, settings.crossfeed)
                assertTrue(chain.preampDb <= -8.49)
                repeat(1001) { i ->
                    val hz = if (i == 0) 0.0 else (sampleRate / 2.0).pow(i / 1000.0)
                    repeat(2) { channel ->
                        assertTrue("$mode / $sampleRate / $hz", chain.channelResponseDb(hz, channel) + chain.preampDb < 0.01)
                    }
                }
            }
        }
    }

    @Test fun customComparisonRetainsQuietWithCorrectionAndCompression() {
        val binding = HeadphoneBinding(soundMode = SoundMode.CUSTOM, bassDb = 4.0,
            quietListening = 0.5, nightListening = true, crossfeed = true)
        val compared = HeadphoneDsp.settingsFor(null, binding, comparingCustom = true)
        assertEquals(QuietListening.bands(0.5), compared.bands)
        assertTrue(compared.compress)
        assertTrue(compared.crossfeed)
        val speaker = CarDspChoice(enabled = true, mode = SoundMode.CUSTOM, bassDb = 4.0,
            quietListening = 0.5, nightListening = true)
        assertEquals(compared.bands, speaker.settings(comparingCustom = true)!!.bands)
        assertNull(speaker.copy(enabled = false).settings())
    }
}
