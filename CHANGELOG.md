# CHANGELOG

All notable changes to the **MusicMate** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

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
