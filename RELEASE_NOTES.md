# MusicMate 3.23.1

Fixes a crash in 3.23.0: opening a song's Tech Info, or anything else that uses FFmpeg, closed the app. The release build had removed methods that FFmpeg's native library looks up by name. Earlier releases had the same build setup and were very likely affected too.

The main screen now has a single Play button that follows the shuffle setting in Now Playing. The mini player is opaque, its title scrolls only while music plays, new tracks are marked with a dot on the album art, and the header reads "22.9 Days of Music". Total library size has moved to About.

The song preview and tag editor are clearer. Save lights up as soon as you edit a field, Back from the editor returns to the song preview, and the form no longer leaves a blank band above the keyboard. Tech Info has plain section names and labelled cover-art actions. In selection mode, the bar now shows how many songs are selected.

Version code: 143. See [CHANGELOG.md](https://github.com/thawee/musicmate/blob/v3.23.1/CHANGELOG.md) for details.
