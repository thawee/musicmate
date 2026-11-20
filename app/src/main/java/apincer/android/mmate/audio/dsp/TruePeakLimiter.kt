package apincer.android.mmate.audio.dsp

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Linked-channel, streaming lookahead limiter. Peaks include the original samples and all four
 * phases of the 12-tap interpolator in ITU-R BS.1770-5, Annex 2. A supplementary
 * 96-tap, four-times Blackman-windowed sinc detector catches high-frequency boundary overshoot
 * that the short ITU interpolator misses. A 1.2 dB allowance below the -1 dBTP ceiling covers
 * finite interpolation and gain-envelope reconstruction.
 * Like any finite oversampled detector, this is not a mathematical bound on ideal sinc peaks.
 *
 * [process] consumes one frame and withholds output during lookahead; [drain] emits the remaining
 * original frames using zero-extended detector input. Neither call allocates. A completed drain
 * requires [reset] before another stream. Input and output may be the same array.
 */
class TruePeakLimiter(sampleRate: Int, private val channels: Int) {
    val latencyFrames: Int = ceil(sampleRate * 0.005).toInt() + TAPS / 2
    private val capacity = latencyFrames + TAPS + 2
    private val frames: DoubleArray
    private val enabledFrames = BooleanArray(capacity)
    private val history: DoubleArray
    private val peaks = DoubleArray(capacity)
    private val maximumIndices = LongArray(capacity)
    private val maximumValues = DoubleArray(capacity)
    private val attack = 1.0 - exp(-1.0 / (sampleRate * 0.001))
    private val release = 1.0 - exp(-1.0 / (sampleRate * 0.050))
    private var historyPosition = 0
    private var maximumHead = 0
    private var maximumSize = 0
    private var detectorFrames = 0L
    private var receivedFrames = 0L
    private var emittedFrames = 0L
    private var draining = false
    private var gain = 1.0

    /** Number of consumed original frames still awaiting output. */
    val pendingFrames: Int get() = (receivedFrames - emittedFrames).toInt()

    /** Current attenuation in dB (zero means unity). */
    val gainReduction: Double get() = -20.0 * log10(gain)

    /** Current linked limiter gain, before a queued frame's bypass flag is applied. */
    val currentGain: Double get() = gain

    /** Linear gain applied to the most recently emitted frame, including its bypass flag. */
    var lastOutputGain: Double = 1.0
        private set

    init {
        require(sampleRate > 0)
        require(channels > 0)
        frames = DoubleArray(capacity * channels)
        history = DoubleArray(TAPS * channels)
    }

    /**
     * Consumes one [channels]-sample frame; returns whether [output] was written. [enabled]
     * travels with the input frame: disabled frames retain the delay but are emitted at unity.
     */
    fun process(input: DoubleArray, output: DoubleArray, enabled: Boolean = true): Boolean {
        require(input.size >= channels && output.size >= channels)
        check(!draining) { "Reset the limiter after draining before processing another stream" }
        val slot = (receivedFrames % capacity).toInt() * channels
        enabledFrames[(receivedFrames % capacity).toInt()] = enabled
        for (channel in 0 until channels) frames[slot + channel] = input[channel]
        receivedFrames++
        detect(input)
        if (receivedFrames - emittedFrames <= latencyFrames) return false
        emit(output)
        return true
    }

    /** Emits one pending original frame, or returns false when none remain. */
    fun drain(output: DoubleArray): Boolean {
        require(output.size >= channels)
        draining = true
        if (pendingFrames == 0) return false
        // A short stream has not yet filled the detector's future context.
        while (detectorFrames <= emittedFrames + latencyFrames) detect(null)
        emit(output)
        return true
    }

    fun reset() {
        frames.fill(0.0)
        enabledFrames.fill(false)
        history.fill(0.0)
        peaks.fill(0.0)
        maximumHead = 0
        maximumSize = 0
        historyPosition = 0
        detectorFrames = 0
        receivedFrames = 0
        emittedFrames = 0
        draining = false
        gain = 1.0
        lastOutputGain = 1.0
    }

