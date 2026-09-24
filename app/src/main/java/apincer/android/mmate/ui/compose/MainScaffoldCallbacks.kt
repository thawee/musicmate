package apincer.android.mmate.ui.compose

import apincer.music.core.model.Track
import apincer.music.core.playback.spi.PlaybackTarget

interface MainScaffoldCallbacks {
    fun onNavigationItemClick(itemId: Int)
    fun onSearchQueryChange(query: String)
    fun onSearchBackClick()
    fun onListRefresh()
    fun onLoadMoreMusic()
    fun onTrackClick(track: Track, position: Int)
    fun onTrackLongClick(track: Track, position: Int)
    fun onTrackMenuClick(track: Track, position: Int)
    fun onTrackQuickPlayClick(track: Track)
    fun onFolderPlayClick(track: Track)
    fun onFolderEnqueueClick(track: Track)
    fun onDockPlayPauseClick()
    fun onDockNextClick()
    fun onDockLongClick() { onAudioHubTrackClick() }
    fun onSelectPlaybackTargetClick()
    fun onPlayerTargetSelected(target: PlaybackTarget)
    fun onRescanTargets()
    fun onOpenSystemAudioOutput()
    fun onAudioHubPlayPause()
    fun onAudioHubNext()
    fun onAudioHubPrevious()
    fun onAudioHubShuffleToggle()
    fun onAudioHubRepeatToggle()
    fun onAudioHubSeek(position: Float)
    fun onAudioHubVolumeDown()
    fun onAudioHubVolumeUp()
    fun onAudioHubVolumeChanged(volume: Float)
    fun onAudioHubSleepTimerSelected(minutes: Long, endOfTrack: Boolean)
    fun onAudioHubTrackClick()
    fun onAudioHubQueueTrackClick(track: Track)
    fun onAudioHubQueueTrackRemove(track: Track, index: Int)
    fun onAudioHubQueueTrackMoved(fromIndex: Int, toIndex: Int)
    fun onAudioHubQueueClear()
    fun onAudioHubQueueJumpToPlaying()
    fun onEngineChanged(engine: String)
    fun onStartServerClicked()
    fun onStopServerClicked()
    fun onCopyUrlClicked()
    fun onOpenUrlClicked()
    fun onQrCodeClicked()
    fun onSmartPlaylistCreated(entry: apincer.music.core.model.PlaylistEntry) {}
}
