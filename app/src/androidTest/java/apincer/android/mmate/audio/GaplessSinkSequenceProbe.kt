package apincer.android.mmate.audio

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import apincer.android.mmate.audio.dsp.CarDsp
import apincer.android.mmate.audio.dsp.CarDspSettings
import apincer.android.mmate.audio.dsp.PeqBand
import apincer.android.mmate.audio.dsp.TruePeakLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Records what Media3 asks of the lookahead sink at a FLAC gapless boundary, and what it forwards. */
@RunWith(AndroidJUnit4::class)
class GaplessSinkSequenceProbe {
    private val events = mutableListOf<String>()
    private val start = System.nanoTime()
    private val streamGain = StreamGain()

    private inner class Recorder(private val label: String, sink: AudioSink) : ForwardingAudioSink(sink) {
        private var lastTimeUs = Long.MIN_VALUE

        private fun note(event: String) = synchronized(events) {
            events += "%7.1f ms %s %s gain=%.2f".format((System.nanoTime() - start) / 1e6, label, event, streamGain.current)
        }

        override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
            val f = audioSinkConfig.format
            note("configure encoding=${f.pcmEncoding} rate=${f.sampleRate} channels=${f.channelCount} " +
                "delay=${f.encoderDelay} padding=${f.encoderPadding} id=${f.id}")
            super.configure(audioSinkConfig)
        }

        override fun handleDiscontinuity() {
            note("handleDiscontinuity")
            super.handleDiscontinuity()
        }

        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            val bytes = buffer.remaining()
            if (presentationTimeUs != lastTimeUs) note("handleBuffer pts=$presentationTimeUs bytes=$bytes")
            lastTimeUs = presentationTimeUs
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        override fun flush() {
            note("flush")
            super.flush()
        }

        override fun playToEndOfStream() {
            note("playToEndOfStream")
            super.playToEndOfStream()
        }
    }

    @Test
    fun flacGaplessBoundaryKeepsDspState() = probe(listOf("a.flac", "b.flac"), 44100)

    @Test
    fun hiResFlacGaplessBoundaryKeepsDspState() = probe(listOf("hires_a.flac", "hires_b.flac"), 96000)

    @Test
    fun wavGaplessBoundaryKeepsDspState() = probe(listOf("a.wav", "b.wav"), 44100)

    private fun probe(names: List<String>, sampleRate: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "gapless-probe").apply { mkdirs() }
        val files = names.map { name ->
            File(directory, name).also { file ->
                instrumentation.context.assets.open("gapless/$name").use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
            }
        }
        val speaker = context.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
        // Speaker correction is what engages the limiter delay the timing check measures
        assumeTrue("no built-in speaker route", speaker.isNotEmpty())
        val savedTarget = CarDsp.target
        CarDsp.updateRoute(speaker)
        CarDsp.target = CarDspSettings(listOf(PeqBand(PeqBand.Type.PEAK, 1000.0, 3.0, 1.0)))
        val ended = CountDownLatch(1)
        var player: ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                val factory = object : DefaultRenderersFactory(context) {
                    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean,
                                                enableAudioTrackPlaybackParams: Boolean): AudioSink {
                        val output = DefaultAudioSink.Builder(context).setEnableFloatOutput(true).build()
                        return Recorder("outer", LookaheadAudioSink(Recorder("inner", output),
                            levelingGain = streamGain::current))
                    }

                    override fun buildAudioRenderers(context: Context, extensionRendererMode: Int,
                                                     mediaCodecSelector: MediaCodecSelector, enableDecoderFallback: Boolean,
                                                     audioSink: AudioSink, eventHandler: Handler,
                                                     eventListener: AudioRendererEventListener, out: ArrayList<Renderer>) {
                        out.add(LevelingAudioRenderer(context, codecAdapterFactory, mediaCodecSelector,
                            enableDecoderFallback, eventHandler, eventListener, audioSink, streamGain))
                    }
                }.setMediaCodecSelector(FlacDecoderSelector)
                player = ExoPlayer.Builder(context, factory).build().apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) ended.countDown()
                        }
                    })
                    setMediaItems(files.mapIndexed { index, file ->
                        MediaItem.Builder().setUri(Uri.fromFile(file))
                            .setTag(TrackGain(file.path, if (index == 0) 1.0 else 0.5)).build()
                    })
                    prepare()
                    play()
                }
            }
            assertTrue("playlist did not end", ended.await(30, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { player?.release() }
            CarDsp.target = savedTarget
            directory.deleteRecursively()
        }
        val log = synchronized(events) { events.toList() }
        log.forEach { Log.i(TAG, it) }
        val outerChange = log.indexOfFirst { " outer handleDiscontinuity" in it }
        val innerChange = log.indexOfFirst { " inner handleDiscontinuity" in it }
        assertTrue("no gapless discontinuity reached the output", outerChange in 0 until innerChange)
        assertTrue("the old track's tail was drained, so DSP state was reset",
            log.subList(outerChange, innerChange).none { " inner handleBuffer" in it })
        val newTrackUs = pts(log.drop(outerChange).first { " outer handleBuffer" in it })
        val firstOutputUs = pts(log.drop(innerChange).first { " inner handleBuffer" in it })
        val heldUs = TruePeakLimiter(sampleRate, 2).latencyFrames * 1_000_000.0 / sampleRate
        assertEquals(newTrackUs - heldUs, firstOutputUs.toDouble(), 1.0)
        Log.i(TAG, "continuous boundary: new track at $newTrackUs us, first output after it at $firstOutputUs us " +
            "carries %.1f us of held frames".format(newTrackUs - firstOutputUs.toDouble()))
    }

    private fun pts(event: String): Long = Regex("pts=(\\d+)").find(event)!!.groupValues[1].toLong()

    private companion object {
        const val TAG = "GaplessProbe"
    }
}
