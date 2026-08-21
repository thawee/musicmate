# Audio Badge Text Wrapping & Layout Density Fix

## Objectives
1. **Prevent Text Wrapping in Badges (`AudioBadges.kt`)**: Add `maxLines = 1` and `softWrap = false` to all badge text elements (`QualityBadge`, `ResolutionBadge`, `NewBadge`, `RatingBadge`) to prevent multi-line badge distortion.
2. **Compact & Responsive Sizing (`AudioBadges.kt`, `DynamicRangeMeters.kt`)**: Optimize badge padding, letter spacing, font size, and DR meter canvas dimensions (width 30dp x height 6dp) to comfortably fit standard and compact screen widths.
3. **Layout Spacing Optimization (`TrackListItem.kt`)**: Refine horizontal padding and spacers in the metadata row to ensure duration, DR meter, resolution badge, and quality indicator fit seamlessly without horizontal overflow.
4. **Verification & Build**: Compile and test to ensure zero regressions.

- [x] **1. Prevent Text Wrapping & Polish Badges in `AudioBadges.kt`**
  - Added `maxLines = 1` and `softWrap = false` to `QualityBadge`, `ResolutionBadge`, `NewBadge`, and `RatingBadge`.
  - Optimized badge horizontal padding (4.5dp) and letter spacing (0.2sp) so text fits comfortably without clipping.
  - Added quality fallback lookup via `TagUtils.getQualityIndicator(track)` when `track.qualityInd` is blank/empty.
- [x] **2. Compact Dynamic Range Meter in `DynamicRangeMeters.kt`**
  - Adjusted DR score text size to `11.sp` with `maxLines = 1` and `softWrap = false`.
  - Refined DR meter canvas to a sleek `30dp x 6dp` bar with `3dp` corner radius.
- [x] **3. Optimize Spacing in `TrackListItem.kt`**
  - Tightened horizontal spacing between album art, text column, duration, DR meter, resolution badge, and quality indicator.
  - Reduced more options button footprint to `32dp` so the metadata row has full breathing room across all screen widths.
- [x] **4. Build & Verification (`./gradlew compileDebugSources testDebugUnitTest`)**
  - Verified with Gradle: `BUILD SUCCESSFUL in 31s`, 0 compilation errors, all unit tests passed.
- [x] **5. Update Documentation & Lessons**
  - Updated `tasks/todo.md` and `tasks/lessons.md`.

## Review & Results
- **Root Cause**: Compose `Text` defaults to `softWrap = true` and `maxLines = Int.MAX_VALUE`. When the horizontal metadata row ran out of space on standard-width screens, the `QualityBadge` ("HI-RES", "24-BIT") was squished and wrapped into 3 vertical lines across hyphens.
- **Fix**: Implemented strict single-line enforcement (`maxLines = 1, softWrap = false`), compact monospace letter spacing, streamlined DR meter bar dimensions, and optimized item layout margins.
- **Verification**: `./gradlew compileDebugSources testDebugUnitTest` succeeded cleanly with 0 errors.

# DLNA Renderer Discovery & Stale Caching Resolution Plan

## Objectives
1. **Multi-Target SSDP M-SEARCH (`MediaServerHubImpl.java`)**: Broadcast queries for `ssdp:all`, `urn:schemas-upnp-org:device:MediaRenderer:1`, and `urn:schemas-upnp-org:service:AVTransport:1` with 2-stage burst to eliminate discovery misses on congested Wi-Fi.
2. **Broad Service-Based Renderer Identification (`MediaServerHubImpl.java`)**: Recognize all renderers exposing the `AVTransport` service even if custom device types are used.
3. **Network Interface Sanitation (`MediaServerAddressFactory.java`)**: Remove cellular networks from multicast address factory to prevent multicast bind failures.
4. **Live Registry Listener (`SimpleRegistryListener.java`, `MediaServerHub.java`, `MediaServerHubImpl.java`)**: Notify `MusicMateServiceImpl` immediately on `remoteDeviceAdded`, `remoteDeviceUpdated`, and `remoteDeviceRemoved`.
5. **Dynamic Target Reconciliation & Stale Timeout Fallback (`MusicMateServiceImpl.java`)**: Reconcile dummy startup placeholder targets to live discovered devices immediately, and add an 8s timeout to fall back to `localTarget` if the previous session's DLNA renderer is offline.

