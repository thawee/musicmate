package apincer.android.mmate.audio

import android.util.Log
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioOutputProvider.InitializationException
import androidx.media3.exoplayer.audio.AudioOutputProvider.OutputConfig
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import apincer.android.mmate.audio.dsp.CarDspProcessor

/**
 * Opens MusicMate's audio outputs: integer PCM only (float becomes 24-bit), with the USB DAC
 * asked to run at the track's rate. If Android refuses that output, it retries once with the
 * DAC on Android's default format before reporting the failure to the player. Each output gets its
 * own Car DSP processor at the track's sample rate, except on a USB DAC, which is never processed.
 */
class MusicAudioOutputProvider(
    base: AudioOutputProvider,
    private val usbOutputFormat: UsbOutputFormat
) : ForwardingAudioOutputProvider(base) {

    override fun getAudioOutput(config: OutputConfig): AudioOutput {
        val channelCount = Integer.bitCount(config.channelMask)
        val trackConfig = if (config.encoding == C.ENCODING_PCM_FLOAT) {
            config.buildUpon()
                .setEncoding(C.ENCODING_PCM_24BIT)
                .setBufferSize(config.bufferSize / 4 * 3)
                .build()
        } else config
        usbOutputFormat.prepare(config.sampleRate, channelCount)
        val output = try {
            super.getAudioOutput(trackConfig)
        } catch (error: InitializationException) {
            Log.w(TAG, "Audio output refused; retrying with the default USB format", error)
            usbOutputFormat.clear()
            super.getAudioOutput(trackConfig)
        }
        val carDsp = if (channelCount > 0 && !usbOutputFormat.routesToUsb()) CarDspProcessor(config.sampleRate, channelCount) else null
        return IntegerPcmAudioOutput(output, config.encoding, channelCount, carDsp)
    }

    private companion object {
        const val TAG = "MusicAudioOutputProvider"
    }
}
