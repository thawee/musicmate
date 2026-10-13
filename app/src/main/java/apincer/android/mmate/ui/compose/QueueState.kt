package apincer.android.mmate.ui.compose

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import apincer.music.core.model.Track

class QueueState(initialTracks: List<Track>, initialPlayingKey: String?) {
    val tracks = mutableStateListOf<Track>().apply { addAll(initialTracks) }
    var currentPlayingKey by mutableStateOf(initialPlayingKey)
    var totalDurationText by mutableStateOf("")
    var manager by mutableStateOf<apincer.music.core.repository.QueueManager?>(null)

    fun updateQueue(newTracks: List<Track>, playingKey: String?, durationText: String = "") {
        tracks.clear()
        tracks.addAll(newTracks)
        currentPlayingKey = playingKey
        totalDurationText = durationText
    }

    /**
     * The track that plays next, in play order. [isShuffle] is passed in from Compose state, so a
     * caller recomposes when shuffle changes the order without changing [tracks]. With nothing
     * playing it is what Play starts (MainActivity.onDockPlayPauseClicked): the current track, else
     * the first; null when shuffle picks a random one.
     */
    fun upNext(isShuffle: Boolean): Track? {
        val m = manager
        val isPlaying = tracks.any { it.uniqueKey == currentPlayingKey }
        if (m != null) {
            if (isPlaying) return m.peekNextTrack()
            if (isShuffle) return null
            return m.currentTrack ?: tracks.firstOrNull()
        }
        val playing = tracks.indexOfFirst { it.uniqueKey == currentPlayingKey }
        return tracks.getOrNull(playing + 1)
    }

    /** Label for [upNext], or null when the queue is empty. */
    fun upNextLabel(isShuffle: Boolean): String? {
        if (tracks.isEmpty()) return null
        upNext(isShuffle)?.let { return it.title.orEmpty() }
        val isPlaying = tracks.any { it.uniqueKey == currentPlayingKey }
        return if (!isPlaying && isShuffle) "Random track" else "End of queue"
    }

    fun updateTracks(newTracks: List<Track>) {
        tracks.clear()
        tracks.addAll(newTracks)
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        if (fromIndex in tracks.indices && toIndex in tracks.indices && fromIndex != toIndex) {
            val item = tracks.removeAt(fromIndex)
            tracks.add(toIndex, item)
        }
    }
}