- [x] **1. Upgrade SSDP Discovery & Renderer Identification in `server-jupnp`**
  - Implemented multi-target SSDP M-SEARCH in `MediaServerHubImpl.java` querying `ssdp:all`, `urn:schemas-upnp-org:device:MediaRenderer:1`, and `urn:schemas-upnp-org:service:AVTransport:1` in 2 burst pulses (0s and 1.5s).
  - Enhanced `findRenderersRecursively()` to identify renderers by either `MediaRenderer` device type or `AVTransport` service.
  - Added subnet & reachability validation in `isDeviceValidAndReachable()` to filter out stale renderers on disparate subnets.
  - Sanitized `MediaServerAddressFactory.java` to explicitly exclude cellular network interfaces from UPnP multicast.
- [x] **2. Connect Registry Listener to `MediaServerHub` & `MusicMateServiceImpl`**
  - Updated `SimpleRegistryListener.java` to notify `MediaServerHubImpl` on `remoteDeviceAdded`, `remoteDeviceUpdated`, and `remoteDeviceRemoved`.
  - Added `setOnRenderersChangedListener(Consumer<List<PlaybackTarget>>)` in `MediaServerHub.java` and `MediaServerHubImpl.java`.
- [x] **3. Implement Dynamic Reconciliation & Stale Target Timeout in `MusicMateServiceImpl`**
  - Added `handleDiscoveredRenderers()` in `MusicMateServiceImpl.java` to automatically reconcile startup placeholder targets (`"Scanning for players…"`) to live discovered `DMRPlayer` instances.
  - Added an 8-second startup fallback timer (`dmrStartupTimeoutTask`): if the previous session's DLNA renderer does not appear within 8 seconds, the service automatically falls back to `localTarget` (Phone / DAC / BT), eliminating ghost players.
- [x] **4. Build & Unit Test Verification**
  - Verified with `./gradlew compileDebugSources testDebugUnitTest` (BUILD SUCCESSFUL in 19s, 0 errors).
- [x] **5. Update Documentation & Lessons**
  - Updated `tasks/todo.md`, `tasks/lessons.md`, `CHANGELOG.md`, and `NETWORK_RESILIENCE.md`.

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


## Music Center Media Server UI Redesign & Start/Stop Controls
- [x] **1. Architecture & Design Specification**
  - Fixed vertical overflow and clipping by adding `verticalScroll(rememberScrollState())`.
  - Moved Start / Stop power action directly into the top Hero Status Card so it is immediately visible and never cut off.
  - Fixed awkward text wrapping on server URL, broadcast info, and engine captions.
  - Fixed engine button truncation (`SonicNI` -> `SonicNIO`, `CoreHTT` -> `CoreHTTP`) by using custom segmented switcher styling and optimal padding.
  - Enhanced QR code presentation with clean container, tap-to-zoom modal dialog, and clear scan instructions.
  - Created polished dark audiophile aesthetic matching `NowPlayingPage` and `QueuePage`.
- [x] **2. Implement `MediaServerPage.kt` & Support Vectors**
  - Implemented Hero Status Card with live status dot, Wi-Fi SSID chip, and prominent Start/Stop power action button.
  - Implemented WebUI Endpoint card with copy, open in browser, and QR thumbnail.
  - Implemented full-size QR code preview modal when thumbnail is clicked.
  - Implemented responsive Segmented Engine Switcher with dynamic latency/concurrency specs.
  - Added Material rounded power (`ic_round_power_settings_new_24`), copy (`ic_round_content_copy_24`), and QR (`ic_round_qr_code_24`) vector drawables.
  - Removed obsolete legacy XML child-hiding hack from `AudioHubBottomSheet.java`.
- [x] **3. Build & Test Verification**
  - Verified with `./gradlew compileDebugSources testDebugUnitTest` (BUILD SUCCESSFUL in 48s, 0 errors).
