package apincer.android.mmate.audio.calibration

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AudioEffect
import androidx.annotation.RequiresPermission
import apincer.android.mmate.utils.AudioOutputHelper
import kotlin.math.max

/** What the calibration wizard needs from a recorder. */
interface SweepCapture {
    /** True when the phone offers a mic path without noise suppression or gain control. */
    val unprocessed: Boolean
    val micSource: String

    /**
     * Plays [pcm], interleaved over [channels], and records from [SweepRecorder.LEAD_SECONDS] before it to
     * [SweepRecorder.TAIL_SECONDS] after it. Blocks meanwhile; call off the main thread. [cancelled] stops it early.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun capture(pcm: ShortArray, channels: Int = 1, cancelled: () -> Boolean = { false }): SweepRecorder.Capture
}

/**
 * Plays a sweep on its own media AudioTrack and records it with the selected microphone. The
 * track uses the player's attributes, so Android routes it like music (to Android Auto in the
 * car); nothing enters the queue, and ReplayGain, speed and Car DSP do not touch it. Taking
 * exclusive focus pauses MusicMate's player for the duration.
 */
class SweepRecorder(context: Context) : SweepCapture {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    /** [builtIn] for the phone's own mic, which records when none is chosen. */
    data class Microphone(val id: Int, val name: String, val builtIn: Boolean = false)
    private var requestedMicId: Int? = null
    private var capturedRoute: Pair<Int, Int>? = null
    val microphones: List<Microphone> get() = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        .filter { it.type in setOf(AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADSET) }
        .map { Microphone(it.id, "${it.productName} (${AudioOutputHelper.typeToString(it.type)})", it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) }

    fun selectMicrophone(id: Int) { requestedMicId = id }
    fun beginSession() { capturedRoute = null }

    /** [problem] is set when the take must be thrown away whatever it sounds like; it is advice shown as is. */
    class Capture(val recording: DoubleArray, val problem: String?)

    override val unprocessed: Boolean =
        audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

    override val micSource: String get() = (if (unprocessed) "UNPROCESSED" else "VOICE_RECOGNITION") +
        (microphones.firstOrNull { it.id == requestedMicId }?.let { "; ${it.name.take(160)}" } ?: "")

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun capture(pcm: ShortArray, channels: Int, cancelled: () -> Boolean): Capture {
        val lead = (RATE * LEAD_SECONDS).toInt()
        val total = lead + pcm.size / channels + (RATE * TAIL_SECONDS).toInt()
        val layout = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val recording = FloatArray(total)
        val record = AudioRecord.Builder()
            .setAudioSource(if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(max(AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 4, RATE * 4))
            .build()
        val requested = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { if (requestedMicId == null) it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC else it.id == requestedMicId }
        if (requested == null || !record.setPreferredDevice(requested)) {
            record.release()
            return Capture(DoubleArray(0), "The selected microphone is unavailable. Reconnect it and restart calibration.")
        }
        requestedMicId = requested.id
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(MEDIA)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE).setChannelMask(layout).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(AudioTrack.getMinBufferSize(RATE, layout, AudioFormat.ENCODING_PCM_16BIT) * 4)
                .build()
        } catch (failure: Exception) {
            record.release()
            throw failure
        }
        // Streamed from its own thread: a 1 MB static buffer is more than some phones accept
        val player = Thread({
            track.play()
            track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
        }, "CarCalibrationSweep")
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setAudioAttributes(MEDIA).build()
        var problem: String? = null
        var effects = emptyList<AudioEffect>()
        try {
            effects = disableMicEffects(record.audioSessionId)
            if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                return Capture(DoubleArray(0), "Another app is holding the audio, such as a call. End it and measure again.")
            }
            record.startRecording()
            var read = 0
            while (read < total) {
                if (cancelled()) return Capture(DoubleArray(0), "Cancelled")
                if (read >= lead && player.state == Thread.State.NEW) player.start()
                if (read >= lead + RATE) {
                    problem = routeProblem(record, track)
                    if (problem != null) return Capture(DoubleArray(0), problem)
                }
                val count = record.read(recording, read, minOf(CHUNK, total - read), AudioRecord.READ_BLOCKING)
                if (count <= 0) return Capture(DoubleArray(0), "The microphone stopped delivering sound ($count). Measure again.")
                read += count
            }
        } finally {
            runCatching { track.stop() }
            if (player.isAlive) player.join(1_000)
            track.release()
            runCatching { record.stop() }
            effects.forEach { runCatching { it.release() } }
            record.release()
            audioManager.abandonAudioFocusRequest(focus)
        }
        return Capture(DoubleArray(total) { recording[it].toDouble() }, problem)
    }

    /** Reject route changes so every take describes the same microphone and playback output. */
    private fun routeProblem(record: AudioRecord, track: AudioTrack): String? {
        val mic = record.routedDevice
        val output = track.routedDevice
        val music = audioManager.getAudioDevicesForAttributes(MEDIA).firstOrNull()
        return when {
            mic == null || output == null || music == null -> "Could not confirm the microphone and playback route. Measure again."
            mic.id != requestedMicId -> "Recording did not use the selected microphone. Restart calibration after checking the connection."
            output.id != music.id -> "The sweep did not play through the music output. Restart calibration after checking the connection."
            capturedRoute != null && capturedRoute != (mic.id to output.id) -> "The microphone or output changed. Restart calibration so all measurements use the same rig."
            else -> { capturedRoute = mic.id to output.id; null }
        }
    }

    companion object {
        const val RATE = 48_000
        const val LEAD_SECONDS = 0.5
        const val TAIL_SECONDS = 3.0
        /** A recording of one 10 s sweep. */
        const val TOTAL_SECONDS = LEAD_SECONDS + 10 + TAIL_SECONDS
        private const val CHUNK = 4_800
        private val MEDIA: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
