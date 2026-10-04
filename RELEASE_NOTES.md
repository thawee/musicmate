# MusicMate 3.23.2

Fixes a crash when scanning a library that contains WAV files. Release builds moved one of the tag library's classes so that a lookup of its package returned nothing, and the scan stopped at the first WAV file. Earlier releases were very likely affected too.

WAV files now keep their genre and embedded cover art. Previously the genre showed as "Unknown" unless MusicMate had written it, and covers stored inside WAV files were not shown.

Also adds small null-safety fixes, including one for the server QR code if the server stops while the QR code is enlarged.

Version code: 144. See [CHANGELOG.md](https://github.com/thawee/musicmate/blob/v3.23.2/CHANGELOG.md) for details.