- [x] **4. Document Results & Lessons**
  - Updated `tasks/todo.md` and `tasks/lessons.md`.

## Floating Bottom Dock Navbar Layout Optimization
- [x] **1. Swap Cover Art and (M) Menu Positions**
  - Moved Album Art thumbnail (`bar_album_art`) to the far left of the dock (`layout_alignParentStart="true"`).
  - Moved Collections / Menu icon (`navigation_collections`) to the far right of the dock (`layout_alignParentEnd="true"`).
  - Positioned track title/subtitle and playback transport controls in the middle between Album Art and Menu.
- [x] **2. Verification & Build**
  - Verified with `./gradlew compileDebugSources testDebugUnitTest` (BUILD SUCCESSFUL, 0 errors).

## Audio Anatomy Header Icon & Specs Presentation
- [x] **1. Add `ic_round_info_24` Icon to AUDIO ANATOMY Header**
  - Integrated `Icon(painter = painterResource(id = R.drawable.ic_round_info_24))` inside the `AUDIO ANATOMY` header row on the flip side of the Now Playing card.
  - Enhanced and formatted the audio specifications list (Codec, Resolution, Bitrate, Dynamic Range, and File Size) with high contrast and graceful fallback.
- [x] **2. Verification & Build**
  - Verified with `./gradlew compileDebugSources testDebugUnitTest` (BUILD SUCCESSFUL, 0 errors).

## Documentation & Changelog Update
- [x] **1. Update CHANGELOG.md**
  - Added `[3.18.21] - 2026-08-21` documenting the Media Server Compose overhaul, Hero Status Card with Start/Stop controls, Floating Dock thumb ergonomic swap, Audio Anatomy `ic_round_info_24` badge, compact spec presentation, and MediaSession self-package filtering fixes.
- [x] **2. Update DESIGN.md & PLAYBACK_ARCHITECTURE.md**
  - Updated Section 6A (Floating Dock anatomy & left-to-right hierarchy), Section 6C (Compose viewports for Playback, Queue, Server), and added **ADR-011**.
  - Updated `PLAYBACK_ARCHITECTURE.md` with active MediaSession self-package filtering and Audio Module Map entries for Compose pages.

## Review & Results
- **Prominent Server Control:** Start / Stop power action button is now positioned right in the top Hero Status Card with high contrast and intuitive feedback (Gold Start button / Tinted Red Stop button), ensuring it is never cut off or hidden.
- **Fixed Stop/Start Action Execution & Observation:**
  - `AudioHubBottomSheet.java` now explicitly invokes `observeServerStatus()` in `setupMediaServerTab()` to subscribe to server lifecycle state changes.
  - Wired direct control to `((MusicMateServiceImpl) playbackService).stopServers()` / `startServers()` with seamless fallback to `MediaServerViewModel`.
  - Fixed `MediaServerManager.stopServer()` to properly send command intents (`context.startService(intent)`) and immediately sync initial state on service connection.
- **Zero Clipping & Responsive Layout:** Added `verticalScroll(rememberScrollState())` to ensure smooth scrolling and zero clipping on all display aspect ratios.
- **Crystal-Clear Typography & Layout:** Full URL and broadcast info layout rewritten to prevent awkward multi-line breaks; engine button labels (`SonicNIO`, `CoreHTTP`, `Netty`) now fit perfectly without truncation.
- **Interactive QR Code Dialog:** Users can tap the QR code thumbnail to expand a large, high-contrast modal dialog for easy scanning across the room.
- **Floating Bottom Dock Layout:** Aligned with modern audio player standards (Cover Art on the left as visual anchor, Play/Pause/Next in center, Collections/Menu on far right for comfortable one-handed thumb reach).
- **Audio Anatomy Card:** Integrated the gold `ic_round_info_24` icon in the `AUDIO ANATOMY` header and redesigned the technical specs layout into compact rows with `verticalScroll` to completely eliminate vertical clipping and ensure 16-bit / 44.1 kHz, bitrate, DR, and file size are always fully visible.

## Dynamic Ambient Glow Behind Cover Art Plan

