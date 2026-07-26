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
*   **Library Browser**: Hierarchical browsing of Artists, Genres, Playlists, and Recently Added tracks.
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

> **Maintenance policy:** SonicNIO, CoreHTTP and Netty are actively maintained. Jetty 12 and Undertow are archived (build only, no further updates).

| Feature | SonicNIO | CoreHTTP | Netty | Jetty 12 *(archived)* | Undertow *(archived)* |
|:---|:---|:---|:---|:---|:---|
| **Library** | Custom NIO | Apache HttpCore 5.4.2 | Netty 4.2.13 | Jetty 12.1.9 | Undertow 2.4.0 |
| **Primary Use** | **Default · Balanced** | **Ultra-Low Memory** | **High Throughput** | Standard | Audiophile |
| **Status** | ✅ Production | ✅ Production | ✅ Production | 🗄 Archived | 🗄 Archived |
| **Zero-Copy** | ✅ Optimised | ✅ Full | ✅ Yes | ✅ Yes | ✅ Optimised |
| **Network Priority** | ✅ DSCP 0x18 | ✅ DSCP 0x18 | ✅ DSCP 0x18 | ✅ DSCP 0x18 | ✅ DSCP 0x18 |
| **Memory / Conn** | **~8 KB** | **~64 KB** | 256–512 MB | 128–256 MB | 256–300 MB |
| **GC Pause** | **< 20 ms** | **< 30 ms** | < 150 ms | < 100 ms | < 50 ms |
| **Seeking (Range)** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **WebSocket** | ✅ | ✅ | ⚠️ | ✅ | ✅ |
| **Stability** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

### API & Routing
The server exposes three primary context paths:
*   `/ws`: WebSocket endpoint for real-time commands and state updates.
*   `/music/{id}`: High-performance audio streaming endpoint with support for Range requests (seeking) and DLNA headers.
*   `/coverart/{key}`: Serves optimized album art images.

### Advanced Features
*   **Waveform Generation**: Servers generate 480-point peak data on-the-fly via `MusicAnalyser` and cache results in a 10MB `LruCache`.
*   **Audiophile Headers**: Automatic injection of `X-Audio-Sample-Rate`, `X-Audio-Bit-Perfect`, and DLNA content features.
*   **Client Profiling**: The `ProfileManager` detects the connecting client (e.g., BubbleUPnP, WiiM, Sony TV) to tune buffer sizes and header compatibility.

---

## 3. Communication Flow
1.  **Handshake**: Client connects to `/ws`. Server sends "Welcome Messages" (Library stats, Renderers, Current Queue).
2.  **Commands**: Client sends a JSON command (e.g., `{ "command": "play", "trackId": 123 }`).
3.  **Execution**: `BaseServer` interacts with the Android `PlaybackService`.
4.  **Broadcast**: `PlaybackService` triggers a callback; `BaseServer` broadcasts the new state to all connected clients.
