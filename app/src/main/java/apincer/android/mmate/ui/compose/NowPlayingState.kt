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
    val specsReplayGain = mutableStateOf("")
    val specsFileSize = mutableStateOf("")
    
    // Controls
    val isShuffle = mutableStateOf(false)
    val repeatMode = mutableStateOf(0) // 0: None, 1: All, 2: One
    
    // Volume & Timer
    val volume = mutableStateOf(0f)
    // False while a network player's level is unknown or unreliable
    val volumeEnabled = mutableStateOf(true)
    val volumeOnPlayer = mutableStateOf(false)
    // Highest level the slider may set, 0..1 (1 = no limit); network players only
    val volumeLimit = mutableStateOf(1f)
    val sleepTimerText = mutableStateOf("")
    val isSleepTimerActive = mutableStateOf(false)
    val sleepTimerMinutes = mutableStateOf(0L) // the chosen countdown; 0 for none or end of track
    
    // Signal Path
    val signalPathSteps = mutableStateListOf<String>()
    val targetTitle = mutableStateOf("")
    val targetBadge = mutableStateOf("")
    val targetDetails = mutableStateOf("")
}
