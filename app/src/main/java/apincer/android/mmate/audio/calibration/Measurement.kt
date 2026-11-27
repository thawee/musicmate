package apincer.android.mmate.audio.calibration

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The 1/48-octave grid every response curve uses, 20 Hz to 20 kHz. */
object FrequencyGrid {
    @JvmField
    val HZ: DoubleArray = generateSequence(20.0) { it * 2.0.pow(1.0 / 48) }.takeWhile { it <= 20_000.0 }.toList().toDoubleArray()
}

/**
 * Exponential sine sweep (Farina, AES 108, 2000) and its inverse filter: the time-reversed sweep
 * with a +6 dB/octave envelope, so sweep convolved with inverse is flat.
 */
class LogSweep(
    val sampleRate: Int = 48_000,
    seconds: Double = 10.0,
    private val startHz: Double = 20.0,
    private val endHz: Double = 20_000.0,
    amplitude: Double = 0.5
) {
    val length = (sampleRate * seconds).roundToInt()
    private val rate = seconds / ln(endHz / startHz)

    val samples = DoubleArray(length) { n ->
        val t = n.toDouble() / sampleRate
        amplitude * sin(2 * PI * startHz * rate * (exp(t / rate) - 1)) * fade(n)
    }

    val inverse = DoubleArray(length) { n ->
        val original = length - 1 - n
        samples[original] * exp(original.toDouble() / sampleRate / rate)
    }

    /** 16-bit PCM for the AudioTrack. */
    fun toPcm16(source: DoubleArray = samples): ShortArray =
        ShortArray(source.size) { (source[it] * 32_767).roundToInt().coerceIn(-32_768, 32_767).toShort() }

    /** Time the sweep crosses a frequency, relative to the beginning of playback. */
    fun timeAtFrequency(hz: Double): Double = rate * ln(hz / startHz)

    private fun fade(n: Int): Double {
        val fadeIn = (sampleRate * 0.05).roundToInt()
        val fadeOut = (sampleRate * 0.005).roundToInt()
        return when {
            n < fadeIn -> 0.5 - 0.5 * cos(PI * n / fadeIn)
            n >= length - fadeOut -> 0.5 - 0.5 * cos(PI * (length - 1 - n) / fadeOut)
            else -> 1.0
        }
    }
}

/**
 * Recording convolved with the inverse sweep, through one 2^20 FFT. The inverse's spectrum is
 * computed once per session. Circular wrap only lands in the first samples, far from the response.
 */
class Deconvolver(val sweep: LogSweep, val size: Int = 1 shl 20) {
    private val fft = Fft(size)
    private val inverseRe = DoubleArray(size)
    private val inverseIm = DoubleArray(size)

    init {
        sweep.inverse.copyInto(inverseRe)
        fft.forward(inverseRe, inverseIm)
    }

    fun deconvolve(recording: DoubleArray): DoubleArray {
        require(recording.size <= size) { "Recording longer than the FFT: ${recording.size}" }
        val re = DoubleArray(size)
        val im = DoubleArray(size)
        recording.copyInto(re)
        fft.forward(re, im)
        for (k in 0 until size) {
            val r = re[k] * inverseRe[k] - im[k] * inverseIm[k]
            im[k] = re[k] * inverseIm[k] + im[k] * inverseRe[k]
            re[k] = r
        }
        fft.inverse(re, im)
        return re
    }
}

/** One position's measurement: accepted with its response, or rejected with the reason. */
sealed class Take {
    /**
     * [responseDb] is on [FrequencyGrid.HZ], 1/6-octave smoothed; level is arbitrary. [bandSnrDb]
     * follows [TakeAnalyzer.SNR_BANDS_HZ]; low bass and a bounded set of noisy bands may be under
     * the limit. Fitting must respect both [TakeAnalyzer.reliableFromHz] and [TakeAnalyzer.unreliableBands].
     */
    class Accepted(val responseDb: DoubleArray, val bandSnrDb: DoubleArray, val delaySeconds: Double) : Take()

    class Rejected(val reason: Reason, val detail: String) : Take()

