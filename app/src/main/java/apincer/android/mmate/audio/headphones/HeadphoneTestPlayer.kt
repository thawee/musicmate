package apincer.android.mmate.audio.headphones

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import apincer.android.mmate.audio.dsp.CarDspSettings
import apincer.android.mmate.audio.dsp.DspChain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class HeadphoneTestSignal { LEFT, RIGHT, ALTERNATING, CENTER }

enum class HeadphoneTestBand(val label: String, val frequencyHz: Double?) {
    MULTITONE("Final music check", null),
    BASS("Bass impact", 100.0),
    WARMTH("Warmth", 300.0),
    MIDS("Vocal naturalness", 1_000.0),
    CLARITY("Clarity", 3_000.0),
    TREBLE("Treble detail", 8_000.0)
}

/** Quiet musical samples or diagnostic tones, never a microphone measurement. */
internal object HeadphoneTestTone {
    const val RATE = 48_000
    private const val PEAK = 0.0158 // About -36 dBFS; system volume is left under the user's control.
    private val frequencies = doubleArrayOf(125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0)

    fun render(signal: HeadphoneTestSignal, settings: CarDspSettings,
               band: HeadphoneTestBand = HeadphoneTestBand.MULTITONE, source: FloatArray? = null,
               original: Boolean = false): ShortArray {
        require(source == null || source.size == RATE * 3 && source.all { it.isFinite() && kotlin.math.abs(it) <= 1f })
        val flat = CarDspSettings(emptyList())
        val selectedSettings = if (original) flat else settings
        var gain = 1.0
        var reference: DoubleArray? = null
        // Match musical A/B energy using the same stereo reference for every ear selection.
        // Single-frequency diagnostics retain gain cues: matching would erase their EQ change.
        if (source != null || band == HeadphoneTestBand.MULTITONE) {
            val originalReference = renderSamples(HeadphoneTestSignal.CENTER, flat, band, source)
            val adjustedReference = renderSamples(HeadphoneTestSignal.CENTER, settings, band, source)
            val originalEnergy = originalReference.sumOf { it * it }
            val adjustedEnergy = adjustedReference.sumOf { it * it }
            val targetEnergy = minOf(originalEnergy, adjustedEnergy)
            val originalGain = if (originalEnergy == 0.0) 1.0 else sqrt(targetEnergy / originalEnergy)
            val adjustedGain = if (adjustedEnergy == 0.0) 1.0 else sqrt(targetEnergy / adjustedEnergy)
            val peak = max(originalReference.maxOf { kotlin.math.abs(it) } * originalGain,
                adjustedReference.maxOf { kotlin.math.abs(it) } * adjustedGain)
            val ceilingGain = if (peak > PEAK) PEAK / peak else 1.0
            gain = (if (original) originalGain else adjustedGain) * ceilingGain
            if (signal == HeadphoneTestSignal.CENTER) reference = if (original) originalReference else adjustedReference
        }
        val samples = reference ?: renderSamples(signal, selectedSettings, band, source)
        return ShortArray(samples.size) { i ->
            ((samples[i] * gain).coerceIn(-PEAK, PEAK) * Short.MAX_VALUE).roundToInt().toShort()
        }
    }

    private fun renderSamples(signal: HeadphoneTestSignal, settings: CarDspSettings,
                              band: HeadphoneTestBand, source: FloatArray?): DoubleArray {
        val seconds = if (signal == HeadphoneTestSignal.ALTERNATING) { if (source == null) 4 else 6 } else 3
        val frames = RATE * seconds
        val testFrequencies = band.frequencyHz?.let { doubleArrayOf(it) } ?: frequencies
        val chain = DspChain(settings.bands, RATE, 2, settings.requestedPreampDb,
            settings.leftBands, settings.rightBands, settings.balanceDb)
        val frame = DoubleArray(2)
        val pcm = DoubleArray(frames * 2)
        for (i in 0 until frames) {
            val segment = if (source == null) RATE else source.size
            val position = if (signal == HeadphoneTestSignal.ALTERNATING) i % segment else i
            val length = if (signal == HeadphoneTestSignal.ALTERNATING) segment else frames
            val edge = minOf(position, length - 1 - position, RATE / 25).toDouble() / (RATE / 25)
            val envelope = (1 - cos(PI * edge)) * 0.5
            val sample = (source?.get(position)?.toDouble()
                ?: (testFrequencies.sumOf { sin(2 * PI * it * i / RATE) } / testFrequencies.size)) * PEAK * envelope
            val left = signal == HeadphoneTestSignal.LEFT || signal == HeadphoneTestSignal.CENTER ||
                (signal == HeadphoneTestSignal.ALTERNATING && i / segment % 2 == 0)
            val right = signal == HeadphoneTestSignal.RIGHT || signal == HeadphoneTestSignal.CENTER ||
                (signal == HeadphoneTestSignal.ALTERNATING && i / segment % 2 == 1)
            frame[0] = if (left) sample else 0.0
            frame[1] = if (right) sample else 0.0
            chain.process(frame)
            for (c in 0..1) {
                // Gate filter tails on the silent ear and fade the final output, too.
                val value = if ((c == 0 && left) || (c == 1 && right)) frame[c] * envelope else 0.0
                pcm[i * 2 + c] = value
            }
        }
        return pcm
    }
}

