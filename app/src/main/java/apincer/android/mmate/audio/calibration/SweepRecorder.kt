package apincer.android.mmate.audio.calibration

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import apincer.android.mmate.utils.AudioOutputHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * Plays a sweep on its own media AudioTrack and records it with the phone's built-in mic. The
 * track uses the player's attributes, so Android routes it like music (to Android Auto in the
 * car); nothing enters the queue, and ReplayGain, speed and Car DSP do not touch it. Taking
 * exclusive focus pauses MusicMate's player for the duration.
 */
class SweepRecorder(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** [problem] is set when the take must be thrown away whatever it sounds like. */
    class Capture(val recording: DoubleArray, val problem: String?)

    /** True when the phone offers a mic path without noise suppression or gain control. */
    val unprocessed: Boolean =
        audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

    val micSource: String get() = if (unprocessed) "UNPROCESSED" else "VOICE_RECOGNITION"

    /** Blocks for about [TOTAL_SECONDS]; call off the main thread. [cancelled] stops it early. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun capture(pcm: ShortArray, cancelled: () -> Boolean = { false }): Capture {
        val total = (RATE * TOTAL_SECONDS).toInt()
        val lead = (RATE * LEAD_SECONDS).toInt()
        val recording = FloatArray(total)
        val record = AudioRecord.Builder()
            .setAudioSource(if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(max(AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 4, RATE * 4))
            .build()
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?.let { record.setPreferredDevice(it) }
        val track = AudioTrack.Builder()
            .setAudioAttributes(MEDIA)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4)
            .build()
        // Streamed from its own thread: a 1 MB static buffer is more than some phones accept
        val player = Thread({
            track.play()
            track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
        }, "CarCalibrationSweep")
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setAudioAttributes(MEDIA).build()
        var problem: String? = null
        try {
            audioManager.requestAudioFocus(focus)
            record.startRecording()
            var read = 0
            var routesChecked = false
            while (read < total) {
                if (cancelled()) return Capture(DoubleArray(0), "Cancelled")
                if (read >= lead && player.state == Thread.State.NEW) player.start()
                if (!routesChecked && read >= lead + RATE) {
                    routesChecked = true
                    problem = routeProblem(record, track)
                }
                val count = record.read(recording, read, minOf(CHUNK, total - read), AudioRecord.READ_BLOCKING)
                if (count <= 0) return Capture(DoubleArray(0), "The microphone stopped delivering sound ($count)")
                read += count
            }
        } finally {
            runCatching { track.stop() }
            if (player.isAlive) player.join(1_000)
            track.release()
            runCatching { record.stop() }
            record.release()
            audioManager.abandonAudioFocusRequest(focus)
        }
        return Capture(DoubleArray(total) { recording[it].toDouble() }, problem)
    }

    /** The recorder must be on the built-in mic and the sweep on the same output as music. */
    private fun routeProblem(record: AudioRecord, track: AudioTrack): String? {
        val mic = record.routedDevice
        val output = track.routedDevice
        val music = audioManager.getAudioDevicesForAttributes(MEDIA).firstOrNull()
        Log.i(TAG, "mic=${mic?.type}:${mic?.productName} sweep=${output?.type}:${output?.productName}#${output?.id} music=${music?.type}#${music?.id}")
        return when {
            mic == null -> "Could not confirm the phone's microphone was recording"
            mic.type != AudioDeviceInfo.TYPE_BUILTIN_MIC ->
                "Recording used ${AudioOutputHelper.typeToString(mic.type)} instead of the phone's microphone"
            output != null && music != null && output.id != music.id ->
                "The test sound went to ${AudioOutputHelper.typeToString(output.type)}, not where music plays"
            else -> null
        }
    }

    companion object {
        const val RATE = 48_000
        const val LEAD_SECONDS = 0.5
        /** Lead, 10 s sweep, 3 s after. */
        const val TOTAL_SECONDS = 13.5
        private const val CHUNK = 4_800
        private const val TAG = "CarCalibration"
        private val MEDIA: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}

/** The Bluetooth device connected for calls, which in the car is the car itself. */
object HeadsetLink {
    class Device(val name: String?, val address: String)

    suspend fun connected(context: Context): Device? {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        return suspendCancellableCoroutine { continuation ->
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    val device = try {
                        proxy.connectedDevices.firstOrNull()?.let { Device(it.name, it.address) }
                    } catch (error: SecurityException) {
                        null
                    }
                    adapter.closeProfileProxy(profile, proxy)
                    if (continuation.isActive) continuation.resume(device)
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            if (!adapter.getProfileProxy(context.applicationContext, listener, BluetoothProfile.HEADSET)) continuation.resume(null)
        }
    }
}
