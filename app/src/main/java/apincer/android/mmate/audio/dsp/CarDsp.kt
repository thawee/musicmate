package apincer.android.mmate.audio.dsp

import android.media.AudioDeviceInfo
import android.util.Log
import apincer.android.mmate.audio.UsbOutputFormat
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/** What Car DSP applies: the car correction plus the sound mode, as one fixed band list. */
class CarDspSettings(bands: List<PeqBand>) {
    val bands: List<PeqBand> = bands.toList()
}

/**
 * The switch every audio output reads at each block. A USB DAC route is never processed, so the
 * bit-perfect path stays untouched whatever the target says (docs/technical/CAR_AUDIO_DSP.md).
 */
object CarDsp {
    @JvmStatic
    @Volatile
    var target: CarDspSettings? = null

    /** Closed until the first route report, so a missed report never lets DSP reach a USB DAC. */
    @JvmStatic
    @Volatile
    var routeEligible = false
        private set

    @JvmStatic
    fun effectiveTarget(): CarDspSettings? = target.takeIf { routeEligible }

    /** Call with the devices Android routes media to. */
    @JvmStatic
    fun updateRoute(routed: List<AudioDeviceInfo>) {
        routeEligible = routed.none { UsbOutputFormat.isUsbType(it.type) }
    }
}

/**
 * Applies [CarDsp]'s target to one output's PCM: chain, 30 ms crossfade on every change (on and
 * off included), then a linked safety limiter. Changes are picked up only at a block boundary, so
 * a block is processed once even when the AudioTrack takes it in several writes.
 */
class CarDspProcessor(private val sampleRate: Int, private val channels: Int, seed: Long = System.nanoTime()) {
    private var settings: CarDspSettings? = CarDsp.effectiveTarget()
    private var chain: DspChain? = settings?.let { DspChain(it.bands, sampleRate, channels) }
    private var fadeFrom: DspChain? = null
    private var fading = false
    private var fadePosition = 0
    private val fadeFrames = max(1, (sampleRate * FADE_SECONDS).roundToInt())
    private val frame = DoubleArray(channels)
    private val previous = DoubleArray(channels)
    private var limiterGain = 1.0
    private val release = 1 - exp(-1 / (sampleRate * RELEASE_SECONDS))
    private var random = seed or 1L
    private var engaged = false

    /** Takes up a changed target; true while this block needs processing. */
    fun beginBlock(): Boolean {
        val wanted = CarDsp.effectiveTarget()
        if (wanted !== settings) {
            fadeFrom = chain
            settings = wanted
            chain = wanted?.let { DspChain(it.bands, sampleRate, channels) }
            fading = true
            fadePosition = 0
        }
        val now = chain != null || fading
        if (now != engaged) {
            engaged = now
            Log.i(TAG, if (now) "engaged at $sampleRate Hz, $channels ch" else "bypassed")
        }
        return now
    }

    /** After a seek: clear filter state and land on the current target without a fade. */
    fun reset() {
        settings = CarDsp.effectiveTarget()
        chain = settings?.let { DspChain(it.bands, sampleRate, channels) }
        fadeFrom = null
        fading = false
        limiterGain = 1.0
    }

    /** Processes the remaining float samples of [source] into little-endian 24-bit PCM. */
    fun processFloatToInt24(source: ByteBuffer, reuse: ByteBuffer): ByteBuffer {
        val samples = source.remaining() / 4
        val target = buffer(reuse, samples * 3)
        var position = source.position()
        repeat(samples / channels) {
            for (c in 0 until channels) {
                frame[c] = source.getFloat(position).toDouble()
                position += 4
            }
            processFrame()
            for (c in 0 until channels) putInt24(target, (frame[c] * INT24_SCALE).roundToInt())
        }
        repeat(samples % channels) {
            putInt24(target, (source.getFloat(position) * INT24_SCALE).roundToInt())
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
        limit()
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
        const val INT24_SCALE = 8_388_608.0
        const val INT16_SCALE = 32_768.0

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
