# MusicMate 3.26.1

DLNA streaming no longer stops after you skip songs. On Android 17, a player such as the HiBy R3 could stop playing after about 16 skips and stay on "Transitioning" until the streaming engine was restarted. Closing a song file sometimes fails on Android 17 when the player drops the connection, and each failure permanently used up one of the media server's 16 stream slots. The slot is now always returned, so playback keeps working however often you skip.

Version code: 149. See [CHANGELOG.md](https://github.com/thawee/musicmate/blob/v3.26.1/CHANGELOG.md) for details.
