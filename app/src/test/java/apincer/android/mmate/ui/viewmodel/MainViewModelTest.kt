package apincer.android.mmate.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import apincer.music.core.model.AudioTag
import apincer.music.core.model.SearchCriteria
import apincer.music.core.model.SearchResultStats
import apincer.music.core.model.Track
import apincer.music.core.repository.FileRepository
import apincer.music.core.repository.TagRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fileRepository: FileRepository
    private lateinit var tagRepository: TagRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fileRepository = mockk(relaxed = true)
        tagRepository = mockk(relaxed = true)
        viewModel = MainViewModel(fileRepository, tagRepository, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadMusicItems_success() = runTest(testDispatcher) {
        val criteria = SearchCriteria(SearchCriteria.TYPE.LIBRARY)
        val expectedTracks: List<Track> = listOf(
            AudioTag().apply { setTitle("Track 1"); setArtist("Artist A") },
            AudioTag().apply { setTitle("Track 2"); setArtist("Artist B") }
        )
        val expectedStats = SearchResultStats(2, 1000000L, 360.0)

        every { tagRepository.getSearchStats(criteria) } returns expectedStats
        every { tagRepository.findMusic(criteria, 0L, 500L) } returns expectedTracks

        viewModel.loadMusicItems(criteria)
        advanceUntilIdle()

        assertEquals(expectedTracks, viewModel.musicItems.value)
        assertEquals(expectedTracks, viewModel.musicItemsFlow.value)
        assertEquals(expectedStats, viewModel.searchStats.value)
        assertEquals(expectedStats, viewModel.searchStatsFlow.value)
        assertFalse(viewModel.musicItemsLoading.value ?: true)
        assertFalse(viewModel.musicItemsLoadingFlow.value)
    }

    @Test
    fun testSearch_updatesCriteriaAndLoads() = runTest(testDispatcher) {
        val criteria = SearchCriteria(SearchCriteria.TYPE.LIBRARY)
        val tracks: List<Track> = listOf(AudioTag().apply { setTitle("Hotel California") })
        val expectedStats = SearchResultStats(1, 500000L, 180.0)

        every { tagRepository.getSearchStats(criteria) } returns expectedStats
        every { tagRepository.findMusic(criteria, 0L, 500L) } returns tracks

        viewModel.search(criteria, "Eagles")
        advanceUntilIdle()

        assertTrue(criteria.isSearchMode)
        assertEquals("Eagles", criteria.searchText)
        assertEquals(tracks, viewModel.musicItems.value)
        assertEquals(tracks, viewModel.musicItemsFlow.value)
    }

    @Test
    fun testDeleteMediaTag_invokesRepository() = runTest(testDispatcher) {
        val track = AudioTag().apply { setTitle("To Delete") }

        viewModel.deleteMediaTag(track)
        advanceUntilIdle()

        verify { tagRepository.deleteMediaTag(track) }
    }

    @Test
    fun olderRequestFinishingLastDoesNotReplaceNewerLibraryResults() = runTest(testDispatcher) {
        val io = QueuedDispatcher()
        val model = MainViewModel(fileRepository, tagRepository, io)
        val oldCriteria = SearchCriteria(SearchCriteria.TYPE.GENRE, "Old genre")
        val newCriteria = SearchCriteria(SearchCriteria.TYPE.GENRE, "New genre")
        val oldTracks = tracks(1, 2)
        val newTracks = tracks(101, 3)
        val oldStats = SearchResultStats(2, 20L, 20.0)
        val newStats = SearchResultStats(3, 30L, 30.0)
        every { tagRepository.getSearchStats(oldCriteria) } returns oldStats
        every { tagRepository.getSearchStats(newCriteria) } returns newStats
        every { tagRepository.findMusic(oldCriteria, 0L, 500L) } returns oldTracks
        every { tagRepository.findMusic(newCriteria, 0L, 500L) } returns newTracks

        model.loadMusicItems(oldCriteria)
        model.loadMusicItems(newCriteria)
        io.runNewest()
        advanceUntilIdle()
        io.drain()
        advanceUntilIdle()

        assertEquals(newTracks, model.musicItemsFlow.value)
        assertEquals(newStats, model.searchStatsFlow.value)
        assertFalse(model.musicItemsLoadingFlow.value)
    }

    @Test
    fun pendingPageFromPreviousCategoryDoesNotAppendToNewCategory() = runTest(testDispatcher) {
        val io = QueuedDispatcher()
        val model = MainViewModel(fileRepository, tagRepository, io)
        val oldCriteria = SearchCriteria(SearchCriteria.TYPE.GENRE, "Old genre")
        val newCriteria = SearchCriteria(SearchCriteria.TYPE.GENRE, "New genre")
        val newTracks = tracks(1001, 3)
        every { tagRepository.findMusic(oldCriteria, 0L, 500L) } returns tracks(1, 500)
        every { tagRepository.findMusic(oldCriteria, 500L, 500L) } returns tracks(501, 20)
        every { tagRepository.findMusic(newCriteria, any(), any()) } returns newTracks

        model.loadMusicItems(oldCriteria)
        io.drain()
        advanceUntilIdle()
        model.loadMoreMusicItems()
        model.loadMusicItems(newCriteria)
        io.runNewest()
        advanceUntilIdle()
        io.drain()
        advanceUntilIdle()

        assertEquals(newTracks, model.musicItemsFlow.value)
        assertFalse(model.musicItemsLoadingFlow.value)
    }

    @Test
    fun loadsMoreThanFiveHundredTracksInOrderWithoutDuplicates() = runTest(testDispatcher) {
        val criteria = SearchCriteria(SearchCriteria.TYPE.LIBRARY)
        val allTracks = tracks(1, 1003)
        every { tagRepository.findMusic(criteria, any(), 500L) } answers {
            val offset = secondArg<Long>().toInt()
            allTracks.drop(offset).take(500)
        }

        viewModel.loadMusicItems(criteria)
        advanceUntilIdle()
        repeat(3) {
            viewModel.loadMoreMusicItems()
            advanceUntilIdle()
        }

        assertEquals(allTracks.map { it.id }, viewModel.musicItemsFlow.value.map { it.id })
        assertEquals(1003, viewModel.musicItemsFlow.value.map { it.id }.distinct().size)
        assertFalse(viewModel.musicItemsLoadingFlow.value)
    }

    @Test
    fun quickPlayQueuesAllResultsEvenWhenOnlyFirstPageIsLoaded() = runTest(testDispatcher) {
        val criteria = SearchCriteria(SearchCriteria.TYPE.LIBRARY).apply { searchFor("Artist") }
        val allTracks = tracks(1, 1003)
        every { tagRepository.findMusic(criteria, 0L, 500L) } returns allTracks.take(500)
        every { tagRepository.findMusic(criteria, 0L, Long.MAX_VALUE) } returns allTracks
        viewModel.loadMusicItems(criteria)
        advanceUntilIdle()
        val service = mockk<apincer.music.core.playback.spi.PlaybackService>(relaxed = true)
        val queue = mockk<apincer.music.core.repository.QueueManager>(relaxed = true)
        var queued: List<Track> = emptyList()
        every { service.queueManager } returns queue
        every { queue.setPlayingQueue(any()) } answers { queued = firstArg(); Unit }
        every { queue.songs } answers { queued }

        viewModel.playCurrentResults(allTracks[20], service)
        advanceUntilIdle()

        assertEquals(allTracks, queued)
        verify { service.playSong(allTracks[20]) }
        assertEquals(500, viewModel.musicItems.value!!.size)
    }

    @Test
    fun failedQuickPlayLookupLeavesExistingQueueUntouchedAndReportsError() = runTest(testDispatcher) {
        val criteria = SearchCriteria(SearchCriteria.TYPE.LIBRARY)
        every { tagRepository.findMusic(criteria, 0L, 500L) } returns tracks(1, 2)
        every { tagRepository.findMusic(criteria, 0L, Long.MAX_VALUE) } throws IllegalStateException("Database unavailable")
        viewModel.loadMusicItems(criteria)
        advanceUntilIdle()
        val service = mockk<apincer.music.core.playback.spi.PlaybackService>(relaxed = true)

        viewModel.playCurrentResults(viewModel.musicItems.value!![0], service)
        advanceUntilIdle()

        verify(exactly = 0) { service.queueManager }
        verify(exactly = 0) { service.playSong(any()) }
        assertEquals("Couldn't start playback. Please try again.", viewModel.playbackError.value)
    }

    @Test
    fun zeroSearchResultsAreNotMistakenForAnEmptyLibrary() = runTest(testDispatcher) {
        val criteria = SearchCriteria(SearchCriteria.TYPE.LIBRARY).apply { searchFor("No match") }
        every { tagRepository.findMusic(criteria, any(), any()) } returns emptyList()
        every { tagRepository.totalSongs } returns 100L
        viewModel.loadMusicItems(criteria)
        advanceUntilIdle()
        assertTrue(viewModel.musicItems.value!!.isEmpty())
        assertEquals(false, viewModel.libraryEmpty.value)

        every { tagRepository.totalSongs } returns 0L
        viewModel.loadMusicItems(criteria)
        advanceUntilIdle()
        assertEquals(true, viewModel.libraryEmpty.value)
    }

    private fun tracks(firstId: Int, count: Int): List<Track> =
        (firstId until firstId + count).map { id ->
            AudioTag().apply {
                setId(id.toLong())
                setUniqueKey("track-$id")
                setTitle("Track $id")
            }
        }

    // Reorders pending IO jobs explicitly; no sleeps or real-thread timing are involved.
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = java.util.ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runNewest() {
            tasks.removeLast().run()
        }

        fun drain() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}
