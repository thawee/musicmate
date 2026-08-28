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
}
