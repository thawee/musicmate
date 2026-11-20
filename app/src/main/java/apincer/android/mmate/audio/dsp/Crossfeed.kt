package apincer.android.mmate.audio.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * Bauer stereophonic-to-binaural crossfeed, after libbs2b by Boris Mikhaylov (MIT licence), at its
 * default level (700 Hz, 4.5 dB). Each ear also hears the other channel low-passed, as it would
 * from speakers, while its own lows are cut to match, so centred bass stays at its level and
 * centred treble sits 1.8 dB lower. No frequency is boosted, but a full-scale anti-phase transient
 * can peak about 2 dB higher; the safety limiter after the chain catches it. Coefficients come
 * from the real sample rate, without libbs2b's fallback to 44.1 kHz outside 2 to 384 kHz.
 */
class Crossfeed(sampleRate: Int) {
    private val a0Lo: Double
    private val b1Lo: Double
    private val a0Hi: Double
    private val a1Hi: Double
    private val b1Hi: Double
    private val gain: Double
    private val lo = DoubleArray(2)
    private val hi = DoubleArray(2)
    private val previous = DoubleArray(2)

    init {
        val lowGainDb = FEED_DB * -5 / 6 - 3
        val highGainDb = FEED_DB / 6 - 3
        val lowGain = 10.0.pow(lowGainDb / 20)
        val highCut = 1 - 10.0.pow(highGainDb / 20)
        val highHz = CUT_HZ * 2.0.pow((lowGainDb - 20 * log10(highCut)) / 12)
        val x = exp(-2 * PI * CUT_HZ / sampleRate)
        b1Lo = x
        a0Lo = lowGain * (1 - x)
        val y = exp(-2 * PI * highHz / sampleRate)
        b1Hi = y
        a0Hi = 1 - highCut * (1 - y)
        a1Hi = -y
        gain = 1 / (1 - highCut + lowGain)
    }

    /** Crossfeeds one stereo frame in place. */
    fun process(frame: DoubleArray) {
        for (c in 0..1) {
            lo[c] = a0Lo * frame[c] + b1Lo * lo[c]
            hi[c] = a0Hi * frame[c] + a1Hi * previous[c] + b1Hi * hi[c]
            previous[c] = frame[c]
        }
        frame[0] = (hi[0] + lo[1]) * gain
        frame[1] = (hi[1] + lo[0]) * gain
    }

    fun reset() {
        lo.fill(0.0)
        hi.fill(0.0)
        previous.fill(0.0)
    }

    private companion object {
        const val CUT_HZ = 700.0
        const val FEED_DB = 4.5
    }
}
