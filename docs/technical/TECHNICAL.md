# MusicMate Technical Overview

How MusicMate is built: the DLNA server, the SonicNIO streaming engine, its performance baseline, and the libraries it uses. For using the app, see the [README](../../README.md) and the [User Guide](../USER_GUIDE.md).

## 🗺 Developer Documentation

*   🌐 **[Ecosystem Overview](ECOSYSTEM.md)** - How the Core Server, Players, and Controllers fit together.
*   🎛️ **[Playback Architecture](PLAYBACK_ARCHITECTURE.md)** - How playback control, queue, Android app control, and DLNA control are integrated.
*   🔌 **[WebSocket API](WEBSOCKET_API.md)** - Technical specification for real-time remote control.
*   🖥️ **[Web UI & Server Architecture](WEBUI.md)** - Deep dive into the remote interface and the SonicNIO streaming server.
*   🛠️ **[Contributing Guide](../../CONTRIBUTING.md)** - Developer setup, build instructions, and architecture overview.
*   🏗️ **[System Architecture & Design](DESIGN.md)** - Technical topology, audio engine pipelines, multi-target playback routing, and system ADRs.
*   🎨 **[UI/UX Design System & Guidelines](UI.md)** - Interaction models, gestures, menus, Obsidian-Glass theming, and UI decision records.
*   ⚡ **[Performance](PERFORMANCE.md)** - Measurement method and results for the streaming server.
*   📶 **[Network Resilience](NETWORK_RESILIENCE.md)** - How the server follows Wi-Fi and hotspot changes.
*   🗄️ **[Archive](../archive/)** - Superseded reviews and engine write-ups, kept for history.

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
