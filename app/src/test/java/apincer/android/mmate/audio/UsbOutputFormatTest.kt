package apincer.android.mmate.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsbOutputFormatTest {
    private fun mixer(rate: Int, encoding: Int, behavior: Int = AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT,
                      mask: Int = AudioFormat.CHANNEL_OUT_STEREO): AudioMixerAttributes {
        val format = mockk<AudioFormat> {
            every { sampleRate } returns rate
            every { this@mockk.encoding } returns encoding
            every { channelMask } returns mask
        }
        return mockk {
            every { mixerBehavior } returns behavior
            every { this@mockk.format } returns format
        }
    }

    // The SNOWSKY TINY B on a Galaxy S25: 48 kHz and up, 16/24/32-bit, no 44.1 kHz.
    private val offered = listOf(48000, 96000, 192000).flatMap { rate ->
        listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED, AudioFormat.ENCODING_PCM_32BIT)
            .map { mixer(rate, it) }
    }

    @Test
    fun choosesTheTrackRateAtTheDeepestBitDepth() {
        val chosen = UsbOutputFormat.choose(offered, 96000, 2)!!
        assertEquals(96000, chosen.format.sampleRate)
        assertEquals(AudioFormat.ENCODING_PCM_32BIT, chosen.format.encoding)
    }

    @Test
    fun leavesAndroidDefaultWhenTheDacLacksTheRateOrChannelLayout() {
        assertNull(UsbOutputFormat.choose(offered, 44100, 2))
        assertNull(UsbOutputFormat.choose(offered, 96000, 6))
        val bitPerfectOnly = listOf(mixer(96000, AudioFormat.ENCODING_PCM_32BIT, AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT))
        assertNull(UsbOutputFormat.choose(bitPerfectOnly, 96000, 2))
    }

    private class Fixture(offered: List<AudioMixerAttributes>) {
        val manager = mockk<AudioManager>(relaxed = true)
        val attributes = mockk<AudioAttributes>()
        val dac = mockk<AudioDeviceInfo> {
            every { id } returns 42
            every { type } returns AudioDeviceInfo.TYPE_USB_HEADSET
        }
        val format = UsbOutputFormat(manager, attributes)
        var androidPreference: AudioMixerAttributes? = null

        init {
            every { manager.getAudioDevicesForAttributes(attributes) } returns listOf(dac)
            every { manager.getSupportedMixerAttributes(dac) } returns offered
            every { manager.setPreferredMixerAttributes(attributes, dac, any()) } answers {
                androidPreference = thirdArg()
                true
            }
            every { manager.clearPreferredMixerAttributes(attributes, dac) } answers {
                androidPreference = null
                true
            }
            every { manager.getPreferredMixerAttributes(attributes, dac) } answers { androidPreference }
        }
    }

    @Test
    fun requestsTheRateOnceAndReleasesItForOtherApps() {
        val f = Fixture(offered)
        f.format.prepare(96000, 2)
        f.format.prepare(96000, 2)
        verify(exactly = 1) { f.manager.setPreferredMixerAttributes(f.attributes, f.dac, any()) }
        assertEquals(96000, f.format.requestedFormat(42)!!.sampleRate)

        f.format.clear()
        verify(exactly = 1) { f.manager.clearPreferredMixerAttributes(f.attributes, f.dac) }
        assertNull(f.format.requestedFormat(42))
    }

    @Test
    fun pauseReleasesTheRateAndResumeRequestsItAgain() {
        val f = Fixture(offered)
        f.format.prepare(96000, 2)

        f.format.suspend()
        verify(exactly = 1) { f.manager.clearPreferredMixerAttributes(f.attributes, f.dac) }
        assertNull(f.format.requestedFormat(42))

        f.format.resume()
        verify(exactly = 2) { f.manager.setPreferredMixerAttributes(f.attributes, f.dac, any()) }
        assertEquals(96000, f.format.requestedFormat(42)!!.sampleRate)
    }

    @Test
    fun resumeAfterStopDoesNotRequestAnything() {
        val f = Fixture(offered)
        f.format.prepare(96000, 2)
        f.format.clear()
        f.format.resume()
        verify(exactly = 1) { f.manager.setPreferredMixerAttributes(f.attributes, f.dac, any()) }
    }

    @Test
    fun aTrackAtAnUnsupportedRateReturnsTheDacToAndroidDefault() {
        val f = Fixture(offered)
        f.format.prepare(96000, 2)
        f.format.prepare(44100, 2)
        verify(exactly = 1) { f.manager.clearPreferredMixerAttributes(f.attributes, f.dac) }
        assertNull(f.format.requestedFormat(42))
    }

    @Test
    fun bluetoothOrSpeakerRoutesNeverQueryUsbMixerFormats() {
        val f = Fixture(offered)
        val headset = mockk<AudioDeviceInfo> { every { type } returns AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        every { f.manager.getAudioDevicesForAttributes(f.attributes) } returns listOf(headset)
        f.format.prepare(96000, 2)
        verify(exactly = 0) { f.manager.getSupportedMixerAttributes(any()) }
        verify(exactly = 0) { f.manager.setPreferredMixerAttributes(any(), any(), any()) }
    }

    @Test
    fun describesRatesTheWayListenersReadThem() {
        assertEquals("88.2 kHz / 32-bit", UsbOutputFormat.describe(mixer(88200, AudioFormat.ENCODING_PCM_32BIT).format))
        assertEquals("96 kHz / 24-bit", UsbOutputFormat.describe(mixer(96000, AudioFormat.ENCODING_PCM_24BIT_PACKED).format))
    }

    @Test
    fun aRefusedRequestIsNotReportedAsTheDacFormat() {
        val f = Fixture(offered)
        every { f.manager.setPreferredMixerAttributes(f.attributes, f.dac, any()) } returns false
        f.format.prepare(96000, 2)
        assertNull(f.format.requestedFormat(42))
    }

    @Test
    fun theLabelFollowsAndroidNotMusicMatesOwnRecord() {
        val f = Fixture(offered)
        f.format.prepare(96000, 2)
        assertNull(f.format.requestedFormat(7))

        f.androidPreference = null
        assertNull(f.format.requestedFormat(42))

        f.format.prepare(96000, 2)
        val headset = mockk<AudioDeviceInfo> { every { type } returns AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        every { f.manager.getAudioDevicesForAttributes(f.attributes) } returns listOf(headset)
        assertNull(f.format.requestedFormat(42))
    }
}
