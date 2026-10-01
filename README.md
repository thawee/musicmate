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

> **Maintenance policy:** Three engines are built and maintained: SonicNIO, CoreHTTP and Netty.
> The unbuilt Jetty 12, Undertow and HttpCore 5.4 modules were removed on 2026-10-01.

> **Default engine:** `httpcore` (CoreHTTP). Every code path that reads the preference — `CompositeWebServer`, `MainActivity`, `SettingsActivity` — defaults to `"httpcore"` when the preference is unset.

---

### ✅ Actively Maintained Engines

All three are listed in `settings.gradle` and built into the shipping APK.

#### ✅ CoreHTTP (`server-jupnp-httpcore` / engine key `httpcore`) — *Default · Ultra-Low Memory*
*   **Status:** **Production Grade — this is the default engine.**
*   **Architecture:** Apache HttpCore 5.5-beta3 (`H2ServerBootstrap`), 2 IO threads. Android compatibility comes from the `patchHttpCore` Gradle task, which rewrites the stock jar's blocked `jdk.net` hidden-API calls with ASM (ADR-032); no HttpCore classes are shadowed.
*   **Strengths:**
    *   ✅ Full WebSocket support (RFC 6455)
    *   ✅ ~64 KB/connection memory footprint via pooled direct `ByteBuffer`s (`MAX_BUFFER_POOL = 2× cores`)
    *   ✅ < 30 ms GC pauses with buffer pooling
    *   ✅ `X-Audio-*` audiophile headers including `X-Audio-Bit-Perfect`
    *   ✅ `setTrafficClass(0x10)` (DSCP Low Delay)
*   **Streaming:** 64 KB direct-buffer reads in `PartialFileProducer` (see note below).

#### 🚀 SonicNIO (`server-jupnp` / engine key `nio`) — *Balanced*
*   **Status:** **Production Grade.**
*   **Architecture:** Custom-built, zero-dependency Reactor-pattern NIO engine optimised for Android (`NioHttpServer`, single selector + worker pool).
*   **Strengths:** Minimalist Direct ByteBuffer pooling (< 20 ms GC pauses), 256 KB streaming chunks, intelligent LruCache for ETags and client profiles, `IP_TOS = 0x18` (DSCP Low Delay | High Throughput), 512 KB `SO_SNDBUF`.
*   **Header coverage note:** SonicNIO emits the DLNA `transferMode.dlna.org` and `contentFeatures.dlna.org` headers but **not** the `X-Audio-*` set. Renderers relying on `X-Audio-Sample-Rate` / `X-Audio-Bit-Perfect` should use CoreHTTP or Netty.

#### ✅ Netty (`server-jupnp-netty` / engine key `netty`) — *High Throughput*
*   **Status:** **Production Grade — Best for high-concurrency / scalability.**
*   **Strengths:** Netty 4.2.18 event-loop model (1 boss / 2 worker + a 4-thread logic executor), zero-copy `DefaultFileRegion` (with `ChunkedFile` fallback), 256 KB low / 512 KB high write-buffer watermarks, `IP_TOS = 0x18` low-jitter transport, and a Netty-only REST bridge accepting the same JSON commands as the WebSocket API.
*   **Header coverage note:** Netty emits `X-Audio-Sample-Rate`, `X-Audio-Bit-Depth`, `X-Audio-Bitrate`, and `X-Audio-Format`, but **not** `X-Audio-Bit-Perfect`.

---

### ⚠️ Note on the "Zero-Copy" Claim

Only two engines actually use `FileChannel.transferTo()` / OS-level file-region transfer:

| Engine | Actual streaming path |
|:---|:---|
| **SonicNIO** | ✅ `FileChannel.transferTo()` — true zero-copy, 256 KB chunks |
| **Netty** | ✅ `DefaultFileRegion` — true zero-copy (`ChunkedFile` when TLS is in play) |
| **CoreHTTP** | ⚠️ **Not** zero-copy. `PartialFileProducer` reads the file into a 64 KB direct `ByteBuffer`, then writes it to the channel, rewinding the file position on partial writes. This is deliberate and still efficient, but its own Javadoc ("Zero-copy file streaming via FileChannel.transferTo()") is inaccurate. |

---

## 📊 Server Engine Comparison

### Actively Maintained

| Feature | SonicNIO | CoreHTTP | Netty |
|:---|:---|:---|:---|
| **Engine key** | `nio` | `httpcore` **(default)** | `netty` |
| **Recommended Use** | Balanced | Default / Ultra-Low Memory | High Throughput |
| **True Zero-Copy** | ✅ `transferTo` | ⚠️ 64 KB direct buffer | ✅ `DefaultFileRegion` |
| **Network Priority (DSCP)** | ✅ `0x18` | ✅ `0x10` (Low Delay) | ✅ `0x18` |
| **Memory footprint** | **~8 KB / conn** | **~64 KB / conn** | Pooled, watermarks 256 KB / 512 KB |
| **GC Pause Duration** | **< 20 ms** | **< 30 ms** | < 150 ms |
| **Seeking (Range)** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **WebSocket RFC 6455** | ✅ | ✅ | ✅ |
| **`X-Audio-*` headers** | ❌ | ✅ (incl. Bit-Perfect) | ⚠️ (no Bit-Perfect) |
| **Stability** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Actively Maintained** | ✅ | ✅ | ✅ |

---

## 🛠 Tech Stack

*   **Language:** Java 17 / Kotlin
*   **Async/Reactive:** RxJava 3
*   **DI/Architecture:** Hilt, Jetpack (ViewModel, LiveData)
*   **Database:** Room (Google Jetpack)
*   **Active Engines:** Apache HttpCore 5.5-beta3 (CoreHTTP, default), Netty 4.2.18, **Custom SonicNIO Reactor**
*   **Library:** jUPnP 3.0.5 (fork of Cling), JAudiotagger, FFmpeg

---

## 🔧 Developer Notes & Android Compatibility

Running enterprise-grade Java servers on Android requires specific workarounds due to platform limitations (e.g., missing APIs, restricted reflection). Music Mate applies targeted build-time patches to ensure platform interoperability:

*   **HttpCore Hacks:**
    *   `patchHttpCore` strips HttpCore's blocked `jdk.net` hidden-API calls from the upstream jar at build time (ADR-032); no HttpCore classes are vendored.

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
