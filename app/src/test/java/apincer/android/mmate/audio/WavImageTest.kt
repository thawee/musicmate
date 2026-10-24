package apincer.android.mmate.audio

import androidx.media3.common.C
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class WavImageTest {
    private val header = byteArrayOf(-1, -2, -3, -4)
    private val frameSize = 4
    private val rate = 2 // frames per second, so one second is 8 bytes
    private val pcm = ByteArray(40) { (it + 1).toByte() } // 10 frames
    private val starts = mutableListOf<Long>()
    private var closed = false

    /** FFmpeg stand-in: the PCM from a frame on, cut to [produced] bytes of the whole. */
    private fun opener(produced: Int = pcm.size): (Long) -> InputStream = { frame ->
        starts += frame
        object : ByteArrayInputStream(pcm.copyOfRange((frame * frameSize).toInt().coerceAtMost(produced), produced)) {
            override fun close() {
                closed = true
            }
        }
    }

    private fun image(position: Long, length: Long? = null, produced: Int = pcm.size) =
        WavImage(header, frameSize, rate, header.size + pcm.size.toLong(), position, length, "test", opener(produced))

    private fun readAll(image: WavImage): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(3) // odd size, so reads straddle the header and frames
        while (true) {
            val n = image.read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) return out.toByteArray()
            out.write(buffer, 0, n)
        }
    }

    @Test
    fun fromTheStartServesHeaderThenPcm() {
        val image = image(0)
        assertEquals(44L, image.remaining)
        assertArrayEquals(header + pcm, readAll(image))
        assertEquals(listOf(0L), starts)
    }

    @Test
    fun aSeekInsideAFrameStartsFfmpegAtThatFrameAndDropsTheLeadingBytes() {
        val position = header.size + 2L * frameSize + 1
        assertArrayEquals(pcm.copyOfRange(9, pcm.size), readAll(image(position)))
        assertEquals(listOf(2L), starts)
    }

    @Test
    fun aReadInsideTheHeaderNeedsNoFfmpegUntilThePcm() {
        val image = image(1, length = 3)
        assertArrayEquals(header.copyOfRange(1, 4), readAll(image))
        assertTrue(starts.isEmpty())
    }

    @Test
    fun aShortDecodeIsPaddedToTheProbedLength() {
        val bytes = readAll(image(0, produced = 30))
        assertEquals(44, bytes.size)
        assertArrayEquals(pcm.copyOfRange(0, 30), bytes.copyOfRange(4, 34))
        assertTrue(bytes.copyOfRange(34, 44).all { it == 0.toByte() })
    }

    @Test(expected = IOException::class)
    fun noAudioFarFromTheEndIsAnError() {
        readAll(image(0, produced = 0))
    }

    @Test
    fun noAudioInTheLastSecondIsSilence() {
        // 8 bytes left is one second at this rate
        val bytes = readAll(image(header.size + 32L, produced = 32))
        assertArrayEquals(ByteArray(8), bytes)
    }

    @Test
    fun closeStopsFfmpeg() {
        val image = image(header.size.toLong())
        image.read(ByteArray(4), 0, 4)
        image.close()
        assertTrue(closed)
    }
}
