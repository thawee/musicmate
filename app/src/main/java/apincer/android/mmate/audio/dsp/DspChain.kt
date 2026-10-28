package apincer.android.mmate.audio.dsp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Preamp then the bands in series, for one sample rate and channel layout. The preamp keeps the
 * greater attenuation of the requested gain and either ear's combined response's largest boost.
 * Per-ear bands and attenuation-only balance apply to stereo. Mono has its own optional correction;
 * other layouts use shared bands only.
 */
class DspChain(
    bands: List<PeqBand>,
    private val sampleRate: Int,
    private val channels: Int,
    requestedPreampDb: Double = 0.0,
    leftBands: List<PeqBand> = emptyList(),
    rightBands: List<PeqBand> = emptyList(),
    balanceDb: Double = 0.0,
    monoBands: List<PeqBand> = emptyList()
) {
    private val filters = design(bands)
    private val channelFilters = when (channels) {
        1 -> listOf(filters + design(monoBands))
        2 -> listOf(filters + design(leftBands), filters + design(rightBands))
        else -> List(channels) { filters }
    }
    private val centres = when (channels) {
        1 -> bands + monoBands
        2 -> bands + leftBands + rightBands
        else -> bands
    }.map { it.frequencyHz }
    private val channelGains = DoubleArray(channels) { channel ->
        val attenuationDb = when {
            channels != 2 -> 0.0
            channel == 0 -> -balanceDb.coerceAtLeast(0.0)
            else -> balanceDb.coerceAtMost(0.0)
        }
        10.0.pow(attenuationDb / 20)
    }

    /** dB, never positive. */
    val preampDb: Double = min(requestedPreampDb.coerceAtMost(0.0), -max(0.0, maxResponseDb()))
    private val preamp = 10.0.pow(preampDb / 20)

    /** Two delay elements per filter per channel (transposed direct form II). */
    private val state = Array(channels) { DoubleArray(channelFilters[it].size * 2) }

    /** Combined gain of the shared bands in dB at [frequencyHz]. */
    fun responseDb(frequencyHz: Double): Double = filters.sumOf { it.magnitudeDb(frequencyHz, sampleRate) }

    /** Filters one interleaved frame in place. */
    fun process(frame: DoubleArray) {
        for (channel in 0 until channels) {
            var x = frame[channel] * preamp * channelGains[channel]
            val delay = state[channel]
            var s = 0
            for (f in channelFilters[channel]) {
                val y = f.b0 * x + delay[s]
                delay[s] = f.b1 * x - f.a1 * y + delay[s + 1]
                delay[s + 1] = f.b2 * x - f.a2 * y
                x = y
                s += 2
            }
            frame[channel] = x
        }
    }

    fun reset() = state.forEach { it.fill(0.0) }

    private fun design(bands: List<PeqBand>): List<Biquad> =
        bands.filter { it.frequencyHz < sampleRate * NYQUIST_LIMIT }.map { Biquad.design(it, sampleRate) }

    private fun combinedResponseDb(frequencyHz: Double): Double =
        channelFilters.maxOfOrNull { ear -> ear.sumOf { it.magnitudeDb(frequencyHz, sampleRate) } } ?: 0.0

    /** Checks a 1/48-octave grid plus every band centre, so a narrow peak is not stepped over. */
    private fun maxResponseDb(): Double {
        if (channelFilters.all { it.isEmpty() }) return 0.0
        val top = min(MAX_HZ, sampleRate * NYQUIST_LIMIT)
        var peak = Double.NEGATIVE_INFINITY
        var frequency = MIN_HZ
        while (frequency <= top) {
            peak = max(peak, combinedResponseDb(frequency))
            frequency *= GRID_STEP
        }
        for (centre in centres) if (centre in MIN_HZ..top) peak = max(peak, combinedResponseDb(centre))
        return peak
    }

    private companion object {
        const val MIN_HZ = 20.0
        const val MAX_HZ = 20_000.0
        const val NYQUIST_LIMIT = 0.45
        val GRID_STEP = 2.0.pow(1.0 / 48)
    }
}