    /** [CAPTURE] means the recording itself failed; its detail is complete advice for the user. */
    enum class Reason { CLIPPED, NO_RESPONSE, TOO_QUIET, TOO_SHORT, CAPTURE }
}

/** Keeps the session's noise exclusions bounded even when noise changes between positions. */
class MeasurementSession {
    private val positions = mutableListOf<Take.Accepted>()
    val takes: List<Take.Accepted> get() = positions.toList()

    fun clear() = positions.clear()

    /** A rejected position leaves previous measurements intact so the user can retry it. */
    fun add(take: Take.Accepted): Boolean {
        if (TakeAnalyzer.unreliableBands(positions + take).size > TakeAnalyzer.MAX_UNRELIABLE_BANDS) return false
        positions += take
        return true
    }
}

/** Turns one recording of the sweep into a smoothed response, or says why it cannot. */
class TakeAnalyzer(private val deconvolver: Deconvolver) {
    private val sweep = deconvolver.sweep
    private val rate = sweep.sampleRate
    private val preRoll = (rate * PRE_SECONDS).roundToInt()
    private val window = preRoll + (rate * IR_SECONDS).roundToInt()
    private val fadeIn = (rate * FADE_IN_SECONDS).roundToInt()
    private val spectrumFft = Fft(SPECTRUM_SIZE)
    private val ambientFft = Fft(AMBIENT_FFT_SIZE)

    fun analyze(recording: DoubleArray): Take {
        val loudest = recording.maxOfOrNull { abs(it) } ?: 0.0
        if (loudest >= CLIP) return Take.Rejected(Take.Reason.CLIPPED, "Recording reached %.2f of full scale".format(loudest))

        val output = deconvolver.deconvolve(recording)
        // Output sample i sums recording samples i - sweep + 1 .. i, so only i < recording.size holds the full noise
        val validEnd = min(recording.size, deconvolver.size)
        val searchFrom = sweep.length - 1
        val searchTo = min(searchFrom + (rate * MAX_DELAY_SECONDS).roundToInt(), validEnd - 1)
        var peak = searchFrom
        for (i in searchFrom..searchTo) if (abs(output[i]) > abs(output[peak])) peak = i

        val irStart = peak - preRoll
        val noiseStart = peak + (rate * NOISE_OFFSET_SECONDS).roundToInt()
        if (irStart < 0 || noiseStart + window > validEnd) {
            val lateBy = (peak - searchFrom).toDouble() / rate
            val latest = (validEnd - window - (noiseStart - peak) - searchFrom).toDouble() / rate
            return Take.Rejected(Take.Reason.TOO_SHORT, "The sweep arrived %.1f s after recording began; at most %.1f s fits".format(lateBy, latest))
        }

        val irPower = power(output, irStart)
        // A late reflection can occupy the first noise window. Prefer the quieter whole
        // window after the response has decayed, without selecting individual quiet bands.
        // The independent raw before/after noise check below still rejects noisy captures.
        val earlyNoise = power(output, noiseStart)
        val lateNoise = power(output, validEnd - window)
        val noisePower = if (lateNoise.sum() < earlyNoise.sum()) lateNoise else earlyNoise
        val noiseRms = sqrt(noisePower.sum() / noisePower.size)
        val peakRms = sqrt(irPower.sum() / irPower.size)
        if (peakRms < noiseRms * MIN_PEAK_RATIO) {
            return Take.Rejected(Take.Reason.NO_RESPONSE, "No sweep found in the recording")
        }

        val delay = (peak - (sweep.length - 1)).toDouble() / rate
        // The recorder leaves silence before and after playback. Compare it with the raw sweep
        // as well: matched filtering alone can give steady background noise processing gain.
        val ambientLength = (rate * AMBIENT_SECONDS).roundToInt()
        val before = ambientPower(recording, 0, ambientLength)
        val after = ambientPower(recording, recording.size - ambientLength, ambientLength)
        val bandSnr = DoubleArray(SNR_BANDS_HZ.size) { b ->
            val hz = SNR_BANDS_HZ[b]
            val deconvolvedSnr = 10 * log10(bandSum(irPower, hz, 1.0 / 6) / max(bandSum(noisePower, hz, 1.0 / 6), TINY))
            val start = ((delay + sweep.timeAtFrequency(hz)) * rate).roundToInt() - ambientLength / 2
            val playing = ambientPower(recording, start, ambientLength)
            val background = max(bandSum(before, hz, 1.0 / 6, AMBIENT_FFT_SIZE), bandSum(after, hz, 1.0 / 6, AMBIENT_FFT_SIZE))
            val rawSnr = 10 * log10(max(bandSum(playing, hz, 1.0 / 6, AMBIENT_FFT_SIZE), TINY) / max(background, TINY))
            min(deconvolvedSnr, rawSnr)
        }
        // A few contaminated bands can be protected during fitting; broad noise still rejects.
        val noisy = SNR_BANDS_HZ.indices.filter { SNR_BANDS_HZ[it] >= 100.0 && bandSnr[it] < MIN_SNR_DB }
        if (noisy.size > MAX_UNRELIABLE_BANDS) {
            val worst = noisy.minBy { bandSnr[it] }
            return Take.Rejected(Take.Reason.TOO_QUIET, "Only %.0f dB above the noise near %.0f Hz".format(bandSnr[worst], SNR_BANDS_HZ[worst]))
        }
        return Take.Accepted(smoothedDb(irPower, rate), bandSnr, delay)
    }

