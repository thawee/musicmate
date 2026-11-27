package apincer.android.mmate.audio.dsp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Crossfeed, then preamp and the bands in series, for one sample rate and channel layout. The preamp
 * keeps the greater attenuation of the requested gain and either ear's combined response's largest
 * boost; crossfeed boosts no frequency, so it adds nothing there. Crossfeed, per-ear bands and
 * attenuation-only balance apply to stereo, so the headphone is corrected after the speakers are
 * simulated. [monoBands] stand in for the per-side bands in every other layout. A per-side delay
 * (stereo only) delays the side nearer the listener; [reset] drops what it holds.
 */
class DspChain(
    bands: List<PeqBand>,
    private val sampleRate: Int,
    private val channels: Int,
    requestedPreampDb: Double = 0.0,
    leftBands: List<PeqBand> = emptyList(),
    rightBands: List<PeqBand> = emptyList(),
    balanceDb: Double = 0.0,
    monoBands: List<PeqBand> = emptyList(),
    crossfeed: Boolean = false,
    leftDelayMs: Double = 0.0,
    rightDelayMs: Double = 0.0
) {
    private val crossfeedFilter = if (crossfeed && channels == 2) Crossfeed(sampleRate) else null
    private val filters = design(bands)
    private val channelFilters = when (channels) {
        1 -> listOf(filters + design(monoBands))
        2 -> listOf(filters + design(leftBands), filters + design(rightBands))
        else -> List(channels) { filters + design(monoBands) }
    }
    private val centres = when (channels) {
        2 -> bands + leftBands + rightBands
        else -> bands + monoBands
    }.map { it.frequencyHz }
    /** One ring per channel; the oldest sample is played and replaced. */
    private val delays = Array(channels) { channel ->
        val ms = if (channels != 2) 0.0 else if (channel == 0) leftDelayMs else rightDelayMs
        DoubleArray((ms.coerceIn(0.0, MAX_DELAY_MS) * sampleRate / 1000).roundToInt())
    }
    private val delayIndex = IntArray(channels)
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

    /** Per-channel EQ response before preamp and balance. */
    internal fun channelResponseDb(frequencyHz: Double, channel: Int): Double =
        channelFilters[channel].sumOf { it.magnitudeDb(frequencyHz, sampleRate) }

    /** Filters one interleaved frame in place. */
    fun process(frame: DoubleArray) {
        crossfeedFilter?.process(frame)
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
            val ring = delays[channel]
            if (ring.isNotEmpty()) {
                val i = delayIndex[channel]
                val delayed = ring[i]
                ring[i] = x
                delayIndex[channel] = if (i + 1 == ring.size) 0 else i + 1
                x = delayed
            }
            frame[channel] = x
        }
    }

    fun reset() {
        crossfeedFilter?.reset()
        state.forEach { it.fill(0.0) }
        delays.forEach { it.fill(0.0) }
        delayIndex.fill(0)
    }

    private fun design(bands: List<PeqBand>): List<Biquad> =
        bands.filter { it.frequencyHz < sampleRate * NYQUIST_LIMIT }.map { Biquad.design(it, sampleRate) }

    private fun combinedResponseDb(frequencyHz: Double): Double =
        channelFilters.maxOfOrNull { ear -> ear.sumOf { it.magnitudeDb(frequencyHz, sampleRate) } } ?: 0.0

    /** Estimates the full PCM band on a 1/48-octave grid, plus band centres, DC and Nyquist. */
    private fun maxResponseDb(): Double {
        if (channelFilters.all { it.isEmpty() }) return 0.0
        val top = sampleRate / 2.0
        var peak = max(combinedResponseDb(0.0), combinedResponseDb(top))
        var frequency = MIN_HZ
        while (frequency <= top) {
            peak = max(peak, combinedResponseDb(frequency))
            frequency *= GRID_STEP
        }
        for (centre in centres) if (centre in MIN_HZ..top) peak = max(peak, combinedResponseDb(centre))
        return peak
    }

    companion object {
        /** Every setting reaches the chain; build it here rather than field by field. */
        fun of(settings: CarDspSettings, sampleRate: Int, channels: Int, requestedPreampDb: Double = settings.requestedPreampDb) =
            DspChain(settings.bands, sampleRate, channels, requestedPreampDb, settings.leftBands, settings.rightBands,
                settings.balanceDb, settings.monoBands, settings.crossfeed, settings.leftDelayMs, settings.rightDelayMs)

        /** About 7 m of path difference; more than any car or room needs. */
        const val MAX_DELAY_MS = 20.0
        private const val MIN_HZ = 1.0
        private const val NYQUIST_LIMIT = 0.45
        private val GRID_STEP = 2.0.pow(1.0 / 48)
    }
}
