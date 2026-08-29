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

    fun updateQueue(newTracks: List<Track>, playingKey: String?, durationText: String = "") {
        tracks.clear()
        tracks.addAll(newTracks)
        currentPlayingKey = playingKey
        totalDurationText = durationText
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
