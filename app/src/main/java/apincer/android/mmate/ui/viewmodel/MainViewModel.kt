package apincer.android.mmate.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import apincer.music.core.model.SearchCriteria
import apincer.music.core.model.SearchResultStats
import apincer.music.core.model.Track
import apincer.music.core.playback.spi.PlaybackService
import apincer.music.core.repository.FileRepository
import apincer.music.core.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import apincer.android.mmate.ui.compose.MainScaffoldState
import apincer.music.core.utils.StringUtils
import javax.inject.Inject

@HiltViewModel
class MainViewModel(
    private val fileRepos: FileRepository,
    private val repos: TagRepository,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    @Inject
    constructor(
        fileRepos: FileRepository,
        repos: TagRepository
    ) : this(fileRepos, repos, Dispatchers.IO)

    // --- LiveData for Java interop ---
    private val _musicItems = MutableLiveData<List<Track>>()
    @JvmField
    val musicItems: LiveData<List<Track>> = _musicItems

    private val _musicItemsLoading = MutableLiveData(false)
    @JvmField
    val musicItemsLoading: LiveData<Boolean> = _musicItemsLoading

    private val _searchStats = MutableLiveData<SearchResultStats>()
    @JvmField
    val searchStats: LiveData<SearchResultStats> = _searchStats

    // --- StateFlow for Modern Compose Screens ---
    private val _musicItemsFlow = MutableStateFlow<List<Track>>(emptyList())
    val musicItemsFlow: StateFlow<List<Track>> = _musicItemsFlow.asStateFlow()

    private val _musicItemsLoadingFlow = MutableStateFlow(false)
    val musicItemsLoadingFlow: StateFlow<Boolean> = _musicItemsLoadingFlow.asStateFlow()

    private val _searchStatsFlow = MutableStateFlow<SearchResultStats?>(null)
    val searchStatsFlow: StateFlow<SearchResultStats?> = _searchStatsFlow.asStateFlow()

    private var currentCriteria: SearchCriteria? = null
    private var currentPage = 0
    private var isLastPage = false

    companion object {
        private const val PAGE_SIZE = 500L
    }

    fun loadMusicItems() {
        loadMusicItems(currentCriteria)
    }

    fun loadMusicItems(criteria: SearchCriteria?) {
        currentCriteria = criteria
        currentPage = 0
        isLastPage = false
        _musicItemsLoading.value = true
        _musicItemsLoadingFlow.value = true

        viewModelScope.launch(ioDispatcher) {
            try {
                val stats = repos.getSearchStats(criteria)
                val items: List<Track> = repos.findMusic(criteria, 0L, PAGE_SIZE) ?: emptyList()

                withContext(Dispatchers.Main) {
                    _searchStats.value = stats
                    _searchStatsFlow.value = stats

                    _musicItems.value = items
                    _musicItemsFlow.value = items

                    if (items.size < PAGE_SIZE) {
                        isLastPage = true
                    } else {
                        currentPage = 1
                    }
                    _musicItemsLoading.value = false
                    _musicItemsLoadingFlow.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _musicItems.value = emptyList()
                    _musicItemsFlow.value = emptyList()
                    _musicItemsLoading.value = false
                    _musicItemsLoadingFlow.value = false
                }
            }
        }
    }

    fun loadMoreMusicItems() {
        if (_musicItemsLoading.value == true || isLastPage) return

        _musicItemsLoading.value = true
        _musicItemsLoadingFlow.value = true

        viewModelScope.launch(ioDispatcher) {
            try {
                val items: List<Track> = repos.findMusic(currentCriteria, currentPage * PAGE_SIZE, PAGE_SIZE) ?: emptyList()
                withContext(Dispatchers.Main) {
                    if (items.isNotEmpty()) {
                        val currentItems = ArrayList(_musicItems.value ?: emptyList())
                        currentItems.addAll(items)
                        _musicItems.value = currentItems
                        _musicItemsFlow.value = currentItems
                        currentPage++
                        if (items.size < PAGE_SIZE) {
                            isLastPage = true
                        }
                    } else {
                        isLastPage = true
                    }
                    _musicItemsLoading.value = false
                    _musicItemsLoadingFlow.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _musicItemsLoading.value = false
                    _musicItemsLoadingFlow.value = false
                }
            }
        }
    }

    fun reloadMusicItems() {
        val criteria = currentCriteria ?: return
        _musicItemsLoading.value = true
        _musicItemsLoadingFlow.value = true
        val limit = maxOf(1L, currentPage.toLong()) * PAGE_SIZE

        viewModelScope.launch(ioDispatcher) {
            try {
                val stats = repos.getSearchStats(criteria)
                val items: List<Track> = repos.findMusic(criteria, 0L, limit) ?: emptyList()

                withContext(Dispatchers.Main) {
                    _searchStats.value = stats
                    _searchStatsFlow.value = stats

                    _musicItems.value = items
                    _musicItemsFlow.value = items

                    if (items.isEmpty()) {
                        currentPage = 0
                        isLastPage = true
                    } else {
                        currentPage = Math.ceil(items.size.toDouble() / PAGE_SIZE).toInt()
                        if (items.size < limit || (items.size.toLong() % PAGE_SIZE != 0L)) {
                            isLastPage = true
                        }
                    }
                    _musicItemsLoading.value = false
                    _musicItemsLoadingFlow.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _musicItemsLoading.value = false
                    _musicItemsLoadingFlow.value = false
                }
            }
        }
    }

    fun loadUntilFound(target: Track?, onLoaded: Runnable?) {
        if (target == null) {
            onLoaded?.run()
            return
        }

        val currentList = _musicItems.value
        if (currentList != null && currentList.contains(target)) {
            onLoaded?.run()
            return
        }

        if (isLastPage) {
            onLoaded?.run()
            return
        }

        _musicItemsLoading.value = true
        _musicItemsLoadingFlow.value = true

        viewModelScope.launch(ioDispatcher) {
            try {
                var found = false
                val current = ArrayList(_musicItems.value ?: emptyList())

                while (!found && !isLastPage) {
                    val items: List<Track> = repos.findMusic(currentCriteria, currentPage * PAGE_SIZE, PAGE_SIZE) ?: emptyList()
                    if (items.isEmpty()) {
                        isLastPage = true
                        break
                    }
                    current.addAll(items)
                    currentPage++
                    if (items.size < PAGE_SIZE) {
                        isLastPage = true
                    }
                    if (items.contains(target)) {
                        found = true
                    }
                }

                withContext(Dispatchers.Main) {
                    _musicItems.value = current
                    _musicItemsFlow.value = current
                    _musicItemsLoading.value = false
                    _musicItemsLoadingFlow.value = false
                    onLoaded?.run()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _musicItemsLoading.value = false
                    _musicItemsLoadingFlow.value = false
                    onLoaded?.run()
                }
            }
        }
    }

    fun search(criteria: SearchCriteria, query: String?) {
        if (query.isNullOrEmpty()) {
            criteria.resetSearch()
        } else {
            criteria.searchFor(query)
        }
        loadMusicItems(criteria)
    }

    fun deleteMediaTag(tag: Track) {
        viewModelScope.launch(ioDispatcher) {
            repos.deleteMediaTag(tag)
        }
    }

    fun playCollection(collectionTag: Track?, playbackService: PlaybackService?, enqueueOnly: Boolean) {
        if (collectionTag == null || playbackService == null) return

        viewModelScope.launch(ioDispatcher) {
            try {
                var items: List<Track> = emptyList()

                // If collectionTag has directory path (e.g. music folder)
                val path = collectionTag.uniqueKey?.takeIf { it.isNotBlank() && (it.startsWith("/") || it.contains("/")) }
                    ?: collectionTag.path?.takeIf { it.isNotBlank() && (it.startsWith("/") || it.contains("/")) }

                if (path != null && (collectionTag.containerType == SearchCriteria.TYPE.LIBRARY || collectionTag.containerType == null)) {
                    items = repos.findInPath(path) ?: emptyList()
                }

                if (items.isEmpty()) {
                    val criteria = SearchCriteria(collectionTag.containerType ?: SearchCriteria.TYPE.LIBRARY)
                    criteria.keyword = collectionTag.title
                    items = repos.findMusic(criteria, 0L, Long.MAX_VALUE) ?: emptyList()
                }

                // Filter out any container items to ensure pure playable songs
                val songsToPlay = items.filter { !it.isContainer }
                if (songsToPlay.isNotEmpty()) {
                    val queue = playbackService.queueManager
                    if (!enqueueOnly) {
                        queue.setPlayingQueue(songsToPlay)
                    } else {
                        queue.enqueuePlayingQueue(songsToPlay)
                    }

                    if (!enqueueOnly) {
                        withContext(Dispatchers.Main) {
                            playbackService.playSong(songsToPlay[0])
                        }
                    }

                    // Sync queue state into Compose UI
                    val allQueueSongs = queue.songs ?: emptyList()
                    val nowPlaying = playbackService.nowPlayingSong ?: if (!enqueueOnly) songsToPlay[0] else null
                    val playingKey = nowPlaying?.uniqueKey
                    val totalSec = allQueueSongs.sumOf { (if (it.audioDuration > 0) it.audioDuration else 0.0).toLong() }
                    val totalDurationStr = if (totalSec > 0) StringUtils.formatDuration(totalSec.toDouble(), true) else ""
                    withContext(Dispatchers.Main) {
                        MainScaffoldState.updateQueue(ArrayList(allQueueSongs), playingKey, totalDurationStr)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playTrackList(items: List<Track>?, startTrack: Track?, playbackService: PlaybackService?) {
        if (items.isNullOrEmpty() || playbackService == null) return

        viewModelScope.launch(ioDispatcher) {
            try {
                // Filter out container items
                val songsToPlay = items.filter { !it.isContainer }
                if (songsToPlay.isEmpty()) return@launch

                val queue = playbackService.queueManager
                queue.setPlayingQueue(songsToPlay)

                val targetTrack = startTrack ?: songsToPlay[0]
                withContext(Dispatchers.Main) {
                    playbackService.playSong(targetTrack)
                }

                // Sync queue state into Compose UI
                val allQueueSongs = queue.songs ?: emptyList()
                val playingKey = targetTrack.uniqueKey
                val totalSec = allQueueSongs.sumOf { (if (it.audioDuration > 0) it.audioDuration else 0.0).toLong() }
                val totalDurationStr = if (totalSec > 0) StringUtils.formatDuration(totalSec.toDouble(), true) else ""
                withContext(Dispatchers.Main) {
                    MainScaffoldState.updateQueue(ArrayList(allQueueSongs), playingKey, totalDurationStr)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getTagRepository(): TagRepository = repos

    fun getFileRepository(): FileRepository = fileRepos
}
