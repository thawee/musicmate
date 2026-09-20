package apincer.android.mmate.ui.compose

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import apincer.music.core.model.Track

class MainScaffoldState {
    var searchQuery = mutableStateOf("")
    var isBackVisible = mutableStateOf(false)
    var headerStatsText = mutableStateOf("")
    var isScanning = mutableStateOf(false)
    var scanProgressText = mutableStateOf("")

    var nowPlayingTrack = mutableStateOf<Track?>(null)
    var isPlaying = mutableStateOf(false)
    var outputTargetSubtitle = mutableStateOf("")
    var playbackProgress = mutableFloatStateOf(0f)
    var isFloatingDockVisible = mutableStateOf(true)

    var showAudioHubSheet = mutableStateOf(false)
    var showFullscreenConsole = mutableStateOf(false)
    var audioHubInitialTab = mutableIntStateOf(0)

    // Player picker modal state
    var showPlayerPickerDialog = mutableStateOf(false)
    val playerTargets = mutableStateListOf<PlayerTargetItem>()
    var isPlayerScanning = mutableStateOf(false)

    // Smart playlist creator modal state
    var showCreateSmartPlaylistDialog = mutableStateOf(false)

    // Track list, selection & scrolling state
    val tracks = mutableStateListOf<Track>()
    val selectedTracks = mutableStateListOf<Track>()
    var isRefreshing = mutableStateOf(false)
    var scrollToIndex = mutableIntStateOf(-1)

    // Sub-states for AudioHubSheet
    val nowPlayingState = NowPlayingState()
    val queueState = QueueState(mutableListOf(), null)
    val mediaServerState = MediaServerState()

    companion object {
        private val instance = MainScaffoldState()

        @JvmStatic
        fun get(): MainScaffoldState = instance

        @JvmStatic
        fun setTracks(tracks: List<Track>) {
            instance.tracks.clear()
            instance.tracks.addAll(tracks)
        }

        @JvmStatic
        fun setSelectedTracks(tracks: List<Track>) {
            instance.selectedTracks.clear()
            instance.selectedTracks.addAll(tracks)
        }

        @JvmStatic
        fun clearSelectedTracks() {
            instance.selectedTracks.clear()
        }

        @JvmStatic
        fun setRefreshing(refreshing: Boolean) {
            instance.isRefreshing.value = refreshing
        }

        @JvmStatic
        fun scrollToPosition(index: Int) {
            instance.scrollToIndex.intValue = index
        }

        @JvmStatic
        fun resetScrollToIndex() {
            instance.scrollToIndex.intValue = -1
        }

        @JvmStatic
        fun updateSearchQuery(query: String) {
            instance.searchQuery.value = query
        }

        @JvmStatic
        fun updateBackVisible(visible: Boolean) {
            instance.isBackVisible.value = visible
        }

        @JvmStatic
        fun updateHeaderStats(statsText: String) {
            instance.headerStatsText.value = statsText
        }

        @JvmStatic
        fun updateScanning(scanning: Boolean, progressText: String) {
            instance.isScanning.value = scanning
            instance.scanProgressText.value = progressText
        }

        @JvmStatic
        fun updateNowPlaying(track: Track?, playing: Boolean, targetSubtitle: String, progress: Float) {
            instance.nowPlayingTrack.value = track
            instance.isPlaying.value = playing
            instance.outputTargetSubtitle.value = targetSubtitle
            instance.playbackProgress.floatValue = progress
        }

        @JvmStatic
        fun setFloatingDockVisible(visible: Boolean) {
            instance.isFloatingDockVisible.value = visible
        }

        @JvmStatic
        fun openAudioHub(initialTab: Int) {
            instance.audioHubInitialTab.intValue = initialTab.coerceIn(0, 2)
            instance.showAudioHubSheet.value = true
        }

        @JvmStatic
        fun closeAudioHub() {
            instance.showAudioHubSheet.value = false
        }

        @JvmStatic
        fun isAudioHubOpen(): Boolean {
            return instance.showAudioHubSheet.value
        }

        @JvmStatic
        fun setPlayerTargets(targets: List<PlayerTargetItem>) {
            instance.playerTargets.clear()
            instance.playerTargets.addAll(targets)
        }

        @JvmStatic
        fun showPlayerPicker(show: Boolean) {
            instance.showPlayerPickerDialog.value = show
        }

        @JvmStatic
        fun setPlayerScanning(scanning: Boolean) {
            instance.isPlayerScanning.value = scanning
        }

        @JvmStatic
        fun openCreateSmartPlaylistDialog() {
            instance.showCreateSmartPlaylistDialog.value = true
        }

        @JvmStatic
        @JvmOverloads
        fun updateQueue(tracks: List<Track>, currentPlayingKey: String?, totalDurationText: String = "") {
            instance.queueState.updateQueue(tracks, currentPlayingKey, totalDurationText)
        }
    }
}
