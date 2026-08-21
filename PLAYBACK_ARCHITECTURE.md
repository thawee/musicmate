# Playback Module Architecture & Audio Engine Specification

This document provides a comprehensive architectural reference for MusicMate's audio playback engine, queue management, hardware audio pipeline, Android app controller bridge, and DLNA/UPnP network streaming renderer integration.

---

## 1. High-Level Architecture

MusicMate implements a multi-backend **Strategy Pattern** with a central foreground service orchestrator (`MusicMateServiceImpl`) routing transport, metadata, and queue commands to either the **Direct Local Audio Engine** or the **Asynchronous DLNA/UPnP Streamer**.

```
                        ┌──────────────────────────────────────────────────┐
                        │                     UI Layer                     │
                        │  ┌─────────────────┐   ┌──────────────────────┐  │
                        │  │  MainActivity   │   │ AudioHubBottomSheet  │  │
                        │  │  Floating Dock  │   │ 3-Tab Music Center   │  │
                        │  │  WebUI Remote   │   │ (Playing/Queue/Serv) │  │
                        │  └────────┬────────┘   └──────────┬───────────┘  │
                        └───────────┼───────────────────────┼──────────────┘
                                    │ bindService()         │ StateFlow / LiveData
                        ┌───────────▼───────────────────────▼──────────────┐
                        │             MusicMateServiceImpl                 │
                        │              (PlaybackService)                   │
                        │                                                  │
                        │  ┌────────────────────────────────────────────┐  │
                        │  │                QueueManager                │  │
                        │  │  Deduplication • Shuffle • Repeat • Room   │  │
                        │  └────────────────────────────────────────────┘  │
                        └───────────┬───────────────────────┬──────────────┘
                                    │                       │
                    ┌───────────────▼───┐       ┌───────────▼──────────────────┐
                    │  AndroidPlayer    │       │     MediaServerHubImpl       │
                    │   Controller      │       │     (DLNA / UPnP Engine)     │
                    │                   │       │                              │
                    │ ┌───────────────┐ │       │ ┌──────────────────────────┐ │
                    │ │ Local Exo     │ │       │ │ ControlPoint             │ │
                    │ │ AudioTrack    │ │       │ │ (SSDP M-SEARCH + SOAP)   │ │
                    │ └───────────────┘ │       │ └──────────────────────────┘ │
                    │ ┌───────────────┐ │       │ ┌──────────────────────────┐ │
                    │ │ MediaSession  │ │       │ │ BaseServer               │ │
                    │ │ App Bridge    │ │       │ │ (HTTP Streamer :9000)    │ │
                    │ └───────────────┘ │       │ └──────────────────────────┘ │
                    └─────────┬─────────┘       └──────────────┬───────────────┘
                              │                                │
                 ┌────────────┴────────────┐             ┌─────┴───────────────┐
                 │                         │             │                     │
            ┌────▼─────┐             ┌─────▼─────┐       │  Network Streamers  │
            │ On-Device│             │ Third-    │       │  (WiiM, Eversolo,   │
            │ BT / DAC │             │ Party App │       │   HiBy, Rose, DLNA) │
            └──────────┘             └───────────┘       └─────────────────────┘
```

---

## 2. SPI Core Contracts

The SPI contract layer (`core/src/main/java/apincer/music/core/playback/spi/`) decouples UI presentation, queue orchestration, and hardware renderers.

### `PlaybackTarget` (Interface)
Defines any physical or network playback destination:

| Method | Purpose |
| :--- | :--- |
| `getTargetId()` | Unique identifier (UDN string for DLNA renderers, package name for external apps, `local` for on-device). |
| `getDisplayName()` | Clean label for UI headers and dialogs. |
| `getTargetType()` | Target category: `LOCAL`, `EXTERNAL_APP`, `STREAMING`. |
| `isStreaming()` | `true` when routing audio packets over Wi-Fi / Ethernet to UPnP renderers. |
| `canReadSate()` | Whether real-time playback position monitoring is supported. |

