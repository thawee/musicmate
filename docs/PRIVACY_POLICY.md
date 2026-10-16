# MusicMate Privacy Policy

Effective: 2026-10-16

MusicMate is a music library manager, player and media server that runs on your device. It has no accounts, no ads, no analytics and no tracking, and the developer receives no data from it.

## What stays on your device

Your music files, their tags, cover art, playlists, listening history, settings and crash logs stay on your device. MusicMate reads and changes music files only in the folders you choose (in the Google Play version, folders you grant in Android's folder picker).

Crash logs are kept on the device. They are shared only if you choose to share one yourself.

## What leaves your device, and when

MusicMate contacts these services only when you use the feature that needs them:

| Feature | Service | What is sent |
|---|---|---|
| Search & Match Tags | [MusicBrainz](https://musicbrainz.org) | The song's title and artist, and the release identifier of a match |
| Search & Match Tags, for a song with no title and no artist | [AcoustID](https://acoustid.org) | An audio fingerprint computed from the song, and its duration |
| Cover art from Search & Match | [Cover Art Archive](https://coverartarchive.org) | The release identifier of the match |
| Artist and album details in the web player | [Wikipedia](https://www.wikipedia.org) | The artist and album names |
| Search Song on Web | Your web browser and its search engine | The search text you see |

These services receive your device's IP address, as with any internet request. Their own policies apply to what they receive: [MusicBrainz and the Cover Art Archive (MetaBrainz)](https://metabrainz.org/privacy), [Wikipedia (Wikimedia)](https://foundation.wikimedia.org/wiki/Policy:Privacy_policy), and for AcoustID see [acoustid.org](https://acoustid.org). All of these connections use HTTPS.

## Your local network

When you start the media server, MusicMate shares your music library with DLNA players and web browsers on your Wi-Fi network, and announces itself there so they can find it. Nothing is sent outside your network. Stop the server to stop sharing.

## Other apps

If you open a song in another music app, or give MusicMate access to external player sessions, Android passes only that song, or that playback information, between the apps on your device.

## Permissions

MusicMate uses access to your music, notifications (to show playback and server status), the local network (for the media server), Bluetooth (to show which headphones or DAC is playing), and optionally notification access to see playback in other music apps. It does not ask for your location, contacts, camera or microphone.

## Children

MusicMate is not directed at children and collects no personal information from anyone.

## Changes

Changes to this policy are published in the [MusicMate repository](https://github.com/thawee/musicmate/blob/main/docs/PRIVACY_POLICY.md), with the date above updated.

## Contact

Questions: open an issue at [github.com/thawee/musicmate/issues](https://github.com/thawee/musicmate/issues).
