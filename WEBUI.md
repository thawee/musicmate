# MusicMate Web UI & Server Architecture

This document provides a technical overview of the MusicMate Web interface and the SonicNIO server that powers it.

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
The backend separates core logic (`BaseServer`, `WebSocketContent`) from the network transport, which is SonicNIO.

### Core Components
*   **`BaseServer`**: The foundation class. It handles path normalization, routing, and contains the core WebSocket command logic.
*   **`WebSocketContent`**: The command processor. It handles JSON messages (e.g., `browse`, `play`, `getTrackMetadata`) and manages the `PlaybackCallback` to broadcast state changes.
*   **`ContentHolder`**: A DTO used to encapsulate resolved resources (File Path, MimeType, and Metadata).

### Streaming Engine
MusicMate streams with **SonicNIO**, a custom Reactor-pattern NIO server (`NioHttpServer`, `NioWebServerImpl`): zero-copy `transferTo` in 256 KB chunks, DSCP 0x18 network priority, about 8 KB per connection, RFC 7233 ranges and RFC 6455 WebSocket. There is no engine setting.

> CoreHTTP and the unbuilt Jetty 12 and Undertow modules were removed on 2026-10-01 (ADR-035), and Netty the same day after on-device benchmarks showed no advantage (ADR-037).

### API & Routing
The server exposes three primary context paths:
*   `/ws`: WebSocket endpoint for real-time commands and state updates.
*   `/music/{id}`: High-performance audio streaming endpoint with support for Range requests (seeking) and DLNA headers.
*   `/coverart/{key}`: Serves optimized album art images.

### Advanced Features
*   **Waveform Generation**: Servers generate 480-point peak data on-the-fly via `MusicAnalyser.generateWaveform(context, tag, 480, 0.6)` and cache results in a **256-entry** `LruCache` (bounded by entry count, not bytes, to prevent OOM), guarded by double-checked locking.
*   **Audiophile Headers**: DLNA content features (`contentFeatures.dlna.org`, `transferMode.dlna.org`) are emitted with every stream, along with the `X-Audio-*` set (`X-Audio-Sample-Rate`, `X-Audio-Bit-Depth`, `X-Audio-Bitrate`, `X-Audio-Format`, `X-Audio-Bit-Perfect`) from `DLNAHeaderHelper.getAudioHeaders()`.
*   **Client Profiling**: The `ProfileManager` detects the connecting client (e.g., BubbleUPnP, WiiM, Sony TV) to tune buffer sizes and header compatibility.

---

## 3. Communication Flow
1.  **Handshake**: Client connects to `/ws`. Server sends "Welcome Messages" (Library stats, Renderers, Current Queue).
2.  **Commands**: Client sends a JSON command (e.g., `{ "command": "play", "trackId": 123 }`).
3.  **Execution**: `BaseServer` interacts with the Android `PlaybackService`.
4.  **Broadcast**: `PlaybackService` triggers a callback; `BaseServer` broadcasts the new state to all connected clients.
