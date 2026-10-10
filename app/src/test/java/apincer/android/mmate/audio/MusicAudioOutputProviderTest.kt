package apincer.android.mmate.audio

import android.media.AudioFormat
import android.util.Log
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioOutputProvider.InitializationException
import androidx.media3.exoplayer.audio.AudioOutputProvider.OutputConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MusicAudioOutputProviderTest {
    private val usb = mockk<UsbOutputFormat>(relaxed = true)
    private val base = mockk<AudioOutputProvider>(relaxed = true)
    private val provider = MusicAudioOutputProvider(base, usb)

    init {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
    }

    private fun config(encoding: Int) = OutputConfig.Builder()
        .setEncoding(encoding)
        .setSampleRate(96_000)
        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
        .setBufferSize(96_000)
        .build()

    @Test
    fun floatOpensA24BitOutputAtTheTrackRate() {
        val opened = slot<OutputConfig>()
        every { base.getAudioOutput(capture(opened)) } returns mockk<AudioOutput>(relaxed = true)

        provider.getAudioOutput(config(C.ENCODING_PCM_FLOAT))

        verify { usb.prepare(96_000, 2) }
        assertEquals(C.ENCODING_PCM_24BIT, opened.captured.encoding)
        assertEquals(72_000, opened.captured.bufferSize)
    }

    @Test
    fun aRefusedOutputRetriesOnceWithAndroidsDefaultUsbFormat() {
        every { base.getAudioOutput(any()) } throws InitializationException() andThen mockk<AudioOutput>(relaxed = true)

        provider.getAudioOutput(config(C.ENCODING_PCM_16BIT))

        verifyOrder {
            usb.prepare(96_000, 2)
            base.getAudioOutput(any())
            usb.clear()
            base.getAudioOutput(any())
        }
    }

    @Test
    fun aSecondRefusalReachesThePlayer() {
        every { base.getAudioOutput(any()) } throws InitializationException()
        assertThrows(InitializationException::class.java) { provider.getAudioOutput(config(C.ENCODING_PCM_16BIT)) }
        verify(exactly = 2) { base.getAudioOutput(any()) }
    }
}
