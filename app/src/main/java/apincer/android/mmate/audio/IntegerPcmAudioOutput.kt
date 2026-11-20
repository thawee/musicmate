package apincer.android.mmate.audio

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.ForwardingAudioOutput
import apincer.android.mmate.audio.dsp.CarDspProcessor
import apincer.android.mmate.audio.headphones.HeadphoneDsp
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Hands Media3's PCM to Android as integer samples and feeds the VU meters on the way.
 * Float and 16-bit input become 24-bit integer: float PCM distorts on Bluetooth A2DP and some
 * device mixers (tasks/lessons.md), 24 bits hold a 24-bit source exactly, and a 16-bit source
 * gains no rounding at 16 bits when it is leveled or corrected.
 * Volume leveling runs here on every encoding, so the AudioTrack stays at unity volume. DSP, when
 * engaged, runs on float and 16-bit input, plus packed 24/32-bit for headphone routes. At unity
 * gain with DSP bypassed, 24/32-bit input is untouched and 16-bit is only widened.
 */
class IntegerPcmAudioOutput(
    output: AudioOutput,
    private val inputEncoding: Int,
    private val channelCount: Int,
    private val carDsp: CarDspProcessor? = null,
    private val levelingGain: () -> Double = { 1.0 }
) : ForwardingAudioOutput(output) {
    private var converted: ByteBuffer = ByteBuffer.allocateDirect(0)
    private var inputBytesPerSample = 4
    private var convertedBytesPerSample = 3
    private var inputPosition = 0

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        if (!converted.hasRemaining()) {
            val widened = inputEncoding == C.ENCODING_PCM_FLOAT || inputEncoding == C.ENCODING_PCM_16BIT
            val dsp = carDsp?.takeIf { it.beginBlock(levelingGain(), widened || HeadphoneDsp.current.handlesRoute) }
            if (!widened && dsp == null) {
                val start = buffer.position()
                val handled = super.write(buffer, encodedAccessUnitCount, presentationTimeUs)
                AudioLevelMeter.measure(buffer, start, buffer.position(), inputEncoding, channelCount)
                return handled
            }
            inputPosition = buffer.position()
            AudioLevelMeter.measure(buffer, buffer.position(), buffer.limit(), inputEncoding, channelCount)
            if (inputEncoding == C.ENCODING_PCM_FLOAT) {
                converted = dsp?.processFloatToInt24(buffer, converted) ?: floatToInt24(buffer, converted)
                inputBytesPerSample = 4
                convertedBytesPerSample = 3
            } else if (inputEncoding == C.ENCODING_PCM_16BIT) {
                converted = dsp?.processInt16ToInt24(buffer, converted) ?: int16ToInt24(buffer, converted)
                inputBytesPerSample = 2
                convertedBytesPerSample = 3
            } else if (inputEncoding == C.ENCODING_PCM_24BIT) {
                converted = dsp!!.processInt24(buffer, converted)
                inputBytesPerSample = 3
                convertedBytesPerSample = 3
            } else {
                converted = dsp!!.processInt32(buffer, converted)
                inputBytesPerSample = 4
                convertedBytesPerSample = 4
            }
        }
        super.write(converted, encodedAccessUnitCount, presentationTimeUs)
        buffer.position(inputPosition + converted.position() / convertedBytesPerSample * inputBytesPerSample)
        return !buffer.hasRemaining()
    }

    override fun flush() {
        converted.position(converted.limit())
        carDsp?.reset()
        super.flush()
    }

    companion object {
        /** Converts the remaining float samples of [source] to little-endian 24-bit PCM. */
        @JvmStatic
        fun floatToInt24(source: ByteBuffer, reuse: ByteBuffer): ByteBuffer {
            val samples = source.remaining() / 4
            val target = if (reuse.capacity() >= samples * 3) reuse.apply { clear() } else ByteBuffer.allocateDirect(samples * 3)
            var position = source.position()
            repeat(samples) {
                val sample = source.getFloat(position)
                // A decoder's NaN becomes silence; roundToInt would throw and stop playback
                val value = if (sample.isNaN()) 0 else (sample * 8_388_608f).roundToInt().coerceIn(-8_388_608, 8_388_607)
                target.put(value.toByte()).put((value shr 8).toByte()).put((value shr 16).toByte())
                position += 4
            }
            target.flip()
            return target
        }

        /** Widens the remaining 16-bit samples of [source] to little-endian 24-bit PCM; exact. */
        @JvmStatic
        fun int16ToInt24(source: ByteBuffer, reuse: ByteBuffer): ByteBuffer {
            val samples = source.remaining() / 2
            val target = if (reuse.capacity() >= samples * 3) reuse.apply { clear() } else ByteBuffer.allocateDirect(samples * 3)
            var position = source.position()
            repeat(samples) {
                val sample = source.getShort(position).toInt()
                target.put(0).put(sample.toByte()).put((sample shr 8).toByte())
                position += 2
            }
            target.flip()
            return target
        }
    }
}
