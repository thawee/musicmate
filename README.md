# MusicMate

MusicMate is a music player and library manager for Android, made for people with high-resolution music collections. It plays your files on the phone, through a USB DAC or Bluetooth, or on DLNA/UPnP players around the house. It also shows exactly what each track is (format, bit depth, sample rate, dynamic range) and helps you keep your tags tidy.

<p align="center">
  <img src="docs/screenshots/library.png" width="19%" alt="Library">
  <img src="docs/screenshots/music-center.png" width="19%" alt="Music Center">
  <img src="docs/screenshots/player-picker.png" width="19%" alt="Player picker">
  <img src="docs/screenshots/song-details.png" width="19%" alt="Song details">
  <img src="docs/screenshots/server.png" width="19%" alt="Media server">
</p>

---

## What you can do

**Browse a large library.** Scroll thousands of songs without waiting. Find music by artist, genre, folder, playlist or audio quality, or search. Smart playlists such as Hi-Res or DR12+ fill themselves.

**Play anywhere.** Play on the phone, bit-perfect through a USB DAC, or over Bluetooth with the codec shown (LDAC, aptX, AAC, SBC). Send music to a DLNA renderer such as a HiBy, WiiM or Eversolo, or hand it to another music app. One picker lists every option, and it updates as players appear on the network.

**See what you are hearing.** Music Center shows the track, the queue and the server in three tabs. The audio path shows the source file, how it travels, and where it plays, with sample rate, bit depth and whether the output is bit-perfect.

**Stream from your phone.** MusicMate runs a DLNA media server, so players and apps like mConnect or BubbleUPnP can browse and play your library directly, with no transcoding. It works on home Wi-Fi and on the phone's own hotspot. A web remote is available at `http://<phone-ip>:9000` in any browser.

**Know your files.** MusicMate detects Hi-Res, DSD and MQA, measures dynamic range (DR), and flags "Hi-Res" files that are really upsampled CD audio. The [Music Quality Guide](MUSIC_QUALITY_GUIDE.md) explains what the labels mean.

**Fix your tags.** Edit title, artist, album, genre, mood and style with ready-made choices. Look up details on MusicBrainz, manage cover art, and organize files into folders.

---

## Install

1. Download `MusicMate-v<version>.apk` from the latest [GitHub release](https://github.com/thawee/musicmate/releases/latest).
2. Open it on your phone and allow installing from your browser or file manager when Android asks.

MusicMate needs **Android 16** or newer.

Releases before 3.24.0 published an unsigned APK that Android refuses to install. If you have a copy of MusicMate from anywhere other than these releases, uninstall it once before installing. After that, each new release updates in place and keeps your library.

---

## Getting started

1. **Add your music.** On first launch, choose the folders that hold your music and tap Scan. MusicMate asks for storage access so it can read and edit your files.
2. **Pick where to play.** Tap the player button to choose the phone, a Bluetooth or USB device, a DLNA player on your network, or another music app.
3. **Play.** Tap a song to play it. Press and hold a song to open its details, where you can edit its tags.

The [User Guide](USER_GUIDE.md) covers every screen in detail.

---

## A typical setup

```text
  Tablet or phone             Android phone               Hi-Fi streamer
  (control app)               (MusicMate)                 (DLNA renderer)
  mConnect, BubbleUPnP  --->  your music library  <---    plays the stream
                    browse                      fetch       into your DAC
```

Your music stays on the phone. A control app browses it, and the streamer plays it bit-perfect with full tags and cover art. You can also pick the streamer directly in MusicMate and control playback from the phone.

---

## More information

*   **[User Guide](USER_GUIDE.md):** How to use each screen.
*   **[Music Quality Guide](MUSIC_QUALITY_GUIDE.md):** Bit depth, sample rate and dynamic range explained.
*   **[Changelog](CHANGELOG.md):** What changed in each release.
*   **[Technical Overview](TECHNICAL.md):** For developers. How the server and streaming engine work, performance figures, and links to the design documents.

---

## License

Copyright 2014–2026 Thawee Prakaipetch. Licensed under the Apache License, Version 2.0.
