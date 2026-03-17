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
The underlying HTTP engine can be swapped at compile time via Gradle flavors to optimize for specific hardware or audio requirements:

#### 🛡️ NIO (`server-jupnp-nio`) — *The Optimized Secret Sauce* (Recommended)
*   **Status:** **Production Grade.**
*   **Architecture:** A **custom-built, zero-dependency** Reactor-pattern engine written from scratch for Android.
*   **Superpowers:**
    *   **Hi-Res Optimized:** Built-in native support for DSD (DSF/DFF), FLAC, and 32-bit WAV.
    *   **Zero-Copy Direct I/O:** Utilizes `FileChannel.transferTo()` and a global **Direct ByteBuffer Pool** for maximum throughput with zero CPU overhead.
    *   **Extreme Efficiency:** Memory footprint is a mere **~8KB per connection**, with GC pauses consistently **< 20ms**.
    *   **Deterministic Streaming:** Fixed 64KB chunking ensures jitter-free delivery to sensitive high-end DACs.

#### 🚀 Undertow 2.4.0.RC1 (`server-jupnp-undertow`) — *The Performance Leader*
*   **Status:** **Production Grade.**
*   **Metrics:** Smooth playback, **Memory: 256–300 MB**, CPU < 10%.
*   **Superpowers:** **Zero-Copy Streaming** via NIO `transferTo()`, full Byte-Range support, and robust WebSockets.
*   **Note:** Highly stable on Android; optimized for DSD and 192kHz peaks. *(Requires Android-specific class hacks)*.

#### 🛡️ Jetty 12.1.7 (`server-jupnp-jetty`) — *The Robust Standard*
*   **Status:** **Production Grade.**
*   **Metrics:** **12% faster than Jetty 11**, fully compatible with Android 14+ (API 34).
*   **Superpowers:** **Dynamic Resilience.** Automatically scales threads and buffers based on available RAM to prevent OOM on entry-level devices.

#### ⚡ HttpCore 5.4.2 (`server-jupnp-httpcore`) — *Modern & Efficient*
*   **Status:** **Experimental.**
*   **Metrics:** **Memory: 256–380 MB**, CPU < 10%.
*   **Superpower:** **Perceived Sound Quality (SQ)** often exceeds Netty in testing.
*   **Known Issues:** Occasional playback stops on some clients. *(Requires Android-specific class hacks; No WebSocket support)*.

#### 🧪 Netty 4.2.10.Final (`server-jupnp-netty`) — *High-Throughput*
*   **Status:** **Experimental.**
*   **Metrics:** **Memory: 256 MB (Short peaks to 512 MB)**, CPU < 10%.
*   **Known Issues:** Susceptible to jUPnP auto-restart loops during Wi-Fi signal loss. *(WebSocket support non-functional)*.

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
| Platform | Android | Java (PC/NAS) | PC/NAS | PC/NAS | PC/NAS |
| Bit-Perfect Streaming | ✔ | ✔ | ❌ | ✔ | ✔ |
| MQA Detection | ✔ | ❌ | ❌ | ❌ | ✔ |
| Dynamic Range Analysis | ✔ | ❌ | ❌ | ❌ | ❌ |
| Mobile-Native Server | ✔ | ❌ | ❌ | ❌ | ❌ |
| Open Source | ✔ | ❌ | ❌ | ❌ | ❌ |

---

## 📋 Pre-requisites

*   **Android OS:** Android 14 (API 34) or higher is required.

---

## 📄 License

Copyright 2014–2025 Thawee Prakaipetch. Licensed under the Apache License, Version 2.0.
