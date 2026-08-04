# CHANGELOG

All notable changes to the **MusicMate** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [3.18.10] - 2026-08-04

### Fixed
- **Lossless Quality & Audio Authenticity Engine**:
  - **Quality Badge Color Reset**: Fixed default `upscaledScore` and `resampledScore` in `IconProviders.java` returning `1`, which previously forced quality badges to display red (`quality_scale_not_matched`) for all tracks.
  - **AIFF IEEE 80-Bit Extended Precision Sample Rate Parser**: Fixed IEEE 754 80-bit float parsing in `AudioDecoder.java` to compute mantissa and exponent, correctly resolving AIFF sample rates (e.g., 44.1 kHz).
  - **Fake Hi-Res & Upscaled Audio Verification**: Updated `AudioAuthenticityAnalyzer.java` to detect tracks with sample rates > 48 kHz but spectral rolloff ≤ 24 kHz or rolloff ratio < 0.75, accurately classifying upsampled CD content as `Upscaled Content`.
  - **PCM Bit Depth Decoding Fallback**: Updated `AudioDecoder.getBitsPerSample` to safely default to 16-bit PCM rather than throwing unhandled `IllegalArgumentException` on unexpected encodings.
- **TripMate Module Exception Handling**:
  - Replaced over 200 silent empty catch blocks (`// TODO: handle exception`) and method stubs across `TripMate` activities and parsers with explicit error and warning logging (`Log.e` / `Log.w`).

---

## [3.18.9] - 2026-08-03

### Added
- **Audio Converter Sample Rate Downsampling**:
  - Added ExposedDropdownMenu for target sample rate selection in Convert Files dialog (`Original`, `96 kHz`, `48 kHz`, `44.1 kHz`).
  - Overloaded `FFMpegHelper.convert` and `FileOperationTask.encodeFiles` to support `-ar <sampleRate>` downsampling flag.

### Fixed
- **FFmpeg 24-Bit Encoding Error**:
  - Fixed invalid `-sample_fmt s24` flag by replacing it with valid `-sample_fmt s32` for 24-bit FLAC/ALAC audio conversion.
- **Converted Track Metadata Inheritance & Instant Library Indexing**:
  - Updated `FileOperationTask.java` to copy track metadata (`Title`, `Artist`, `Album`, `AlbumArtist`, `Genre`, `Track`, `Year`, `Comment`, `Composer`, `Publisher`) from source track to newly converted track.
  - Automatically writes metadata tags to disk via `TagWriter.writeTagToFile` and updates database, ensuring converted files (`_001.flac`) immediately match active Artist/Album view filters upon completion.
- **Selection State & Playback Interruption**:
  - Prevented selection mode from clearing during playback status updates or background dataset refreshes by guarding `isListFollowNowPlaying` / `scrollToSong()` with `(actionMode == null)` and preserving `SelectionTracker` selection keys.
- **Action Dialog List View Layout & Icons**:
  - Redesigned `view_action_listview_item.xml` layout to use a responsive `LinearLayout` (`layout_weight="1"` for track name with `ellipsize="end"`), fixing collapsed/hidden song names.
  - Added `getTrackDisplayName()` fallback helper (`getSimpleName()` -> `getTitle()` -> `FileUtils.getFileName()`).
  - Synchronized dialog header icons with context menu action bar icons for **Move Songs** (`rounded_drive_file_move_24`) and **Convert Files** (`rounded_swap_horiz_24`).

## [3.18.8] - 2026-08-02

### Added
- **Split-Tap Zone on Song List Items**:
  - Tapping the **Song Title / Details** plays the track immediately.
  - Tapping the **Album Cover Art Thumbnail** directly opens **`TagsActivity`** (Metadata Editor) for fast 1-tap tag editing.
- **Now Playing & Queue Bottom Sheet (`NowPlayingQueueSheet`)**:
  - Implemented modern bottom sheet showing Now Playing album art, track details, technical format badge (e.g. `FLAC 352.8 kHz / 24bit`), active player badge, and scrollable queue list with current track gold highlighting.
  - Added embedded **Previous**, **Play / Pause**, and **Next** transport controls inside the Now Playing card.
  - Added **Play All** button in queue header to instantly enqueue all currently displayed library songs into the playback queue and begin playback.
  - Added **Shuffle Toggle (🔀)** and **Repeat Mode Toggle (🔁)** controls.
  - Added dedicated gold **Signal Path icon button** (`ic_baseline_audio_path_24`) in sheet header.
  - Added **Tap-to-Scroll** list navigation: tapping the track card or any queue row dismisses the sheet and scrolls the main library list to that song's position.
- **Top Header & Selection Mode Icon Audit & Alignment**:
  - Replaced top header back button icon with standard left navigation arrow (`@drawable/ic_baseline_arrow_back_24`).
  - Aligned selection mode contextual action bar icons: **Play Now** (`▶`), **Add to Queue** (`≡+`), and **Song Info** (`📝`).
- **Top-Anchored Quick Player Picker (`showPlayerPickerPopup`)**:
  - Introduced a top-right anchored popup menu triggered by the header cast button for fast 1-tap renderer switching right beneath the tap target.
- **Dynamic Media Server Status Tint**:
  - Bound `navigation_media_server` icon in bottom navigation bar to `MediaServerManager.getServerStatus()` LiveData to dynamically display active running status (Teal tint when server is active, Muted tint when offline).

