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

    fun updateTracks(newTracks: List<Track>) {
        tracks.clear()
        tracks.addAll(newTracks)
    }
}