#### Concrete Implementations:
1. **`LocalAndroidPlayer` (`LOCAL`):** Direct on-device playback via ExoPlayer and Android `AudioTrack`.
2. **`ExternalAndroidPlayer` (`EXTERNAL_APP`):** Third-party audiophile player integration (USB Audio Player PRO, Neutron, Poweramp, HiBy Music, Foobar2000, NePLAYER Lite).
3. **`DMRPlayer` (`STREAMING`):** DLNA / UPnP Digital Media Renderers on the local network.
4. **`WebStreamingPlayer` (`STREAMING`):** Browser-based WebUI HTTP audio streaming clients.

### `PlaybackService` (Interface)
The master service API contract implemented by `MusicMateServiceImpl`:
- **Transport Controls:** `playSong(Track)`, `pausePlayer()`, `stopPlaying()`, `skipToNextInQueue()`, `skipToPrevious()`, `seekTo(long)`.
- **Queue Controls:** `setNextSongInQueue()`, `setShuffleMode(boolean)`, `setRepeatMode(RepeatMode)`.
- **Target Management:** `switchPlayer(PlaybackTarget, boolean)`, `getPlayer()`, `getPlaybackTargets()`, `refreshPlayerDiscovery()`.
- **Reactive State Subscriptions:** `subscribePlaybackState()`, `subscribeNowPlayingSong()`, `subscribePlaybackTarget()`.

### `PlaybackCallback` (Abstract Class)
Unified event bus bridging native ExoPlayer, third-party MediaSessions, and remote UPnP GENA events:
- `onMediaTrackChanged(Track track)`: Fired on track changes.
- `onPlaybackStateChanged(PlaybackState state)`: Fired on state transitions (`PLAYING`, `PAUSED`, `STOPPED`).
- `onPlaybackCompleted()`: Fired upon track end to trigger seamless queue advancement.
- `onPlaybackStateTimeElapsedSeconds(long sec)`: Real-time progress updates for seek bars.

---

## 3. Direct Audio Engine & Hardware Pipeline

*Path:* `app/src/main/java/apincer/android/mmate/service/AndroidPlayerController.java`

### High-Resolution AudioTrack & ExoPlayer Setup
MusicMate's internal player leverages Media3 ExoPlayer configured for hardware audio reproduction:

1. **Auto-Negotiated Integer PCM:** Configured with standard `ExoPlayer.Builder(context)` to automatically negotiate bit-perfect 16-bit and 24-bit integer PCM (`ENCODING_PCM_16BIT` / `ENCODING_PCM_24BIT_PACKED`) with Android's Bluetooth A2DP audio HAL (`a2dp.default.so`) and connected USB DACs, completely avoiding float quantization distortion.
2. **CPU Wakelock Protection:** Configured with `setWakeMode(C.WAKE_MODE_LOCAL)` to acquire `PowerManager.PARTIAL_WAKE_LOCK` automatically during active playback, preventing Android Doze sleep pauses and buffer underruns during screen-off listening.
3. **Hardware Disconnect Protection:** Configured with `setHandleAudioBecomingNoisy(true)` to automatically pause playback when headphones, USB DACs, or Bluetooth devices are disconnected.
4. **Sample-Accurate Seeking:** Configured with `SeekParameters.EXACT` for sample-accurate scrubbing across 24-bit/96kHz+ FLAC and DSD files.
5. **Lifecycle Cleanup:** Complete `release()` method tied directly into `MusicMateServiceImpl.onDestroy()`.

### True Dual-Engine Gapless Playback Pipeline

