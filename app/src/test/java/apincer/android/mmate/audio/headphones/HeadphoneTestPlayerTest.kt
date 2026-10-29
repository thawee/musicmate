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
import kotlin.math.sqrt

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
        val source = FloatArray(144_000) { ((sin(2 * PI * 300 * it / 48_000) + sin(2 * PI * 3_000 * it / 48_000)) * 0.25).toFloat() }
        val settings = ManualHeadphoneTuning(left = HeadphoneTone(warmthDb = -6.0), right = HeadphoneTone(warmthDb = -6.0)).settings()
        val original = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings, source = source, original = true)
        val adjusted = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings, source = source)
        assertArrayEquals(original, HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings, source = source, original = true))
        assertEquals(rms(original), rms(adjusted), 0.5)
        assertTrue(abs(amplitude(adjusted, 300.0) / amplitude(adjusted, 3_000.0) -
            amplitude(original, 300.0) / amplitude(original, 3_000.0)) > 0.15)
        assertEquals(0, adjusted.first().toInt())
        assertEquals(0, adjusted.last().toInt())
    }

    @Test fun comparisonAttenuatesLouderReferenceWithoutBoostingQuieterDraft() {
        val settings = CarDspSettings(emptyList(), requestedPreampDb = -12.0)
        val flat = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, CarDspSettings(emptyList()))
        val original = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings, original = true)
        val adjusted = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings)
        assertEquals(rms(flat) * 0.25119, rms(adjusted), 0.5)
        assertEquals(rms(original), rms(adjusted), 0.5)
        assertTrue(original.maxOf { abs(it.toInt()) } < flat.maxOf { abs(it.toInt()) })
    }

    @Test fun matchingUsesOneCenterReferenceAcrossEarsAndSignals() {
        val settings = CarDspSettings(emptyList(), balanceDb = 6.0)
        val center = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings)
        assertEquals(0.50119, rms(center, 0) / rms(center, 1), 0.005)
        for (signal in listOf(HeadphoneTestSignal.LEFT, HeadphoneTestSignal.RIGHT)) {
            val ear = if (signal == HeadphoneTestSignal.LEFT) 0 else 1
            val pcm = HeadphoneTestTone.render(signal, settings)
            for (i in ear until pcm.size step 2) assertEquals(center[i], pcm[i])
            val original = HeadphoneTestTone.render(signal, settings, original = true)
            val originalCenter = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings, original = true)
            for (i in ear until pcm.size step 2) assertEquals(originalCenter[i], original[i])
        }
        val alternating = HeadphoneTestTone.render(HeadphoneTestSignal.ALTERNATING, settings)
        val segment = HeadphoneTestTone.RATE * 2
        assertEquals(0.50119, rms(alternating.copyOfRange(0, segment), 0) /
            rms(alternating.copyOfRange(segment, segment * 2), 1), 0.005)
        assertEquals(rms(HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings, original = true)), rms(center), 0.5)
    }

    @Test fun silentSourceAndQuietCeilingRemainSafe() {
        val settings = ManualHeadphoneTuning(left = HeadphoneTone(warmthDb = 6.0), right = HeadphoneTone(warmthDb = -6.0)).settings()
        for (original in listOf(true, false)) {
            val silence = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings, source = FloatArray(144_000), original = original)
            assertTrue(silence.all { it == 0.toShort() })
            val loud = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings, source = FloatArray(144_000) { 1f }, original = original)
            assertTrue(loud.all { abs(it.toInt()) <= 518 })
        }
    }

    @Test fun diagnosticToneRetainsTheAdjustedGainCue() {
        val settings = CarDspSettings(emptyList(), requestedPreampDb = -6.0)
        val original = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings, HeadphoneTestBand.MIDS, original = true)
        val adjusted = HeadphoneTestTone.render(HeadphoneTestSignal.CENTER, settings, HeadphoneTestBand.MIDS)
        assertEquals(0.50119, rms(adjusted) / rms(original), 0.005)
    }

    private fun rms(pcm: ShortArray, channel: Int? = null): Double {
        val values = if (channel == null) pcm.toList() else pcm.filterIndexed { i, _ -> i % 2 == channel }
        return sqrt(values.sumOf { it.toDouble() * it } / values.size)
    }

    private fun amplitude(pcm: ShortArray, frequency: Double): Double = abs(
        (HeadphoneTestTone.RATE until HeadphoneTestTone.RATE * 2).sumOf { i ->
            pcm[i * 2] * sin(2 * PI * frequency * i / HeadphoneTestTone.RATE)
        })

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
