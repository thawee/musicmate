package apincer.android.mmate.ui.compose

import apincer.music.core.model.Track

/**
 * Interop bridge between legacy Java controllers and Compose UI state.
 * All state is unified in [MainScaffoldState].
 */
object ListInterop {

    @JvmStatic
    fun updateRefreshing(isRefreshing: Boolean) {
        MainScaffoldState.setRefreshing(isRefreshing)
    }

    @JvmStatic
    fun getTracks(): List<Track> = MainScaffoldState.get().tracks

    @JvmStatic
    fun updateTracks(tracks: List<Track>) {
        MainScaffoldState.setTracks(tracks)
    }

    @JvmStatic
    fun updateSelectedTracks(selections: Set<Track>) {
        MainScaffoldState.setSelectedTracks(selections.toList())
    }

    @JvmStatic
    fun updateNowPlaying(track: Track?, isPlaying: Boolean) {
        val state = MainScaffoldState.get()
        state.nowPlayingTrack.value = track
        state.isPlaying.value = isPlaying
    }

    @JvmStatic
    fun scrollToPosition(index: Int) {
        MainScaffoldState.scrollToPosition(index)
    }

    @JvmStatic
    fun getSelectedTracks(): Set<Track> = MainScaffoldState.get().selectedTracks.toSet()

    @JvmStatic
    fun isRefreshing(): Boolean = MainScaffoldState.get().isRefreshing.value

    @JvmStatic
    fun getScrollToIndex(): Int = MainScaffoldState.get().scrollToIndex.intValue

    @JvmStatic
    fun resetScrollToIndex() {
        MainScaffoldState.resetScrollToIndex()
    }
}
