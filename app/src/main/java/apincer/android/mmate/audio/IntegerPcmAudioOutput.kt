package apincer.android.mmate.audio

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.ForwardingAudioOutput
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Hands Media3's PCM to Android as integer samples and feeds the VU meters on the way.
 * High-resolution float becomes 24-bit integer: float PCM distorts on Bluetooth A2DP and some
 * device mixers (tasks/lessons.md), and 24 bits hold a 24-bit source exactly.
 */
class IntegerPcmAudioOutput(
    output: AudioOutput,
    private val inputEncoding: Int,
    private val channelCount: Int
) : ForwardingAudioOutput(output) {
    private var converted: ByteBuffer = ByteBuffer.allocateDirect(0)

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        if (inputEncoding != C.ENCODING_PCM_FLOAT) {
            val start = buffer.position()
            val handled = super.write(buffer, encodedAccessUnitCount, presentationTimeUs)
            AudioLevelMeter.measure(buffer, start, buffer.position(), inputEncoding, channelCount)
            return handled
        }
        if (!converted.hasRemaining()) {
            AudioLevelMeter.measure(buffer, buffer.position(), buffer.limit(), inputEncoding, channelCount)
            converted = floatToInt24(buffer, converted)
        }
        val before = converted.remaining()
        super.write(converted, encodedAccessUnitCount, presentationTimeUs)
        buffer.position(buffer.position() + (before - converted.remaining()) / 3 * 4)
        return !buffer.hasRemaining()
    }

    override fun flush() {
        converted.position(converted.limit())
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
                val value = (source.getFloat(position) * 8_388_608f).roundToInt().coerceIn(-8_388_608, 8_388_607)
                target.put(value.toByte()).put((value shr 8).toByte()).put((value shr 16).toByte())
                position += 4
            }
            target.flip()
            return target
        }
    }
}
