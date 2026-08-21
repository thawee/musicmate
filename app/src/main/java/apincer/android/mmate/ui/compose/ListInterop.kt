package apincer.android.mmate.ui.compose

import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import apincer.music.core.model.Track
import apincer.android.mmate.ui.MainActivity

object ListInterop {

    private var _tracks = mutableStateListOf<Track>()
    private var _selectedTracks = mutableStateOf<Set<Track>>(emptySet())
    private var _nowPlayingTrack = mutableStateOf<Track?>(null)
    private var _isPlaying = mutableStateOf(false)
    private var _isRefreshing = mutableStateOf(false)
    private var _scrollToIndex = mutableIntStateOf(-1)

    @JvmStatic
    fun setMusicListContent(
        view: ComposeView,
        activity: MainActivity
    ) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            MusicMateTheme {
                MusicListScreen(
                    tracks = _tracks,
                    selectedTracks = _selectedTracks.value,
                    nowPlayingTrack = _nowPlayingTrack.value,
                    isPlaying = _isPlaying.value,
                    scrollToIndex = _scrollToIndex.intValue,
                    onScrollComplete = { _scrollToIndex.intValue = -1 },
                    onTrackClick = { track, index ->
                        activity.onTrackClicked(track, index)
                    },
                    onTrackLongClick = { track, index ->
                        activity.onTrackLongClicked(track, index)
                    },
                    isRefreshing = _isRefreshing.value,
                    onRefresh = { activity.onListRefresh() },
                    onTrackMenuClick = { track, index ->
                        activity.onTrackMenuClicked(track, index)
                    },
                    onFolderPlayClick = { track ->
                        activity.onFolderPlayClicked(track)
                    },
                    onFolderEnqueueClick = { track ->
                        activity.onFolderEnqueueClicked(track)
                    }
                )
            }
        }
    }

    @JvmStatic
    fun updateRefreshing(isRefreshing: Boolean) {
        _isRefreshing.value = isRefreshing
    }
    @JvmStatic
    fun getTracks(): List<Track> = _tracks

    @JvmStatic
    fun updateTracks(tracks: List<Track>) {
        _tracks.clear()
        _tracks.addAll(tracks)
    }

    @JvmStatic
    fun updateSelectedTracks(selections: Set<Track>) {
        _selectedTracks.value = selections
    }

    @JvmStatic
    fun updateNowPlaying(track: Track?, isPlaying: Boolean) {
        _nowPlayingTrack.value = track
        _isPlaying.value = isPlaying
    }

    @JvmStatic
    fun scrollToPosition(index: Int) {
        _scrollToIndex.intValue = index
    }
}