```
Local Engine (ExoPlayer)                     DLNA / UPnP Network Engine
────────────────────────                     ──────────────────────────
play(currentTrack)                           playerPlaySong(currentTrack)
   │                                            │
preloadNextTrack()                           preloadNextTrack()
   │                                            │
internalExoPlayer.addMediaItem(nextTrack)    mediaHub.setNextTrack(nextTrack)
   │                                            │
(ExoPlayer double-buffers audio samples)     (Sends UPnP SetNextAVTransportURI)
   │                                            │
[Track Finishes]                             [Track Finishes]
   │                                            │
onMediaItemTransition(REASON_AUTO)           Renderer auto-transitions
   ▼                                            ▼
onPlaybackCompleted()                        GENA LastChange Event
   ▼                                            ▼
Queue advances & primes upcoming track       Queue advances & primes upcoming track
```

- **Local Gapless:** `setNextTrack(nextSong)` double-buffers the next track into ExoPlayer's playlist. When the track finishes, `Player.Listener.onMediaItemTransition(MEDIA_ITEM_TRANSITION_REASON_AUTO)` notifies `PlaybackCallback.onPlaybackCompleted()` with **zero gap**.
- **Network Gapless:** `MediaServerHubImpl.setNextTrack()` dispatches UPnP `SetNextAVTransportURI` with complete DIDL-Lite metadata. A safety fallback timer scheduled at `100% duration + 1.5s` ensures queue progression if the renderer firmware drops the transition.

---

## 4. Audiophile DSD & Resampling Pipeline

*Path:* `core/src/main/java/apincer/music/core/codec/FFMpegHelper.java`

For high-resolution DSD (DSF/DFF) processing and conversions:
- **Exact Integer Downsampling:** DSD64 (2.8224 MHz) is transcoded using native 32x integer multiples (**88.2 kHz** or **176.4 kHz**) rather than asynchronous non-integer 48 kHz resampling, eliminating inter-modulation distortion and timing jitter.
- **Ultrasonic Noise Attenuation:** Applies an 8th-order 30 kHz lowpass filter (`-af "lowpass=30000, volume=6dB"`) to eliminate DSD high-frequency quantization noise while maintaining a +6dB standard DSD-to-PCM level match.

---

## 5. Universal Queue Deduplication

*Path:* `core/src/main/java/apincer/music/core/repository/QueueManager.java`

MusicMate enforces strict single-instance uniqueness across the playing queue:
- **`addPlayingQueue(Track)` & `addPlayNext(Track)`:** Automatically remove any prior instance of the song before adding or moving it, maintaining index pointer alignment.
- **Index Synchronization:** Left-shifts `currentIndex` and `playbackIndex` when removing preceding items to prevent skipping tracks.
- **Persistent State:** Saves and restores `isShuffle` and `repeatMode` to database/preferences on app restarts.

---

## 6. Now Playing UI & Audiophile Telemetry

*Path:* `app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java`

### 1-Line Audio Signal Path Telemetry
Displays live, full-width audio route information directly on the Now Playing screen:
- **Node 1 (Source):** Format, Bit Depth, Sample Rate (e.g. `[FLAC 24/44.1k]` in Gold).
- **Node 2 (Transport):** `Local Transport` vs. `Net Streamer` (Cyan).
- **Node 3 (Target):** Connected Bluetooth device with active codec (e.g. `Shanling UP4 • LDAC`) or DLNA renderer.

### Dynamic Range (DR) Metrics Chip
- Displays a dedicated amber chip (e.g. `[DR 14]`) indicating mastering dynamic health and uncompressed dynamic range.

### `DIRECT` / Bit-Perfect Verification Badge
- Displays a glowing emerald `[DIRECT]` badge when streaming uncompressed lossless audio (FLAC, DSD, ALAC, WAV) to network renderers or bit-perfect local outputs.

### 1-Tap 3D Flip Technical Specs Card
- Tapping the album artwork or info badge triggers a smooth 3D Y-axis card flip (`rotationY 90° ➔ -90° ➔ 0°`) revealing a glassmorphic **Audio Anatomy** card:
  - **Audio Format & Resolution:** `FLAC • 24-bit / 44.1 kHz`
  - **Bitrate:** `846 kbps (VBR)`
  - **Dynamic Range:** `Dynamic Range: DR 14`
  - **Physical File Size:** Formatted accurately via `android.text.format.Formatter.formatFileSize`.

