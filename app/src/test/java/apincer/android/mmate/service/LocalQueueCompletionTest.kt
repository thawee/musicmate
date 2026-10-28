package apincer.android.mmate.service

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import apincer.music.core.model.AudioTag
import apincer.music.core.model.Track
import apincer.music.core.playback.AudioStreamCacheManager
import apincer.music.core.playback.ExternalAndroidPlayer
import apincer.music.core.playback.ListeningHistoryTracker
import apincer.music.core.playback.PlaybackState
import apincer.music.core.playback.spi.PlaybackTarget
import apincer.music.core.repository.QueueManager
import apincer.music.core.repository.TagRepository
import apincer.music.core.repository.spi.DbHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Optional

class LocalQueueCompletionTest {
    private lateinit var service: MusicMateServiceImpl
    private lateinit var queue: QueueManager
    private lateinit var targets: MutableStateFlow<Optional<PlaybackTarget>>
    private val played = mutableListOf<Long>()
    private val tracks = (1L..4L).map { id -> AudioTag().apply { setId(id) } }

    @Before
    fun setUp() {
        mockkStatic(Looper::class, SystemClock::class, Log::class, AudioStreamCacheManager::class)
        every { Looper.getMainLooper() } returns null
        every { SystemClock.elapsedRealtime() } returns 0L
        every { Log.d(any(), any()) } returns 0
        val cache = mockk<AudioStreamCacheManager>(relaxed = true)
        every { AudioStreamCacheManager.getInstance() } returns cache

        val database = mockk<DbHelper>(relaxed = true)
        every { database.playingQueue } returns tracks
        every { database.repeatMode } returns "OFF"
        every { database.smartQueueState } returns ""
        val repository = mockk<TagRepository>()
        every { repository.dbHelper } returns database
        queue = QueueManager(repository)
        queue.setPlaybackTrack(tracks[1])

        val controller = mockk<AndroidPlayerController>(relaxed = true)
        every { controller.play(any()) } answers { played += firstArg<Track>().id }
        // Bypass Android Service construction; the transport and completion methods run real code.
        service = spyk(mockk<MusicMateServiceImpl>())
        targets = MutableStateFlow(Optional.of(target(ExternalAndroidPlayer.LOCAL_TARGET_ID)))
        field("queueManager", queue)
        field("androidPlayer", controller)
        field("historyTracker", mockk<ListeningHistoryTracker>(relaxed = true))
        field("currentPlayerFlow", targets)
        field("currentTrackFlow", MutableStateFlow<Optional<Track>>(Optional.empty()))
        field("playbackStateFlow", MutableStateFlow(PlaybackState()))
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun selectedLocalTrackAdvancesToItsFollowerOnCompletion() {
        service.playSong(tracks[2])

        completeTrack()

        assertEquals(listOf(3L, 4L), played)
        assertEquals(4L, service.nowPlayingSong.id)
    }

    @Test
    fun repeatOneRepeatsTheSelectedLocalTrackOnCompletion() {
        queue.setRepeatMode(QueueManager.RepeatMode.ONE)
        service.playSong(tracks[2])

        completeTrack()

        assertEquals(listOf(3L, 3L), played)
    }

    @Test
    fun explicitNextSkipsSelectedLocalTrackEvenWithRepeatOne() {
        queue.setRepeatMode(QueueManager.RepeatMode.ONE)
        service.playSong(tracks[2])

        service.skipToNextInQueue()

        assertEquals(listOf(3L, 4L), played)
    }

    @Test
    fun playingOnAnotherAndroidTargetDoesNotConfirmTheLocalPlaybackAnchor() {
        targets.value = Optional.of(target("another.android.player"))

        service.playSong(tracks[2])

        assertEquals(3L, queue.getNextTrack(false).id)
    }

    private fun completeTrack() {
        MusicMateServiceImpl::class.java.getDeclaredMethod("advanceQueue", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(service, false)
    }

    private fun field(name: String, value: Any) {
        MusicMateServiceImpl::class.java.getDeclaredField(name).apply { isAccessible = true }
            .set(service, value)
    }

    private fun target(id: String) = mockk<PlaybackTarget>(relaxed = true).also {
        every { it.targetId } returns id
    }
}
