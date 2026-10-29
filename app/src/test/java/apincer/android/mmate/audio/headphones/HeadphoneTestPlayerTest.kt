package apincer.android.mmate.audio.headphones

import apincer.android.mmate.audio.dsp.CarDspSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sin

class HeadphoneTestPlayerTest {
    @Test fun guidedTonesContainOnlyTheSelectedFrequencyAndKeepEarsSeparate() {
        for (band in listOf(HeadphoneTestBand.BASS, HeadphoneTestBand.MIDS, HeadphoneTestBand.TREBLE)) {
            val pcm = HeadphoneTestTone.render(HeadphoneTestSignal.LEFT, CarDspSettings(emptyList()), band)
            assertTrue(pcm.size <= HeadphoneTestTone.RATE * 2 * 3)
            assertEquals(0, pcm.first().toInt())
            assertEquals(0, pcm[pcm.size - 2].toInt())
            assertTrue(pcm.all { abs(it.toInt()) <= 520 })
            for (frame in pcm.indices step 2) assertEquals(0, pcm[frame + 1].toInt())
            for (frequency in listOf(100.0, 1_000.0, 8_000.0)) {
                var projection = 0.0
                for (frame in HeadphoneTestTone.RATE until HeadphoneTestTone.RATE * 2) {
                    projection += pcm[frame * 2] * sin(2 * PI * frequency * frame / HeadphoneTestTone.RATE)
                }
                val amplitude = abs(projection) * 2 / HeadphoneTestTone.RATE
                if (frequency == band.frequencyHz) assertTrue("Missing selected frequency $band", amplitude > 400)
                else assertTrue("Unexpected $frequency Hz in $band", amplitude < 1)
            }
        }
    }

    @Test fun musicalComparisonUsesTheSameCompletePhraseForEachEar() {
        val source = FloatArray(48_000 * 3) { i -> (sin(2 * PI * 300 * i / 48_000) * (0.2 + 0.8 * i / (48_000.0 * 3))).toFloat() }
        val pcm = HeadphoneTestTone.render(HeadphoneTestSignal.ALTERNATING, CarDspSettings(emptyList()), source = source)
        assertEquals(48_000 * 6 * 2, pcm.size)
        for (i in source.indices) {
            assertEquals(pcm[i * 2], pcm[(i + source.size) * 2 + 1])
            assertEquals(0, pcm[i * 2 + 1].toInt())
            assertEquals(0, pcm[(i + source.size) * 2].toInt())
        }
        assertTrue(pcm.all { abs(it.toInt()) <= 520 })
    }

