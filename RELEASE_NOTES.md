# MusicMate 3.24.0

The APK on this page now installs. Releases 3.21.0 to 3.23.2 published an unsigned APK, which Android refuses with "App not installed". This release is signed with the MusicMate release key. If MusicMate is already on your phone from another source, uninstall it once first; later GitHub releases will then update in place.

DLNA renderers on the phone's hotspot are now found without restarting MusicMate. Turning the hotspot on or off no longer makes every renderer disappear. The player picker updates while it is open, so renderers that answer late appear in place, and the Rescan action is gone.

Playback and streaming are more robust. An unreadable track skips to the next one instead of stopping the queue, long DLNA sessions keep the server awake past 4 hours, and an error in a background task is logged instead of closing the app. Database reads that froze the screen now run in the background.

Genre, mood and style presets use single names, so "R&B / Soul" is no longer saved as two genres, and genre playlists match each of a track's genres. Older values are mapped to the new names. The DR12+ playlist no longer includes silent tracks.

The tag editor opens Genre, Style, Mood and Origin with the full list, first run and the library screens are clearer, and the search box no longer opens the keyboard at launch.

Version code: 145. See [CHANGELOG.md](https://github.com/thawee/musicmate/blob/v3.24.0/CHANGELOG.md) for details.
