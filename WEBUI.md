# MusicMate Web UI & Server Architecture

This document provides a technical overview of the MusicMate Web interface and the multi-engine server architecture that powers it.

## 1. Web UI (Frontend)
The Web UI is a modern, responsive Single Page Application (SPA) designed to serve as a remote control for the MusicMate ecosystem.

### Technologies
*   **Styling**: [Tailwind CSS](https://tailwindcss.com/) (Utility-first CSS framework).
*   **Dynamic Theming**: [Vibrant.js](https://jariz.github.io/vibrant.js/) (Extracts colors from album art to style the player).
*   **Icons**: [Bootstrap Icons](https://icons.getbootstrap.com/).
*   **Visualization**: HTML5 `<canvas>` for high-performance waveform rendering.
*   **Communication**: WebSockets for real-time state synchronization.

### Key Features
*   **Library Browser**: Hierarchical browsing of Artists, Genres, Playlists, and Incoming Tracks.
*   **Immersive "Now Playing"**: Full-screen view with a dynamic waveform, high-res art, and artist biographies.
*   **Audio Quality Badges**: Detailed technical info (Format, Bit Depth, Sample Rate, and Dynamic Range Score) displayed with contextual logic.
*   **Multi-Renderer Control**: Switch playback between local device and discovered UPnP/DLNA targets.

---

## 2. Server Architecture (Backend)
The backend follows a "Core Logic + Plugin Engine" pattern, allowing the application to use different networking libraries while maintaining consistent behavior.

### Core Components
*   **`BaseServer`**: The foundation class. It handles path normalization, routing, and contains the core WebSocket command logic.
*   **`WebSocketContent`**: The command processor. It handles JSON messages (e.g., `browse`, `play`, `getTrackMetadata`) and manages the `PlaybackCallback` to broadcast state changes.
*   **`ContentHolder`**: A DTO used to encapsulate resolved resources (File Path, MimeType, and Metadata).

### Server Engines
MusicMate supports multiple pluggable server implementations to balance performance, memory footprint, and audiophile integrity.

> **Maintenance policy:** SonicNIO, CoreHTTP and Netty are the only engines; they are listed in `settings.gradle` and maintained. The unbuilt Jetty 12 and Undertow modules were removed on 2026-10-01.

| Feature | SonicNIO | CoreHTTP | Netty |
|:---|:---|:---|:---|
| **Library** | Custom NIO | Apache HttpCore 5.5-beta3 | Netty 4.2.18 |
| **Engine key** | `nio` | `httpcore` **(default)** | `netty` |
| **Primary Use** | Balanced | **Default · Ultra-Low Memory** | **High Throughput** |
| **Status** | ✅ Production | ✅ Production | ✅ Production |
| **True Zero-Copy** | ✅ `transferTo` | ⚠️ 64 KB direct buffer | ✅ `DefaultFileRegion` |
| **Network Priority** | ✅ DSCP 0x18 | ✅ DSCP 0x10 (Low Delay) | ✅ DSCP 0x18 |
| **Memory / Conn** | **~8 KB** | **~64 KB** | Watermarks 256 KB / 512 KB |
| **GC Pause** | **< 20 ms** | **< 30 ms** | < 150 ms |
| **Seeking (Range)** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **WebSocket** | ✅ | ✅ | ✅ |
| **`X-Audio-*` headers** | ❌ | ✅ (incl. Bit-Perfect) | ⚠️ (no Bit-Perfect) |
| **Stability** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |

### API & Routing
The server exposes three primary context paths:
*   `/ws`: WebSocket endpoint for real-time commands and state updates.
*   `/music/{id}`: High-performance audio streaming endpoint with support for Range requests (seeking) and DLNA headers.
*   `/coverart/{key}`: Serves optimized album art images.

### Advanced Features
*   **Waveform Generation**: Servers generate 480-point peak data on-the-fly via `MusicAnalyser.generateWaveform(context, tag, 480, 0.6)` and cache results in a **256-entry** `LruCache` (bounded by entry count, not bytes, to prevent OOM), guarded by double-checked locking.
*   **Audiophile Headers**: DLNA content features (`contentFeatures.dlna.org`, `transferMode.dlna.org`) are emitted by all engines. The `X-Audio-*` set (`X-Audio-Sample-Rate`, `X-Audio-Bit-Depth`, `X-Audio-Bitrate`, `X-Audio-Format`, `X-Audio-Bit-Perfect`) is emitted by **CoreHTTP** and **Netty** only — SonicNIO omits it, and Netty omits `X-Audio-Bit-Perfect` specifically.
*   **Client Profiling**: The `ProfileManager` detects the connecting client (e.g., BubbleUPnP, WiiM, Sony TV) to tune buffer sizes and header compatibility.

---

## 3. Communication Flow
1.  **Handshake**: Client connects to `/ws`. Server sends "Welcome Messages" (Library stats, Renderers, Current Queue).
2.  **Commands**: Client sends a JSON command (e.g., `{ "command": "play", "trackId": 123 }`).
3.  **Execution**: `BaseServer` interacts with the Android `PlaybackService`.
4.  **Broadcast**: `PlaybackService` triggers a callback; `BaseServer` broadcasts the new state to all connected clients.
