package apincer.android.mmate.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import apincer.music.core.model.Track
import apincer.music.core.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.function.Function
import javax.inject.Inject

@HiltViewModel
class TagsViewModel(
    private val repos: TagRepository,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    @Inject
    constructor(repos: TagRepository) : this(repos, Dispatchers.IO)

    companion object {
        const val MULTIPLE_ALBUMS = "[Multiple Albums]"
        const val MULTIPLE_ARTISTS = "[Multiple Artists]"
        const val MULTIPLE_ALBUM_ARTISTS = "[Multiple Album Artists]"
        const val MULTIPLE_EMPTY = ""

        /**
         * Generic method to find a common string value from a list of Tracks for a given field.
         */
        @JvmStatic
        fun getCommonStringValue(
            tags: List<Track>?,
            valueExtractor: Function<Track, String?>,
            multipleValueString: String,
            defaultValueForNullCommon: String
        ): String {
            if (tags.isNullOrEmpty()) {
                return multipleValueString
            }

            val values = tags.filterNotNull().map { valueExtractor.apply(it) }
            if (values.isEmpty()) {
                return multipleValueString
            }

            val distinctCount = values.distinct().take(2).size
            return if (distinctCount > 1) {
                multipleValueString
            } else {
                values[0] ?: defaultValueForNullCommon
            }
        }
    }

    // --- Core Data ---
    private val _editItems = MutableLiveData<List<Track>>(emptyList())
    @JvmField
    val editItems: LiveData<List<Track>> = _editItems

    private val _displayTag = MutableLiveData<Track?>()
    @JvmField
    val displayTag: LiveData<Track?> = _displayTag

    // StateFlow equivalents for modern Compose screens
    private val _editItemsFlow = MutableStateFlow<List<Track>>(emptyList())
    val editItemsFlow: StateFlow<List<Track>> = _editItemsFlow.asStateFlow()

    private val _displayTagFlow = MutableStateFlow<Track?>(null)
    val displayTagFlow: StateFlow<Track?> = _displayTagFlow.asStateFlow()

    private fun setEditItems(items: List<Track>) {
        _editItemsFlow.value = items
        _editItems.postValue(items)
    }

    private fun setDisplayTag(tag: Track?) {
        _displayTagFlow.value = tag
        _displayTag.postValue(tag)
    }

    fun processAudioTagEditEvent(items: List<Track>?) {
        val safeItems = items ?: emptyList()
        setEditItems(safeItems)
        redisplayTag(safeItems)
    }

    fun updateWithPlayingSong(playingSong: Track?) {
        if (playingSong == null) return
        viewModelScope.launch(ioDispatcher) {
            repos.load(playingSong)
            val singleItemList = listOf(playingSong.copy())
            withContext(Dispatchers.Main) {
                setEditItems(singleItemList)
                redisplayTag(singleItemList)
            }
        }
    }

    fun redisplayTag(currentItems: List<Track>?) {
        if (currentItems.isNullOrEmpty()) {
            setDisplayTag(null)
            return
        }

        val newDisplayTag = currentItems[0].copy()
        if (currentItems.size > 1) {
            newDisplayTag.title = "[${currentItems.size} songs selected]"
            newDisplayTag.album = getCommonStringValue(currentItems, { it.album }, MULTIPLE_ALBUMS, MULTIPLE_EMPTY)
            newDisplayTag.artist = getCommonStringValue(currentItems, { it.artist }, MULTIPLE_ARTISTS, MULTIPLE_EMPTY)
            newDisplayTag.albumArtist = getCommonStringValue(currentItems, { it.albumArtist }, MULTIPLE_ALBUM_ARTISTS, MULTIPLE_EMPTY)
            newDisplayTag.genre = getCommonStringValue(currentItems, { it.genre }, MULTIPLE_EMPTY, MULTIPLE_EMPTY)
            newDisplayTag.track = getCommonStringValue(currentItems, { it.track }, MULTIPLE_EMPTY, MULTIPLE_EMPTY)
            newDisplayTag.year = getCommonStringValue(currentItems, { it.year }, MULTIPLE_EMPTY, MULTIPLE_EMPTY)
            newDisplayTag.publisher = getCommonStringValue(currentItems, { it.publisher }, MULTIPLE_EMPTY, MULTIPLE_EMPTY)
        }
        setDisplayTag(newDisplayTag)
    }

    fun refreshDisplayTag() {
        val items = _editItems.value
        if (items.isNullOrEmpty()) return

        viewModelScope.launch(ioDispatcher) {
            val updatedItems = ArrayList<Track>(items.size)
            for (musicTag in items) {
                repos.load(musicTag)
                updatedItems.add(musicTag)
            }
            withContext(Dispatchers.Main) {
                setEditItems(updatedItems)
                redisplayTag(updatedItems)
            }
        }
    }
}
