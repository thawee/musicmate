package apincer.android.mmate.audio.dsp

import android.media.AudioDeviceInfo
import android.util.Log
import apincer.android.mmate.audio.UsbOutputFormat
import apincer.android.mmate.audio.headphones.HeadphoneDsp
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * What Car DSP applies: the car correction plus the sound mode, as one fixed band list, and
 * whether the light compressor runs (Night only).
 */
class CarDspSettings(
    bands: List<PeqBand>,
    val compress: Boolean = false,
    val requestedPreampDb: Double = 0.0,
    leftBands: List<PeqBand> = emptyList(),
    rightBands: List<PeqBand> = emptyList(),
    val balanceDb: Double = 0.0,
    monoBands: List<PeqBand> = emptyList()
) {
    val bands: List<PeqBand> = bands.toList()
    val leftBands: List<PeqBand> = leftBands.toList()
    val rightBands: List<PeqBand> = rightBands.toList()
    val monoBands: List<PeqBand> = monoBands.toList()
}

/**
 * The switch every audio output reads at each block. USB remains untouched unless the headphone
 * route selection supplies an explicitly enabled correction.
 */
object CarDsp {
    @JvmStatic
    @Volatile
    var target: CarDspSettings? = null

    /** Closed until the first route report, so a missed report never lets DSP reach a USB DAC. */
    @JvmStatic
    val routeEligible: Boolean get() = HeadphoneDsp.current.speakerEligible

    @JvmStatic
    fun effectiveTarget(): CarDspSettings? = effectiveTarget(HeadphoneDsp.current)

    internal fun effectiveTarget(headphone: HeadphoneDsp.Selection): CarDspSettings? =
        if (headphone.handlesRoute) headphone.settings else target.takeIf { headphone.speakerEligible }

    /** Call with the devices Android routes media to. */
    @JvmStatic
    fun updateRoute(routed: List<AudioDeviceInfo>) {
        HeadphoneDsp.updateRoute(routed)
    }
}

/**
 * Applies [CarDsp]'s target to one output's PCM: chain, 30 ms crossfade on target changes, the light
 * compressor when requested, then a linked safety limiter. Unconsented USB bypass is immediate.
 * Changes are picked up only at a block boundary, so a block is processed once even when the
 * AudioTrack takes it in several writes.
 */
class CarDspProcessor(private val sampleRate: Int, private val channels: Int, seed: Long = System.nanoTime()) {
    private var settings: CarDspSettings? = CarDsp.effectiveTarget()
    private var chain: DspChain? = settings?.let {
        DspChain(it.bands, sampleRate, channels, it.requestedPreampDb, it.leftBands, it.rightBands, it.balanceDb, it.monoBands)
    }
    private var fadeFrom: DspChain? = null
    private var fading = false
    private var fadePosition = 0
    private val fadeFrames = max(1, (sampleRate * FADE_SECONDS).roundToInt())
    private val frame = DoubleArray(channels)
    private val previous = DoubleArray(channels)
    private var limiterGain = 1.0
    private val release = 1 - exp(-1 / (sampleRate * RELEASE_SECONDS))
    private var compressorGain = 1.0
    private val compressorAttack = 1 - exp(-1 / (sampleRate * COMPRESSOR_ATTACK_SECONDS))
    private val compressorRelease = 1 - exp(-1 / (sampleRate * COMPRESSOR_RELEASE_SECONDS))
    private var random = seed or 1L
    private var engaged = false
    private var warnedInput = false
    private var warnedOutput = false

    /** Takes up a changed target; true while this block needs processing. */
    fun beginBlock(): Boolean {
        val route = HeadphoneDsp.current
        val wanted = CarDsp.effectiveTarget(route)
        if (!route.speakerEligible && wanted == null) {
            // A route change or revoked USB consent must not carry the old correction into USB.
            settings = null
            chain = null
            fadeFrom = null
            fading = false
            compressorGain = 1.0
            limiterGain = 1.0
        } else if (!fading && wanted !== settings) {
            // Finish the current mix before taking the latest target. Replacing fadeFrom
            // mid-fade jumps from the audible mix to just one of its chains and can click.
            fadeFrom = chain
            settings = wanted
            chain = wanted?.let {
                DspChain(it.bands, sampleRate, channels, it.requestedPreampDb, it.leftBands, it.rightBands, it.balanceDb, it.monoBands)
            }
            fading = true
            fadePosition = 0
        }
        val now = chain != null || fading || compressorGain < RECOVERED
        if (now != engaged) {
            engaged = now
            Log.i(TAG, if (now) "engaged at $sampleRate Hz, $channels ch" else "bypassed")
        }
        return now
    }

