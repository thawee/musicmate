# Music Mate

**Music Mate** is a comprehensive High-Resolution Audio management, playback, and streaming application for Android. Built around **Music Center** — a unified audiophile control hub for real-time **Audio Route Path** telemetry, queue management, and target output control — it transforms your mobile device into a high-performance **DLNA/UPnP Media Server** to stream your local high-fidelity music collection to any compatible renderer (Hi-Fi streamers, Smart TVs, AV Receivers) or play it locally with bit-perfect quality.

---

## 🗺 Documentation Map

*   🌐 **[Ecosystem Overview](ECOSYSTEM.md)** - How the Core Server, Players, and Controllers fit together.
*   🎛️ **[Playback Architecture](PLAYBACK_ARCHITECTURE.md)** - How playback control, queue, Android app control, and DLNA control are integrated.
*   🔌 **[WebSocket API](WEBSOCKET_API.md)** - Technical specification for real-time remote control.
*   🖥️ **[Web UI & Server Architecture](WEBUI.md)** - Deep dive into the remote interface and pluggable HTTP engines.
*   🎼 **[Music Quality Guide](MUSIC_QUALITY_GUIDE.md)** - A reference for understanding Bit Depth and Dynamic Range.
*   🛠️ **[Contributing Guide](CONTRIBUTING.md)** - Developer setup, build instructions, and architecture overview.
*   🏗️ **[System Architecture & Design](DESIGN.md)** - Technical topology, audio engine pipelines, multi-target playback routing, and system ADRs.
*   🎨 **[UI/UX Design System & Guidelines](UI.md)** - Interaction models, gestures, menus, Obsidian-Glass theming, and UI decision records.
*   📜 **[Changelog](CHANGELOG.md)** - Detailed history of changes and release updates.

---

## 🚀 Key Features

### 🎛️ Music Center & Playback Hub
*   **Dedicated 3-Tab Architecture (`AudioHubSheet.kt`):** Consolidated master bottom sheet with 3 full-height segmented tabs (**`Playback`**, **`Queue`**, and **`Server`**) for instant 1-tap switching between Now Playing artwork, full-height Queue management, and Media Server status.
*   **Audio Route Path Telemetry:** Live 3-stage audiophile flow visualization (`Source File` ➔ `Transport Route` ➔ `Target Output`), displaying real-time sample rates, bit depth, transport mode (`MusicMate Server` vs `Local`), and bit-perfect flags.
*   **Target Player Selector:** Fast top-anchored output target picker for seamless 1-tap renderer switching between local Android apps, DLNA/UPnP streamers, and web browser clients.
*   **Unified Floating Dock:** Streamlined 20dp radius floating bar combining mini-player marquee playback controls with main library navigation. Its artwork and title open Music Center by touch or screen-reader action.

### 📶 Bluetooth Audio Playback Suite
*   **Live Bluetooth Codec & Device Telemetry:** Detects active Bluetooth A2DP & BLE codecs (**LDAC**, **aptX**, **AAC**, **SBC**) and displays the exact Bluetooth device product name (e.g. `Sony WH-1000XM5`, `Bose QC45`) in the Audio Route Path.
*   **1-Tap System Output Switcher:** Instant access to native Android Media Output panel (`Bluetooth / System Output...`) directly inside the target player selector.
*   **Auto-Pause on Disconnect:** Automatically pauses playback when wireless headphones, Bluetooth DACs, or car receivers disconnect (`ACTION_AUDIO_BECOMING_NOISY`), preventing accidental loudspeaker playback.

### 🎧 High-Res Audio & Authenticity Analysis
*   **MQA & Format Detection:** Automatically identifies Master Quality Authenticated (MQA) tracks and native DXD/DSD streams.
*   **Audio Authenticity Analyzer:** Spectral rolloff analysis detects up-sampled CD content and fake Hi-Res files.
*   **Dynamic Range (DR) & Quality Grading:** Evaluates loudness compression and bit-depth authenticity.
*   **Broad Format Support:** Native decoding for FLAC, WAV, AIFF, ALAC, AAC, MP3, and DSD (DoP).

### 📡 Advanced Media Server
*   **DLNA/UPnP 1.0 Compliance:** Full Digital Media Server (DMS) implementation compatible with UPnP control points (mConnect, BubbleUPnP, RoPieeeXL, JPlay).
*   **Bit-Perfect Streaming:** Delivers unmodified, bit-perfect audio streams without transcoding or resampling.
*   **HTTP/1.1 Optimization:** Range request support for efficient seeking; ETag caching for reduced bandwidth (99%+ savings on cache hits).
*   **WebSocket Real-Time Control:** RFC 6455 compliant WebSocket server for live UI updates and player status synchronization.
*   **Rich Metadata:** Serves extensive metadata including Album Art, Artist, Genre, and technical details.
*   **Explicit Server Control:** Stopping the server stays in effect across main-screen recreation and reopening until you tap Start again; choosing a streaming target can start it when needed.

