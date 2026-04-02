# Music Mate

**Music Mate** is a comprehensive High-Resolution Audio management and streaming application for Android. It transforms your mobile device into a powerful **DLNA/UPnP Media Server**, allowing you to stream your local high-fidelity music collection to any compatible renderer (Hi-Fi streamers, Smart TVs, AV Receivers) or play it locally with audiophile-grade quality.

---

## 🚀 Key Features

### 🎧 High-Res Audio Support
*   **MQA Identification:** Automatically detects Master Quality Authenticated (MQA) tracks and displays sample rates.
*   **Audiophile Analysis:** Measures Dynamic Range (DR) and bit-depth to help identify "bad" or up-sampled music files.
*   **Visual Verification:** Integrated **Spectrum View** for verifying audio quality and true frequency response.
*   **Format Support:** FLAC, WAV, AIFF, ALAC, AAC, MP3, and DSD (DoP).

### 📡 Media Server
*   **DLNA/UPnP 1.0 Compliance:** Full Digital Media Server (DMS) implementation compatible with UPnP control points (mConnect, BubbleUPnP, RoPieeeXL).
*   **Bit-Perfect Streaming:** Delivers unmodified, bit-perfect audio streams without transcoding or resampling.
*   **Rich Metadata:** Comprehensive metadata support including Album Art, Artist, Genre, Bitrate, Sample Rate, and MQA flags.
*   **HTTP/1.1 Optimization:** Range request support for efficient seeking; ETag caching for reduced bandwidth (99%+ savings on cache hits).
*   **WebSocket Real-Time Control:** RFC 6455 compliant WebSocket server for live UI updates and player status synchronization.


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

### Server Architecture

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

Music Mate employs a **pluggable server architecture** that decouples business logic from network transport, allowing compile-time selection of the optimal HTTP engine for specific hardware and use cases.

### The Pluggable Server Engine

All engines implement the same `UpnpServer` interface and share consistent behavior:
- **Dynamic ETags** for efficient caching (SHA-256 hash of file content and size)
- **Audiophile Headers** for renderer metadata
- **HTTP/1.1 Compliance** with Range request support, conditional validation, and Keep-Alive optimization
- **Zero-Copy Streaming** using `FileChannel.transferTo()` for maximum throughput


## 🚀 HTTP Server Engines

### 1️⃣ Undertow 2.4.0.RC1 (`server-jupnp-undertow`) — *Recommended for Audiophile Deployment*

**Status:** Production Grade (Highest Recommendation)

**Architecture:** Enterprise-grade async I/O with per-channel thread pools

**Strengths:**
*   **Transport Optimization:** 256 KB buffer tuning for 192 kHz and DSD playback
*   **Network Priority:** DSCP 0x18 (Low Delay + High Throughput) QoS tagging
*   **Seeking Performance:** Highly optimized Range request handling
*   **Memory Efficiency:** Consistent 256–300 MB footprint
*   **Stability:** Excellent uptime metrics
---
### 2️⃣ SonicNIO (`server-jupnp-nio`) — *Balanced Optimization (Default)*

**Status:** Production Grade (Lightweight & Reliable)

**Architecture:** Custom-built Reactor-pattern engine optimized for Android

**Strengths:**
*   **Memory Efficiency:** ~8 KB per connection; scales to 1000+ concurrent connections
*   **Garbage Collection:** < 20 ms GC pauses via Direct ByteBuffer pooling
*   **Jitter-Free Streaming:** Adaptive 64 KB chunking with predictable latency
*   **Intelligent Caching:** LruCache for ETag lookups and MIME type resolution
*   **Client Profiling:** Dynamic header injection based on renderer capabilities
*   **Built-In Protections:** Rate limiting for web UI abuse; request size validation
*   **Network Priority:** TCP Traffic Class (DSCP 0x18) for QoS marking
---
### 3️⃣ Jetty 12.1.7 (`server-jupnp-jetty`) — *Most Feature-Complete*

**Status:** Production Grade (Enterprise-Grade)

**Strengths:**
*   **Modern Web Standards:** Full HTTP/2, WebSocket (RFC 6455), Server-Sent Events support
*   **Dynamic Resilience:** Auto-scaling thread pools based on available RAM
*   **Advanced WebSockets:** High-performance POJO-style message handling
*   **Network Priority:** Custom connection customizers for TCP Traffic Class tuning
*   **Compatibility:** Fully compatible with Android 14+ (API 34)

**Performance:** 12% faster than Jetty 11

---
### 4️⃣ HttpCore 5.4.2 (`server-jupnp-httpcore`) — *Minimalist (Ultra-Low Resources)*

**Status:** Experimental

**Strengths:**
*   **Minimal Footprint:** Designed for ultra-low memory and CPU usage
*   **Large Buffer Support:** 1 MB send buffer for DSD and 192 kHz

**Limitations:**
*   No WebSocket support
*   Basic ETag implementation
*   Occasional playback stops on some renderers