    private fun ambientPower(signal: DoubleArray, start: Int, length: Int): DoubleArray {
        val re = DoubleArray(AMBIENT_FFT_SIZE)
        val im = DoubleArray(AMBIENT_FFT_SIZE)
        for (i in 0 until length) {
            re[i] = signal[start + i] * (0.5 - 0.5 * cos(2 * PI * i / (length - 1)))
        }
        ambientFft.forward(re, im)
        return DoubleArray(AMBIENT_FFT_SIZE / 2) { re[it] * re[it] + im[it] * im[it] }
    }

    /**
     * Power per FFT bin of a [window]-sample slice of [signal] starting at [start]: a short fade in,
     * then the low-frequency ringing ahead of the peak kept whole, and a fade over the last tenth.
     */
    private fun power(signal: DoubleArray, start: Int): DoubleArray {
        val re = DoubleArray(SPECTRUM_SIZE)
        val im = DoubleArray(SPECTRUM_SIZE)
        val taper = window / 10
        for (i in 0 until window) {
            val fade = when {
                i < fadeIn -> 0.5 - 0.5 * cos(PI * i / fadeIn)
                i >= window - taper -> 0.5 + 0.5 * cos(PI * (i - (window - taper)) / taper)
                else -> 1.0
            }
            re[i] = signal[start + i] * fade
        }
        spectrumFft.forward(re, im)
        return DoubleArray(SPECTRUM_SIZE / 2) { re[it] * re[it] + im[it] * im[it] }
    }

    private fun bandSum(power: DoubleArray, centre: Double, halfWidthOctaves: Double, fftSize: Int = SPECTRUM_SIZE): Double {
        val binHz = rate.toDouble() / fftSize
        val low = (centre * 2.0.pow(-halfWidthOctaves) / binHz).roundToInt().coerceIn(1, power.size - 1)
        val high = (centre * 2.0.pow(halfWidthOctaves) / binHz).roundToInt().coerceIn(low, power.size - 1)
        var sum = 0.0
        for (k in low..high) sum += power[k]
        return sum
    }

