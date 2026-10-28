package apincer.android.mmate.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import apincer.music.core.model.AudioTag
import apincer.music.core.playback.PlaybackState
import apincer.music.core.playback.ExternalAndroidPlayer
import apincer.music.core.repository.QueueManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LocalQueueCompletionProbe {
    @Test
    fun manuallySelectedSong_advancesToItsFollowerOnNaturalCompletion() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("localQueueProbe") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bound = CompletableFuture<MusicMateServiceImpl>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                bound.complete((binder as MusicMateServiceImpl.MusicMateServiceImplBinder).service)
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val intent = Intent(context, MusicMateServiceImpl::class.java)
        check(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        val service = bound.get(30, TimeUnit.SECONDS)
        val directory = File(context.cacheDir, "local-queue-probe").apply { mkdirs() }
        val tracks = (1L..3L).map { id ->
            val file = File(directory, "$id.wav")
            val samples = 44100 * 3
            val dataSize = samples * 2
            val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
            buffer.putInt(16).putShort(1).putShort(1).putInt(44100).putInt(88200)
            buffer.putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize)
            file.writeBytes(buffer.array())
            AudioTag(id + 800000).apply {
                path = file.absolutePath
                title = "Queue probe $id"
                audioDuration = 3.0
                audioSampleRate = 44100
                audioBitsDepth = 16
                audioChannels = "1"
                audioEncoding = "WAV"
            }
        }
        val queue = service.queueManager
        val originalQueue = queue.songs.toList()
        val originalRepeat = queue.repeatMode
        val originalShuffle = queue.isShuffle
        try {
            instrumentation.runOnMainSync {
                service.switchPlayer(ExternalAndroidPlayer.LOCAL_TARGET_ID, true)
                queue.setPlayingQueue(tracks)
                queue.setRepeatMode(QueueManager.RepeatMode.OFF)
                queue.setShuffle(false)
                queue.setPlaybackTrack(tracks[0])
                service.playSong(tracks[1])
            }
            runBlocking {
                withTimeout(15000) {
                    service.playbackStateFlow.first { it.currentPositionSecond >= 1 }
                }
            }
            runBlocking {
                withTimeout(15000) {
                    service.playbackStateFlow.first {
                        it.currentPositionSecond == 0L && it.currentState == PlaybackState.State.PLAYING
                    }
                }
            }
            instrumentation.waitForIdleSync()
            assertEquals("natural completion must play the follower, not replay the selected song",
                tracks[2].id, service.nowPlayingSong.id)
            runBlocking {
                withTimeout(15000) {
                    service.playbackStateFlow.first {
                        it.currentTrack?.id == tracks[2].id && it.currentPositionSecond >= 1
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                service.stopPlaying()
                if (originalQueue.isEmpty()) {
                    tracks.forEach { queue.removeTrackById(it.id) }
                } else {
                    queue.setPlayingQueue(originalQueue)
                }
                queue.setRepeatMode(originalRepeat)
                queue.setShuffle(originalShuffle)
            }
            context.unbindService(connection)
            directory.deleteRecursively()
        }
    }
}