### Improved
- **Unified Floating Navigation & Playback Dock**:
  - Combined the floating playback bar and bottom navigation into a single, high-efficiency floating dock (`20dp` radius card).
  - **Idle State**: Displays Library icon, default app title, dynamic Media Server status icon, and Menu.
  - **Playback Active State**: Seamlessly expands to show mini album art, marquee track title, player target subtitle (e.g. `HiBy R3 • DLNA Renderer`), and clean gesture access.
  - **Gestures**: Tapping track title or artwork opens `NowPlayingQueueSheet`; long-pressing opens `SignalPathBottomSheet`.
  - Maximizes vertical list viewable area and eliminates double bar UI overlap.
- **Single Web Server Engine Build**:
  - Consolidated legacy Gradle `server` build flavor dimension into `src/main` with **HttpCore** as default engine. All 3 engines (`HttpCore`, `NIO`, `Netty`) remain dynamically switchable from App Settings in a single APK build.
- **Streamlined Navigation & Signal Path Access**:
  - Integrated direct transition from `NowPlayingQueueSheet` (via header icon button) to `SignalPathBottomSheet`.
  - Added long-press shortcut on Floating Playback Bar to quickly open `SignalPathBottomSheet` for audiophiles and power users.
  - Cleaned up bottom navigation bar by removing duplicate signal path label triggers.

### Added
- **Unified Player Target Display Formatting**:
  - Added centralized 2-line and 1-line player label formatters in `PlayerNameUtils`.
  - Display standardized titles and sub-metadata across Media Server Management Sheet, player selection dropdowns, and Signal Path Bottom Sheet.
  - Distinct player target type labels: **DLNA Renderer** (e.g. `HiBy R3 (192.168.1.50 • DLNA Renderer)`), **Web Streaming**, and **Android App** (e.g. `Poweramp (com.maxmpz.audioplayer • Android App)`).
- **Runtime Server Engine Selector (App Settings)**:
  - Implemented `CompositeWebServer` proxy to allow users to switch HTTP streaming web server engines at runtime (**SonicNIO**, **CoreHTTP**, or **Netty**) directly from App Settings without rebuilding or restarting the application process.
- **Full DLNA Controller (DMC) Transport Actions**:
  - Implemented `playerPause()`, `playerSeek()`, and `playerSetVolume()` in `MediaServerHubImpl` to provide full control over remote DLNA renderers (`AVTransport` + `RenderingControl` UPnP services).
- **IP Address Sanitization Utility**:
  - Added `NetworkUtils.extractIpAddress()` to extract clean IPv4 addresses by stripping schemes, ports, leading slashes, and paths.

### Improved
- **Ultra High-Res (352.8 kHz / DXD) Streaming Optimizations**:
  - Increased file streaming chunk size from 64 KB to **256 KB** in both `SonicNIO` (`NioHttpServer`) and `HttpCore` (`HttpCoreWebServerImpl`).
  - Expanded TCP socket send buffers (`SO_SNDBUF`) to **512 KB** to prevent Wi-Fi bandwidth stalls during high-rate (>10 Mbps) FLAC transfers to low-power DAPs (e.g. HiBy R3).

### Fixed
- **DLNA Renderer Name Resolution**:
  - Fixed issue where incoming HTTP stream requests for discovered DLNA Renderers (like HiBy R3) were fallback-named as generic `"Streaming Player"` or `"Mozilla"`.
  - Added automatic resolution in `MusicMateServiceImpl` to map incoming HTTP stream IP addresses to registered UPnP DMR device friendly names.
- **HiBy User-Agent Parsing**:
  - Added explicit HiBy player detection in `PlayerNameUtils.getFriendlyNameFromUserAgent()`.
  - Filtered out generic `"Mozilla"` fallback labels for embedded DAP web browser user agents.
- **DLNA Renderer Disappears After Device Reformat / Factory Reset**:
  - Fixed issue where a DLNA device (e.g. HiBy M3) would no longer appear in the player picker after being reformatted or factory reset (new UDN / new IP address assigned).
  - Added `refreshDiscovery()` to `MediaServerHub` SPI and `MediaServerHubImpl` (triggers immediate UPnP SSDP M-SEARCH).
  - Exposed `refreshPlayerDiscovery()` in `PlaybackService` interface and implemented in `MusicMateServiceImpl`.
  - Added **"🔄 Rescan for players"** action at the bottom of the Cast / Player Picker popup — triggers an immediate rescan and automatically reopens the picker after 2.5 s showing freshly discovered devices.

---

## [2026.06.0] - 2026-06-15

### Added
- **Network Resilience Layer**:
  - Automatic media server pause/resume on Wi-Fi state loss and recovery (`ConnectivityManager.NetworkCallback`).
  - Hotspot mode auto-binding (`WIFI_AP_STATE_CHANGED`).
  - Wake locks, Wi-Fi locks, and Multicast locks for continuous background streaming.

---

## [2026.05.0] - 2026-05-31

### Improved
- **CoreHTTP Engine Production-Grade Optimization**:
  - Memory footprint reduction to ~64 KB per connection using object pooling and `FileChannel.transferTo()` zero-copy streaming.
  - Enhanced WebSocket handling and GC pause optimizations (<30 ms).