    companion object {
        const val SPECTRUM_SIZE = 1 shl 16
        private const val IR_SECONDS = 0.5
        /** Well clear of the harmonic responses, about 1 s ahead of the linear one. */
        private const val PRE_SECONDS = 0.2
        private const val FADE_IN_SECONDS = 0.005
        private const val NOISE_OFFSET_SECONDS = 0.6
        private const val MAX_DELAY_SECONDS = 2.5
        private const val CLIP = 0.99
        const val MIN_SNR_DB = 20.0
        /** At most one octave's worth of bands above 100 Hz may be excluded in a session. */
        const val MAX_UNRELIABLE_BANDS = 3
        private const val AMBIENT_SECONDS = 0.25
        private const val AMBIENT_FFT_SIZE = 1 shl 14
        private const val MIN_PEAK_RATIO = 3.0
        private const val TINY = 1e-30

        /** 1/3-octave centres from 31.5 Hz to 8 kHz, the corrected range. */
        @JvmField
        val SNR_BANDS_HZ: List<Double> = (-15..9).map { 1000.0 * 2.0.pow(it / 3.0) }

        /** Exclude a band if any measured position could not distinguish it from the noise. */
        @JvmStatic
        fun unreliableBands(takes: List<Take.Accepted>): List<Double> = SNR_BANDS_HZ.indices
            .filter { b -> SNR_BANDS_HZ[b] >= 100.0 && takes.any { it.bandSnrDb[b] < MIN_SNR_DB } }
            .map { SNR_BANDS_HZ[it] }

        /**
         * The lowest frequency every take measured cleanly: just above the highest band under the
         * limit below 100 Hz in any take, else 30 Hz. The fit leaves everything below it alone.
         */
        @JvmStatic
        fun reliableFromHz(takes: List<Take.Accepted>): Double {
            var from = 30.0
            for (take in takes) for (b in SNR_BANDS_HZ.indices) {
                if (SNR_BANDS_HZ[b] < 100.0 && take.bandSnrDb[b] < MIN_SNR_DB) from = max(from, SNR_BANDS_HZ[b] * 2.0.pow(1.0 / 6))
            }
            return from
        }

        /** 1/6-octave power smoothing of a power-per-bin spectrum onto [FrequencyGrid.HZ], in dB. */
        @JvmStatic
        fun smoothedDb(power: DoubleArray, sampleRate: Int, fftSize: Int = SPECTRUM_SIZE): DoubleArray {
            val binHz = sampleRate.toDouble() / fftSize
            return DoubleArray(FrequencyGrid.HZ.size) { g ->
                val f = FrequencyGrid.HZ[g]
                val low = (f * 2.0.pow(-1.0 / 12) / binHz).roundToInt().coerceIn(1, power.size - 1)
                val high = (f * 2.0.pow(1.0 / 12) / binHz).roundToInt().coerceIn(low, power.size - 1)
                var sum = 0.0
                for (k in low..high) sum += power[k]
                10 * log10(max(sum / (high - low + 1), TINY))
            }
        }

        /** Average of several takes in power, so one position's null does not dominate. [weights] sum to 1. */
        @JvmStatic
        @JvmOverloads
        fun powerAverage(curves: List<DoubleArray>, weights: List<Double> = List(curves.size) { 1.0 / curves.size }): DoubleArray {
            require(weights.size == curves.size && abs(weights.sum() - 1) < 1e-9) { "${weights.size} weights summing to ${weights.sum()} for ${curves.size} curves" }
            return DoubleArray(FrequencyGrid.HZ.size) { g -> 10 * log10(curves.indices.sumOf { weights[it] * 10.0.pow(curves[it][g] / 10) }) }
        }

        /**
         * How much the takes disagree at each frequency: the standard deviation of their dB values,
         * after removing each take's own level offset over the midrange (a spot 15 cm closer reads
         * louder everywhere, which is not disagreement).
         */
        @JvmStatic
        fun spreadDb(curves: List<DoubleArray>): DoubleArray {
            val grid = FrequencyGrid.HZ
            val mean = DoubleArray(grid.size) { g -> curves.sumOf { it[g] } / curves.size }
            val midrange = grid.indices.filter { grid[it] in SPREAD_ALIGN_HZ }
            val levelled = curves.map { curve ->
                val offset = midrange.map { curve[it] - mean[it] }.sorted().let { it[it.size / 2] }
                DoubleArray(grid.size) { curve[it] - offset }
            }
            return DoubleArray(grid.size) { g ->
                val m = levelled.sumOf { it[g] } / levelled.size
                sqrt(levelled.sumOf { (it[g] - m).pow(2) } / levelled.size)
            }
        }

        private val SPREAD_ALIGN_HZ = 200.0..4_000.0
    }
}
