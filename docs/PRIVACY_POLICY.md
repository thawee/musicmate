# MusicMate Privacy Policy

Effective: 2026-10-23

MusicMate is a music library manager, player and media server that runs on your device. It has no accounts, no ads, no analytics and no tracking. The app sends the developer no data; if you buy Pro, Google Play handles the sale (see Purchases).

## What stays on your device

Your music files, their tags, cover art, playlists, listening history, settings and crash logs stay on your device. MusicMate reads and changes music files only in the folders you choose (in the Google Play version, folders you grant in Android's folder picker).

Crash logs are kept on the device. They are shared only if you choose to share one yourself.

Speaker Tuning uses the microphone, and only while you run it. MusicMate plays a test sweep and records it to measure how your car or speaker sounds. The recording is analysed in memory on the device and then discarded; only the resulting frequency response, the EQ settings and the name and Bluetooth address of the car or speaker you are connected to are saved, as a profile on the device. The recording is never saved to a file and never sent anywhere.

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

## Purchases (Google Play version)

MusicMate Pro is a one-time purchase sold through Google Play. Google Play handles the payment under [Google's terms and privacy policy](https://policies.google.com/privacy); MusicMate never sees your payment details. On your device, MusicMate asks Google Play whether you own Pro and remembers the answer so Pro works offline. Google Play gives the developer the sales records it gives every seller. The version from GitHub includes Pro and has no purchases.

## Your local network

When you start the media server, MusicMate shares your music library with DLNA players and web browsers on your Wi-Fi network, and announces itself there so they can find it. Nothing is sent outside your network. Stop the server to stop sharing.

## Other apps

If you open a song in another music app, MusicMate passes only that song to it.

If you turn on External Player Access, Android asks you to grant MusicMate notification access, which Android requires before an app can see other apps' media sessions. MusicMate uses it only to see which music apps are playing and to control them. It never reads, stores or sends your notifications.

## Permissions

MusicMate uses access to your music, notifications (to show playback and server status), the local network (for the media server), Bluetooth (to show which headphones or DAC is playing, and to recognise your car or speaker for Speaker Tuning), optionally notification access to see playback in other music apps, and the microphone only when you run Speaker Tuning. It does not ask for your location, contacts or camera.

## Children

MusicMate is not directed at children and collects no personal information from anyone.

## Changes

Changes to this policy are published in the [MusicMate repository](https://github.com/thawee/musicmate/blob/main/docs/PRIVACY_POLICY.md), with the date above updated.

## Contact

Questions: open an issue at [github.com/thawee/musicmate/issues](https://github.com/thawee/musicmate/issues).
