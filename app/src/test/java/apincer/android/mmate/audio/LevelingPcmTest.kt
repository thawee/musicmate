package apincer.android.mmate.audio

import android.media.AudioDeviceInfo
import android.util.Log
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioOutput
import apincer.android.mmate.audio.dsp.CarDsp
import apincer.android.mmate.audio.dsp.CarDspProcessor
import apincer.android.mmate.audio.headphones.HeadphoneDsp
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class LevelingPcmTest {
    private var gain = 1.0
    private val written = ByteArrayOutputStream()
    private val handed = mutableListOf<ByteBuffer>()
    private val delegate = mockk<AudioOutput>(relaxed = true) {
        every { write(any(), any(), any()) } answers {
            val source = firstArg<ByteBuffer>()
            handed += source
            while (source.hasRemaining()) written.write(source.get().toInt())
            true
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        mockkObject(HeadphoneDsp)
        every { HeadphoneDsp.current } returns HeadphoneDsp.Selection(false, null)
        CarDsp.target = null
        OutputGuard.reset()
    }

    @After
    fun tearDown() {
        CarDsp.updateRoute(emptyList())
        unmockkObject(HeadphoneDsp)
        unmockkStatic(Log::class)
    }

    private fun output(encoding: Int) =
        IntegerPcmAudioOutput(delegate, encoding, 1, CarDspProcessor(48_000, 1)) { gain }

    @Test
    fun unityGainHandsTwentyFourBitThroughUntouched() {
        val source = int24(4_000_000, -1_234_567, 8_388_607)
        output(C.ENCODING_PCM_24BIT).write(source, 1, 0)
        assertSame(source, handed.single())
    }

    @Test
    fun theFirstBlockOfAStreamIsAlreadyAtItsGain() {
        gain = 0.5
        output(C.ENCODING_PCM_24BIT).write(int24(4_000_000, -1_234_566, 8_388_607), 1, 0)
        assertArrayEquals(intArrayOf(2_000_000, -617_283, 4_194_304), samples24())
    }

    @Test
    fun aGainChangeRampsAndEndsExactlyOnPassthrough() {
        gain = 0.5
        val output = output(C.ENCODING_PCM_24BIT)
        output.write(int24(4_000_000), 1, 0)
        gain = 1.0
        // 20 ms at 48 kHz
        val ramp = IntArray(960) { 4_000_000 }
        written.reset()
        output.write(int24(*ramp), 1, 0)
        val heard = samples24()
        assertEquals(2_000_000.0, heard.first().toDouble(), 4_000.0)
        assertEquals(4_000_000, heard.last())
        assertEquals(heard.sorted(), heard.toList())

        val after = int24(4_000_000)
        output.write(after, 1, 0)
        assertSame(after, handed.last())
    }

    @Test
    fun levelingAloneIsNotLimitedAtFullScale() {
        gain = 2.0
        output(C.ENCODING_PCM_24BIT).write(int24((0.49 * 8_388_608).roundToInt()), 1, 0)
        assertEquals(0.98 * 8_388_608, samples24().single().toDouble(), 2.0)
        assertEquals(OutputGuardState(), OutputGuard.state.value)
    }

    @Test
    fun aBoostToExactlyFullScaleClampsInsteadOfWrapping() {
        gain = 2.0
        output(C.ENCODING_PCM_24BIT).write(int24(4_194_304, -4_194_304), 1, 0)
        assertArrayEquals(intArrayOf(8_388_607, -8_388_608), samples24())
        assertEquals(0L, OutputGuard.state.value.clippedAtMs)

        written.reset()
        output(C.ENCODING_PCM_16BIT).write(int16(16_384, -16_384), 1, 0)
        assertArrayEquals(intArrayOf(8_388_607, -8_388_608), samples24())

        written.reset()
        output(C.ENCODING_PCM_32BIT).write(int32(1 shl 30, -(1 shl 30)), 1, 0)
        assertArrayEquals(intArrayOf(Int.MAX_VALUE, Int.MIN_VALUE), samples32())
        assertEquals(0L, OutputGuard.state.value.clippedAtMs)

        written.reset()
        gain = 2.5
        output(C.ENCODING_PCM_24BIT).write(int24(4_194_304, -4_194_304), 1, 0)
        assertArrayEquals(intArrayOf(8_388_607, -8_388_608), samples24())
        assertTrue(OutputGuard.state.value.clippedAtMs > 0)
    }

    @Test
    fun levelingAppliesOnAUsbDacWithoutHeadphoneEq() {
        CarDsp.updateRoute(listOf(mockk<AudioDeviceInfo> {
            every { type } returns AudioDeviceInfo.TYPE_USB_DEVICE
            every { address } returns "test-usb"
            every { productName } returns "Test USB"
        }))
        gain = 0.5
        output(C.ENCODING_PCM_24BIT).write(int24(4_000_000), 1, 0)
        assertArrayEquals(intArrayOf(2_000_000), samples24())

        written.reset()
        output(C.ENCODING_PCM_16BIT).write(int16(8_192), 1, 0)
        assertArrayEquals(intArrayOf(1_048_576), samples24())

        written.reset()
        output(C.ENCODING_PCM_32BIT).write(int32(1 shl 29), 1, 0)
        assertArrayEquals(intArrayOf(1 shl 28), samples32())
    }

    @Test
    fun levelingSixteenBitKeepsTheFractionInTwentyFourBits() {
        gain = 0.5
        output(C.ENCODING_PCM_16BIT).write(int16(1, -3, 32_767), 1, 0)
        assertArrayEquals(intArrayOf(128, -384, 4_194_176), samples24())
    }

    private fun int24(vararg values: Int): ByteBuffer = ByteBuffer.allocate(values.size * 3).apply {
        values.forEach { put(it.toByte()).put((it shr 8).toByte()).put((it shr 16).toByte()) }
        flip()
    }

    private fun int16(vararg values: Int): ByteBuffer =
        ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach { putShort(it.toShort()) }
            flip()
        }

    private fun int32(vararg values: Int): ByteBuffer =
        ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach { putInt(it) }
            flip()
        }

    private fun samples32(): IntArray {
        val buffer = ByteBuffer.wrap(written.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(buffer.remaining() / 4) { buffer.getInt() }
    }

    private fun samples24(): IntArray {
        val bytes = written.toByteArray()
        return IntArray(bytes.size / 3) {
            (bytes[it * 3].toInt() and 0xFF) or ((bytes[it * 3 + 1].toInt() and 0xFF) shl 8) or (bytes[it * 3 + 2].toInt() shl 16)
        }
    }
}