    /** After a seek: clear filter state and land on the current target without a fade. */
    fun reset() {
        settings = CarDsp.effectiveTarget()
        chain = settings?.let {
            DspChain(it.bands, sampleRate, channels, it.requestedPreampDb, it.leftBands, it.rightBands, it.balanceDb, it.monoBands)
        }
        fadeFrom = null
        fading = false
        limiterGain = 1.0
        compressorGain = 1.0
    }

    /** Processes the remaining float samples of [source] into little-endian 24-bit PCM. */
    fun processFloatToInt24(source: ByteBuffer, reuse: ByteBuffer): ByteBuffer {
        val samples = source.remaining() / 4
        val target = buffer(reuse, samples * 3)
        var position = source.position()
        repeat(samples / channels) {
            for (c in 0 until channels) {
                frame[c] = finite(source.getFloat(position).toDouble())
                position += 4
            }
            processFrame()
            for (c in 0 until channels) putInt24(target, (frame[c] * INT24_SCALE).roundToInt())
        }
        repeat(samples % channels) {
            putInt24(target, (finite(source.getFloat(position).toDouble()) * INT24_SCALE).roundToInt())
            position += 4
        }
        target.flip()
        return target
    }

    /** Processes the remaining 16-bit samples of [source] into TPDF-dithered little-endian 16-bit PCM. */
    fun processInt16(source: ByteBuffer, reuse: ByteBuffer): ByteBuffer {
        val samples = source.remaining() / 2
        val target = buffer(reuse, samples * 2)
        var position = source.position()
        repeat(samples / channels) {
            for (c in 0 until channels) {
                frame[c] = source.getShort(position) / INT16_SCALE
                position += 2
            }
            processFrame()
            for (c in 0 until channels) putInt16(target, (frame[c] * INT16_SCALE + dither()).roundToInt())
        }
        repeat(samples % channels) {
            putInt16(target, source.getShort(position).toInt())
            position += 2
        }
        target.flip()
        return target
    }

    /** Processes packed little-endian 24-bit samples without changing [source]'s position. */
    fun processInt24(source: ByteBuffer, reuse: ByteBuffer): ByteBuffer = processInteger(source, reuse, 3, INT24_SCALE)

    /** Processes little-endian 32-bit samples without changing [source]'s position. */
    fun processInt32(source: ByteBuffer, reuse: ByteBuffer): ByteBuffer = processInteger(source, reuse, 4, INT32_SCALE)

    private fun processInteger(source: ByteBuffer, reuse: ByteBuffer, width: Int, scale: Double): ByteBuffer {
        val samples = source.remaining() / width
        val target = buffer(reuse, samples * width)
        var position = source.position()
        repeat(samples / channels) {
            for (c in 0 until channels) {
                frame[c] = readInteger(source, position, width) / scale
                position += width
            }
            processFrame()
            for (c in 0 until channels) {
                val value = (frame[c] * scale).roundToLong().coerceIn(-scale.toLong(), scale.toLong() - 1).toInt()
                putInteger(target, value, width)
            }
        }
        repeat(samples % channels) {
            putInteger(target, readInteger(source, position, width), width)
            position += width
        }
        target.flip()
        return target
    }

    private fun processFrame() {
        if (fading) {
            System.arraycopy(frame, 0, previous, 0, channels)
            fadeFrom?.process(previous)
            chain?.process(frame)
            val mix = (fadePosition + 1).toDouble() / fadeFrames
            for (c in 0 until channels) frame[c] = previous[c] + (frame[c] - previous[c]) * mix
            if (++fadePosition >= fadeFrames) {
                fading = false
                fadeFrom = null
            }
        } else {
            chain?.process(frame)
        }
        compress()
        limit()
        if (frame.any { !it.isFinite() }) recover()
    }

    /** A decoder's NaN or infinity becomes silence, so one bad sample cannot stop playback. */
    private fun finite(sample: Double): Double {
        if (sample.isFinite()) return sample
        if (!warnedInput) {
            warnedInput = true
            Log.w(TAG, "Non-finite input sample at $sampleRate Hz replaced with silence")
        }
        return 0.0
    }

