# MusicMate Technical & System Architecture Specification

> **Last Updated:** 2026-09-24 · **Owner:** @thawee
>
> **Scope:** This document is the authoritative specification for MusicMate's system topology, multi-target playback routing, audio engine pipelines, UPnP/DLNA streaming architecture, metadata tagging, Room DB persistence, and system Architectural Decision Records (ADRs).
>
> **Modular Documentation Index:**
> - For UI/UX design tokens, interaction models, menus, styling, and Jetpack Compose sheets, see [`UI.md`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md).
> - For SPI contracts and low-level playback engine internals, see [`PLAYBACK_ARCHITECTURE.md`](file:///Users/thawee.p/Workspaces/github/musicmate/PLAYBACK_ARCHITECTURE.md).
> - For network resilience, retry policies, and Wi-Fi state handling, see [`NETWORK_RESILIENCE.md`](file:///Users/thawee.p/Workspaces/github/musicmate/NETWORK_RESILIENCE.md).
> - For WebUI and WebSocket protocols, see [`WEBUI.md`](file:///Users/thawee.p/Workspaces/github/musicmate/WEBUI.md) and [`WEBSOCKET_API.md`](file:///Users/thawee.p/Workspaces/github/musicmate/WEBSOCKET_API.md).

---

## 1. High-Level System Architecture & Module Topology

MusicMate is structured as a multi-module Android project enforcing a strict separation between presentation, domain abstractions, database persistence, streaming servers, and low-level audio codec libraries:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                :app MODULE                                  │
│  - Presentation (MainActivity, Jetpack Compose, AudioHubSheet, TagsActivity)│
│  - Foreground Service Orchestration (MusicMateServiceImpl)                  │
│  - Queue Management & Player Controllers (AndroidPlayerController)          │
│  - UPnP Control Point & Dispatch (MediaServerHubImpl)                       │
└───────────────────────┬───────────────────────────────┬─────────────────────┘
                        │                               │
        ┌───────────────▼──────────────┐ ┌──────────────▼──────────────┐
        │         :db-room             │ │       :server-jupnp         │
        │ - SQLite / Room Caching      │ │ - MediaServerDevice (DMS)   │
        │ - SongEntity, TagEntity      │ │ - ContentDirectory Browsers │
        │ - Dynamic Query Builders     │ │ - NioWebServerImpl (HTTP)   │
        └───────────────┬──────────────┘ └──────────────┬──────────────┘
                        │                               │
                        └───────────────┬───────────────┘
                                        │
┌───────────────────────────────────────▼─────────────────────────────────────┐
│                                :core MODULE                                 │
│  - Domain Models (Song, MusicTag, AudioTag)                                 │
│  - Playback SPI Interfaces (PlaybackTarget, MusicPlayer, PlaybackCallback)  │
│  - Tag Reading & Writing Engines (TagWriter, JThinkWriter, ArtworkFactory)   │
│  - Hardware Audio Utilities (AudioOutputHelper, MimeTypeUtils)              │
│  - Secure Storage & Sharing (MusicFileProvider, Storage Access Framework)   │
└───────────────────────────────────────┬─────────────────────────────────────┘
                                        │
        ┌───────────────────────────────┴───────────────────────────────┐
        │                     :library SUBMODULES                       │
        │ - jaudiotagger-android (ID3v1, ID3v2, Vorbis Comments, MP4)   │
        │ - JustFLAC (Native FLAC frame & metadata parsing)             │
        │ - justdsd (Direct Stream Digital .dsf & .dff parsing)         │
        │ - crashreporter (In-process diagnostic capture)               │
        └───────────────────────────────────────────────────────────────┘
```

---

## 2. Multi-Target Playback & Output Device Architecture

MusicMate's playback architecture coordinates audio delivery across three distinct architectural domains: Local Audio (Speaker/Wired/USB), Network DLNA Renderers, and Installed Android External Music Players.

### A. Triple Playback Domain Model

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              MUSICMATE PLAYBACK ARCHITECTURE                           │
├──────────────────────────┬─────────────────────────────┬───────────────────────────────┤
│ Domain                   │ Engine & Transport          │ Queue & Control Ownership     │
├──────────────────────────┼─────────────────────────────┼───────────────────────────────┤
│ 1. Local Device          │ Internal AndroidX Media3    │ MusicMate owns queue, preloads│
│    (Speaker / USB DAC)   │ (ExoPlayer) with 32-bit     │ gapless next tracks, and      │
│                          │ Float PCM AudioSink         │ manages hardware audio focus. │
├──────────────────────────┼─────────────────────────────┼───────────────────────────────┤
│ 2. Network Streamer      │ Embedded HTTP Media Server  │ MusicMate streams URLs via    │
│    (DLNA / UPnP DMR)     │ + UPnP SOAP Control Point   │ SetAVTransportURI & preloads  │
│                          │                             │ with SetNextAVTransportURI.   │
├──────────────────────────┼─────────────────────────────┼───────────────────────────────┤
│ 3. External Music Apps   │ Android MediaSession Binder │ Companion Controller mode:    │
│    (Poweramp, UAPP,      │ IPC (play, pause, next,     │ External app owns its queue;  │
│     Neutron, HiBy, etc.) │ prev, seek)                 │ MusicMate observes metadata.  │
└──────────────────────────┴─────────────────────────────┴───────────────────────────────┘
```

---

### B. Direct Local Playback Engine (Speaker & USB DAC)

1. **Audio Engine Architecture:**
   - Driven by `ExoPlayer` configured with `C.WAKE_MODE_LOCAL`, `SeekParameters.EXACT`, and `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC`.
   - **Float 32-bit PCM (`DefaultAudioSink`):** Configured with `enableFloatOutput = true` (`ENCODING_PCM_FLOAT`), providing >1500 dB internal dynamic range to eliminate digital integer clipping prior to the Android HAL.
   - **Zero-Copy Audio Telemetry:** Injects `AudioLevelProcessor` into `DefaultAudioSink`'s processor chain to non-destructively sample peak and RMS stereo levels for the real-time Analog VU meter via `AudioTelemetryManager`.
   - **ReplayGain 2.0 / EBU R128 Leveling:** Evaluates track and album gain tags before playback and attenuates `internalExoPlayer.setVolume(gain)` with an anti-clipping true-peak limiter ($scalar \times peak \le 1.0$) to prevent inter-sample clipping on external DACs.

2. **Hardware Sink Routing Priority:**
   - `AudioOutputHelper` queries `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` and evaluates routing priorities:
     `USB DAC (Priority 4) > Bluetooth A2DP (Priority 3) > Wired Headphones (Priority 1) > Phone Speaker (Priority 0)`.
   - **Android 14+ Bit-Perfect USB Mode:** Inspects `AudioManager.getSupportedMixerAttributes(device)` to detect `MIXER_BEHAVIOR_BIT_PERFECT` matching the track's sample rate, presenting the Bit-Perfect Gold USB badge (`ic_baseline_usb_24`).
   - **Audio Becoming Noisy Guard:** Disconnection of USB DACs, wired headphones, or Bluetooth streams is intercepted by `setHandleAudioBecomingNoisy(true)`, pausing playback instantly to prevent unintentional speaker blaring.

3. **Native Gapless Transition:**
   - Preloads the upcoming song directly into the running player instance via `internalExoPlayer.addMediaItem(buildMediaItem(nextSong))`, ensuring zero buffer resets and zero DAC lock re-negotiation across consecutive tracks.

---

### C. External Player Companion Controller Pattern

1. **The Anti-Pattern of Track-by-Track File Pushing:**
   - Pushing file URLs track-by-track via `ACTION_VIEW` for every song change breaks down: it triggers Android background activity restrictions (`BackgroundActivityStartException`), forces external player windows into the foreground, wipes the external player's native playlist, and resets hardware USB DAC locks (causing audible clicks and destroying gapless playback).

2. **Companion Controller Model:**
   - **1-Time Explicit Handoff:** When a user explicitly taps a song to play in an external app, MusicMate launches `ACTION_VIEW` once with secure content URI read permissions granted via `MusicFileProvider`.
   - **MediaSession Binder IPC:** All ongoing transport commands (`skipToNext`, `skipToPrevious`, `pause`, `resume`, `seekTo`, `stopPlaying`) route strictly via `MediaController.getTransportControls()`.
   - **Passive State Synchronization:** MusicMate listens to `MediaController.Callback` (`onPlaybackStateChanged`, `onMetadataChanged`) to passively reflect track title, artist, album art, duration, and elapsed time in MusicMate's UI and notification without micro-managing the external player's queue.
   - **Target-Scoped Safety Timers:** Gapless fallback timers (`scheduleFallback()`) are restricted strictly to controllable DLNA/DMR renderers, preventing unwanted track-skip timers from firing on external apps or local ExoPlayer.

---

### D. Dual-Mode Network Streaming Architecture (DMS + DMC vs. DMS Only)

MusicMate implements a dual-mode UPnP AV / DLNA network architecture that flexibly operates either as a fully integrated controller + server (pushing audio to Wi-Fi speakers and streamers) or as a standalone media server (allowing external audiophile control points to browse and stream the library).

```
MODE A: Integrated Controller + Server (DMS + DMC)
┌──────────────────────────────────────┐          UPnP AVTransport SOAP
│      MusicMate (DMS + DMC)           │ ──(Play, Pause, SetNextAVTransportURI)──> ┌─────────────────────────┐
│  - MediaServerHubImpl (SSDP Discovery)│                                         │ Remote DLNA/UPnP        │
│  - NioWebServerImpl (HTTP Streaming) ├─── RFC 7233 Byte-Range HTTP Stream ────> │ Renderer (DMR)          │
│  - Queue & Gapless Fallback Engine   │                                         │ (Wi-Fi Hi-Fi, AVR, DAP) │
└──────────────────────────────────────┘                                         └─────────────────────────┘

MODE B: Standalone Media Server with External Controller (DMS Only)
┌──────────────────────────────────────┐          UPnP AVTransport SOAP
│ External Control Point (DMC)         │ ────────────────────────────────────────> ┌─────────────────────────┐
│ (BubbleUPnP, mconnect, WiiM, Audirvana)                                          │ Remote DLNA/UPnP        │
└──────────────────┬───────────────────┘                                          │ Renderer (DMR)          │
                   │ UPnP Browse                                                  │                         │
                   │ (ContentDirectory)                                           │                         │
                   ▼                                                              │                         │
┌──────────────────────────────────────┐                                          │                         │
│      MusicMate (DMS Only)            │                                          │                         │
│  - MediaServerDevice (SSDP Advertised)│                                         │                         │
│  - ContentDirectory Virtual Tree     │ ─── RFC 7233 Byte-Range HTTP Stream ────┘                         │
│  - onAccessMediaTrack Collision Guard│                                                                   │
└──────────────────────────────────────┘
```

1. **Mode A: Integrated Controller + Server (MusicMate = DMS + DMC):**
   - **Full Playback Ownership:** MusicMate acts simultaneously as the media source and the active control point.
   - **Device Discovery:** `MediaServerHubImpl` broadcasts SSDP M-SEARCH queries (`urn:schemas-upnp-org:device:MediaRenderer:1`) to discover network renderers on the local subnet.
   - **SOAP Transport Control:** Issues UPnP AVTransport actions (`SetAVTransportURI`, `Play`, `Pause`, `Stop`, `Seek`) to the remote DMR's control URL.
   - **Gapless Preloading (`SetNextAVTransportURI`):** Dispatches the upcoming track's HTTP stream URL to the renderer before the current track finishes, allowing hardware streamers with gapless buffers to transition smoothly without pauses.
   - **Target-Scoped Safety Timers:** Uses `scheduleFallback()` to monitor track end in case a DMR fails to fire the standard GENA `STOPPED` state transition.

2. **Mode B: Standalone Media Server with External Controller (MusicMate = DMS Only):**
   - **Server-Only Operation:** MusicMate functions as a standards-compliant DLNA Digital Media Server (DMS) advertised over SSDP via `MediaServerDevice`.
   - **External DMC Compatibility:** Third-party audiophile control points (e.g., BubbleUPnP, mconnect Player, WiiM Home, Audirvana, Linn Kazoo, Foobar2000 UPnP) discover MusicMate automatically on the Wi-Fi network.
   - **Structured `ContentDirectory` Hierarchy:** MusicMate exposes its library through a high-performance virtual tree:
     - `LibraryBrowser`: Root virtual directory.
     - `AlbumsBrowser`: Albums indexed by title/artist with high-resolution embedded album art URLs (`/cover/<songId>`).
     - `ArtistsBrowser`: Complete artist discographies.
     - `GenresBrowser`: Genre-based indexing.
     - `CollectionsBrowser`: Curated collections, smart playlists, and custom tags.
     - `SourcesBrowser`: Physical storage root folders (SD Card, Internal Storage, OTG).
   - **Direct Audio Stream Delivery:** The external controller instructs any network renderer (or local phone player) to pull HTTP audio streams directly from MusicMate's embedded server.

3. **High-Performance Embedded HTTP Streaming Engine:**
   - **Zero-Copy NIO Streaming (`NioWebServerImpl`):** Serves audio via non-blocking Java NIO channels with minimal CPU and memory overhead, even when serving large DSD (`.dsf`, `.dff`) or 24-bit/192kHz FLAC files.
   - **RFC 7233 Byte-Range Clamping (`206 Partial Content`):** Strictly enforces valid byte ranges matching actual file bounds. This allows external renderers to seek instantly without re-requesting the whole file, and fixes premature track-skip bugs common in DAP microstacks (ADR-021).
   - **Accurate Audiophile MIME Types:** Dynamically sets correct MIME headers (`audio/flac`, `audio/x-dsd`, `audio/dsf`, `audio/dff`, `audio/alac`, `audio/x-wavpack`, `audio/x-ape`) via `MimeTypeUtils`.

4. **Passive Stream Observation & Non-Collision Guard (`onAccessMediaTrack`):**
   - When external control points or renderers initiate an HTTP audio stream from MusicMate, the embedded server fires an access callback into `MusicMateServiceImpl.onAccessMediaTrack()`.
   - **Active DMR Session Collision Guard:** If MusicMate is currently actively streaming to and controlling a DMR (`activePlayer.isStreaming() && isControllable(activePlayer)`), it drops incoming stream access notifications. This ensures external library browsing or background metadata scraping cannot desynchronize, overwrite, or hijack MusicMate's active DMR queue.
   - **Passive State Telemetry when Idle:** When MusicMate is not actively controlling a DMR, incoming external HTTP stream requests update MusicMate's now-playing telemetry and status notifications passively (title, artist, album art, sample rate/format), providing live monitoring of what external clients are streaming without usurping transport controls.

---

## 3. Audio Tagging & Metadata Engine

MusicMate houses a comprehensive audio tagging subsystem designed to protect file integrity and maintain compatibility across disparate audiophile platforms:

1. **Transactional File Writes (`TagWriter` / `FileRepository`):**
   - `TagWriter.writeTag()` returns an explicit `boolean` verifying physical disk commit before updating Room database cache state.
   - Batch writes are executed sequentially on a dedicated single-thread executor (`MusicMateExecutors.getExecutorService()`) to prevent filesystem lockups on slow SD cards or OTG drives.
2. **Dual-Chunk WAV Metadata Preservation:**
   - Standard WAV files lack native tag containers. MusicMate writes both `WavInfoTag` (legacy RIFF INFO chunk) for legacy stereos and `ID3Tag` (ID3v2 chunk) for modern audiophile software.
3. **Delimiter Normalization:**
   - Multi-value tags (e.g. multiple artists) formatted with varying delimiters (`/`, `;`, `&`, `,`) are normalized cleanly to comma-separated strings (`", "`) on read, preserving legitimate band titles like `AC/DC`.
4. **Metadata Automation & Clean Pipeline:**
   - **AcoustID & MusicBrainz:** Generates acoustic fingerprints via Chromaprint to match unidentified tracks against the MusicBrainz online database.
   - **Thai Encoding Auto-Repair:** Intercepts corrupted legacy Windows-874 / TIS-620 character byte sequences and recovers legitimate Thai text.
   - **Title Casing & Junk Noise Stripping:** Strips common rip artifacts (e.g., `[FLAC 24-96]`, `www.sitename.com`, track numbers prefixed in titles) and standardizes capitalization.

---

## 4. Data Layer, Storage & Persistence

1. **Room Database Cache:**
   - `SongEntity` caches technical specs (format, sample rate, bit depth, bitrate, channels, dynamic range DR score, ReplayGain values) and metadata fields (title, artist, album, genre, year, track number).
   - Indexed columns optimize complex search queries and multi-attribute filtering across libraries containing 50,000+ tracks.
2. **Dynamic Query Builder & Audiophile Query Studio:**
   - `PlaylistEntry` supports both static playlists (`TYPE_STATIC`) and dynamic rule-based smart playlists (`TYPE_SMART`).
   - Dynamic criteria evaluate DR score thresholds (`minDrScore`), format tiers (`hiresOnly`, `dsdOnly`, `losslessOnly`), bit depth, and sample rate in real time.
   - User-defined smart playlists are serialized to `custom_playlists.json` in private app storage.
3. **Storage Access Framework (SAF) & Content Sharing:**
   - Supports Scoped Storage via SAF document tree URIs.
   - `MusicFileProvider` safely exposes content URIs (`content://apincer.android.mmate.provider/music/...`) with strict `FLAG_GRANT_READ_URI_PERMISSION` flags for external playback handoffs.

---

## 5. Architectural Decision Records (ADRs)

> **Convention:** Each ADR records **Status** (`Proposed` / `Accepted` / `Superseded by ADR-XXX`), **Date**, **Context**, **Decision**, and **Consequences**. Never delete a superseded ADR — mark it `Superseded` and link its replacement so decision history is preserved.

### ADR-007: Robust Audio Tag Read/Write & Dual-Chunk WAV Support
- **Status:** Accepted
- **Date:** 2026-08
- **Context:** Previously, tag write exceptions in `JThinkWriter` were swallowed and returned `void`, causing the Room database to update and report success even when storage writes failed. In addition, multi-value delimiters were inconsistent between reader/writer, WAV files lacked standard RIFF INFO chunks for legacy stereos, and batch I/O wasn't throttled.
- **Decision:** 
  1. `TagWriter.writeTag()` and `FileRepository.setMusicTag()` return `boolean`, updating the Room DB and displaying success only when physical disk commit succeeds.
  2. Normalize multi-value delimiters (`/`, `;`, `&`, `,`) to clean comma-separated strings (`", "`) on read, preserving band names like `AC/DC`.
  3. Support dual-chunk WAV writing: write standard `WavInfoTag` (RIFF INFO chunk) for legacy car stereos alongside `ID3Tag` (ID3v2 chunk) for modern audiophile software.
  4. Enable embedded artwork writing via `ArtworkFactory` in `JThinkWriter`.
  5. Throttle bulk save operations sequentially on `MusicMateExecutors.getExecutorService()` to avoid I/O lockup on slow micro-SD cards.
- **Consequences:** Safe and predictable file persistence, zero silent data loss, universal WAV compatibility, and resilient bulk editing.

### ADR-008: Automatic Bluetooth Audio Optimization & Real-Time Codec Telemetry
- **Status:** Accepted
- **Date:** 2026-08-14
- **Context:** Output device labels were inconsistent across surfaces: Bluetooth devices used verbose parenthesis formatting (`Sony WH-1000XM5 (Bluetooth Audio)`), while DLNA renderers used bullet formatting (`HiBy R3 • 192.168.1.50`) and Android apps used versions (`Poweramp • v935`). Bluetooth devices lacked dynamic codec detection, and the signal path target badge incorrectly fell back to `DIRECT SYSTEM OUTPUT`. Furthermore, users playing 24/96 Hi-Res files over Bluetooth had no automatic way to request LDAC / aptX HD.
- **Decision:**
  1. Adopt the compact **`{Name} • BT ({Codec})`** / **`{Name} • BT`** format for Bluetooth devices (e.g. `Sony WH-1000XM5 • BT (LDAC)`), saving horizontal space and keeping brand names visible on mobile displays.
  2. Implement `AudioOutputHelper.getCompactLabel()` as the single source of truth for both `MainActivity` (player dropdown) and `AudioHubBottomSheet` (Playback tab header & route widget).
  3. Implement real-time Bluetooth A2DP codec detection (`LDAC`, `aptX HD`, `aptX`, `AAC`, `LC3`, `SBC`, `Opus`, `SSC`) via reflection on `BluetoothCodecStatus`, promoting resolution to 24-bit / 96 kHz for hi-res codecs.
  4. Implement **Automatic Background Codec Optimization** (`AudioOutputHelper.autoOptimizeBluetoothCodec`): MusicMate silently requests highest codec priority (`LDAC 24/96` or `aptX HD`) upon Bluetooth connection without requiring manual dialog button presses.
  5. Tapping the Step 3 output card in the Music Center directly opens Android's native Media Output panel or Bluetooth settings with zero modal dialog friction.
  6. Standardize output type names to Title Case (`"Bluetooth Audio"`, `"USB DAC"`, `"Wired Headphones"`, `"Phone Speaker"`).
  7. Fix Audio Route Path Step 3 target badge to correctly display `BLUETOOTH A2DP` and `Active Bluetooth A2DP Wireless Stream`.
- **Consequences:** Harmonized naming across all playback renderers, optimal single-line fit on high-DPI smartphone displays, rich audiophile telemetry, and a zero-friction, automatic path to the highest Bluetooth audio quality.

### ADR-010: Build System Hygiene & Dependency Modularization
- **Status:** Accepted
- **Date:** 2026-08-14
- **Context:** Over successive releases, `gradle/libs.versions.toml` accumulated over 60 lines of dead/commented-out dependencies (Jackson, RxJava, Guava, Skydoves, old Cling/UPnP forks, unreferenced Jetty/HttpCore54 entries), `settings.gradle` contained dozens of commented module includes, and `core/build.gradle` contained duplicate dependency declarations. Local library modules (`androidtagview`, `crashreporter`) also carried unused transitive dependencies.
- **Decision:**
  1. Restructure `libs.versions.toml` into clean domain groups (*SDK & Toolchain*, *Core Architecture*, *Media & Playback*, *UI & Utilities*, *Networking & UPnP*, *Testing*) with 100% active dependencies and zero commented bloat.
  2. Clean `settings.gradle` to only include active project modules.
  3. Prune unused transitive dependencies from local submodules (`androidtagview`, `crashreporter`) and remove duplicate dependencies in `core/build.gradle`.
- **Consequences:** Cleaner dependency tree, faster compilation times, lower memory footprint during builds, and straightforward dependency auditing.

### ADR-018: Visual "Audiophile Query Studio" & Dynamic Rule-Based Smart Playlists
- **Status:** Accepted
- **Date:** 2026-09-10
- **Context:** Audiophiles require instant access to specific tiers of their music library (e.g. uncompressed high-dynamic-range masters, pure DSD archives, 24-bit studio releases). Static M3U playlists required manual curation and quickly became stale as new music was imported.
- **Decision:**
  1. Extend `PlaylistEntry.java` with `TYPE_SMART = "smart"` and criteria fields (`minDrScore`, `hiresOnly`, `dsdOnly`, `losslessOnly`, `minBitDepth`, `minSampleRate`).
  2. Register 4 built-in flagship audiophile smart playlists (*Audiophile Sanctuary DR12+*, *Studio Masters Hi-Res*, *Pure DSD Archive*, *Lossless Master Vault*).
  3. Implement `CreateSmartPlaylistDialog.kt` (Audiophile Query Studio) in Jetpack Compose:
     - Real-time library match telemetry (`⚡ Live Match: X tracks • Y GB`).
     - DR score threshold slider ($0\dots 16$).
     - Quality tier chips (`Hi-Res`, `Lossless`, `DSD`, `24-bit Studio`).
  4. Persist custom smart playlists to `custom_playlists.json` in app storage and expose across the Native UI, DLNA Media Server, and Web Remote UI.
- **Consequences:** Dynamic, auto-updating audiophile collections with zero manual playlist maintenance and live query feedback.

### ADR-020: Active ReplayGain 2.0 / EBU R128 Playback Leveling Engine with Anti-Clipping True-Peak Guard
- **Status:** Accepted
- **Date:** 2026-09-10
- **Context:** Audio tracks in large libraries vary widely in mastering loudness, causing abrupt volume jumps between tracks. Furthermore, applying digital gain without peak limiting causes digital clipping distortion.
- **Decision:**
  1. Implement `ReplayGainManager.java` in `:core` to parse, cache, and compute loudness volume scaling ($10^{\frac{\text{gainDb} + \text{preAmpDb}}{20}}$) across Vorbis Comments, ID3v2 TXXX, and MP4 tags.
  2. Integrate real-time ExoPlayer volume scaling in `AndroidPlayerController.java` during `play()` and gapless `onMediaItemTransition()`.
  3. Anti-Clipping True-Peak Limiter: Dynamically clamp gain scalars ($scalar \times peak \le 1.0$) to guarantee zero digital overs.
  4. User Preferences: Added configurable mode (`Track Gain`, `Album Gain`, `Off`), pre-amp slider ($-12\text{ dB} \dots +12\text{ dB}$), and limiter toggle in `SettingsScreen.kt`.
  5. Universal Tag Interoperability: Writes standard Vorbis Comments, ID3v2 TXXX, and MP4 tags to audio files on disk, ensuring full compatibility with Poweramp, UAPP, Foobar2000, and Neutron.
- **Consequences:** Consistent listening loudness across disparate masterings, zero clipping distortion, and complete cross-application metadata parity.

### ADR-021: HTTP Streaming Reliability, RFC 7233 Range Clamping & DLNA Completion Latching
- **Status:** Accepted
- **Date:** 2026-09-10
- **Context:**
  1. Audio tracks streaming to DLNA renderers were skipping prematurely at ~58 seconds due to in-memory 4MB buffer splicing in `PartialFileProducer.java`.
  2. Unbounded range requests (`Range: bytes=0-2147483647`) sent by modern renderers were not clamped, reporting 2GB Content-Length and causing stream truncation errors.
  3. DIDL-Lite metadata passed raw seconds to a millisecond formatter, reporting sub-second durations (`0:00:00.238`).
  4. Track completion polling in `MediaServerHubImpl` lacked completion latching, allowing recurring polling loops and duplicate GENA `STOPPED` event bursts to skip two songs at once.
- **Decision:**
  1. Re-architect `PartialFileProducer.java` to stream directly from `FileChannel` in 64KB chunks without fragile in-memory byte splicing.
  2. Implement strict RFC 7233 range parsing in `HttpCoreWebServerImpl`: clamp `end = Math.min(end, fileLength - 1)`, return HTTP `416 Range Not Satisfiable` for out-of-bound offsets, parse suffix ranges (`bytes=-500`), and discard entity bodies on `HEAD` requests.
  3. Correct DIDL-Lite duration scaling: `song.getAudioDuration() * 1000.0`.
  4. Latch `isUserInitiatedStop = true;` and call `stopPolling()` immediately upon natural track completion and `STOPPED` GENA notifications to prevent double-skipping.
  5. Replace `AudioStreamCacheManager` 16MB heap cache with direct buffer OS page cache pre-warming (`ByteBuffer.allocateDirect(64 * 1024)`).
  6. Add animation idling in `AnalogVUMeter.kt` to break `withFrameNanos` loops once needles settle to rest at 0 while playback is paused.
- **Consequences:** Bit-perfect, uninterrupted track streaming across all DLNA/UPnP renderers, zero phantom heap memory waste, and zero battery drain while paused.

### ADR-026: Multi-Target Playback Architecture, Companion Controller Pattern & Bit-Perfect USB Pipeline
- **Status:** Accepted
- **Date:** 2026-09-21
- **Context:**
  1. Integrating with external Android music players (Poweramp, USB Audio Player PRO, Neutron, HiBy Music, Foobar2000) by pushing file URLs (`ACTION_VIEW`) track-by-track on queue progression suffered from severe defects:
     - Android 10+ background activity start restrictions (`BackgroundActivityStartException`) caused silent failures when the screen was off.
     - Launching `ACTION_VIEW` stole window focus, repeatedly popping external players to the foreground on every song change.
     - Audiophile players with direct USB DAC hardware locks (e.g. UAPP, Neutron) re-initialized audio endpoints upon receiving intents, causing audible DAC clicks/pops and destroying gapless playback.
     - Pushing single file URLs wiped or collided with the external player's native playlist.
  2. In `MusicFileProvider.java`, `query()` threw `UnsupportedOperationException` when called with `projection == null`, causing external players to fail when inspecting shared content URIs. `getType()` returned generic `application/octet-stream` for audiophile formats (`.flac`, `.dsf`, `.dff`, `.ape`, `.wv`).
  3. `seekTo()` and fallback timers mistakenly routed non-streaming external player targets to UPnP SOAP calls (`mediaHub.playerSeek`), causing IPC crashes.
  4. Local device playback required clear architectural separation and documentation between phone speaker routing and bit-perfect USB DAC output.
- **Decision:**
  1. **Triple Playback Domain Architecture:**
     - **Local Device (Internal ExoPlayer):** Primary engine with 32-bit Float PCM output (`ENCODING_PCM_FLOAT`), zero-copy stereo VU meter telemetry (`AudioLevelProcessor`), ReplayGain 2.0 leveling, and native gapless preloading.
     - **Network Streamer (DLNA/UPnP Dual Modes):** MusicMate operates both as an integrated controller + server (Mode A: DMS + DMC with `SetNextAVTransportURI` gapless preloading and safety fallback timers) and as a standalone media server (Mode B: DMS Only browsable by external controllers like BubbleUPnP, mconnect, and WiiM via `ContentDirectory` with RFC 7233 byte-range HTTP streaming and passive `onAccessMediaTrack` collision guards).
     - **External Music Apps (Companion Controller Pattern):** MusicMate acts as a remote companion controller via Android `MediaSession` Binder IPC (`MediaController.getTransportControls()`), observing metadata and state passively.
  2. **One-Time Explicit Handoff vs. IPC Control:**
     - URL dispatch via `ACTION_VIEW` is restricted exclusively to explicit 1-time user handoff (e.g. tapping "Play with [Player]").
     - All ongoing playback controls (`skipToNext`, `skipToPrevious`, `pause`, `resume`, `seekTo`, `stopPlaying`) route strictly through `MediaController.getTransportControls()`.
     - Removed track-end heuristics that forced `ACTION_VIEW` intents, allowing external players to manage their own native audio loops without interruption.
  3. **ContentProvider Robustness:**
     - Rewrote `MusicFileProvider.query()` to support `projection == null` using standard `OpenableColumns` (`_display_name`, `_size`, `_data`) and preserved requested column ordering.
     - Updated `getType()` to delegate to `MimeTypeUtils.getMimeTypeFromPath()` for accurate audiophile MIME types and uppercase extension handling.
  4. **Target-Scoped Safety Timers:**
     - Gated `scheduleFallback()` to run strictly on controllable streaming DLNA renderers (`activePlayer.isStreaming() && isControllable(activePlayer)`), bypassing Local ExoPlayer and external apps.
  5. **Direct Hardware Audio Routing & Bit-Perfect Inspection:**
     - Documented hardware routing priority (`USB DAC (4) > Bluetooth A2DP (3) > Wired Headphones (1) > Speaker (0)`).
     - Standardized Android 14+ `AudioMixerAttributes` bit-perfect USB sink detection.
- **Consequences:** Eliminates foreground window theft, guarantees 100% background reliability with screen off, preserves audiophile USB DAC hardware locks and gapless playback, prevents queue collisions, and establishes a robust, extensible multi-target playback architecture.

### ADR-029: Stable Library Identity, Safe Reconciliation & Bounded Paging
- **Status:** Accepted
- **Date:** 2026-09-24
- **Context:** Full library rescans previously risked replacing database identities that queues and other references rely on. A disconnected removable volume could also look like an empty library and trigger destructive pruning. Large library queries needed bounded result sets without allowing late responses from an older search to replace newer results. File move/import and conversion flows also needed explicit collision-safe destinations.
- **Decision:**
  1. Preserve each track's database ID during metadata refresh and full rescans. Reconcile rather than wholesale-delete records so persisted queue references remain valid.
  2. Prune a missing track only when its parent directory is available and readable and confirms the file is absent. Treat unavailable storage as unknown; retain its records and queue entries.
  3. Load library results in pages of 500. Cancel or reject responses from obsolete query generations, and expose loading and retry states as pages are requested.
  4. Never overwrite an existing destination during move or import. Select a unique conversion output path and persist that actual path; commit the database path before attempting ancillary copies.
- **Consequences:** Rescans preserve identity and playback continuity, transient storage loss cannot erase library state, large libraries load incrementally with consistent search results, and file operations avoid silent replacement or path divergence.

---

### Cross-Reference: UI & Interaction Decision Records
The following Architectural Decision Records govern visual design, gestures, dialogs, and Obsidian-Glass layouts. Full details can be found in [`UI.md`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#8-ui-architectural-decision-records-adrs):

* [`ADR-001`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-001-unconditional-single-tap-to-tag-editor): Unconditional Single-Tap to Tag Editor *(Superseded by ADR-016)*
* [`ADR-002`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-002-decoupled-menu-definitions): Decoupled Menu Definitions
* [`ADR-003`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-003-auto-discovery--divided-player-picker): Auto-Discovery & Divided Player Picker
* [`ADR-004`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-004-3-tab-audiohubbottomsheet): 3-Tab AudioHubBottomSheet
* [`ADR-005`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-005-unified-floating-dock): Unified Floating Dock
* [`ADR-006`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-006-standardized-action-dialog-controls--dual-dismiss-affordance): Standardized Action Dialog Controls & Dual Dismiss Affordance
* [`ADR-009`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-009-song-detail--tag-editor-tagsactivity-viewport-hierarchy--metadata-deduplication): Song Detail / Tag Editor (`TagsActivity`) Viewport Hierarchy & Metadata Deduplication
* [`ADR-011`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-011-floating-bottom-dock-layout-hierarchy--music-center-compose-architecture): Floating Bottom Dock Layout Hierarchy & Music Center Compose Architecture
* [`ADR-012`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-012-flagship-audiophile-provenance-hierarchy-tactile-micro-haptics--pure-compose-bottom-sheet): Flagship Audiophile Provenance Hierarchy, Tactile Micro-Haptics & Pure Compose Bottom Sheet
* [`ADR-013`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-013-tag-activity-accessible-2-row-command-bar-architecture--pro-curation-workflows): Tag Activity Accessible 2-Row Command Bar Architecture & Pro Curation Workflows
* [`ADR-014`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-014-high-end-studio-provenance-capsules--in-place-related-tracks-sheet): High-End Studio Provenance Capsules & In-Place Related Tracks Sheet
* [`ADR-015`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-015-elimination-of-on-screen-volume-slider-for-audiophile-bit-perfect-clarity--ergonomic-focus): Elimination of On-Screen Volume Slider for Audiophile Bit-Perfect Clarity & Ergonomic Focus
* [`ADR-016`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-016-dual-persona-curator-vs-listener-interaction-architecture): Dual Persona "Curator vs. Listener" Interaction Architecture
* [`ADR-017`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-017-responsive-badge-synthesis--screen-density--390dp): Responsive Badge Synthesis & Screen Density (< 390dp)
* [`ADR-019`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-019-vintage-analog-needle-vu-meter--ballistic-studio-telemetry-in-audio-anatomy): Vintage Analog Needle VU Meter & Ballistic Studio Telemetry in Audio Anatomy
* [`ADR-022`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-022-dual-mode-audio-telemetry-real-time-pcm-stereo-vu-meter--vintage-reel-to-reel-tape-deck): Dual-Mode Audio Telemetry: Real-Time PCM Stereo VU Meter & Vintage Reel-to-Reel Tape Deck
* [`ADR-023`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-023-fullscreen-landscape-studio-console-hi-fi-desk-mode): Fullscreen Landscape Studio Console ("Hi-Fi Desk Mode")
* [`ADR-024`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-024-fluid-audiophile-glass-pill-tab-switcher--full-surface-card-flip): Fluid Audiophile Glass Pill Tab Switcher & Full-Surface Card Flip
* [`ADR-025`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md#adr-025-tag-editor--auxiliary-dialog-modernization-to-obsidian-glass-design-system): Tag Editor & Auxiliary Dialog Modernization to Obsidian Glass Design System

---

## 6. Non-Goals

Deliberately out of scope to protect the core architecture and tag-management focus:

- **No playlist editing in the single-track popup** — queueing beyond `Play Now` / `Play Next` / `Add to Queue` belongs in the Queue tab.
- **No playback controls in Multi-Select Action Mode** — batch mode is for tag and file operations only.
- **No destructive actions (`Delete`, `Move`) in the single-track popup** — reserved for batch mode with mandatory preview dialogs.
- **No full music-player feature parity** — MusicMate delegates rich playback UX to external players/renderers; the built-in transport is intentionally minimal.
- **No cloud sync / streaming-service integration** — the library model is strictly local & LAN (DLNA/UPnP) only.
