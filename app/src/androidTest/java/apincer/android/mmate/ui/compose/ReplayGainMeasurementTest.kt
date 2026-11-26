package apincer.android.mmate.ui.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import apincer.android.mmate.MusixMateApp
import apincer.music.core.model.AudioTag
import apincer.music.core.playback.Loudness
import apincer.music.core.playback.LoudnessTagger
import apincer.music.core.playback.ReplayGainManager
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ReplayGainMeasurementTest {
    @Test fun retryAfterPartialAlbumWriteMeasuresTheWholeAlbum() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tracks = (1..2).map { AudioTag().apply { path = "/retry/$it.flac"; album = "Retry album" } }
        val writes = AtomicInteger()
        val albums = mutableListOf<Loudness?>()
        val tagger = LoudnessTagger({ Loudness(-20.0, -1.0, 3.0) }, { _, _, album ->
            albums += album
            writes.incrementAndGet() != 2
        }, { tracks })
        val measurement = ReplayGainMeasurement(context, MusixMateApp.getInstance().tagRepository, tagger = tagger)
        try {
            measurement.start { tracks }
            withTimeout(5_000) { measurement.state.first { it.phase == ReplayGainMeasurement.Phase.COMPLETE } }
            assertEquals(1, measurement.state.value.failedTracks.size)
            // Completion notifications are published before the coroutine's finally block finishes.
            delay(100)
            measurement.retryFailed()
            val result = withTimeout(5_000) {
                measurement.state.first { it.phase == ReplayGainMeasurement.Phase.COMPLETE && it.succeeded == 2 }
            }
            assertTrue(result.failedTracks.isEmpty())
            assertEquals(4, writes.get())
            assertTrue(albums.all { it != null })
        } finally { measurement.close() }
    }

    @Test fun cancelDuringMeasurementPreventsWrites() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writes = AtomicInteger()
        val tagger = LoudnessTagger({
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            Loudness(-20.0, -1.0, 3.0)
        }, { _, _, _ -> writes.incrementAndGet(); true }, { emptyList() })
        val measurement = ReplayGainMeasurement(context, MusixMateApp.getInstance().tagRepository, tagger = tagger)
        try {
            measurement.measureTrack(AudioTag().apply { path = "/cancel.flac" })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            measurement.cancel()
            release.countDown()
            withTimeout(5_000) { measurement.state.first { it.phase == ReplayGainMeasurement.Phase.CANCELLED } }
            assertEquals(0, writes.get())
        } finally { release.countDown(); measurement.close() }
    }

    @Test fun measuringRealFlacWritesReadableTagsAndPreservesDecodedSamples() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "replaygain-verification").apply { mkdirs() }
        val source = File(directory, "measurement.flac")
        fun ffmpeg(command: String) {
            val session = FFmpegKit.execute(command)
            assertTrue("Fixture command failed", ReturnCode.isSuccess(session.returnCode))
        }
        ffmpeg("-y -f lavfi -i sine=frequency=440:sample_rate=44100:duration=3 -c:a flac '${source.absolutePath}'")
        fun sampleHash(name: String): List<Byte> {
            val pcm = File(directory, name)
            ffmpeg("-y -i '${source.absolutePath}' -f s16le '${pcm.absolutePath}'")
            return MessageDigest.getInstance("SHA-256").digest(pcm.readBytes()).toList()
        }
        val before = sampleHash("before.pcm")
        val measurement = ReplayGainMeasurement(context, MusixMateApp.getInstance().tagRepository)
        try {
            measurement.measureTrack(AudioTag().apply { path = source.absolutePath; audioDuration = 3.0 })
            val result = withTimeout(30_000) {
                measurement.state.first { it.phase == ReplayGainMeasurement.Phase.COMPLETE }
            }
            assertEquals(1, result.succeeded)
            assertTrue(result.failedTracks.isEmpty())
            ReplayGainManager.getInstance().invalidate(source.absolutePath)
            val tags = ReplayGainManager.getInstance().getReplayGain(source.absolutePath)
            assertNotNull(tags)
            assertTrue(tags.hasTrackGain)
            assertTrue(tags.trackGainDb.isFinite())
            assertEquals(before, sampleHash("after.pcm"))
        } finally { measurement.close() }
    }
}
