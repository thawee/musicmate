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
- **Transport Controls:** `playSong(Track)`, `pausePlayer()`, `resumePlayer()`, `stopPlaying()`, `skipToNextInQueue()`, `skipToPrevious()`, `seekTo(long)`.
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

## 8. Active ReplayGain 2.0 / EBU R128 Loudness Leveling Architecture

MusicMate incorporates an active, real-time loudness leveling engine (`ReplayGainManager.java`) designed to eliminate abrupt volume jumps between tracks and albums while safeguarding bit-perfect digital audio integrity.

```
                  ┌──────────────────────────────────────────────┐
                  │ Audio File (FLAC, MP3, M4A, DSF, WAV, OGG)   │
                  │ Reads: REPLAYGAIN_TRACK_GAIN / TRACK_PEAK    │
                  │ Reads: REPLAYGAIN_ALBUM_GAIN / ALBUM_PEAK    │
                  └──────────────────────┬───────────────────────┘
                                         │
                                         ▼
                  ┌──────────────────────────────────────────────┐
                  │            ReplayGainManager                 │
                  │  Gain Scalar = 10^((gainDb + preAmpDb) / 20) │
                  │  Peak Guard: scalar * peak <= 1.0            │
                  └──────────────────────┬───────────────────────┘
                                         │
                                         ▼
                  ┌──────────────────────────────────────────────┐
                  │          AndroidPlayerController             │
                  │  ExoPlayer.setVolume(clampedScalar)          │
                  │  Recalculated on onMediaItemTransition()     │
                  └──────────────────────────────────────────────┘
```

- **Loudness Modes:**
  - `Track Gain`: Normalizes every song to standard $-18\text{ LUFS}$ / $89\text{ dB SPL}$.
  - `Album Gain`: Preserves intentional dynamic volume contrasts across multi-track concept albums.
  - `Off`: Passes unmodified linear output (fixed $1.0$).
- **Anti-Clipping True-Peak Guard:** Prevents digital clipping distortion by evaluating $scalar \times peak \le 1.0$. If the boosted scalar would exceed full scale ($0\text{ dBFS}$), it is automatically clamped down.
- **Cross-Player Metadata Parity:** Loudness tags analyzed by MusicMate are written back to standardized Vorbis Comments (`REPLAYGAIN_TRACK_GAIN`, `REPLAYGAIN_TRACK_PEAK`), ID3v2 TXXX, and MP4 tags via Jaudiotagger, ensuring full interoperability with Poweramp, UAPP, Foobar2000, and Neutron.

---

## 9. Dynamic Rule-Based Smart Playlists Engine

Smart Playlists (`PlaylistEntry.TYPE_SMART`) provide real-time, rule-based music categorization driven by MusicMate's hardware audio telemetry:

- **Matching Criteria (`PlaylistEntry.java`):**
  - `minDrScore`: Minimum Dynamic Range threshold ($0\dots 16$).
  - `hiresOnly`: Requires bit depth $\ge 24\text{-bit}$ and sample rate $> 48\text{ kHz}$.
  - `dsdOnly`: Matches 1-bit Direct Stream Digital tracks (DSD64 through DSD512).
  - `losslessOnly`: Matches bit-perfect formats (FLAC, ALAC, WAV, AIFF, DSD).
  - `minBitDepth` & `minSampleRate`: Custom hardware thresholds.
- **Audiophile Query Studio (`CreateSmartPlaylistDialog.kt`):** Live in-app query builder calculating matching track counts and storage footprint in real-time (`⚡ Live Match: X tracks • Y GB`).
- **Persistence & Cross-Platform Sharing:** Saved to `custom_playlists.json` in app storage and dynamically served across the Native Android UI, DLNA/UPnP Media Server, and Web Remote UI.

---

## 10. HTTP Zero-Copy Streaming Engine & RFC 7233 Specification

MusicMate embeds high-performance HTTP servers (Apache HttpCore 5 and Netty) optimized for bit-perfect audio streaming to low-latency DAPs, network streamers, and web browsers:

- **64KB NIO Direct Streaming (`PartialFileProducer.java`):**
  - Streams directly from underlying `FileChannel` in 64KB chunks rather than splicing intermediate in-memory buffers, eliminating reactive backpressure aborts and buffer exhaustion.
  - Zero-latency startup is achieved by background page-cache warming via `AudioStreamCacheManager.preloadTrack()`, leveraging the Linux kernel VFS cache with zero JVM heap allocations.
- **RFC 7233 Range Specification Compliance (`HttpCoreWebServerImpl.java`):**
  - **Open-Ended Clamping:** Range headers with open or unbounded limits (e.g. `bytes=0-2147483647` sent by WiiM, Sony, Yamaha, and Chrome) are clamped to `fileLength - 1`. Prevents incorrect multi-gigabyte `Content-Length` headers and stream truncation errors on DLNA renderers.
  - **416 Status Guard:** Returns HTTP `416 Range Not Satisfiable` with `Content-Range: bytes */fileLength` when requested start offsets exceed the file size.
  - **Suffix Range Support:** Accurately resolves suffix range queries (e.g. `bytes=-500` for ID3v1 / trailing tag inspection).
  - **HEAD Entity Body Suppression:** Conforms to RFC 7231 by returning `Content-Length` and `Content-Type` headers without attaching an entity stream body.
