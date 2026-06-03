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

| Feature | Undertow | SonicNIO | Jetty 12 | HttpCore | Netty |
|:---|:---|:---|:---|:---|:---|
| **Library** | Undertow 2.4.0 | Custom NIO | Jetty 12.1.9 | Apache Core 5.4.2 | Netty 4.2.13 |
| **Primary Use** | **Audiophile** | **Balanced** | **Standard** | **Minimalist** | **Scalable** |
| **Zero-Copy** | ✅ Optimized | ✅ Optimized | ✅ Yes | ⚠️ Partial | ✅ Yes |
| **Network Priority** | ✅ DSCP 0x18 | ✅ DSCP 0x18 | ✅ DSCP 0x18 | ✅ DSCP 0x18 | ✅ DSCP 0x18 |
| **Memory / Conn** | 256–300 MB | **~8 KB** | 128–256 MB | 256–300 MB | 256–512 MB |
| **GC Pause** | < 50 ms | **< 20 ms** | < 100 ms | < 100 ms | < 150 ms |
| **Seeking (Range)** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **WebSocket** | ✅ | ✅ v2.2+ | ✅ | ❌ | ⚠️ |
| **Stability** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |

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
