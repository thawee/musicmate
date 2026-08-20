package apincer.android.mmate.ui.compose

import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import apincer.music.core.model.Track
import apincer.android.mmate.ui.MainActivity
import androidx.compose.material3.MaterialTheme

object ListInterop {

    private var _tracks = mutableStateListOf<Track>()
    private var _selectedTracks = mutableStateOf<Set<Track>>(emptySet())
    private var _nowPlayingTrack = mutableStateOf<Track?>(null)
    private var _isPlaying = mutableStateOf(false)

    @JvmStatic
    fun setMusicListContent(
        view: ComposeView,
        activity: MainActivity
    ) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            MaterialTheme {
                MusicListScreen(
                    tracks = _tracks,
                    selectedTracks = _selectedTracks.value,
                    nowPlayingTrack = _nowPlayingTrack.value,
                    isPlaying = _isPlaying.value,
                    onTrackClick = { track, index ->
                        activity.onTrackClicked(track, index)
                    },
                    onTrackLongClick = { track, index ->
                        activity.onTrackLongClicked(track, index)
                    },
                    onTrackMenuClick = { track, index ->
                        activity.onTrackMenuClicked(track, index)
                    }
                )
            }
        }
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
}