### 📂 Library Management & Fast Touch Workflows
*   **Split-Tap List Navigation:** Tapping a song row opens its metadata tag editor (`TagsActivity`); tapping album artwork quick-plays the track when a player is available.
*   **Obsidian-Glass Tag Studio (`TagsActivity`):** Fluid Audiophile Glass Pill switcher for seamless 1:1 finger tracking between Song Info and Technical Info, online tag matching via MusicBrainz in pure Jetpack Compose dialogs with Coil 3 cover art loading, and lossless spectrum analysis.
*   **Collection Quick Actions:** Direct **Play** and **Add to Queue** action icons on artist, genre, and folder cards.
*   **High-Performance Indexing:** Parallel metadata parsing powered by `jaudiotagger` and Google's `Room` database.
*   **Paged Collections & Recoverable Edits:** Large folders and playlists scroll without repeating items. Online tag matches become unsaved drafts until you Save; Back warns before discarding them.

---

## 🏗 System Architecture

Music Mate follows a modular Clean Architecture, implementing a full DLNA stack on Android.

### DLNA Media Server Components
*   **UPnP Framework:** Core addressing, device discovery, and SOAP eventing.
*   **Content Directory Service (CDS):** Handles library browsing with integrated Digital Content Decoding and Profiling.
*   **Connection Manager Service:** Manages active streaming sessions.
*   **HTTP Streamer:** The high-performance engine delivering digital content to clients.
*   *Note: AV Transport Service is intentionally not implemented to maintain focus on bit-perfect audio delivery and specialized renderer compatibility.*