    @Test fun musicalOriginalAndAdjustedUseIdenticalSourceWithAudibleEq() {
        val source = FloatArray(144_000) { sin(2 * PI * 300 * it / 48_000).toFloat() * 0.5f }
        val original = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, CarDspSettings(emptyList()), source = source)
        val settings = ManualHeadphoneTuning(left = HeadphoneTone(warmthDb = -6.0), right = HeadphoneTone(warmthDb = -6.0)).settings()
        val adjusted = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings, source = source)
        assertArrayEquals(original, HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, CarDspSettings(emptyList()), source = source))
        assertTrue(adjusted.sumOf { abs(it.toInt()).toLong() } < original.sumOf { abs(it.toInt()).toLong() } * 0.7)
        assertEquals(0, adjusted.first().toInt())
        assertEquals(0, adjusted.last().toInt())
    }

    private val endpoint = HeadphoneEndpoint("usb:test", "DAC", true)

    @Test fun quietSignalsIdentifyEarsAndCenterUsesIdenticalSamples() {
        for (signal in HeadphoneTestSignal.entries) {
            val pcm = HeadphoneTestTone.render(signal, CarDspSettings(emptyList()))
            assertTrue(pcm.any { it != 0.toShort() })
            assertTrue(pcm.all { abs(it.toInt()) <= 520 })
            assertTrue(pcm.size <= 48_000 * 2 * 4)
            for (frame in 0 until pcm.size / 2) {
                if (signal == HeadphoneTestSignal.LEFT) assertEquals(0, pcm[frame * 2 + 1].toInt())
                if (signal == HeadphoneTestSignal.RIGHT) assertEquals(0, pcm[frame * 2].toInt())
                if (signal == HeadphoneTestSignal.CENTER) assertEquals(pcm[frame * 2], pcm[frame * 2 + 1])
                if (signal == HeadphoneTestSignal.ALTERNATING) assertTrue(pcm[frame * 2] == 0.toShort() || pcm[frame * 2 + 1] == 0.toShort())
            }
            assertEquals(0, pcm.first().toInt())
            assertEquals(0, pcm.last().toInt())
        }
    }

    @Test fun userStopAndCoroutineCancellationReleaseOutput() = runBlocking {
        val output = FakeOutput()
        val player = HeadphoneTestPlayer { output }
        val job = async { player.play(HeadphoneTestSignal.LEFT, CarDspSettings(emptyList()), endpoint) }
        while (!output.started) delay(1)
        player.stop()
        job.await()
        assertTrue(output.closed)
        val cancelledOutput = FakeOutput()
        val cancelledPlayer = HeadphoneTestPlayer { cancelledOutput }
        val cancelled = async { cancelledPlayer.play(HeadphoneTestSignal.RIGHT, CarDspSettings(emptyList()), endpoint) }
        while (!cancelledOutput.started) delay(1)
        cancelled.cancel()
        cancelled.join()
        assertTrue(cancelledOutput.closed)
    }

    @Test fun routeOrFocusLossStopsPlaybackAndAlwaysCloses() = runBlocking {
        val output = FakeOutput()
        val player = HeadphoneTestPlayer { output }
        val job = async { runCatching { player.play(HeadphoneTestSignal.CENTER, CarDspSettings(emptyList()), endpoint) } }
        while (!output.started) delay(1)
        output.valid = false
        assertTrue(job.await().isFailure)
        assertTrue(output.closed)
    }

    @Test fun normalCompletionAndOutputErrorsReleaseResources() = runBlocking {
        val completed = FakeOutput(consume = true)
        HeadphoneTestPlayer { completed }.play(HeadphoneTestSignal.CENTER, CarDspSettings(emptyList()), endpoint)
        assertTrue(completed.closed)
        assertEquals(48_000 * 3, completed.playedFrames)
        for (failStart in listOf(true, false)) {
            val failed = FakeOutput(failStart = failStart, failWrite = !failStart)
            val result = runCatching {
                HeadphoneTestPlayer { failed }.play(HeadphoneTestSignal.LEFT, CarDspSettings(emptyList()), endpoint)
            }
            assertTrue(result.isFailure)
            assertTrue(failed.closed)
        }
    }

    @Test fun replacingATestClosesItsOutputBeforeOpeningTheNext() = runBlocking {
        val first = FakeOutput()
        val second = FakeOutput()
        var opens = 0
        val player = HeadphoneTestPlayer {
            if (opens++ == 0) first else {
                assertTrue(first.closed)
                second
            }
        }
        val original = async { player.play(HeadphoneTestSignal.LEFT, CarDspSettings(emptyList()), endpoint) }
        while (!first.started) delay(1)
        val replacement = async { player.play(HeadphoneTestSignal.RIGHT, CarDspSettings(emptyList()), endpoint) }
        while (!second.started) delay(1)
        player.stop()
        original.await()
        replacement.await()
        assertTrue(second.closed)
    }

    private class FakeOutput(private val consume: Boolean = false,
        private val failStart: Boolean = false, private val failWrite: Boolean = false) : HeadphoneTestOutput {
        @Volatile var started = false
        @Volatile var closed = false
        @Volatile var valid = true
        override val routeValid get() = valid
        private var written = 0
        override val playedFrames get() = written / 2
        override fun start() { check(!failStart); started = true }
        override fun write(samples: ShortArray, offset: Int, size: Int): Int {
            check(!failWrite)
            if (!consume) return 0
            written += size
            return size
        }
        override fun close() { closed = true }
    }
}
