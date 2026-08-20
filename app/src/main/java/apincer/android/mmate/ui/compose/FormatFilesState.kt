package apincer.android.mmate.ui.compose

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import apincer.music.core.model.Track

class FormatFilesState(initialTracks: List<Track>) {
    val tracks = mutableStateListOf<Track>().apply { addAll(initialTracks) }
    val statusMap = mutableStateMapOf<Track, String>()
    
    val isBusy = mutableStateOf(false)
    val progress = mutableStateOf(0)
    
    val selectedFormat = mutableStateOf("FLAC (Balanced)")
    val selectedSampleRate = mutableStateOf("Original (No Resampling)")
}
