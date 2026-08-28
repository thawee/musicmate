package apincer.android.mmate.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import apincer.music.core.model.AudioTag
import apincer.music.core.model.Track
import apincer.music.core.repository.TagRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TagsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var repository: TagRepository
    private lateinit var viewModel: TagsViewModel

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        viewModel = TagsViewModel(repository)
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
}
