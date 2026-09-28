package apincer.android.mmate.ui.navigation

import apincer.android.mmate.R

/** The sole compatibility boundary between legacy Android menu IDs and typed library routes. */
object LibraryDestinationMenuMapping {
    @JvmStatic
    fun fromMenuItemId(itemId: Int): LibraryDestination? = when (itemId) {
        R.id.menu_library_all_songs -> LibraryDestination.ALL_SONGS
        R.id.menu_library_recently_added -> LibraryDestination.RECENTLY_ADDED
        R.id.menu_library_similar_songs -> LibraryDestination.SIMILAR_TRACKS
        R.id.menu_sound_grade -> LibraryDestination.AUDIO_QUALITY
        R.id.menu_collection -> LibraryDestination.PLAYLISTS
        R.id.menu_tag_genre -> LibraryDestination.GENRES
        R.id.menu_tag_artist -> LibraryDestination.ARTISTS
        else -> null
    }

    @JvmStatic
    fun toMenuItemId(destination: LibraryDestination): Int = when (destination) {
        LibraryDestination.ALL_SONGS -> R.id.menu_library_all_songs
        LibraryDestination.RECENTLY_ADDED -> R.id.menu_library_recently_added
        LibraryDestination.SIMILAR_TRACKS -> R.id.menu_library_similar_songs
        LibraryDestination.AUDIO_QUALITY -> R.id.menu_sound_grade
        LibraryDestination.PLAYLISTS -> R.id.menu_collection
        LibraryDestination.GENRES -> R.id.menu_tag_genre
        LibraryDestination.ARTISTS -> R.id.menu_tag_artist
    }
}
