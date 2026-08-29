package apincer.android.mmate.ui.compose

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
    val specsVerdict = mutableStateOf("")
    val specsFormat = mutableStateOf("")
    val specsBitrate = mutableStateOf("")
    val specsDr = mutableStateOf("")
    val specsFileSize = mutableStateOf("")
    
    // Controls
    val isShuffle = mutableStateOf(false)
    val repeatMode = mutableStateOf(0) // 0: None, 1: All, 2: One
    
    // Volume & Timer
    val volume = mutableStateOf(0f)
    val sleepTimerText = mutableStateOf("")
    val isSleepTimerActive = mutableStateOf(false)
    
    // Signal Path
    val signalPathSteps = mutableStateListOf<String>()
    val targetTitle = mutableStateOf("")
    val targetBadge = mutableStateOf("")
    val targetDetails = mutableStateOf("")
}