### Objectives
1. **Dynamic Palette Color Extraction (`NowPlayingPage.kt`)**: Extract vibrant, muted, and dominant colors from the active album art bitmap with smooth animated color interpolation (`animateColorAsState`).
2. **Layered Diffused Ambient Glow Backdrop (`NowPlayingPage.kt`)**: Render a soft, radial and linear diffused gradient backlight behind the album art card that adapts dynamically to each song's artwork.
3. **Floating Vinyl Sleeve Rim Light & Depth (`NowPlayingPage.kt`, `TrackListItem.kt`)**: Add a hairline gradient border and depth shadow to make the album art look like a physical luxury vinyl sleeve floating in space.
4. **Build & Test Verification**: Compile and verify with `./gradlew compileDebugSources testDebugUnitTest`.

- [x] **1. Implement Palette Extraction & Animated Ambient Colors in `NowPlayingPage.kt`**
  - Integrated `androidx.palette.graphics.Palette` to extract primary vibrant/dark/dominant and secondary colors from the active album art bitmap with a 700ms `animateColorAsState` transition.
- [x] **2. Render Multi-Stop Radial & Linear Ambient Glow Backlights in `NowPlayingPage.kt`**
  - Applied multi-stop dynamic vertical gradient on the root page background and soft radial backlight overlay (`1200f` radius) behind the album art.
- [x] **3. Add Hairline Glass Rim Light & Floating Shadow Depth**
  - Added gradient hairline borders on the flip side Audio Anatomy card and fine rim stroke on song list album art in `TrackListItem.kt`.
- [x] **4. Build & Unit Test Verification**
  - Verified with `./gradlew compileDebugSources testDebugUnitTest` (BUILD SUCCESSFUL in 4s, 0 errors).
- [x] **5. Document Results & Lessons**
  - Updated `tasks/todo.md`, `CHANGELOG.md`.

## Precision Audiophile Telemetry & Quality Indicators Plan

### Objectives
1. **Glass Obsidian Quality Indicator (`AudioBadges.kt`)**: Redesign `QualityBadge` into an 85% frosted obsidian micro-capsule with glowing neon status dot (Gold for Hi-Res/DSD, Violet for MQA, Cyan for CD Lossless, Grey for MP3) and monospace typography.
2. **Track List Audiophile Telemetry (`TrackListItem.kt`)**: Format resolution pills (`24/96`, `16/44.1`, `DSD64`, `320k`) alongside duration, DR score meter, and quality badge.
3. **Now Playing Precision Readouts (`NowPlayingPage.kt`)**: Enhance telemetry badges on the flip Audio Anatomy side and front status row with monospace tracked numbers and luminous output status.
4. **Build & Test Verification**: Compile and verify with `./gradlew compileDebugSources testDebugUnitTest`.

- [x] **1. Upgrade `QualityBadge` with Obsidian Glass & Luminous Accent Dots**
  - Redesigned `QualityBadge` in `AudioBadges.kt` into an 85% frosted obsidian glass capsule (`Color(0xD9121212)`) with a 4dp luminous status dot (Gold for Hi-Res, Cyan for DSD, Purple for MQA, Sky Blue for CD Lossless, Grey for MP3) and monospace typography.
- [x] **2. Add Precision Resolution Pills to `TrackListItem.kt`**
  - Created `ResolutionBadge` component (`24/96`, `16/44.1`, `DSD64`, `320k`) and integrated it into the song list specs row alongside duration and DR meter.
- [x] **3. Polish Now Playing & Audio Anatomy Monospace Readouts**
  - Enhanced front overlay output target pill with dynamic luminous status dot (Emerald for Bit-Perfect Direct USB, Cyan for DLNA Streamer, Sky Blue for Bluetooth, Gold for Local DAC) and formatted all technical readouts on the flip side with tracked monospace typography.
- [x] **4. Build & Unit Test Verification**
  - Verified with `./gradlew compileDebugSources testDebugUnitTest` (BUILD SUCCESSFUL in 45s, 0 errors).
- [x] **5. Document Results & Lessons**
  - Updated `tasks/todo.md`, `CHANGELOG.md`.







