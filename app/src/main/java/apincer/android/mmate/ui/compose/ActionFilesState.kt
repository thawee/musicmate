package apincer.android.mmate.ui.compose

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import apincer.music.core.model.Track

class ActionFilesState(initialTracks: List<Track>) {
    val isBusy = mutableStateOf(false)
    val progress = mutableIntStateOf(0)
    val statusMap = mutableStateMapOf<Track, String>()
    val tracks = initialTracks.toList()

    fun updateStatus(track: Track, status: String) {
        statusMap[track] = status
    }

    fun setProgress(p: Int) {
        progress.intValue = p
    }

    fun setBusy(busy: Boolean) {
        isBusy.value = busy
    }
}