---

### 5️⃣ Netty 4.2.10.Final (`server-jupnp-netty`) — *High-Throughput (Experimental)*

**Status:** Experimental

**Strengths:**
*   **Scalability:** Excellent for > 100 concurrent connections

**Known Issues:**
*   Susceptible to jUPnP auto-restart loops during Wi-Fi signal loss
*   WebSocket support non-functional
*   Higher latency variance unsuitable for audiophile deployments

---
## 📊 Server Engine Comparison

| Feature | Undertow | SonicNIO | Jetty 12 | HttpCore | Netty |
|:---|:---|:---|:---|:---|:---|
| **Recommended Use** | Audiophile Hi-Res | Balanced/Optimized | Feature-Complete | Ultra-Low Resource | Scalability |
| **Zero-Copy** | ✅ Optimized | ✅ Optimized | ✅ Yes | ⚠️ Partial | ✅ Yes |
| **Network Priority (DSCP)** | ✅ 0x18 | ✅ 0x18 | ✅ 0x18 | ⚠️ SndBuf | ⚠️ Basic |
| **Memory per Connection** | 256–300 MB | ~8 KB | 128–256 MB | 256–380 MB | 256–512 MB |
| **GC Pause Duration** | < 50 ms | < 20 ms | < 100 ms | < 100 ms | < 150 ms |
| **Seeking (Range)** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ |
| **WebSocket RFC 6455** | ✅ | ✅ v2.2+ | ✅ | ❌ | ⚠️ |
| **ETag Caching** | ✅ Dynamic | ✅ LRU Cache | ✅ Dynamic | ✅ Dynamic | ✅ Dynamic |
| **Production Ready** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |

---

## 🛠 Tech Stack

*   **Language:** Java 17 / Kotlin
*   **Async/Reactive:** RxJava 3
*   **DI/Architecture:** Hilt, Jetpack (ViewModel, LiveData)
*   **Engines:** Jetty 12.1.7, Undertow 2.4.0.RC1, Apache HttpCore 5.4.2, Netty 4.2.10.Final, **Custom NIO Reactor**
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

## 🔍 Comparison with Other Music Servers

| Feature | **Music Mate** | MinimServer | Plex | BubbleUPnP Server | Roon |
|--------|---------------|-------------|------|-------------------|------|
# Music Mate

**Music Mate** is a comprehensive High-Resolution Audio management and streaming application for Android. It transforms your mobile device into a powerful **DLNA/UPnP Media Server**, allowing you to stream your local high-fidelity music collection to any compatible renderer (Hi-Fi streamers, Smart TVs, AV Receivers) or play it locally with audiophile-grade quality.

---

## 🚀 Key Features

### 🎧 High-Res Audio Support
*   **MQA Identification:** Automatically detects Master Quality Authenticated (MQA) tracks and displays sample rates.
*   **Audiophile Analysis:** Measures Dynamic Range (DR) and bit-depth to help identify "bad" or up-sampled music files.
*   **Visual Verification:** Integrated **Spectrum View** for verifying audio quality and true frequency response.
*   **Format Support:** FLAC, WAV, AIFF, ALAC, AAC, MP3, and DSD (DoP).

### 📡 Advanced Media Server
*   **DLNA/UPnP 1.0 Compliant:** Functions as a Digital Media Server (DMS) compatible with control points like mConnect, BubbleUPnP, and RoPieeeXL.
*   **Transcoding-Free Streaming:** Delivers bit-perfect audio streams directly to renderers.
*   **Rich Metadata:** Serves extensive metadata including Album Art, Artist, Genre, and technical details (Bitrate, Sample Rate).

### 📂 Library Management
*   **Fast Scanning:** High-performance scanning using `jaudiotagger`.
*   **Organization:** Organized browsing by Album, Artist, Genre, and Folder.
*   **Playlists:** Full support for creating and managing custom playlists.

---

## 🏗 System Architecture

Music Mate follows a modular Clean Architecture, implementing a full DLNA stack on Android.

### DLNA Media Server Components
*   **UPnP Framework:** Core addressing, device discovery, and SOAP eventing.
*   **Content Directory Service (CDS):** Handles library browsing with integrated Digital Content Decoding and Profiling.
*   **Connection Manager Service:** Manages active streaming sessions.
*   **HTTP Streamer:** The high-performance engine delivering digital content to clients.
*   *Note: AV Transport Service is intentionally not implemented to maintain focus on bit-perfect audio delivery and specialized renderer compatibility.*

### High-Level Components

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

Music Mate employs a sophisticated "pluggable" architecture that separates business logic from the underlying network transport.

### The Pluggable Server Engine
The underlying HTTP engine can be swapped at compile time via Gradle flavors to optimize for specific hardware or audio requirements. All engines now feature **Dynamic ETags** for efficient client-side caching and consistent **Dynamic Audiophile Headers** (`X-Audio-Sample-Rate`, `X-Audio-Bit-Depth`, `X-Audio-Bitrate`, `X-Audio-Format`, `X-Audio-Bit-Perfect`) for maximum compatibility with high-end renderers.

