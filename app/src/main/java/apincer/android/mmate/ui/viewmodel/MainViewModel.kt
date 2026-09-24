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
import kotlinx.coroutines.ensureActive
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

    @JvmField
    val hasMoreItems = MutableLiveData(false)
    @JvmField
    val loadError = MutableLiveData<String?>(null)
    private var currentCriteria: SearchCriteria? = null
    private var currentPage = 0
    private var isLastPage = false
    private var requestGeneration = 0L
    private var loadJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val PAGE_SIZE = 500L
    }

    private fun snapshot(criteria: SearchCriteria?): SearchCriteria? = criteria?.let {
        SearchCriteria(it.type, it.keyword).apply {
            filterType = it.filterType
            filterText = it.filterText
            isSearchMode = it.isSearchMode
            searchText = it.searchText
        }
    }

    private fun setLoading(loading: Boolean) {
        _musicItemsLoading.value = loading
        _musicItemsLoadingFlow.value = loading
    }

    fun loadMusicItems() = loadMusicItems(currentCriteria)

    fun loadMusicItems(criteria: SearchCriteria?) {
        currentCriteria = snapshot(criteria)
        currentPage = 0
        isLastPage = false
        hasMoreItems.value = false
        loadPage(replace = true, limit = PAGE_SIZE)
    }

    fun loadMoreMusicItems() {
        if (_musicItemsLoading.value == true || isLastPage) return
        loadPage(replace = currentPage == 0, limit = PAGE_SIZE)
    }

    fun reloadMusicItems() {
        if (currentCriteria == null) return
        loadPage(replace = true, limit = maxOf(1L, currentPage.toLong()) * PAGE_SIZE)
    }

    private fun loadPage(replace: Boolean, limit: Long) {
        val generation = ++requestGeneration
        loadJob?.cancel()
        val criteria = snapshot(currentCriteria)
        val offset = if (replace) 0L else currentPage * PAGE_SIZE
        val previousItems = if (replace) emptyList() else _musicItemsFlow.value
        setLoading(true)
        loadError.value = null
        loadJob = viewModelScope.launch(ioDispatcher) {
            try {
                val stats = if (replace) repos.getSearchStats(criteria) else null
                val items = repos.findMusic(criteria, offset, limit) ?: emptyList()
                withContext(Dispatchers.Main) {
                    if (generation != requestGeneration) return@withContext
                    if (replace && stats != null) {
                        _searchStats.value = stats
                        _searchStatsFlow.value = stats
                    }
                    val result = if (replace) items else previousItems + items
                    _musicItems.value = result
                    _musicItemsFlow.value = result
                    currentPage = ((offset + items.size + PAGE_SIZE - 1) / PAGE_SIZE).toInt()
                    isLastPage = items.size < limit
                    hasMoreItems.value = !isLastPage
                    setLoading(false)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (generation != requestGeneration) return@withContext
                    loadError.value = "Couldn't load music. Try again."
                    setLoading(false)
                }
            }
        }
    }

    fun loadUntilFound(target: Track?, onLoaded: Runnable?) {
        if (target == null || _musicItemsFlow.value.contains(target) || isLastPage) {
            onLoaded?.run()
            return
        }
        val generation = ++requestGeneration
        loadJob?.cancel()
        val criteria = snapshot(currentCriteria)
        val current = ArrayList(_musicItemsFlow.value)
        var page = currentPage
        setLoading(true)
        loadError.value = null
        loadJob = viewModelScope.launch(ioDispatcher) {
            try {
                var lastPage = false
                while (!current.contains(target) && !lastPage) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val items = repos.findMusic(criteria, page * PAGE_SIZE, PAGE_SIZE) ?: emptyList()
                    current.addAll(items)
                    page++
                    lastPage = items.size < PAGE_SIZE
                }
                withContext(Dispatchers.Main) {
                    if (generation != requestGeneration) return@withContext
                    currentPage = page
                    isLastPage = lastPage
                    hasMoreItems.value = !lastPage
                    _musicItems.value = current
                    _musicItemsFlow.value = current
                    setLoading(false)
                    onLoaded?.run()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (generation != requestGeneration) return@withContext
                    loadError.value = "Couldn't load music. Try again."
                    setLoading(false)
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