---

## 7. Third-Party App Bridge (MediaSession)

MusicMate monitors and controls external audiophile players via `AndroidPlayerController`:

```
                       MediaSessionManager
                               │
                               │ getActiveSessions()
                               ▼
                        ┌──────────────┐
                        │MediaController│
                        │  .Callback   │
                        └──────┬───────┘
                               │ onMetadataChanged()
                               │ onPlaybackStateChanged()
                               ▼
                        PlaybackCallback
                               │
                               ▼
                       MusicMateServiceImpl
```

- **Supported Apps:** USB Audio Player PRO, Neutron Music Player, Poweramp, HiBy Music, Foobar2000, NePLAYER Lite.
- **Self-Package Filtering:** When querying `MediaSessionManager.getActiveSessions()`, MusicMate explicitly filters out its own package (`context.getPackageName()`) to ensure the app's native local player is not duplicated as an external third-party renderer in player pickers.
- **Progress Tracking:** 1-second adaptive polling handler reading `MediaController.getPlaybackState().getPosition()`.
- **Targeted Launching:** Custom URIs via `MusicFileProvider.getUriForFile()` granting temporary read permissions to external media packages.

---

## 8. Summary of Audio Module Map

| Module | File | Key Responsibility |
| :--- | :--- | :--- |
| `:core` | [`PlaybackTarget.java`](file:///Users/thawee.p/Workspaces/github/musicmate/core/src/main/java/apincer/music/core/playback/spi/PlaybackTarget.java) | Target abstraction for Local, External Apps, and DLNA. |
| `:core` | [`PlaybackService.java`](file:///Users/thawee.p/Workspaces/github/musicmate/core/src/main/java/apincer/music/core/playback/spi/PlaybackService.java) | Master playback service contract. |
| `:core` | [`QueueManager.java`](file:///Users/thawee.p/Workspaces/github/musicmate/core/src/main/java/apincer/music/core/repository/QueueManager.java) | Deduplicated queue, shuffle order, and Room persistence. |
| `:core` | [`FFMpegHelper.java`](file:///Users/thawee.p/Workspaces/github/musicmate/core/src/main/java/apincer/music/core/codec/FFMpegHelper.java) | DSD 30kHz LPF filtering and 88.2kHz integer downsampling. |
| `:server-jupnp` | [`MediaServerHubImpl.java`](file:///Users/thawee.p/Workspaces/github/musicmate/server-jupnp/src/main/java/apincer/music/server/jupnp/MediaServerHubImpl.java) | DLNA SSDP discovery, AVTransport SOAP, and `SetNextAVTransportURI` gapless. |
| `:app` | [`AndroidPlayerController.java`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/service/AndroidPlayerController.java) | Local ExoPlayer AudioTrack engine, CPU wakelocks, and MediaSession bridge. |
| `:app` | [`MusicMateServiceImpl.java`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/service/MusicMateServiceImpl.java) | Central service orchestrator, strategy router, and lifecycle manager. |
| `:app` | [`AudioHubBottomSheet.java`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java) | 3-tab Music Center container hosting Compose viewports. |
| `:app` | [`NowPlayingPage.kt`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/compose/NowPlayingPage.kt) | Jetpack Compose Now Playing UI, 3D flip Audio Anatomy card with `ic_round_info_24` badge. |
| `:app` | [`MediaServerPage.kt`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/compose/MediaServerPage.kt) | Jetpack Compose Media Server management, Hero Status Card with Start/Stop controls & QR zoom dialog. |
| `:app` | [`QueuePage.kt`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/compose/QueuePage.kt) | Jetpack Compose upcoming queue list with duration telemetry and drag reordering. |