#### 🚀 Undertow 2.4.0.RC1 (`server-jupnp-undertow`) — *The Performance Leader*
*   **Status:** **Production Grade (Highest Recommendation for Hi-Res).**
*   **Robustness:** Optimized specifically for high-end transport integrity.
*   **Superpowers:**
    *   **Zero-Copy Streaming** via NIO `transferTo()`.
    *   **Direct Buffer Tuning:** Manual 256KB buffer management optimized for 192kHz/DSD peaks.
    *   **Network Priority:** Hardcoded DSCP `0x18` (Low Delay + High Throughput) for bit-perfect timing.

#### 🛡️ SonicNIO (`server-jupnp-nio`) — *The Optimized Secret Sauce*
*   **Status:** **Production Grade (Lightweight & Reliable).**
*   **Architecture:** A custom-built, zero-dependency Reactor-pattern engine.
*   **Superpowers:**
    *   **Jitter-Free Streaming:** Employs **Adaptive Chunking** and a global **Direct ByteBuffer Pool** to minimize GC pauses.
    *   **GZIP Compression:** Efficiently compresses Web UI resources on-the-fly for faster load times.
    *   **Intelligent Caching:** Internal LruCache for ETags and MIME types reduces disk I/O.
    *   **Audiophile Metadata:** Dynamic header injection based on **Client Profiling**.
    *   **Rate Limiting:** Integrated protection against aggressive web crawlers or UI scripts.
    *   **Optimized Priority:** Includes TCP Traffic Class (DSCP 0x18) tuning for audio packets.

#### 🛡️ Jetty 12.1.7 (`server-jupnp-jetty`) — *The Robust Standard*
*   **Status:** **Production Grade (Most Feature-Complete).**
*   **Robustness:** Best-in-class handling of modern web standards and industrial-grade `Range` (seeking) requests.
*   **Superpowers:**
    *   **Dynamic Resilience:** Automatically scales threads and buffers based on available RAM.
    *   **Modern WebSockets:** High-performance POJO-style WebSocket handling for real-time control.
    *   **Network Priority:** Optimized with TCP Traffic Class tuning via custom Connection Customizers.

#### ⚡ HttpCore 5.4.2 (`server-jupnp-httpcore`) — *Minimalist & Efficient*
*   **Status:** **Experimental (Best for Ultra-Low Resources).**
*   **Robustness:** Focused on a minimal CPU and memory footprint.
*   **Superpower:** **Hi-Res Peak Handling.** Large `SndBufSize` (1MB) ensures smooth delivery of DSD peaks on constrained hardware.

#### 🧪 Netty 4.2.10.Final (`server-jupnp-netty`) — *High-Throughput*
*   **Status:** **Experimental (Scalability Focused).**
*   **Robustness:** Industrial-strength backpressure management using `WriteBufferWaterMark`.
*   **Known Issues:** Susceptible to jUPnP auto-restart loops during Wi-Fi signal loss.

### Server Engine Comparison

| Feature | Undertow | SonicNIO | Jetty 12 | HttpCore | Netty |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Primary Use** | **Audiophile Hi-Res** | **Balanced/Optimized** | **Feature-Complete** | **Low Resource** | **Scalability** |
| **Zero-Copy** | Yes (Optimized) | Yes | Yes | Partial | Yes |
| **Transport Priority** | DSCP 0x18 | DSCP 0x18 | DSCP 0x18 | Large SndBuf | Basic |
| **Audiophile Headers**| **Dynamic** | **Dynamic (Profiled)** | **Dynamic** | **Dynamic** | **Dynamic** |
| **Seeking (Range)** | High Speed | Robust | Industrial | Basic | Basic |
| **WebSocket** | Standard | Robust | Most Modern | Basic | Basic |
| **ETag Caching** | Yes | Yes (Cached) | Yes | Yes | Yes |
| **Stability** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |

---

## 🛠 Tech Stack

*   **Language:** Java 17 / Kotlin
*   **Async/Reactive:** RxJava 3
*   **DI/Architecture:** Hilt, Jetpack (ViewModel, LiveData)
*   **Engines:** Jetty 12.1.7, Undertow 2.4.0.RC1, Apache HttpCore 5.4.2, Netty 4.2.10.Final, **Custom NIO Reactor**
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

*   **Android OS:** Android 14 (API 34) or higher is required.

---

## 📄 License

Copyright 2014–2025 Thawee Prakaipetch. Licensed under the Apache License, Version 2.0.


---

## 📋 Pre-requisites

*   **Android OS:** Android 14 (API 34) or higher is required.

---

## 📄 License

Copyright 2014–2025 Thawee Prakaipetch. Licensed under the Apache License, Version 2.0.
