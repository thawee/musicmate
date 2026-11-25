package apincer.android.mmate.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import apincer.android.mmate.audio.dsp.CarDsp
import apincer.android.mmate.audio.dsp.CarDspProcessor
import apincer.android.mmate.audio.dsp.TruePeakLimiter
import apincer.android.mmate.audio.headphones.HeadphoneDsp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToLong

/** Owns the DSP delay above AudioTrack, where end-of-stream and decoder format changes are visible. */
class LookaheadAudioSink(
    private val delegate: AudioSink,
    private val peakProtection: () -> Boolean = { false },
    private val levelingGain: () -> Double = { 1.0 }
) : ForwardingAudioSink(delegate) {
    private var configuration: AudioSink.AudioSinkConfig? = null
    private var nextConfiguration: AudioSink.AudioSinkConfig? = null
    private var processor: CarDspProcessor? = null
    private var limiter: TruePeakLimiter? = null
    private var delayed = false
    private var pending = emptyBuffer()
    private var pendingTimeUs = 0L
    private var baseTimeUs: Long? = null
    private var outputFrames = 0L
    private var permitted = dspPermitted()
    private var clipped = false
    private var minimumGain = 1.0
    private var discontinuityPending = false
    private var frame = DoubleArray(0)
    private var emitted = DoubleArray(0)
    private var conversion = emptyBuffer()

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        if (configuration != null && hasLocalData()) {
            nextConfiguration = audioSinkConfig
        } else {
            applyConfiguration(audioSinkConfig)
        }
    }

    private fun applyConfiguration(config: AudioSink.AudioSinkConfig) {
        val format = config.format
        val supportedPcm = format.sampleMimeType == MimeTypes.AUDIO_RAW &&
            format.pcmEncoding in intArrayOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT,
                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)
        val outputFormat = if (supportedPcm && format.pcmEncoding in
            intArrayOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            format.buildUpon().setPcmEncoding(C.ENCODING_PCM_24BIT).build()
        } else format
        delegate.configure(AudioSink.AudioSinkConfig.Builder(outputFormat)
            .setPreferredBufferSizeOverride(config.preferredBufferSizeOverride)
            .setOutputChannelMapping(config.outputChannelMapping)
            .setTimeline(config.timeline)
            .setMediaPeriodId(config.mediaPeriodId)
            .build())
        configuration = config
        nextConfiguration = null
        processor = if (supportedPcm) CarDspProcessor(format.sampleRate, format.channelCount,
            samplePeakLimiter = false) else null
        limiter = if (supportedPcm) TruePeakLimiter(format.sampleRate, format.channelCount) else null
        frame = DoubleArray(if (supportedPcm) format.channelCount else 0)
        emitted = DoubleArray(frame.size)
        clearStream()
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        revokeQueuedCorrection()
        if (!writePending()) return false
        if (discontinuityPending) {
            if (!drainLocal()) return false
            clearStream()
            delegate.handleDiscontinuity()
        }
        nextConfiguration?.let {
            if (!drainLocal()) return false
            applyConfiguration(it)
        }
        val dsp = processor ?: return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (!buffer.hasRemaining()) return true
        val format = checkNotNull(configuration).format
        val channels = format.channelCount
        val sourceWidth = sourceWidth(format)
        require(buffer.remaining() % (sourceWidth * channels) == 0) { "PCM buffer must contain whole frames" }
        if (baseTimeUs == null) baseTimeUs = presentationTimeUs
        dsp.beginBlock(levelingGain())
        val active = dsp.isDspActive || peakProtection()
        delayed = delayed || active
        val target = conversionBuffer(buffer.remaining() / sourceWidth * outputWidth())
        val source = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        while (source.hasRemaining()) {
            for (channel in frame.indices) frame[channel] = decode(source, format.pcmEncoding)
            dsp.processFrame(frame)
            if (!delayed) {
                encode(target, frame)
            } else if (checkNotNull(limiter).process(frame, emitted, enabled = active)) {
                encode(target, emitted)
                minimumGain = minOf(minimumGain, checkNotNull(limiter).lastOutputGain)
            }
        }
        buffer.position(buffer.limit())
        enqueue(target)
        writePending()
        report()
        // The input has been consumed; any blocked output remains owned by this sink.
        return true
    }

    private fun enqueue(target: ByteBuffer) {
        target.flip()
        if (!target.hasRemaining()) return
        pending = target
        pendingTimeUs = nextTimeUs()
        outputFrames += target.remaining() / frameBytes()
    }

    private fun nextTimeUs(): Long = (baseTimeUs ?: 0L) +
        outputFrames * 1_000_000L / checkNotNull(configuration).format.sampleRate

    private fun writePending(): Boolean {
        if (!pending.hasRemaining()) return true
        delegate.handleBuffer(pending, pendingTimeUs, 1)
        return !pending.hasRemaining()
    }

    private fun drainLocal(): Boolean {
        if (!writePending()) return false
        val peak = limiter ?: return true
        if (peak.pendingFrames == 0) return true
        val target = conversionBuffer(peak.pendingFrames * frameBytes())
        while (peak.drain(frame)) {
            encode(target, frame)
            minimumGain = minOf(minimumGain, peak.lastOutputGain)
        }
        enqueue(target)
        report()
        return writePending()
    }

    override fun playToEndOfStream() {
        revokeQueuedCorrection()
        if (!drainLocal()) return
        nextConfiguration?.let { applyConfiguration(it) }
        delegate.playToEndOfStream()
    }

    override fun hasPendingData(): Boolean = hasLocalData() || delegate.hasPendingData()
    override fun isEnded(): Boolean = !hasLocalData() && delegate.isEnded()
    private fun hasLocalData(): Boolean = pending.hasRemaining() || (limiter?.pendingFrames ?: 0) > 0

    override fun handleDiscontinuity() {
        // A renderer discontinuity is not a seek: preserve audio preceding the new timestamp.
        discontinuityPending = true
    }

    override fun flush() {
        clearStream()
        delegate.flush()
        nextConfiguration?.let { applyConfiguration(it) }
    }

    override fun reset() {
        clearStream()
        configuration = null
        nextConfiguration = null
        processor = null
        limiter = null
        delegate.reset()
    }

    private fun clearStream() {
        pending = emptyBuffer()
        limiter?.reset()
        processor?.reset()
        delayed = false
        baseTimeUs = null
        outputFrames = 0
        permitted = dspPermitted()
        clipped = false
        minimumGain = 1.0
        discontinuityPending = false
    }

    /** Preserve the queued duration with silence when a route withdraws correction consent. */
    private fun revokeQueuedCorrection() {
        val allowed = dspPermitted()
        if (permitted && !allowed && processor != null) {
            val withheldFrames = limiter?.pendingFrames ?: 0
            val bytes = pending.remaining() + withheldFrames * frameBytes()
            val timeUs = if (pending.hasRemaining()) pendingTimeUs +
                pending.position() / frameBytes() * 1_000_000L / checkNotNull(configuration).format.sampleRate
            else nextTimeUs()
            pending = direct(bytes)
            pendingTimeUs = timeUs
            outputFrames += withheldFrames
            limiter?.reset()
            processor?.reset()
            delayed = false
        }
        permitted = allowed
    }

    private fun decode(source: ByteBuffer, encoding: Int): Double = when (encoding) {
        C.ENCODING_PCM_FLOAT -> source.float.toDouble().let { if (it.isFinite()) it else 0.0 }
        C.ENCODING_PCM_16BIT -> source.short / 32768.0
        C.ENCODING_PCM_32BIT -> source.int / 2147483648.0
        else -> {
            val value = (source.get().toInt() and 255) or
                ((source.get().toInt() and 255) shl 8) or (source.get().toInt() shl 16)
            value / 8388608.0
        }
    }

    private fun encode(target: ByteBuffer, frame: DoubleArray) {
        val width = outputWidth()
        val scale = if (width == 4) 2147483648.0 else 8388608.0
        for (sample in frame) {
            val finite = if (sample.isFinite()) sample else 0.0
            if (abs(finite) > 1.0) clipped = true
            val value = (finite * scale).roundToLong().coerceIn(-scale.toLong(), scale.toLong() - 1)
            repeat(width) { byte -> target.put((value shr (byte * 8)).toByte()) }
        }
    }

    private fun report() {
        OutputGuard.report(minimumGain, clipped)
        minimumGain = 1.0
        clipped = false
    }

    private fun outputWidth() = if (configuration?.format?.pcmEncoding == C.ENCODING_PCM_32BIT) 4 else 3
    private fun frameBytes() = outputWidth() * checkNotNull(configuration).format.channelCount
    private fun sourceWidth(format: Format) = when (format.pcmEncoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        else -> 4
    }

    private fun conversionBuffer(bytes: Int): ByteBuffer {
        if (conversion.capacity() < bytes) conversion = direct(bytes)
        conversion.clear()
        conversion.limit(bytes)
        return conversion
    }

    companion object {
        private fun emptyBuffer(): ByteBuffer = ByteBuffer.allocateDirect(0)
        private fun direct(bytes: Int): ByteBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN)
        private fun dspPermitted() = HeadphoneDsp.current.speakerEligible || CarDsp.effectiveTarget() != null
    }
}