- **DLNA Natural Completion & Double-Skip Prevention (`MediaServerHubImpl.java`):**
  - Upon natural track completion (`position >= duration` or `STOPPED` GENA event), the controller immediately halts active polling loops via `stopPolling()` and latches `isUserInitiatedStop = true;` before notifying `playbackCallback.onPlaybackCompleted()`.
  - Prevents rapid recurring polling ticks or duplicate renderer GENA packets from advancing the queue multiple times.

---

## 11. Summary of Audio Module Map

| Module | File | Key Responsibility |
| :--- | :--- | :--- |
| `:core` | [`AudioStreamCacheManager.java`](file:///Users/thawee.p/Workspaces/github/musicmate/core/src/main/java/apincer/music/core/playback/AudioStreamCacheManager.java) | Zero-allocation OS kernel page-cache pre-warming for instant discrete handover. |
| `:server-jupnp-httpcore` | [`HttpCoreWebServerImpl.java`](file:///Users/thawee.p/Workspaces/github/musicmate/server-jupnp-httpcore/src/main/java/apincer/android/jupnp/server/httpcore/HttpCoreWebServerImpl.java) | Apache HttpCore 5 NIO server with RFC 7233 range parsing and dynamic ETag support. |
| `:server-jupnp-httpcore` | [`PartialFileProducer.java`](file:///Users/thawee.p/Workspaces/github/musicmate/server-jupnp-httpcore/src/main/java/apincer/android/jupnp/server/httpcore/PartialFileProducer.java) | Direct FileChannel 64KB chunked HTTP entity producer with automatic descriptor cleanup. |
| `:core` | [`PlaybackTarget.java`](file:///Users/thawee.p/Workspaces/github/musicmate/core/src/main/java/apincer/music/core/playback/spi/PlaybackTarget.java) | Target abstraction for Local, External Apps, and DLNA. |
| `:core` | [`PlaybackService.java`](file:///Users/thawee.p/Workspaces/github/musicmate/core/src/main/java/apincer/music/core/playback/spi/PlaybackService.java) | Master playback service contract. |
| `:core` | [`ReplayGainManager.java`](file:///Users/thawee.p/Workspaces/github/musicmate/core/src/main/java/apincer/music/core/playback/ReplayGainManager.java) | Active ReplayGain 2.0 loudness scaling and anti-clipping true-peak guard. |
| `:core` | [`QueueManager.java`](file:///Users/thawee.p/Workspaces/github/musicmate/core/src/main/java/apincer/music/core/repository/QueueManager.java) | Deduplicated queue, shuffle order, and Room persistence. |
| `:core` | [`PlaylistEntry.java`](file:///Users/thawee.p/Workspaces/github/musicmate/core/src/main/java/apincer/music/core/model/PlaylistEntry.java) | Dynamic Smart Playlist rules and metadata evaluation. |
| `:core` | [`FFMpegHelper.java`](file:///Users/thawee.p/Workspaces/github/musicmate/core/src/main/java/apincer/music/core/codec/FFMpegHelper.java) | DSD 30kHz LPF filtering and 88.2kHz integer downsampling. |
| `:server-jupnp` | [`MediaServerHubImpl.java`](file:///Users/thawee.p/Workspaces/github/musicmate/server-jupnp/src/main/java/apincer/music/server/jupnp/MediaServerHubImpl.java) | DLNA SSDP discovery, AVTransport SOAP, and `SetNextAVTransportURI` gapless. |
| `:app` | [`AndroidPlayerController.java`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/service/AndroidPlayerController.java) | Local ExoPlayer AudioTrack engine, ReplayGain volume leveling, and CPU wakelocks. |
| `:app` | [`MusicMateServiceImpl.java`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/service/MusicMateServiceImpl.java) | Central service orchestrator, strategy router, and lifecycle manager. |
| `:app` | [`AudioHubBottomSheet.java`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java) | 3-tab Music Center container hosting Compose viewports. |
| `:app` | [`NowPlayingPage.kt`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/compose/NowPlayingPage.kt) | Jetpack Compose Now Playing UI, 3D flip Audio Anatomy card with dual-mode telemetry switcher. |
| `:app` | [`AnalogVUMeter.kt`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/compose/AnalogVUMeter.kt) | Pure Compose Canvas ballistic VU meter with dual stereo dials, 3 audiophile themes, and live PCM input. |
| `:app` | [`ReelToReelTapeDeck.kt`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/compose/ReelToReelTapeDeck.kt) | Pure Compose Canvas vintage reel-to-reel tape deck with differential angular physics and tape counter. |
| `:app` | [`AudioLevelProcessor.kt`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/audio/AudioLevelProcessor.kt) | Media3 BaseAudioProcessor extracting real-time stereo RMS and peak decibels from decoded PCM. |
| `:app` | [`AudioTelemetryManager.kt`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/audio/AudioTelemetryManager.kt) | Thread-safe 60Hz telemetry hub delivering live audio decibel packets to UI widgets. |
| `:app` | [`CreateSmartPlaylistDialog.kt`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/compose/CreateSmartPlaylistDialog.kt) | Visual Audiophile Query Studio modal dialog with live matching telemetry. |
| `:app` | [`MediaServerPage.kt`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/compose/MediaServerPage.kt) | Jetpack Compose Media Server management, Hero Status Card with Start/Stop controls & QR zoom dialog. |
| `:app` | [`QueuePage.kt`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/compose/QueuePage.kt) | Jetpack Compose upcoming queue list with duration telemetry and drag reordering. |
