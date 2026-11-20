package apincer.android.mmate.audio

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import java.util.TreeMap

/** A queued track's leveling gain, carried as its MediaItem tag so a settings change can rewrite it. */
class TrackGain(val path: String, @Volatile var gain: Double)

/**
 * The leveling gain of the stream the audio renderer is outputting. Each stream's gain is noted when
 * the renderer queues it and takes over once the renderer outputs that stream, so a gapless change
 * lands on the first sample of the next track, not when its audio is heard after the buffer.
 */
class StreamGain {
    private val queued = TreeMap<Long, TrackGain>()
    private var output: TrackGain? = null

    @Volatile
    var current = 1.0
        private set

    @Synchronized
    fun streamQueued(offsetUs: Long, gain: TrackGain?) {
        if (gain != null) queued[offsetUs] = gain else queued.remove(offsetUs)
    }

    @Synchronized
    fun outputStreamChanged(offsetUs: Long) {
        queued.headMap(offsetUs).clear()
        output = queued[offsetUs]
        current = output?.gain ?: 1.0
    }

    /** Takes up a rewritten gain for the stream being output. */
    @Synchronized
    fun refresh() {
        current = output?.gain ?: 1.0
    }

    @Synchronized
    fun clear() {
        queued.clear()
        output = null
        current = 1.0
    }
}

/** Reports stream changes to [streamGain]; the base renderer already finds the gapless boundary. */
@OptIn(UnstableApi::class)
class LevelingAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink,
    private val streamGain: StreamGain
) : MediaCodecAudioRenderer(context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback,
    eventHandler, eventListener, audioSink) {
    private val period = Timeline.Period()
    private val window = Timeline.Window()

    override fun onStreamChanged(formats: Array<Format>, startPositionUs: Long, offsetUs: Long,
                                 mediaPeriodId: MediaSource.MediaPeriodId) {
        // Before super: the first stream's output offset is reported from inside it
        streamGain.streamQueued(offsetUs, trackGain(mediaPeriodId))
        super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId)
    }

    override fun onOutputStreamOffsetUsChanged(outputStreamOffsetUs: Long) {
        super.onOutputStreamOffsetUsChanged(outputStreamOffsetUs)
        streamGain.outputStreamChanged(outputStreamOffsetUs)
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean, sampleStreamIsResetToKeyFrame: Boolean) {
        super.onPositionReset(positionUs, joining, sampleStreamIsResetToKeyFrame)
        // A seek takes up a pending stream without reporting its offset
        streamGain.outputStreamChanged(outputStreamOffsetUs)
    }

    override fun onDisabled() {
        super.onDisabled()
        streamGain.clear()
    }

    private fun trackGain(mediaPeriodId: MediaSource.MediaPeriodId): TrackGain? {
        val timeline = timeline
        val index = timeline.getIndexOfPeriod(mediaPeriodId.periodUid)
        if (index == C.INDEX_UNSET) return null
        val item = timeline.getWindow(timeline.getPeriod(index, period).windowIndex, window).mediaItem
        return item.localConfiguration?.tag as? TrackGain
    }
}