### Server Topology

    Music Mate DLNA Server
    │
    ├── UPnP Server (Port 49152)
    │     Purpose: Device discovery and media control.
    │
    └── Web Server (Port 9000)
          ├── /music/*      → Audio streaming endpoint
          ├── /coverart/*   → Album artwork
          ├── /ws/*         → WebSocket control API
          └── /*            → MusicMate Web Remote (Embedded UI)

---

## 📐 Technical Architecture & Implementation

Music Mate employs a sophisticated **pluggable architecture** that decouples business logic from the network transport, allowing runtime selection of the optimal HTTP engine.

### The Pluggable Server Engine

A single APK ships all engines. `CompositeWebServer` reflects the engine named in `Constants.PREF_SERVER_ENGINE` (`preference_media_server_engine`) and delegates `initServer` / `stopServer` / `restartServer` to it, so engines can be hot-swapped at runtime without restarting the app.

All engines extend `BaseServer` and implement the `WebServer` SPI (`core/.../server/spi/WebServer.java`), inheriting shared behavior from `BaseServer`:
- **Dynamic ETags** for efficient caching (SHA-256 of `path + length + lastModified`, truncated to 16 hex chars plus the hex file length).
- **HTTP/1.1 Compliance** with Range request support, conditional validation, and Keep-Alive optimization.
- **Audiophile Headers** for renderer metadata — see the per-engine table below, as coverage differs by engine.

> **Maintenance policy:** Two engines are built and maintained: SonicNIO (default) and Netty.
> CoreHTTP (Apache HttpCore 5.5) and the unbuilt Jetty 12, Undertow and HttpCore 5.4 modules were removed on 2026-10-01 (ADR-035).

> **Default engine:** `nio` (SonicNIO), from `Constants.DEFAULT_SERVER_ENGINE`. Every code path that reads the preference (`CompositeWebServer`, `MainActivity`, `SettingsActivity`, `MediaServerState`) uses it when the preference is unset, and an unknown key also resolves to SonicNIO. A saved `httpcore` choice from earlier versions is migrated to `nio` at startup.

---

### ✅ Actively Maintained Engines

Both are listed in `settings.gradle` and built into the shipping APK.

#### 🚀 SonicNIO (`server-jupnp` / engine key `nio`) — *Default · Balanced*
*   **Status:** **Production Grade — the default engine.** Also serves UPnP control (SOAP/GENA) regardless of the selected engine.
*   **Architecture:** Custom-built, zero-dependency Reactor-pattern NIO engine optimised for Android (`NioHttpServer`, single selector + worker pool).
*   **Strengths:** No object pooling (plain allocations; audio never enters the heap thanks to `transferTo`), 256 KB streaming chunks, intelligent LruCache for ETags and client profiles, `IP_TOS = 0x18` (DSCP Low Delay | High Throughput), 512 KB `SO_SNDBUF`.
*   **Headers:** DLNA `transferMode.dlna.org` / `contentFeatures.dlna.org` plus the full `X-Audio-*` set from `DLNAHeaderHelper.getAudioHeaders()`, shared with Netty.
*   **Threading:** The selector thread owns all connections; workers hand back responses and queue closes, and each WebSocket connection's messages are handled in order (ADR-036).
*   **Tests:** `NioHttpServerTest` (28 tests) drives a real socket: full and partial GETs (suffix, open-ended, clamped, 416), invalid and multi-range requests (200), `If-Range`, HEAD, keep-alive, `Connection: close`, POST bodies split across packets, chunked bodies (501), request-pool integrity after disconnects, stream eviction, stop/teardown, and WebSocket handshake, ordering, close and oversized frames. `RateLimitingHandlerTest` covers the limit and the cover-art exemption.

#### ✅ Netty (`server-jupnp-netty` / engine key `netty`) — *High Throughput*
*   **Status:** **Production Grade — Best for high-concurrency / scalability.**
*   **Strengths:** Netty 4.2.18 event-loop model (1 boss / 2 worker + a 4-thread logic executor), zero-copy `DefaultFileRegion` (with `ChunkedFile` fallback), 256 KB low / 512 KB high write-buffer watermarks, `IP_TOS = 0x18` low-jitter transport, and a Netty-only REST bridge accepting the same JSON commands as the WebSocket API.
*   **Headers:** the same `X-Audio-*` set as SonicNIO, from `DLNAHeaderHelper.getAudioHeaders()`.

---

### ⚠️ Note on the "Zero-Copy" Claim

Both engines use `FileChannel.transferTo()` / OS-level file-region transfer:

| Engine | Actual streaming path |
|:---|:---|
| **SonicNIO** | ✅ `FileChannel.transferTo()` — true zero-copy, 256 KB chunks |
| **Netty** | ✅ `DefaultFileRegion` — true zero-copy (`ChunkedFile` when TLS is in play) |

---

## 📊 Server Engine Comparison

### Actively Maintained

| Feature | SonicNIO | Netty |
|:---|:---|:---|
| **Engine key** | `nio` **(default)** | `netty` |
| **Recommended Use** | Balanced | High Throughput |
| **True Zero-Copy** | ✅ `transferTo` | ✅ `DefaultFileRegion` |
| **Network Priority (DSCP)** | ✅ `0x18` | ✅ `0x18` |
| **Memory footprint** | **~8 KB / conn** | Pooled, watermarks 256 KB / 512 KB |
| **GC Pause Duration** | **< 20 ms** | < 150 ms |
| **Seeking (Range)** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **WebSocket RFC 6455** | ✅ | ✅ |
| **`X-Audio-*` headers** | ✅ | ✅ |
| **Stability** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Actively Maintained** | ✅ | ✅ |

---

## 🛠 Tech Stack

*   **Language:** Java 17 / Kotlin
*   **Async/Reactive:** RxJava 3
*   **DI/Architecture:** Hilt, Jetpack (ViewModel, LiveData)
*   **Database:** Room (Google Jetpack)
*   **Active Engines:** **Custom SonicNIO Reactor** (default), Netty 4.2.18
*   **Library:** jUPnP 3.0.5 (fork of Cling), JAudiotagger, FFmpeg

---

## 🔧 Developer Notes & Android Compatibility

Running enterprise-grade Java servers on Android requires specific workarounds due to platform limitations (e.g., missing APIs, restricted reflection). No engine currently needs a platform patch: SonicNIO uses only JDK NIO, and Netty runs on its NIO transport. CoreHTTP needed a build-time bytecode patch for blocked `jdk.net` hidden APIs (ADR-032) and was removed (ADR-035).

---

## 📖 Typical Use Case: Audiophile Network Streaming

```text
    [ iPad (Controller) ]          [ Android (Server) ]          [ Hi-Fi (Renderer) ]
    |                   |          |                  |          |                  |
    |  mConnect / JPlay |--------->|    Music Mate    |<---------|    RoPieeeXL     |
    |                   |  (UPnP)  |        +         |  (HTTP)  |        +         |
    |                   |          |  Music Library   |          |   External DAC   |
    |___________________|          |__________________|          |__________________|
              |                                                        |
              └────────────────────────────────────────────────────────┘
                                 (Control Commands)
```

1.  **The Server:** Music Mate runs on an Android device hosting your high-fidelity library.
2.  **The Controller:** Use an iPad running **mConnect Player** or **JPlay** as the UPnP Control Point.
3.  **The Renderer:** A Hi-Fi streamer (RoPieeeXL/Volumio) connected to your DAC.
4.  **The Workflow:** Browse and play bit-perfect Hi-Res audio with full metadata and high-resolution album art.

---

## 📋 Pre-requisites

*   **Android OS:** Android 16 (API 36) or higher is required.

---

## 📄 License

Copyright 2014–2026 Thawee Prakaipetch. Licensed under the Apache License, Version 2.0.
