package apincer.android.mmate.audio.dsp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Preamp then the bands in series, for one sample rate and channel layout. The preamp cancels the
 * largest boost of the combined response, so the chain itself never raises a peak.
 */
class DspChain(bands: List<PeqBand>, private val sampleRate: Int, private val channels: Int) {
    private val filters = bands.filter { it.frequencyHz < sampleRate * NYQUIST_LIMIT }.map { Biquad.design(it, sampleRate) }
    private val centres = bands.map { it.frequencyHz }

    /** dB, never positive. */
    val preampDb: Double = -max(0.0, maxResponseDb())
    private val preamp = 10.0.pow(preampDb / 20)

    /** Two delay elements per filter per channel (transposed direct form II). */
    private val state = DoubleArray(filters.size * channels * 2)

    /** Combined gain of all bands in dB at [frequencyHz]. */
    fun responseDb(frequencyHz: Double): Double = filters.sumOf { it.magnitudeDb(frequencyHz, sampleRate) }

    /** Filters one interleaved frame in place. */
    fun process(frame: DoubleArray) {
        for (channel in 0 until channels) {
            var x = frame[channel] * preamp
            var s = channel * filters.size * 2
            for (f in filters) {
                val y = f.b0 * x + state[s]
                state[s] = f.b1 * x - f.a1 * y + state[s + 1]
                state[s + 1] = f.b2 * x - f.a2 * y
                x = y
                s += 2
            }
            frame[channel] = x
        }
    }

    fun reset() = state.fill(0.0)

    /** Checks a 1/48-octave grid plus every band centre, so a narrow peak is not stepped over. */
    private fun maxResponseDb(): Double {
        if (filters.isEmpty()) return 0.0
        val top = min(MAX_HZ, sampleRate * NYQUIST_LIMIT)
        var peak = Double.NEGATIVE_INFINITY
        var frequency = MIN_HZ
        while (frequency <= top) {
            peak = max(peak, responseDb(frequency))
            frequency *= GRID_STEP
        }
        for (centre in centres) if (centre in MIN_HZ..top) peak = max(peak, responseDb(centre))
        return peak
    }

    private companion object {
        const val MIN_HZ = 20.0
        const val MAX_HZ = 20_000.0
        const val NYQUIST_LIMIT = 0.45
        val GRID_STEP = 2.0.pow(1.0 / 48)
    }
}
