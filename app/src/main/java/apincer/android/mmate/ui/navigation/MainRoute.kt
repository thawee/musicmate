package apincer.android.mmate.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
enum class LibraryDestination {
    ALL_SONGS,
    RECENTLY_ADDED,
    SIMILAR_TRACKS,
    AUDIO_QUALITY,
    PLAYLISTS,
    GENRES,
    ARTISTS
}

@Serializable
enum class MusicCenterTab {
    NOW_PLAYING,
    QUEUE,
    MEDIA_SERVER;

    companion object {
        fun fromPage(page: Int): MusicCenterTab = entries[page.coerceIn(0, entries.lastIndex)]
    }
}

@Serializable
sealed interface MainRoute : NavKey {

    @Serializable
    data class Library(
        val destination: LibraryDestination = LibraryDestination.ALL_SONGS
    ) : MainRoute

    @Serializable
    data class MusicCenter(
        val tab: MusicCenterTab = MusicCenterTab.NOW_PLAYING
    ) : MainRoute

    @Serializable
    data object StudioConsole : MainRoute

    @Serializable
    data object Settings : MainRoute

    @Serializable
    data object About : MainRoute

    @Serializable
    data class TagEditor(val trackIds: List<Long>) : MainRoute
}
