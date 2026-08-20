package apincer.android.mmate.ui.compose

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import apincer.music.core.model.Track
import apincer.music.core.playback.PlaybackState

class NowPlayingState {
    val track = mutableStateOf<Track?>(null)
    val albumArt = mutableStateOf<Bitmap?>(null)
    
    // Playback state
    val playbackState = mutableStateOf<PlaybackState>(PlaybackState().apply { currentState = PlaybackState.State.STOPPED })
    val progressMs = mutableStateOf(0L)
    val durationMs = mutableStateOf(0L)
    
    // Tech Specs
    val specsFormat = mutableStateOf("")
    val specsBitrate = mutableStateOf("")
    val specsDr = mutableStateOf("")
    val specsFileSize = mutableStateOf("")
    
    // Controls
    val isShuffle = mutableStateOf(false)
    val repeatMode = mutableStateOf(0) // 0: None, 1: All, 2: One
    
    // Volume
    val volume = mutableStateOf(0f)
    
    // Signal Path
    val signalPathSteps = mutableStateListOf<String>()
}
