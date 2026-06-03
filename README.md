# Music Mate

**Music Mate** is a comprehensive High-Resolution Audio management and streaming application for Android. It transforms your mobile device into a powerful **DLNA/UPnP Media Server**, allowing you to stream your local high-fidelity music collection to any compatible renderer (Hi-Fi streamers, Smart TVs, AV Receivers) or play it locally with audiophile-grade quality.

---

## 🗺 Documentation Map

*   🌐 **[Ecosystem Overview](ECOSYSTEM.md)** - How the Core Server, Players, and Controllers fit together.
*   🔌 **[WebSocket API](WEBSOCKET_API.md)** - Technical specification for real-time remote control.
*   🖥️ **[Web UI & Server Architecture](WEBUI.md)** - Deep dive into the remote interface and pluggable HTTP engines.
*   🎼 **[Music Quality Guide](MUSIC_QUALITY_GUIDE.md)** - A reference for understanding Bit Depth and Dynamic Range.
*   🛠️ **[Contributing Guide](CONTRIBUTING.md)** - Developer setup, build instructions, and architecture overview.
*   📡 **[Network Resilience](NETWORK_RESILIENCE.md)** - WiFi loss recovery and hotspot mode support.

---

## 🚀 Key Features

### 🎧 High-Res Audio Support
*   **MQA Identification:** Automatically detects Master Quality Authenticated (MQA) tracks and displays sample rates.
*   **Audiophile Analysis:** Measures Dynamic Range (DR) and bit-depth to help identify "bad" or up-sampled music files.
*   **Visual Verification:** Integrated **Spectrum View** for verifying audio quality and true frequency response.
*   **Format Support:** FLAC, WAV, AIFF, ALAC, AAC, MP3, and DSD (DoP).

### 📡 Advanced Media Server
*   **DLNA/UPnP 1.0 Compliance:** Full Digital Media Server (DMS) implementation compatible with UPnP control points (mConnect, BubbleUPnP, RoPieeeXL).
*   **Bit-Perfect Streaming:** Delivers unmodified, bit-perfect audio streams without transcoding or resampling.
*   **HTTP/1.1 Optimization:** Range request support for efficient seeking; ETag caching for reduced bandwidth (99%+ savings on cache hits).
*   **WebSocket Real-Time Control:** RFC 6455 compliant WebSocket server for live UI updates and player status synchronization.
*   **Rich Metadata:** Serves extensive metadata including Album Art, Artist, Genre, and technical details (Bitrate, Sample Rate, MQA flags).

### 📂 Library Management
*   **High-Performance Scanning:** Optimized metadata extraction using `jaudiotagger` with parallel processing.
*   **Flexible Organization:** Multi-view library browsing by Album, Artist, Genre, Folder, or custom playlists.
*   **Playlist Support:** Full creation, editing, and export of custom playlists with persistence.

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

Music Mate employs a sophisticated **pluggable architecture** that decouples business logic from the network transport, allowing compile-time selection of the optimal HTTP engine via Gradle flavors.

### The Pluggable Server Engine

All engines implement the same `UpnpServer` interface and share consistent behavior:
- **Dynamic ETags** for efficient caching (SHA-256 hash of file content and size).
- **Audiophile Headers** (`X-Audio-Sample-Rate`, `X-Audio-Bit-Perfect`, etc.) for renderer metadata.
- **HTTP/1.1 Compliance** with Range request support, conditional validation, and Keep-Alive optimization.
- **Zero-Copy Streaming** using `FileChannel.transferTo()` for maximum throughput.

> **Maintenance policy:** Three engines are actively developed and receive all future improvements.
> The remaining engines are archived — they build and work, but will not receive new features or bug fixes.

---

### ✅ Actively Maintained Engines

#### 🚀 SonicNIO (`server-jupnp` / flavor `nio`) — *Default · Balanced*
*   **Status:** **Production Grade — Default recommended build.**
*   **Architecture:** Custom-built, zero-dependency Reactor-pattern NIO engine optimised for Android.
*   **Strengths:** Minimalist Direct ByteBuffer pooling (< 20 ms GC pauses), adaptive 64 KB chunking, intelligent LruCache for ETags and client profiles.