    private fun detect(input: DoubleArray?) {
        var peak = 0.0
        for (channel in 0 until channels) {
            val sample = input?.get(channel) ?: 0.0
            history[historyPosition * channels + channel] = sample
            peak = maxOf(peak, abs(sample))
            for (phase in PHASES) {
                var interpolated = 0.0
                var position = historyPosition
                for (tap in phase.indices) {
                    interpolated += phase[tap] * history[position * channels + channel]
                    if (--position < 0) position = TAPS - 1
                }
                peak = maxOf(peak, abs(interpolated))
            }
            for (phase in SUPPLEMENTARY_PHASES) {
                var interpolated = 0.0
                var position = historyPosition
                for (tap in phase.indices) {
                    interpolated += phase[tap] * history[position * channels + channel]
                    if (--position < 0) position = TAPS - 1
                }
                peak = maxOf(peak, abs(interpolated))
            }
        }
        historyPosition = (historyPosition + 1) % TAPS
        peaks[(detectorFrames % capacity).toInt()] = peak
        val oldest = emittedFrames - TAPS
        while (maximumSize > 0 && maximumIndices[maximumHead] < oldest) {
            maximumHead = (maximumHead + 1) % capacity
            maximumSize--
        }
        while (maximumSize > 0 && maximumValues[(maximumHead + maximumSize - 1) % capacity] <= peak) {
            maximumSize--
        }
        val tail = (maximumHead + maximumSize) % capacity
        maximumIndices[tail] = detectorFrames
        maximumValues[tail] = peak
        maximumSize++
        detectorFrames++
        val target = requiredGain(maximumValues[maximumHead])
        gain += (target - gain) * if (target < gain) attack else release
    }

    private fun emit(output: DoubleArray) {
        // Every FIR result involving this original frame is known before it is emitted. This
        // immediate safety constraint catches attacks that have not settled during lookahead.
        var nearbyPeak = 0.0
        for (offset in 0 until TAPS) {
            val index = emittedFrames + offset
            nearbyPeak = maxOf(nearbyPeak, peaks[(index % capacity).toInt()])
        }
        gain = min(gain, requiredGain(nearbyPeak))
        val slot = (emittedFrames % capacity).toInt() * channels
        val outputGain = if (enabledFrames[(emittedFrames % capacity).toInt()]) gain else 1.0
        lastOutputGain = outputGain
        for (channel in 0 until channels) output[channel] = frames[slot + channel] * outputGain
        emittedFrames++
    }

    private fun requiredGain(peak: Double): Double = if (peak > DETECTOR_CEILING) DETECTOR_CEILING / peak else 1.0

    private companion object {
        const val TAPS = 96
        val DETECTOR_CEILING = 10.0.pow(-2.2 / 20.0)
        val SUPPLEMENTARY_PHASES = Array(3) { phase ->
            DoubleArray(TAPS) { tap ->
                val distance = tap - (TAPS / 2 - 1) - (phase + 1) / 4.0
                val sinc = if (abs(distance) < 1e-12) 1.0 else sin(PI * distance) / (PI * distance)
                val window = if (abs(distance) >= TAPS / 2) 0.0 else
                    0.42 + 0.5 * cos(2 * PI * distance / TAPS) + 0.08 * cos(4 * PI * distance / TAPS)
                sinc * window
            }
        }
        val PHASES = arrayOf(
            doubleArrayOf(0.001708984375, 0.010986328125, -0.0196533203125, 0.033203125,
                -0.0594482421875, 0.1373291015625, 0.97216796875, -0.102294921875,
                0.047607421875, -0.026611328125, 0.014892578125, -0.00830078125),
            doubleArrayOf(-0.0291748046875, 0.029296875, -0.0517578125, 0.089111328125,
                -0.16650390625, 0.465087890625, 0.77978515625, -0.2003173828125,
                0.1015625, -0.0582275390625, 0.0330810546875, -0.0189208984375),
            doubleArrayOf(-0.0189208984375, 0.0330810546875, -0.0582275390625, 0.1015625,
                -0.2003173828125, 0.77978515625, 0.465087890625, -0.16650390625,
                0.089111328125, -0.0517578125, 0.029296875, -0.0291748046875),
            doubleArrayOf(-0.00830078125, 0.014892578125, -0.026611328125, 0.047607421875,
                -0.102294921875, 0.97216796875, 0.1373291015625, -0.0594482421875,
                0.033203125, -0.0196533203125, 0.010986328125, 0.001708984375)
        )
    }
}