    /** Last line of defence: silence this frame and start the filters afresh. */
    private fun recover() {
        if (!warnedOutput) {
            warnedOutput = true
            Log.w(TAG, "DSP output went non-finite at $sampleRate Hz; filters reset")
        }
        frame.fill(0.0)
        chain?.reset()
        fadeFrom?.reset()
        compressorGain = 1.0
        limiterGain = 1.0
    }

    /**
     * 2:1 above [COMPRESSOR_THRESHOLD_DB] with a 6 dB soft knee, linked across channels, no makeup
     * gain: loud moments come down and quiet ones pass untouched. With the target not asking for
     * it the gain only releases back to unity, so switching mode never jumps in level.
     */
    private fun compress() {
        var wanted = 1.0
        if (settings?.compress == true) {
            var peak = 0.0
            for (c in 0 until channels) peak = max(peak, abs(frame[c]))
            if (peak > KNEE_START) {
                val over = 20 * log10(peak) - COMPRESSOR_THRESHOLD_DB
                val reduction = if (over < KNEE_DB / 2) (over + KNEE_DB / 2).pow(2) / (2 * KNEE_DB) else over
                wanted = 10.0.pow(-reduction * (1 - 1 / COMPRESSOR_RATIO) / 20)
            }
        } else if (compressorGain >= RECOVERED) {
            compressorGain = 1.0
            return
        }
        compressorGain += (wanted - compressorGain) * if (wanted < compressorGain) compressorAttack else compressorRelease
        for (c in 0 until channels) frame[c] *= compressorGain
    }

    /** Instant attack, smooth release, same gain on every channel so the image does not shift. */
    private fun limit() {
        var peak = 0.0
        for (c in 0 until channels) peak = max(peak, abs(frame[c]))
        limiterGain += (1 - limiterGain) * release
        if (peak * limiterGain > CEILING) limiterGain = CEILING / peak
        if (limiterGain < 1) for (c in 0 until channels) frame[c] *= limiterGain
    }

    /** Triangular dither of +/-1 LSB from two uniform values (xorshift64). */
    private fun dither(): Double = uniform() + uniform() - 1.0

    private fun uniform(): Double {
        random = random xor (random shl 13)
        random = random xor (random ushr 7)
        random = random xor (random shl 17)
        return (random ushr 11) * (1.0 / (1L shl 53))
    }

    private companion object {
        const val TAG = "CarDsp"
        const val FADE_SECONDS = 0.03
        const val RELEASE_SECONDS = 0.05
        /** -0.5 dBFS. */
        const val CEILING = 0.944
        const val COMPRESSOR_THRESHOLD_DB = -20.0
        const val COMPRESSOR_RATIO = 2.0
        const val KNEE_DB = 6.0
        /** Where the knee begins, -23 dBFS. */
        val KNEE_START = 10.0.pow((COMPRESSOR_THRESHOLD_DB - KNEE_DB / 2) / 20)
        const val COMPRESSOR_ATTACK_SECONDS = 0.01
        const val COMPRESSOR_RELEASE_SECONDS = 0.4
        /** Within 0.01 dB of unity. */
        const val RECOVERED = 0.9988
        const val INT24_SCALE = 8_388_608.0
        const val INT16_SCALE = 32_768.0
        const val INT32_SCALE = 2_147_483_648.0

        fun readInteger(source: ByteBuffer, position: Int, width: Int): Int {
            var value = source.get(position + width - 1).toInt()
            for (byte in width - 2 downTo 0) value = (value shl 8) or (source.get(position + byte).toInt() and 0xff)
            return value
        }

        fun putInteger(target: ByteBuffer, value: Int, width: Int) {
            repeat(width) { target.put((value shr (8 * it)).toByte()) }
        }

        fun buffer(reuse: ByteBuffer, bytes: Int): ByteBuffer =
            if (reuse.capacity() >= bytes) reuse.apply { clear() } else ByteBuffer.allocateDirect(bytes)

        fun putInt24(target: ByteBuffer, value: Int) {
            val v = value.coerceIn(-8_388_608, 8_388_607)
            target.put(v.toByte()).put((v shr 8).toByte()).put((v shr 16).toByte())
        }

        fun putInt16(target: ByteBuffer, value: Int) {
            val v = value.coerceIn(-32_768, 32_767)
            target.put(v.toByte()).put((v shr 8).toByte())
        }
    }
}
