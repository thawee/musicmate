# Netty Web Server Engine 10/10 Upgrade Plan

## Objectives
1. Auto-Closing Zero-Copy File Region: Prevent file descriptor leaks by attaching `ChannelFutureListener` to close `RandomAccessFile` / `FileChannel` when streaming finishes or is aborted.
2. Polymorphic ContentHolder Streaming: Support disk files (zero-copy `DefaultFileRegion`), in-memory byte arrays (WebUI/Art `ByteBuf`), and input streams (`ChunkedStream` for dynamic audio transcoding).
3. REST JSON Command API (POST / PUT): Support JSON command dispatch via `handleCommand()` for complete parity with CoreHTTP and SonicNIO.
4. Client Disconnect Filtering: Route benign connection reset exceptions to `Log.d` instead of `Log.e`.
5. Server Signature & Audiophile Headers: Inject `getServerSignature()`, `Accept-Ranges`, `ETag`, `X-Audio-*` tags on every response.

- [x] **1. Upgrade `WebContentHandler` in `NettyWebServerImpl.java`**
  - Implemented zero-copy auto-closing `ChannelFutureListener` on `LastHttpContent` write completion to guarantee `RandomAccessFile` / `FileChannel` closure and prevent file descriptor leaks.
  - Added REST JSON POST/PUT command support with `wsHandler.handleCommand()` and full HTTP response construction.
  - Added ETag and HTTP `304 NOT_MODIFIED` caching support.
  - Added `isClientDisconnect(Throwable)` exception filtering to route normal client disconnects to `Log.d`.
  - Injected `getServerSignature()`, `Accept-Ranges`, and audiophile headers.
- [x] **2. Upgrade `WebSocketFrameHandler`**
  - Added client disconnect filtering in WebSocket `exceptionCaught`.
- [x] **3. Build & Unit Test Verification**
  - Ran `./gradlew compileDebugSources testDebugUnitTest` (BUILD SUCCESSFUL in 5s).
- [x] **4. Update Documentation & Lessons**
  - Updated `tasks/todo.md`, `tasks/lessons.md`, and `CHANGELOG.md`.

## Music Center Server UI/UX Refinements
- [x] **1. Added Direct WebUI Browser Launch Action** (`btn_open_server_url` / `rounded_open_in_new_24.xml`) to launch WebUI in phone browser with 1 tap.
- [x] **2. Added Dynamic Streaming Engine Explainer Caption** (`tv_engine_description`) showing real-time architecture benefits under the selected engine.
- [x] **3. Added DLNA / UPnP Broadcast Service Badge** (`server_broadcast_info`).
- [x] **4. Styled Material 3 Destructive Tonal Stop Button & Gold Start Button** with translucent tinted surfaces.
- [x] **5. Interactive QR Code Feedback**.

## WebUI Offline & Now Playing Experience Upgrade
- [x] **1. 100% Offline Self-Contained WebUI** (`index.html`, `js/tailwindcss.min.js`): Bundled Tailwind CSS locally and removed external CDN dependency.
- [x] **2. Audiophile Technical Specs Card** (`updateNowPlayingScreenVisuals`): Replaced raw empty bio fallback text with a structured format, resolution, DR score, channel mode, bitrate, track #, year, and path grid.
- [x] **3. In-Modal Playback Transport Controls** (`updateNowPlayingScreenUI`): Added shuffle, previous, play/pause, next, and repeat buttons directly into the fullscreen Now Playing modal.
- [x] **4. Radiant Gold Waveform with Click-to-Seek** (`drawWaveform`): Added gold gradient bar rendering and timestamp seeking on canvas click.
- [x] **5. Click-to-Open Triggers & Null-Safety**: Added listeners to album art, track title, artist name, and expand button (`bi-arrows-angle-expand`), with global hotkeys (<kbd>N</kbd> / <kbd>Esc</kbd>) and full null safety for initial playback state.

## Review & Results
- **Auto-Closing Zero-Copy Streams:** Netty's `DefaultFileRegion` now closes `RandomAccessFile` automatically upon write completion or client abortion via `ChannelFutureListener`.
- **REST JSON Command Parity:** Netty now handles incoming POST/PUT JSON commands identically to CoreHTTP and SonicNIO.
- **Logcat Noise Elimination:** Disconnects during track seeking or browser closure are filtered as debug logs.
- **Music Center Server Refinement:** The server tab now provides 1-tap browser launch, dynamic engine captions, service badge, and polished Material 3 buttons.
- **WebUI Offline & Stability:** WebUI operates 100% locally with bundled Tailwind JS, and Now Playing triggers are fully guarded against event bubbling and null state errors.
- **Verification:** `./gradlew compileDebugSources testDebugUnitTest` passed cleanly with 0 errors.