#### ✅ CoreHTTP (`server-jupnp-httpcore` / flavor `httpcore`) — *Ultra-Low Memory*
*   **Status:** **Production Grade — Best for memory-constrained devices.**
*   **Strengths:**
    *   ✅ Full WebSocket support (RFC 6455)
    *   ✅ Zero-copy streaming via `FileChannel.transferTo()`
    *   ✅ ~64 KB/connection memory footprint (95 % reduction vs. 256 MB)
    *   ✅ < 30 ms GC pauses with buffer pooling
    *   ✅ DSCP `0x18` Low Delay QoS tagging

#### ✅ Netty (`server-jupnp-netty` / flavor `netty`) — *High Throughput*
*   **Status:** **Production Grade — Best for high-concurrency / scalability.**
*   **Strengths:** Optimised 256 KB/512 KB watermarks, `0x18` DSCP tagging for low-jitter transport, Netty 4.2 event-loop model.

---

### 🗄 Archived Engines *(build but not updated)*

#### Jetty 12 (`server-jupnp-jetty` / flavor `jetty`)
*   **Status:** **Archived — no further updates.**
*   Previously noted for HTTP/2, WebSocket and industrial-grade Range request handling.

#### Undertow 2.4 (`server-jupnp-undertow` / flavor `undertow`)
*   **Status:** **Archived — no further updates.**
*   Previously the highest-throughput option; enterprise-grade async I/O with DSCP QoS.

---

## 📊 Server Engine Comparison

### Actively Maintained

| Feature | SonicNIO | CoreHTTP | Netty |
|:---|:---|:---|:---|
| **Recommended Use** | Default / Balanced | Ultra-Low Memory | High Throughput |
| **Zero-Copy** | ✅ Optimised | ✅ Full | ✅ Yes |
| **Network Priority (DSCP)** | ✅ 0x18 | ✅ 0x18 | ✅ 0x18 |
| **Memory footprint** | **~8 KB / conn** | **~64 KB / conn** | 256–512 MB |
| **GC Pause Duration** | **< 20 ms** | **< 30 ms** | < 150 ms |
| **Seeking (Range)** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **WebSocket RFC 6455** | ✅ | ✅ | ⚠️ |
| **Stability** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Actively Maintained** | ✅ | ✅ | ✅ |

### Archived (reference only)

| Feature | Jetty 12 | Undertow 2.4 |
|:---|:---|:---|
| **Status** | 🗄 Archived | 🗄 Archived |
| **Zero-Copy** | ✅ Yes | ✅ Optimised |
| **Memory footprint** | 128–256 MB | 256–300 MB |

---

## 🛠 Tech Stack

*   **Language:** Java 17 / Kotlin
*   **Async/Reactive:** RxJava 3
*   **DI/Architecture:** Hilt, Jetpack (ViewModel, LiveData)
*   **Active Engines:** Apache HttpCore 5.4.2 (CoreHTTP), Netty 4.2, **Custom SonicNIO Reactor**
*   **Archived Engines:** Jetty 12, Undertow 2.4 *(build only — no further updates)*
*   **Library:** jUPnP (fork of Cling), JAudiotagger, FFmpeg, OrmLite

---

## 🔧 Developer Notes & Android Compatibility

Running enterprise-grade Java servers on Android requires specific workarounds due to platform limitations (e.g., missing APIs, restricted reflection). Music Mate uses "shadowed" classes and reflection hacks to ensure platform interoperability:

*   **Undertow Hacks:** 
    *   `org.jboss.logging.Logger`: Custom implementation to bypass JBoss Logging dependency issues on Android.
    *   `org.xnio.XnioWorker`: Modified version to handle Android-specific thread and context management.
*   **HttpCore Hacks:**
    *   `org.apache.hc.core5.util.ReflectionUtils`: Custom implementation to handle restricted `setAccessible` calls and JRE level detection on Android ART.

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

Copyright 2014–2025 Thawee Prakaipetch. Licensed under the Apache License, Version 2.0.
.
