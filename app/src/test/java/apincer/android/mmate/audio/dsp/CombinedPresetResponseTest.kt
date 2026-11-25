package apincer.android.mmate.audio.dsp

import apincer.android.mmate.audio.headphones.HeadphoneBinding
import apincer.android.mmate.audio.headphones.HeadphoneDsp
import apincer.android.mmate.audio.headphones.HeadphoneProfile
import apincer.android.mmate.audio.headphones.HeadphoneProfileParser
import apincer.android.mmate.audio.headphones.HeadphoneTone
import apincer.android.mmate.audio.headphones.HeadphoneTonePreset
import apincer.android.mmate.audio.headphones.ManualHeadphoneTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

class CombinedPresetResponseTest {
    private val sampleRates = listOf(44_100, 48_000, 96_000, 192_000)
    private val asymmetric = ManualHeadphoneTuning(
        left = HeadphoneTone(bassDb = 6.0, warmthDb = 3.0, midDb = -4.0, clarityDb = 2.0, trebleDb = -2.0),
        right = HeadphoneTone(bassDb = -3.0, warmthDb = -2.0, midDb = 6.0, clarityDb = -3.0, trebleDb = 6.0),
        balanceDb = 3.0
    )

    @Test
    fun everyManualPresetAndSelectableModeHasHeadroomAcrossRatesAndLayouts() {
        for (preset in HeadphoneTonePreset.entries) {
            val tuning = ManualHeadphoneTuning(left = preset.tone, right = preset.tone)
            sweep(HeadphoneProfile.createManual(preset.name, tuning))
        }
    }

    @Test
    fun asymmetricEarsAndMonoAverageHaveHeadroomWithEverySelectableMode() {
        sweep(HeadphoneProfile.createManual("Asymmetric", asymmetric))
        sweep(HeadphoneProfile.createManual("Swapped ears", asymmetric.copy(
            left = asymmetric.right, right = asymmetric.left, balanceDb = -asymmetric.balanceDb
        )))
    }

    @Test
    fun importedCorrectionAndSelectableModesKeepRequestedAndAutomaticHeadroom() {
        val profile = HeadphoneProfileParser.parse(
            """
                Preamp: -3.5 dB
                Filter 1: ON LSC Fc 95 Hz Gain 5.5 dB Q 0.71
                Filter 2: ON PK Fc 280 Hz Gain -3 dB Q 1.2
                Filter 3: ON PK Fc 2700 Hz Gain 4 dB Q 2.5
                Filter 4: ON PK Fc 6100 Hz Gain -5 dB Q 4
                Filter 5: ON HSC Fc 10500 Hz Gain 3 dB Q 0.71
            """.trimIndent(), "Imported correction", "sample.txt"
        )
        sweep(profile)
    }

    @Test
    fun combinedChannelResponseMatchesProcessedStereoAndMonoTones() {
        val profile = HeadphoneProfile.createManual("Asymmetric", asymmetric)
        val settings = HeadphoneDsp.settingsFor(profile, HeadphoneBinding(soundMode = SoundMode.LIVELY))
        for (rate in sampleRates) for (channels in listOf(1, 2)) {
            for (frequency in listOf(200.0, 1_000.0, 8_000.0)) {
                val chain = chain(settings, rate, channels)
                val frame = DoubleArray(channels)
                val power = DoubleArray(channels)
                var inputPower = 0.0
                repeat(rate / 4) { sample ->
                    val input = 0.1 * sin(2 * PI * frequency * sample / rate)
                    frame.fill(input)
                    chain.process(frame)
                    if (sample >= rate / 8) {
                        inputPower += input * input
                        for (channel in frame.indices) power[channel] += frame[channel] * frame[channel]
                    }
                }
                for (channel in frame.indices) {
                    val balance = if (channels == 2 && channel == 0) -asymmetric.balanceDb else 0.0
                    val expected = chain.channelResponseDb(frequency, channel) + chain.preampDb + balance
                    val measured = 10 * log10(power[channel] / inputPower)
                    assertEquals("$rate Hz, $channels channels, channel $channel, $frequency Hz",
                        expected, measured, 0.03)
                }
            }
        }
    }

    private fun sweep(profile: HeadphoneProfile) {
        for (mode in SoundMode.selectable) {
            val trims = if (mode == SoundMode.CUSTOM) {
                listOf(0.0 to 0.0, -4.0 to -4.0, -4.0 to 4.0, 4.0 to -4.0, 4.0 to 4.0)
            } else listOf(0.0 to 0.0)
            for ((bass, treble) in trims) {
                val settings = HeadphoneDsp.settingsFor(profile,
                    HeadphoneBinding(soundMode = mode, bassDb = bass, trebleDb = treble))
                for (rate in sampleRates) for (channels in listOf(1, 2)) {
                    val label = "${profile.name}, $mode ($bass/$treble), $rate Hz, $channels channels"
                    val chain = chain(settings, rate, channels)
                    assertTrue(label, chain.preampDb.isFinite() && chain.preampDb <= settings.requestedPreampDb)
                    val frequencies = buildList {
                        add(0.0)
                        add(rate / 2.0)
                        var frequency = 1.0
                        val step = 2.0.pow(1.0 / 192)
                        while (frequency < rate / 2.0) {
                            add(frequency)
                            frequency *= step
                        }
                        addAll((settings.bands + settings.leftBands + settings.rightBands + settings.monoBands)
                            .map { it.frequencyHz }.filter { it < rate / 2.0 })
                    }
                    for (channel in 0 until channels) {
                        var peakDb = Double.NEGATIVE_INFINITY
                        var peakHz = 0.0
                        for (frequency in frequencies) {
                            val response = chain.channelResponseDb(frequency, channel)
                            assertTrue(label, response.isFinite())
                            if (response > peakDb) {
                                peakDb = response
                                peakHz = frequency
                            }
                        }
                        assertTrue("$label, channel $channel: net ${peakDb + chain.preampDb} dB at $peakHz Hz",
                            peakDb + chain.preampDb <= 0.02)
                    }
                    val frame = DoubleArray(channels)
                    repeat(2_048) { sample ->
                        for (channel in frame.indices) {
                            frame[channel] = if (sample == 0) 0.5 else {
                                0.2 * sin(sample * (0.13 + channel * 0.07)) + 0.1 * sin(sample * 0.71)
                            }
                        }
                        chain.process(frame)
                        assertTrue(label, frame.all { it.isFinite() })
                    }
                }
            }
        }
    }

    private fun chain(settings: CarDspSettings, rate: Int, channels: Int) = DspChain(
        settings.bands, rate, channels, settings.requestedPreampDb,
        settings.leftBands, settings.rightBands, settings.balanceDb, settings.monoBands, settings.crossfeed
    )
}
