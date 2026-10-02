# Music Mate

**Music Mate** is a comprehensive High-Resolution Audio management, playback, and streaming application for Android. Built around **Music Center** — a unified audiophile control hub for real-time **Audio Route Path** telemetry, queue management, and target output control — it transforms your mobile device into a high-performance **DLNA/UPnP Media Server** to stream your local high-fidelity music collection to any compatible renderer (Hi-Fi streamers, Smart TVs, AV Receivers) or play it locally with bit-perfect quality.

---

## 🗺 Documentation Map

*   🌐 **[Ecosystem Overview](ECOSYSTEM.md)** - How the Core Server, Players, and Controllers fit together.
*   🎛️ **[Playback Architecture](PLAYBACK_ARCHITECTURE.md)** - How playback control, queue, Android app control, and DLNA control are integrated.
*   🔌 **[WebSocket API](WEBSOCKET_API.md)** - Technical specification for real-time remote control.
*   🖥️ **[Web UI & Server Architecture](WEBUI.md)** - Deep dive into the remote interface and the SonicNIO streaming server.
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

Music Mate decouples business logic from the network transport: the streaming server implements the `WebServer` SPI, and the UPnP layer only talks to that interface.

### The Streaming Server: SonicNIO

The app has one streaming engine, SonicNIO (`server-jupnp`, `NioWebServerImpl` on top of `NioHttpServer`), provided to the UPnP stack by `ServerModule`. There is no engine setting. Netty was retired on 2026-10-01 after on-device benchmarks showed no advantage (ADR-037); CoreHTTP and the unbuilt Jetty 12, Undertow and HttpCore 5.4 modules were removed earlier (ADR-035).

`NioWebServerImpl` extends `BaseServer`, which provides:
- **Dynamic ETags** for efficient caching (SHA-256 of `path + length + lastModified`, truncated to 16 hex chars plus the hex file length).
- **HTTP/1.1 Compliance** with Range request support, conditional validation, and Keep-Alive optimization.
- **Audiophile Headers:** DLNA `transferMode.dlna.org` / `contentFeatures.dlna.org` plus the `X-Audio-*` set (sample rate, bit depth, bitrate, format, Bit-Perfect) from `DLNAHeaderHelper.getAudioHeaders()`.

*   **Architecture:** Custom-built, zero-dependency Reactor-pattern NIO engine optimised for Android (`NioHttpServer`, single selector + worker pool). It also serves UPnP control (SOAP/GENA) on its own port.
*   **Streaming path:** `FileChannel.transferTo()`, true zero-copy in 256 KB chunks; audio never enters the Java heap.
*   **Strengths:** No object pooling (plain allocations), intelligent LruCache for ETags and client profiles, `IP_TOS = 0x18` (DSCP Low Delay | High Throughput), 512 KB `SO_SNDBUF`.
*   **Threading:** The selector thread owns all connections; workers hand back responses and queue closes, and each WebSocket connection's messages are handled in order (ADR-036).
*   **Tests:** `NioHttpServerTest` (37 tests) drives a real socket: full and partial GETs (suffix, open-ended, clamped, 416), invalid and multi-range requests (200), `If-Range`, HEAD, keep-alive, `Connection: close`, HTTP/1.0 close, pipelined requests, `Expect: 100-continue`, the header-read deadline, a stream outliving that deadline, stalled readers, POST bodies split across packets, chunked bodies (501), request-pool integrity after disconnects, stream eviction, stop/teardown, and WebSocket handshake, ordering, close and oversized frames. `NioHttpServerSoakTest` and `NioHttpServerFuzzTest` add load and hostile input; `RateLimitingHandlerTest` covers the limit and the cover-art exemption.
*   **Diagnostics:** `NioHttpServer.getStats()` counts connections, streams, requests, bytes sent, evictions, refusals (503), timeouts and idle closes; `RateLimitingHandler` counts 429s. The Music Center Server tab shows them as one line, e.g. `2 streams • 9.0 MB/s • 1,204 requests`, refreshed every 2 s. Logs go to logcat under the `NioHttpServer` tag.
*   **Benchmark:** `tools/bench/stream-bench.sh <phone-ip> <track-id> [label]` measures single-stream throughput, seek latency, parallel throughput and app CPU from a computer on the same network.

#### Performance Baseline

Measured 2026-10-01. Rerun both after changing `NioHttpServer` and compare against these numbers.

**On device** (`tools/bench/stream-bench.sh`; Galaxy S25 as Wi-Fi hotspot, MacBook client, 261 MB FLAC, 2-3 runs per figure):

| Measure | SonicNIO |
|:---|:---|
| Single stream | 8.0-9.1 MB/s |
| Seek, time to first byte (256 KB range) | p50 28-36 ms, p95 62-134 ms |
| 4 parallel streams (total) | 7.7-8.4 MB/s |
| App CPU while streaming | 5-6% |

Throughput is capped by the Wi-Fi link, not the server: Netty measured the same on this setup (ADR-037). Even one stream is about 7x what 24-bit/192 kHz stereo needs uncompressed (about 1.15 MB/s; FLAC needs less), so time to first byte and CPU are the numbers that matter for playback.

**On the JVM** (`NioHttpServerSoakTest`, loopback, Apple Silicon Mac, 16 clients for 8 s, mixed full files, ranges and keep-alive):

| Measure | SonicNIO |
|:---|:---|
| Throughput | 560-750 MB/s |
| Range time to first byte | p50 4.3-6.0 ms, p95 7-25 ms |
| Errors / leaked streams or connections | 0 / 0 |

This one has no network in the way, so it is the better regression check. Run it with:

```bash
./gradlew :core:testDebugUnitTest --tests '*NioHttpServerSoakTest' --rerun -i | grep SOAK
# heavier: SOAK_CLIENTS=64 SOAK_SECONDS=20
```

---

## 🛠 Tech Stack

*   **Language:** Java 17 / Kotlin
*   **Async/Reactive:** RxJava 3
*   **DI/Architecture:** Hilt, Jetpack (ViewModel, LiveData)
*   **Database:** Room (Google Jetpack)
*   **Streaming Engine:** **Custom SonicNIO Reactor** (JDK NIO only)
*   **Library:** jUPnP 3.0.5 (fork of Cling), JAudiotagger, FFmpeg

---

## 🔧 Developer Notes & Android Compatibility

Running enterprise-grade Java servers on Android requires specific workarounds due to platform limitations (e.g., missing APIs, restricted reflection). SonicNIO needs no platform patch because it uses only JDK NIO. CoreHTTP needed a build-time bytecode patch for blocked `jdk.net` hidden APIs (ADR-032) and was removed (ADR-035).

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
