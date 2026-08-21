package apincer.android.mmate.ui.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class TagsEditorState {
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var album by mutableStateOf("")
    var albumArtist by mutableStateOf("")
    var track by mutableStateOf("")
    var year by mutableStateOf("")
    var genre by mutableStateOf("")
    var style by mutableStateOf("")
    var mood by mutableStateOf("")
    var origin by mutableStateOf("")
    var publisher by mutableStateOf("")

    var titleModified by mutableStateOf(false)
    var artistModified by mutableStateOf(false)
    var albumModified by mutableStateOf(false)
    var albumArtistModified by mutableStateOf(false)
    var trackModified by mutableStateOf(false)
    var yearModified by mutableStateOf(false)
    var genreModified by mutableStateOf(false)
    var styleModified by mutableStateOf(false)
    var moodModified by mutableStateOf(false)
    var originModified by mutableStateOf(false)
    var publisherModified by mutableStateOf(false)

    fun resetModified() {
        titleModified = false
        artistModified = false
        albumModified = false
        albumArtistModified = false
        trackModified = false
        yearModified = false
        genreModified = false
        styleModified = false
        moodModified = false
        originModified = false
        publisherModified = false
    }

    fun isAnyModified(): Boolean {
        return titleModified || artistModified || albumModified || albumArtistModified ||
               trackModified || yearModified || genreModified || styleModified ||
               moodModified || originModified || publisherModified
    }
}
