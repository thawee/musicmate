package apincer.android.mmate.ui.compose

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import apincer.music.core.model.Track

object ListInterop {
    private var _tracks = mutableStateListOf<Track>()
    private var _selectedTracks = mutableStateOf<Set<Track>>(emptySet())
    private var _nowPlayingTrack = mutableStateOf<Track?>(null)
    private var _isPlaying = mutableStateOf(false)
    private var _isRefreshing = mutableStateOf(false)
    private var _scrollToIndex = mutableIntStateOf(-1)

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

    @JvmStatic
    fun getSelectedTracks(): Set<Track> = _selectedTracks.value

    @JvmStatic
    fun isRefreshing(): Boolean = _isRefreshing.value

    @JvmStatic
    fun getScrollToIndex(): Int = _scrollToIndex.intValue

    @JvmStatic
    fun resetScrollToIndex() {
        _scrollToIndex.intValue = -1
    }
}
