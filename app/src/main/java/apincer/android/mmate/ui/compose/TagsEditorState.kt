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
    var showFilenameParserSheet by mutableStateOf(false)

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

    fun snapshot(): Map<String, String> = mapOf(
        "title" to title, "titleModified" to titleModified.toString(),
        "artist" to artist, "artistModified" to artistModified.toString(),
        "album" to album, "albumModified" to albumModified.toString(),
        "albumArtist" to albumArtist, "albumArtistModified" to albumArtistModified.toString(),
        "track" to track, "trackModified" to trackModified.toString(),
        "year" to year, "yearModified" to yearModified.toString(),
        "genre" to genre, "genreModified" to genreModified.toString(),
        "style" to style, "styleModified" to styleModified.toString(),
        "mood" to mood, "moodModified" to moodModified.toString(),
        "origin" to origin, "originModified" to originModified.toString(),
        "publisher" to publisher, "publisherModified" to publisherModified.toString()
    )

    fun restore(values: Map<String, String>) {
        title = values["title"].orEmpty()
        titleModified = values["titleModified"].toBoolean()
        artist = values["artist"].orEmpty()
        artistModified = values["artistModified"].toBoolean()
        album = values["album"].orEmpty()
        albumModified = values["albumModified"].toBoolean()
        albumArtist = values["albumArtist"].orEmpty()
        albumArtistModified = values["albumArtistModified"].toBoolean()
        track = values["track"].orEmpty()
        trackModified = values["trackModified"].toBoolean()
        year = values["year"].orEmpty()
        yearModified = values["yearModified"].toBoolean()
        genre = values["genre"].orEmpty()
        genreModified = values["genreModified"].toBoolean()
        style = values["style"].orEmpty()
        styleModified = values["styleModified"].toBoolean()
        mood = values["mood"].orEmpty()
        moodModified = values["moodModified"].toBoolean()
        origin = values["origin"].orEmpty()
        originModified = values["originModified"].toBoolean()
        publisher = values["publisher"].orEmpty()
        publisherModified = values["publisherModified"].toBoolean()
    }

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

    companion object {
        fun valueForSave(value: String?, originalValue: String?, modified: Boolean, multiSelection: Boolean): String {
            val candidate = value.orEmpty().trim()
            return if (!modified || candidate == MULTI_VALUES_MARKER || (multiSelection && candidate.isEmpty())) {
                originalValue.orEmpty()
            } else candidate
        }

        const val MULTI_VALUES_MARKER = "< Multiple Values >"

        fun isMultiValues(value: String?): Boolean {
            return value == " - " || value == MULTI_VALUES_MARKER
        }
    }
}
