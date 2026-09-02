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

    private val _studioProvenanceFlow = MutableStateFlow(StudioProvenanceInfo())
    val studioProvenanceFlow: StateFlow<StudioProvenanceInfo> = _studioProvenanceFlow.asStateFlow()

    private val _relatedTracksSheetState = MutableStateFlow(RelatedTracksSheetState())
    val relatedTracksSheetState: StateFlow<RelatedTracksSheetState> = _relatedTracksSheetState.asStateFlow()

    private fun setEditItems(items: List<Track>) {
        _editItemsFlow.value = items
        _editItems.postValue(items)
    }

    private fun setDisplayTag(tag: Track?) {
        _displayTagFlow.value = tag
        _displayTag.postValue(tag)
        loadStudioProvenance(tag)
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

    fun loadStudioProvenance(track: Track?) {
        if (track == null) {
            _studioProvenanceFlow.value = StudioProvenanceInfo()
            return
        }

        viewModelScope.launch(ioDispatcher) {
            val artist = track.artist?.trim().orEmpty()
            val album = track.album?.trim().orEmpty()
            val path = track.path?.let { java.io.File(it).parent }.orEmpty()
            val folderName = if (path.isNotEmpty()) java.io.File(path).name else ""

            var artistCount = 0
            var albumCount = 0
            var folderCount = 0

            if (artist.isNotEmpty() && !artist.startsWith("[")) {
                val criteria = apincer.music.core.model.SearchCriteria(apincer.music.core.model.SearchCriteria.TYPE.LIBRARY).apply {
                    filterType = apincer.music.core.Constants.FILTER_TYPE_ARTIST
                    filterText = artist
                }
                artistCount = repos.getSearchStats(criteria)?.totalCount ?: 0
            }

            if (album.isNotEmpty() && !album.startsWith("[")) {
                val criteria = apincer.music.core.model.SearchCriteria(apincer.music.core.model.SearchCriteria.TYPE.LIBRARY).apply {
                    filterType = apincer.music.core.Constants.FILTER_TYPE_ALBUM
                    filterText = album
                }
                albumCount = repos.getSearchStats(criteria)?.totalCount ?: 0
            }

            if (path.isNotEmpty()) {
                val criteria = apincer.music.core.model.SearchCriteria(apincer.music.core.model.SearchCriteria.TYPE.LIBRARY).apply {
                    filterType = apincer.music.core.Constants.FILTER_TYPE_PATH
                    filterText = path
                }
                folderCount = repos.getSearchStats(criteria)?.totalCount ?: 0
            }

            _studioProvenanceFlow.value = StudioProvenanceInfo(
                artist = artist,
                artistCount = artistCount,
                album = album,
                albumCount = albumCount,
                folderName = folderName,
                folderPath = path,
                folderCount = folderCount
            )
        }
    }

    fun openRelatedTracks(filterType: String, filterKeyword: String, title: String) {
        if (filterKeyword.isBlank()) return
        _relatedTracksSheetState.value = RelatedTracksSheetState(
            isVisible = true,
            title = title,
            subtitle = "Loading studio tracks...",
            filterType = filterType,
            filterKeyword = filterKeyword,
            tracks = emptyList(),
            isLoading = true
        )

        viewModelScope.launch(ioDispatcher) {
            val criteria = apincer.music.core.model.SearchCriteria(apincer.music.core.model.SearchCriteria.TYPE.LIBRARY).apply {
                this.filterType = filterType
                this.filterText = filterKeyword
            }
            val results = repos.findMusic(criteria) ?: emptyList()
            val stats = repos.getSearchStats(criteria)
            val count = (stats?.totalCount ?: results.size.toLong()).toInt()
            val durationMin = if (stats != null && stats.totalDuration > 0) {
                val totalSec = stats.totalDuration.toLong()
                val hrs = totalSec / 3600
                val mins = (totalSec % 3600) / 60
                if (hrs > 0) "$hrs hr $mins min" else "$mins min"
            } else ""
            val subtitle = if (durationMin.isNotEmpty()) "$count Studio Tracks • $durationMin" else "$count Studio Tracks"

            _relatedTracksSheetState.value = RelatedTracksSheetState(
                isVisible = true,
                title = title,
                subtitle = subtitle,
                filterType = filterType,
                filterKeyword = filterKeyword,
                tracks = results,
                isLoading = false
            )
        }
    }

    fun closeRelatedTracks() {
        _relatedTracksSheetState.value = _relatedTracksSheetState.value.copy(isVisible = false)
    }
}

data class StudioProvenanceInfo(
    val artist: String = "",
    val artistCount: Int = 0,
    val album: String = "",
    val albumCount: Int = 0,
    val folderName: String = "",
    val folderPath: String = "",
    val folderCount: Int = 0
)

data class RelatedTracksSheetState(
    val isVisible: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val filterType: String = "",
    val filterKeyword: String = "",
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false
)