internal interface HeadphoneTestOutput : AutoCloseable {
    val routeValid: Boolean
    val playedFrames: Int
    fun start()
    fun write(samples: ShortArray, offset: Int, size: Int): Int
}

/** One short test at a time. Nonblocking writes keep stop/cancellation responsive. */
class HeadphoneTestPlayer internal constructor(
    private val loadSpeech: (ListeningSample) -> FloatArray = { error("Speech samples are unavailable.") },
    private val open: (HeadphoneEndpoint) -> HeadphoneTestOutput
) {
    constructor(context: Context) : this(
        loadSpeech = { sample ->
            val bytes = context.applicationContext.assets.open(requireNotNull(sample.speechAssetPath)).use { it.readBytes() }
            require(bytes.size == ListeningSamples.RATE * ListeningSamples.DURATION_SECONDS * 2) { "Invalid listening sample." }
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            FloatArray(bytes.size / 2) { buffer.short / 32768f }
        },
        open = { endpoint -> AndroidHeadphoneTestOutput(context.applicationContext, endpoint) }
    )

    private var active: AtomicBoolean? = null
    private val playback = Mutex()

    @Synchronized fun stop() { active?.set(true) }

    suspend fun play(signal: HeadphoneTestSignal, settings: CarDspSettings, endpoint: HeadphoneEndpoint,
                     band: HeadphoneTestBand = HeadphoneTestBand.MULTITONE, sample: ListeningSample? = null,
                     original: Boolean = false) = withContext(Dispatchers.IO) {
        val stopped = AtomicBoolean(false)
        synchronized(this@HeadphoneTestPlayer) { active?.set(true); active = stopped }
        playback.withLock {
          var output: HeadphoneTestOutput? = null
          try {
            val source = sample?.let { ListeningSamples.render(it, if (it.speechAssetPath != null) loadSpeech(it) else null) }
            val pcm = HeadphoneTestTone.render(signal, settings, band, source, original)
            currentCoroutineContext().ensureActive()
            if (stopped.get()) return@withContext
            val session = open(endpoint)
            output = session
            check(session.routeValid) { "The audio output changed. Reconnect it and try again." }
            session.start()
            var offset = 0
            val deadline = System.nanoTime() + (pcm.size / 2 / HeadphoneTestTone.RATE + 3L) * 1_000_000_000L
            while (!stopped.get() && (offset < pcm.size || session.playedFrames < pcm.size / 2)) {
                currentCoroutineContext().ensureActive()
                check(session.routeValid) { "The output changed or audio focus was lost. Test stopped." }
                check(System.nanoTime() < deadline) { "The output stopped accepting the test sound." }
                if (offset < pcm.size) {
                    val written = session.write(pcm, offset, minOf(960, pcm.size - offset))
                    check(written >= 0) { "The headphone test output failed ($written)." }
                    offset += written
                }
                delay(5)
            }
          } finally {
            try { output?.close() } finally {
                synchronized(this@HeadphoneTestPlayer) { if (active === stopped) active = null }
            }
          }
        }
    }
}

private class AndroidHeadphoneTestOutput(context: Context, private val endpoint: HeadphoneEndpoint) : HeadphoneTestOutput {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusLost = AtomicBoolean(false)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener({ change ->
            if (change <= 0) focusLost.set(true)
        }, Handler(Looper.getMainLooper())).build()
    private val device = mediaDevices().singleOrNull()
        ?: error("Connect one audio output before playing a test.")
    private val track: AudioTrack

    init {
        check(matchesEndpoint()) { "The headphone output changed. Select it again." }
        check(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "Audio focus was unavailable. Stop other audio and try again."
        }
        var created: AudioTrack? = null
        try {
            created = AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(HeadphoneTestTone.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(max(3_840, AudioTrack.getMinBufferSize(HeadphoneTestTone.RATE,
                    AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT))).build()
            check(created.state == AudioTrack.STATE_INITIALIZED && created.setPreferredDevice(device)) {
                "Could not open the selected headphone output."
            }
            track = created
        } catch (failure: Throwable) {
            created?.release()
            audioManager.abandonAudioFocusRequest(focus)
            throw failure
        }
    }

    private fun mediaDevices() = audioManager.getAudioDevicesForAttributes(attributes)
    private fun matchesEndpoint(): Boolean {
        val devices = mediaDevices()
        return devices.singleOrNull()?.id == device.id && HeadphoneEndpoint.of(devices.map {
            Triple(it.type, it.address, it.productName?.toString())
        })?.key == endpoint.key
    }

    override val routeValid: Boolean get() = !focusLost.get() && matchesEndpoint() &&
        (track.routedDevice?.id?.let { it == device.id } ?: true)
    override val playedFrames: Int get() = track.playbackHeadPosition
    override fun start() = track.play()
    override fun write(samples: ShortArray, offset: Int, size: Int): Int =
        track.write(samples, offset, size, AudioTrack.WRITE_NON_BLOCKING)

    override fun close() {
        try {
            runCatching { track.pause(); track.flush(); track.stop() }
            track.release()
        } finally {
            audioManager.abandonAudioFocusRequest(focus)
        }
    }
}
