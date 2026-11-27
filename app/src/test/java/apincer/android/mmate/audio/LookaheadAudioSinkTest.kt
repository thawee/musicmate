package apincer.android.mmate.audio

import android.media.AudioDeviceInfo
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import apincer.android.mmate.audio.dsp.CarDsp
import apincer.android.mmate.audio.dsp.CarDspSettings
import apincer.android.mmate.audio.dsp.PeqBand
import apincer.android.mmate.audio.dsp.TruePeakLimiter
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class LookaheadAudioSinkTest {
    private val delegate = mockk<AudioSink>(relaxed = true)
    private val speaker = mockk<AudioDeviceInfo> { every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    private val bytes = ByteArrayOutputStream()
    private val configurations = mutableListOf<AudioSink.AudioSinkConfig>()
    private val timestamps = mutableListOf<Long>()
    private val events = mutableListOf<String>()
    private var writeLimit = Int.MAX_VALUE
    private var ended = false
    private lateinit var sink: LookaheadAudioSink

    @Before
    fun setup() {
        OutputGuard.reset()
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        CarDsp.updateRoute(listOf(speaker))
        CarDsp.target = null
        every { delegate.configure(any<AudioSink.AudioSinkConfig>()) } answers {
            configurations += firstArg<AudioSink.AudioSinkConfig>()
            events += "configure"
        }
        every { delegate.handleDiscontinuity() } answers { events += "discontinuity" }
        every { delegate.handleBuffer(any(), any(), any()) } answers {
            val buffer = firstArg<ByteBuffer>()
            timestamps += secondArg<Long>()
            events += "buffer@${secondArg<Long>()}"
            val count = minOf(writeLimit, buffer.remaining())
            val part = ByteArray(count)
            buffer.get(part)
            bytes.write(part)
            !buffer.hasRemaining()
        }
        every { delegate.playToEndOfStream() } answers { ended = true }
        every { delegate.isEnded } answers { ended }
        every { delegate.hasPendingData() } returns false
        sink = LookaheadAudioSink(delegate)
    }

    @After
    fun teardown() {
        OutputGuard.reset()
        CarDsp.target = null
        CarDsp.updateRoute(listOf(speaker))
        unmockkStatic(Log::class)
    }

    @Test
    fun bypassWidens16BitExactlyWithoutDelay() {
        configure(C.ENCODING_PCM_16BIT)
        val input = pcm16(-32768, -1, 0, 1, 32767)
        assertTrue(sink.handleBuffer(input, 9000, 1))
        assertArrayEquals(packed(3, -8388608, -256, 0, 256, 8388352), bytes.toByteArray())
        assertEquals(C.ENCODING_PCM_24BIT, configurations.single().format.pcmEncoding)
        assertEquals(listOf(9000L), timestamps)
        assertFalse(sink.hasPendingData())
    }

    @Test
    fun bypassPreserves24And32BitCodes() {
        for (width in intArrayOf(3, 4)) {
            bytes.reset()
            configure(if (width == 3) C.ENCODING_PCM_24BIT else C.ENCODING_PCM_32BIT)
            val codes = if (width == 3) intArrayOf(-8388608, -1, 0, 8388607)
                else intArrayOf(Int.MIN_VALUE, -1, 0, Int.MAX_VALUE)
            val input = packed(width, *codes)
            sink.handleBuffer(ByteBuffer.wrap(input), 0, 1)
            assertArrayEquals(input, bytes.toByteArray())
        }
    }

    @Test
    fun floatBypassConvertsTo24Bit() {
        configure(C.ENCODING_PCM_FLOAT)
        val source = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        floatArrayOf(-1f, -0.5f, 0f, 0.5f, 1f).forEach { source.putFloat(it) }
        source.flip()
        sink.handleBuffer(source, 0, 1)
        assertArrayEquals(packed(3, -8388608, -4194304, 0, 4194304, 8388607), bytes.toByteArray())
    }

    @Test
    fun conversionPreservesGaplessMetadataAndBufferConfiguration() {
        val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setSampleRate(96000).setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setEncoderDelay(2112).setEncoderPadding(765).setId("gapless-track").build()
        sink.configure(AudioSink.AudioSinkConfig.Builder(format).setPreferredBufferSizeOverride(8192).build())
        val forwarded = configurations.single()
        assertEquals(format.buildUpon().setPcmEncoding(C.ENCODING_PCM_24BIT).build(), forwarded.format)
        assertEquals(8192, forwarded.preferredBufferSizeOverride)
    }

    @Test
    fun shortDspStreamDrainsEveryFrameAtEos() {
        CarDsp.target = CarDspSettings(emptyList())
        configure(C.ENCODING_PCM_16BIT)
        val frames = 31
        sink.handleBuffer(pcm16(*IntArray(frames) { 1000 }), 700000, 1)
        assertEquals(0, bytes.size())
        assertTrue(sink.hasPendingData())
        assertFalse(sink.isEnded)
        sink.playToEndOfStream()
        assertEquals(frames * 3, bytes.size())
        assertEquals(700000L, timestamps.first())
        assertTrue(ended)
        assertTrue(sink.isEnded)
    }

    @Test
    fun startupAndTailUseOutputFrameTimestamps() {
        CarDsp.target = CarDspSettings(emptyList())
        configure(C.ENCODING_PCM_16BIT)
        val latency = TruePeakLimiter(48000, 1).latencyFrames
        sink.handleBuffer(pcm16(*IntArray(latency + 40) { 1000 }), 1000000, 1)
        assertEquals(40 * 3, bytes.size())
        sink.playToEndOfStream()
        assertEquals((latency + 40) * 3, bytes.size())
        assertEquals(listOf(1000000L, 1000000L + 40L * 1000000 / 48000), timestamps)
    }

    @Test
    fun blockedWritesNeverReprocessInputOrLoseTail() {
        CarDsp.target = CarDspSettings(emptyList())
        configure(C.ENCODING_PCM_16BIT)
        val latency = TruePeakLimiter(48000, 1).latencyFrames
        writeLimit = 6
        val source = pcm16(*IntArray(latency + 20) { 1000 })
        assertTrue(sink.handleBuffer(source, 3000, 1))
        assertEquals(0, source.remaining())
        val next = pcm16(2000)
        assertFalse(sink.handleBuffer(next, 9999, 1))
        assertEquals(2, next.remaining())
        repeat(latency + 30) { sink.playToEndOfStream() }
        assertEquals((latency + 20) * 3, bytes.size())
        assertTrue(ended)
    }

    @Test
    fun flushDiscardsTailAndDiscontinuityPreservesItBeforeNewTimestamp() {
        CarDsp.target = CarDspSettings(emptyList())
        configure(C.ENCODING_PCM_16BIT)
        sink.handleBuffer(pcm16(1000, 1000), 0, 1)
        sink.flush()
        sink.handleBuffer(pcm16(2000), 500000, 1)
        sink.handleDiscontinuity()
        sink.handleBuffer(pcm16(3000), 900000, 1)
        sink.playToEndOfStream()
        assertEquals(6, bytes.size())
        assertEquals(listOf(500000L, 900000L), timestamps)
    }

    @Test
    fun formatSwitchDrainsOldFramesBeforeDelegateReconfiguration() {
        CarDsp.target = CarDspSettings(emptyList())
        configure(C.ENCODING_PCM_16BIT)
        sink.handleBuffer(pcm16(1000, 1000), 100000, 1)
        configure(C.ENCODING_PCM_32BIT, 44100)
        assertEquals(1, configurations.size)
        sink.handleBuffer(ByteBuffer.wrap(packed(4, 200000)), 200000, 1)
        assertEquals(2, configurations.size)
        assertEquals(6, bytes.size())
        sink.playToEndOfStream()
        assertEquals(10, bytes.size())
        assertEquals(listOf(100000L, 200000L), timestamps)
    }

    @Test
    fun gainOnlyDoesNotEngageLookahead() {
        sink = LookaheadAudioSink(delegate) { 0.5 }
        configure(C.ENCODING_PCM_16BIT)
        sink.handleBuffer(pcm16(1000, -1000), 0, 1)
        assertArrayEquals(packed(3, 128000, -128000), bytes.toByteArray())
        assertFalse(sink.hasPendingData())
    }

    @Test
    fun replayGainProtectionLimitsWithoutEqAndDrainsEveryFrame() {
        sink = LookaheadAudioSink(delegate, peakProtection = { true }, levelingGain = { 2.0 })
        configure(C.ENCODING_PCM_FLOAT)
        val input = DoubleArray(2400) { 0.75 }
        sink.handleBuffer(samples(C.ENCODING_PCM_FLOAT, input), 0, 1)
        assertTrue(sink.hasPendingData())
        sink.playToEndOfStream()
        assertEquals(input.size * 3, bytes.size())
        val reference = TruePeakLimiter(48000, 1)
        val frame = DoubleArray(1)
        val expectedCodes = mutableListOf<Int>()
        for (value in input) {
            if (reference.process(doubleArrayOf(value * 2), frame)) {
                expectedCodes += (frame[0] * 8388608).roundToInt()
            }
        }
        while (reference.drain(frame)) expectedCodes += (frame[0] * 8388608).roundToInt()
        assertArrayEquals(packed(3, *expectedCodes.toIntArray()), bytes.toByteArray())
        assertTrue(OutputGuard.state.value.limitedAtMs > 0)
        assertFalse(OutputGuard.state.value.clippedAtMs > 0)
    }

    @Test
    fun turningProtectionOffPreservesLaterBypassSamples() {
        var protect = true
        sink = LookaheadAudioSink(delegate, peakProtection = { protect })
        configure(C.ENCODING_PCM_24BIT)
        sink.handleBuffer(samples(C.ENCODING_PCM_24BIT, DoubleArray(1000) { 0.95 }), 0, 1)
        protect = false
        // Longer than the limiter's 50 ms ramp back to unity
        sink.handleBuffer(samples(C.ENCODING_PCM_24BIT, DoubleArray(2500) { 0.95 }), 21000, 1)
        val tail = packed(3, -8388608, -1, 0, 1, 8388607)
        sink.handleBuffer(ByteBuffer.wrap(tail), 73000, 1)
        sink.playToEndOfStream()
        assertEquals(3505 * 3, bytes.size())
        assertArrayEquals(tail, bytes.toByteArray().takeLast(tail.size).toByteArray())
    }

    @Test
    fun withdrawnRouteConsentSilencesWithheldCorrection() {
        CarDsp.target = CarDspSettings(emptyList())
        configure(C.ENCODING_PCM_16BIT)
        sink.handleBuffer(pcm16(1000, 1000), 0, 1)
        CarDsp.updateRoute(emptyList())
        sink.playToEndOfStream()
        assertArrayEquals(ByteArray(6), bytes.toByteArray())
    }

    @Test
    fun highResolutionFormatsEnableAndDisableDspWithoutChangingLaterBypassCodes() {
        for (encoding in intArrayOf(C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)) {
            sink.reset()
            bytes.reset()
            CarDsp.target = null
            configure(encoding)
            sink.handleBuffer(samples(encoding, doubleArrayOf(0.25)), 0, 1)
            assertEquals(if (encoding == C.ENCODING_PCM_32BIT) 4 else 3, bytes.size())
            CarDsp.target = CarDspSettings(emptyList())
            sink.handleBuffer(samples(encoding, DoubleArray(2000) { 0.95 }), 20, 1)
            CarDsp.target = null
            sink.handleBuffer(samples(encoding, DoubleArray(2000) { 0.95 }), 42000, 1)
            // The fade-out block above still counts as active; this one outlasts the limiter's 50 ms ramp to unity
            sink.handleBuffer(samples(encoding, DoubleArray(2500) { 0.95 }), 84000, 1)
            val tail = doubleArrayOf(-1.0, -0.25, 0.0, 0.25, 1.0)
            sink.handleBuffer(samples(encoding, tail), 146000, 1)
            sink.playToEndOfStream()
            val width = if (encoding == C.ENCODING_PCM_32BIT) 4 else 3
            val expected = if (width == 4) packed(4, Int.MIN_VALUE, -536870912, 0, 536870912, Int.MAX_VALUE)
                else packed(3, -8388608, -2097152, 0, 2097152, 8388607)
            assertArrayEquals("encoding=$encoding", expected, bytes.toByteArray().takeLast(tail.size * width).toByteArray())
            assertEquals(6506 * width, bytes.size())
        }
    }

    @Test
    fun hotFloatDspReachesLimiterBeforeQuantization() {
        CarDsp.target = CarDspSettings(emptyList())
        configure(C.ENCODING_PCM_FLOAT)
        val input = DoubleArray(1200) { if (it % 2 == 0) 2.0 else -0.5 }
        sink.handleBuffer(samples(C.ENCODING_PCM_FLOAT, input), 0, 1)
        sink.playToEndOfStream()
        val reference = TruePeakLimiter(48000, 1)
        val frame = DoubleArray(1)
        val expectedCodes = mutableListOf<Int>()
        fun capture() { expectedCodes += (frame[0] * 8388608).roundToInt() }
        for (value in input) {
            if (reference.process(doubleArrayOf(value), frame)) capture()
        }
        while (reference.drain(frame)) capture()
        assertArrayEquals(packed(3, *expectedCodes.toIntArray()), bytes.toByteArray())
    }

    @Test
    fun gaplessBoundaryKeepsEqStateAndStampsTheNewTrackAtItsTimestamp() {
        CarDsp.target = CarDspSettings(listOf(PeqBand(PeqBand.Type.PEAK, 1000.0, 6.0, 1.0)))
        val format = stereo(48000)
        val continuous = playTwoTracks(format, boundary = false)
        val gapless = playTwoTracks(format, boundary = true)
        assertArrayEquals(continuous, gapless)
        val latency = TruePeakLimiter(48000, 2).latencyFrames
        assertEquals(6000L, latency * 1_000_000L / 48000)
        val change = events.indexOf("discontinuity")
        assertEquals("the old track's tail must not be drained at the boundary",
            4, events.subList(0, change).count { it.startsWith("buffer") })
        // The first buffer after the boundary opens with the held old frames, so the new track starts `latency` frames in
        assertEquals(listOf("discontinuity", "configure", "buffer@${NEXT_TRACK_US - 6000}"),
            events.subList(change, change + 3))
    }

    @Test
    fun formatChangeOrEncoderDelayAtBoundaryStillResets() {
        CarDsp.target = CarDspSettings(listOf(PeqBand(PeqBand.Type.PEAK, 1000.0, 6.0, 1.0)))
        for (next in listOf(stereo(44100), stereo(48000).buildUpon().setEncoderDelay(576).build())) {
            playTwoTracks(stereo(48000), boundary = true, next = next)
            val change = events.indexOf("discontinuity")
            assertEquals("tail drained before the change", 5, events.subList(0, change).count { it.startsWith("buffer") })
            assertEquals(listOf("discontinuity", "configure", "buffer@$NEXT_TRACK_US"), events.subList(change, change + 3))
        }
    }

    /** Two 4-block stereo sine tracks; with [boundary], replays Media3's gapless order: discontinuity, configure, new buffer. */
    private fun playTwoTracks(format: Format, boundary: Boolean, next: Format = format): ByteArray {
        bytes.reset()
        timestamps.clear()
        events.clear()
        sink = LookaheadAudioSink(delegate)
        sink.configure(AudioSink.AudioSinkConfig.Builder(format).build())
        val frames = 1024
        for (block in 0 until 8) {
            if (boundary && block == 4) {
                sink.handleDiscontinuity()
                sink.configure(AudioSink.AudioSinkConfig.Builder(next.buildUpon().build()).build())
            }
            val values = DoubleArray(frames * 2) {
                val index = block * frames + it / 2
                0.6 * sin(2 * PI * (if (it % 2 == 0) 997.0 else 1499.0) * index / 48000)
            }
            val pts = if (boundary && block >= 4) NEXT_TRACK_US + (block - 4) * frames * 1_000_000L / 48000
                else block * frames * 1_000_000L / 48000
            assertTrue(sink.handleBuffer(samples(C.ENCODING_PCM_FLOAT, values), pts, 1))
        }
        sink.playToEndOfStream()
        return bytes.toByteArray()
    }

    private fun stereo(rate: Int): Format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setSampleRate(rate).setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_FLOAT).build()

    private fun configure(encoding: Int, rate: Int = 48000) {
        sink.configure(AudioSink.AudioSinkConfig.Builder(Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW).setSampleRate(rate).setChannelCount(1)
            .setPcmEncoding(encoding).build()).build())
    }

    private fun pcm16(vararg values: Int): ByteBuffer = ByteBuffer.wrap(packed(2, *values)).order(ByteOrder.LITTLE_ENDIAN)

    private fun samples(encoding: Int, values: DoubleArray): ByteBuffer {
        if (encoding == C.ENCODING_PCM_FLOAT) {
            return ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).also { buffer ->
                values.forEach { buffer.putFloat(it.toFloat()) }
                buffer.flip()
            }
        }
        val width = if (encoding == C.ENCODING_PCM_32BIT) 4 else 3
        val scale = if (width == 4) 2147483648.0 else 8388608.0
        return ByteBuffer.wrap(packed(width, *values.map {
            (it * scale).toLong().coerceIn(-scale.toLong(), scale.toLong() - 1).toInt()
        }.toIntArray()))
    }

    private fun packed(width: Int, vararg values: Int): ByteArray = ByteArray(values.size * width).also { output ->
        values.forEachIndexed { index, value -> repeat(width) { byte -> output[index * width + byte] = (value shr (8 * byte)).toByte() } }
    }

    private companion object {
        const val NEXT_TRACK_US = 10_000_000L
    }
}
