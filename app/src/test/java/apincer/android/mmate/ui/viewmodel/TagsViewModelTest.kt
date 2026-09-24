package apincer.android.mmate.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import apincer.music.core.model.AudioTag
import apincer.music.core.model.Track
import apincer.music.core.repository.TagRepository
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var repository: TagRepository
    private lateinit var viewModel: TagsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        viewModel = TagsViewModel(repository, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testGetCommonStringValue_singleValue() {
        val t1 = AudioTag().apply { setArtist("Pink Floyd") }
        val t2 = AudioTag().apply { setArtist("Pink Floyd") }
        val result = TagsViewModel.getCommonStringValue(
            listOf(t1, t2),
            { it.artist },
            TagsViewModel.MULTIPLE_ARTISTS,
            TagsViewModel.MULTIPLE_EMPTY
        )
        assertEquals("Pink Floyd", result)
    }

    @Test
    fun testGetCommonStringValue_multipleDistinctValues() {
        val t1 = AudioTag().apply { setArtist("Pink Floyd") }
        val t2 = AudioTag().apply { setArtist("Led Zeppelin") }
        val result = TagsViewModel.getCommonStringValue(
            listOf(t1, t2),
            { it.artist },
            TagsViewModel.MULTIPLE_ARTISTS,
            TagsViewModel.MULTIPLE_EMPTY
        )
        assertEquals(TagsViewModel.MULTIPLE_ARTISTS, result)
    }

    @Test
    fun testGetCommonStringValue_emptyList() {
        val result = TagsViewModel.getCommonStringValue(
            emptyList(),
            { it.artist },
            TagsViewModel.MULTIPLE_ARTISTS,
            TagsViewModel.MULTIPLE_EMPTY
        )
        assertEquals(TagsViewModel.MULTIPLE_ARTISTS, result)
    }

    @Test
    fun testGetCommonStringValue_nullFields() {
        val t1 = AudioTag().apply { setArtist(null) }
        val t2 = AudioTag().apply { setArtist(null) }
        val result = TagsViewModel.getCommonStringValue(
            listOf(t1, t2),
            { it.artist },
            TagsViewModel.MULTIPLE_ARTISTS,
            "DefaultEmpty"
        )
        assertEquals("DefaultEmpty", result)
    }

    @Test
    fun testProcessAudioTagEditEvent_singleTrack() {
        val track = AudioTag().apply {
            setTitle("Time")
            setArtist("Pink Floyd")
            setAlbum("The Dark Side of the Moon")
        }

        viewModel.processAudioTagEditEvent(listOf(track))

        val display = viewModel.displayTag.value
        assertNotNull(display)
        assertEquals("Time", display?.title)
        assertEquals("Pink Floyd", display?.artist)
        assertEquals("The Dark Side of the Moon", display?.album)
    }

    @Test
    fun testProcessAudioTagEditEvent_multipleTracks() {
        val t1 = AudioTag().apply {
            setTitle("Speak to Me")
            setArtist("Pink Floyd")
            setAlbum("The Dark Side of the Moon")
        }
        val t2 = AudioTag().apply {
            setTitle("Breathe")
            setArtist("Pink Floyd")
            setAlbum("The Dark Side of the Moon")
        }
        val t3 = AudioTag().apply {
            setTitle("Whole Lotta Love")
            setArtist("Led Zeppelin")
            setAlbum("Led Zeppelin II")
        }

        viewModel.processAudioTagEditEvent(listOf(t1, t2, t3))

        val display = viewModel.displayTag.value
        assertNotNull(display)
        assertEquals("[3 songs selected]", display?.title)
        assertEquals(TagsViewModel.MULTIPLE_ARTISTS, display?.artist)
        assertEquals(TagsViewModel.MULTIPLE_ALBUMS, display?.album)
    }

    @Test
    fun testRedisplayTag_emptyList() {
        viewModel.redisplayTag(emptyList())
        assertNull(viewModel.displayTag.value)
        assertNull(viewModel.displayTagFlow.value)
    }

    @Test
    fun refreshDisplayPreservesIndividualFilenameDraftsUntilSave() = runTest(testDispatcher) {
        val first = AudioTag().apply {
            setId(1L)
            setTitle("Old title one")
            setTrack("9")
        }
        val second = AudioTag().apply {
            setId(2L)
            setTitle("Old title two")
            setTrack("10")
        }
        val persisted = mapOf(first.id to first.copy(), second.id to second.copy())
        every { repository.load(any()) } answers {
            val target = firstArg<Track>()
            target.copy(requireNotNull(persisted[target.id]))
        }
        viewModel.processAudioTagEditEvent(listOf(first, second))
        advanceUntilIdle()

        // Filename extraction produces different values for each selected song.
        first.title = "Speak to Me"
        first.track = "1"
        second.title = "Breathe"
        second.track = "2"
        viewModel.refreshDisplayTag()
        advanceUntilIdle()

        val drafts = viewModel.editItemsFlow.value
        assertEquals(listOf("Speak to Me", "Breathe"), drafts.map { it.title })
        assertEquals(listOf("1", "2"), drafts.map { it.track })
        assertEquals("[2 songs selected]", viewModel.displayTagFlow.value?.title)
        assertEquals("", viewModel.displayTagFlow.value?.track)
    }

    @Test
    fun refreshDisplayShowsUnsavedFormattingWithoutRevertingToDatabase() = runTest(testDispatcher) {
        val track = AudioTag().apply {
            setTitle("TIME")
            setArtist("PINK FLOYD")
        }
        val persisted = track.copy()
        every { repository.load(any()) } answers {
            firstArg<Track>().copy(persisted)
        }
        viewModel.processAudioTagEditEvent(listOf(track))
        advanceUntilIdle()

        track.title = "Time"
        track.artist = "Pink Floyd"
        viewModel.refreshDisplayTag()
        advanceUntilIdle()

        assertEquals("Time", viewModel.editItemsFlow.value.single().title)
        assertEquals("Pink Floyd", viewModel.editItemsFlow.value.single().artist)
        assertEquals("Time", viewModel.displayTag.value?.title)
        assertEquals("Pink Floyd", viewModel.displayTagFlow.value?.artist)
    }

    @Test
    fun successfulSaveClearsDirtyStateWithoutClearingDisplayedValues() {
        viewModel.editorState.title = "Time"
        viewModel.editorState.titleModified = true
        viewModel.editorState.artist = "Pink Floyd"
        viewModel.editorState.artistModified = true
        viewModel.draftsDirty = true

        val allSaved = viewModel.recordSaveResult(successCount = 2, failureCount = 0)

        assertTrue(allSaved)
        assertFalse(viewModel.draftsDirty)
        assertFalse(viewModel.editorState.isAnyModified())
        assertEquals("Time", viewModel.editorState.title)
        assertEquals("Pink Floyd", viewModel.editorState.artist)
    }

    @Test
    fun partialSaveRetainsDraftAndModifiedFieldsForRetry() {
        viewModel.editorState.title = "Replacement title"
        viewModel.editorState.titleModified = true
        viewModel.draftsDirty = true

        val allSaved = viewModel.recordSaveResult(successCount = 1, failureCount = 1)

        assertFalse(allSaved)
        assertTrue(viewModel.draftsDirty)
        assertTrue(viewModel.editorState.titleModified)
        assertEquals("Replacement title", viewModel.editorState.title)
    }

    @Test
    fun failedSaveNeverSignalsThatEditorCanClose() {
        viewModel.editorState.album = "Unsaved album"
        viewModel.editorState.albumModified = true
        viewModel.draftsDirty = true

        val allSaved = viewModel.recordSaveResult(successCount = 0, failureCount = 2)

        assertFalse(allSaved)
        assertTrue(viewModel.draftsDirty)
        assertTrue(viewModel.editorState.albumModified)
        assertEquals("Unsaved album", viewModel.editorState.album)
    }
}
