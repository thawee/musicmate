# CHANGELOG

All notable changes to the **MusicMate** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.18.22] - 2026-08-22

### Added
- **Flagship Audiophile Provenance Metadata Hierarchy & Tabular Typography (`TrackListItem.kt`, `AudioBadges.kt`)**:
  - Reordered track card metadata into the studio provenance flow: `[QualityBadge]` ➔ `[ResolutionBadge]` ➔ `[DynamicRangeMeter]` ➔ `[Duration]`.
  - Added an 8% accent-tinted luminous aura and frosted glass borders (`38% alpha`) to quality capsules for tactile depth.
  - Enforced OpenType Tabular Figures (`fontFeatureSettings = "tnum"`) on duration strings to prevent horizontal jitter during high-speed scrolling.
- **Tactile Micro-Haptics & Ergonomic Fast-Scroller (`FastScrollbar.kt`, `NowPlayingPage.kt`)**:
  - Integrated subtle micro-ticks (`HapticFeedbackType.TextHandleMove`) on drag initiation and alphabet letter transitions (`A` $\to$ `B` $\to$ `C`).
  - Expanded the drag touch target to a comfortable **24dp hitbox** while preserving a minimalist **4.5dp visible gold indicator**.
  - Added tactile haptic feedback to all transport controls (Play/Pause, Skip, Previous, Shuffle, Repeat).
- **Skeleton Shimmer Loading States (`TrackListItemShimmer.kt`, `MusicListScreen.kt`)**:
  - Built animated linear gradient shimmer placeholder cards for the track list during initial library scans and refresh operations.
  - Enhanced empty states with brand insignia and typography.
- **Pure Compose Audio Hub Bottom Sheet Container (`AudioHubSheet.kt`, `DialogInterop.kt`)**:
  - Created a pure Jetpack Compose `ModalBottomSheet` + `HorizontalPager` container hosting *Playback*, *Queue*, and *Server* pages with unified 120Hz gesture tracking.
- **Active Navigation Drawer Item Highlight (`MainScaffold.kt`, `DrawerInterop.kt`, `MainActivity.java`)**:
  - Added dynamic active item highlighting in the navigation drawer with translucent gold background, border, and trailing gold status dot.
  - Replaced the single music note icon in the sidebar header with the unified (M) brand insignia (`ic_nav_musicmate_menu`).

### Fixed
- **Drawer Open Trigger & Bottom Dock Touch Target Z-Order (`DrawerInterop.kt`, `activity_main.xml`)**:
  - Replaced `SharedFlow` with direct `SideEffect` binding of `drawerState` and `coroutineScope` to ensure 100% reliable execution of `DrawerInterop.openDrawer()`.
  - Reordered the (M) menu button in `activity_main.xml` to top z-order with `elevation = 4dp` to eliminate touch interception by adjacent playback layouts.

## [3.18.21] - 2026-08-21

### Added
- **Music Center Media Server Jetpack Compose Redesign (`MediaServerPage.kt`, `AudioHubBottomSheet.java`)**:
  - Rebuilt the Media Server management page with a prominent top **Hero Status Card** featuring live Wi-Fi SSID connectivity chip, status LED indicator, and high-contrast **Start / Stop** server controls.
  - Implemented an interactive tap-to-enlarge QR code modal dialog with high-contrast presentation for cross-room WebUI discovery.
  - Added dynamic WebUI Endpoint card with 1-tap "Open WebUI in Browser" and "Copy URL" clipboard actions.
  - Designed segmented engine switcher (`SonicNIO` / `CoreHTTP` / `Netty`) with live architecture performance specs and zero label truncation.
  - Wrapped content in `verticalScroll` to guarantee zero layout clipping across all device aspect ratios and font scales.
- **Audio Anatomy Technical Specs Card Upgrade (`NowPlayingPage.kt`)**:
  - Added dedicated gold `ic_round_info_24` icon to the `AUDIO ANATOMY` header row on the flip side of the Now Playing card.
  - Redesigned technical specs into compact audiophile telemetry rows (Codec, Resolution, Bitrate, Dynamic Range score, File Size) with `verticalScroll` to prevent vertical clipping on all screen sizes.
- **Floating Bottom Dock Thumb Ergonomics Optimization (`activity_main.xml`, `MainActivity.java`)**:
  - Swapped positions of Cover Art and Menu button: Album Art is anchored on the far left next to song title/subtitle for natural left-to-right visual hierarchy, while the (M) Collections / Navigation Menu button is on the far right in the primary thumb zone for effortless one-handed reach.

- **Dynamic Artwork Ambient Glow & Floating Sleeve Backdrop (`NowPlayingPage.kt`, `TrackListItem.kt`)**:
  - Integrated `androidx.palette.graphics.Palette` to extract dynamic primary vibrant/dominant and secondary colors from the active album art.
  - Rendered a smooth, 700ms animated multi-stop ambient gradient backlight behind the Now Playing hero container and a diffused radial ambient glow behind the album art.
  - Added gradient hairline rim lighting and floating sleeve borders (`0.75dp`) to elevate cover art presentation to luxury audiophile standards.

- **Precision Audiophile Telemetry & Quality Indicators (`AudioBadges.kt`, `TrackListItem.kt`, `NowPlayingPage.kt`)**:
  - Redesigned `QualityBadge` into an 85% frosted obsidian glass capsule (`Color(0xD9121212)`) with a 4dp luminous status dot (Gold for Hi-Res, Cyan for DSD, Purple for MQA, Sky Blue for CD Lossless, Grey for MP3) and monospace typography.
  - Added new `ResolutionBadge` (`24/96`, `16/44.1`, `DSD64`, `320k`) in song list items for comprehensive studio library metadata scanning.
  - Enhanced output target indicators with luminous audio pipeline status dots (Emerald for Bit-Perfect Direct USB, Cyan for DLNA Network Streamer, Sky Blue for Bluetooth, Gold for Local DAC).

### Fixed
- **Cover Art Indicator Redesign & Glassmorphism Micro-Pill (`AudioBadges.kt`)**:
  - Replaced bulky, flat brownish `NEW` / `DL` stickers with ultra-premium **85% deep frosted obsidian glass micro-pills** featuring a glowing 4dp status dot, hairline accent stroke (`#FFD700` gold / `#64B5F6` cyan), and tracked typography (`8.5sp`).
- **Tag Editor Autocomplete Dropdown Restoration & Catalog Expansion (`TagsEditorPage.kt`, `TagsEditorFragment.kt`, `arrays.xml`)**:
  - Restored Material 3 `ExposedDropdownMenuBox` dropdown selectors for **Genre**, **Style**, **Origin**, **Mood**, **Publisher**, and **Artist** with live type-to-filter suggestions, height bounding (`280dp`), and comprehensive global/audiophile preset catalogs.
- **DLNA Renderer Discovery, SSDP Multi-Target Bursts & Ghost Target Pruning (`MediaServerHubImpl`, `SimpleRegistryListener`, `MusicMateServiceImpl`, `MediaServerAddressFactory`)**:
  - Broadcast multi-target SSDP queries (`ssdp:all`, `urn:schemas-upnp-org:device:MediaRenderer:1`, and `urn:schemas-upnp-org:service:AVTransport:1`) in 2 burst pulses (0s and 1.5s) to eliminate discovery misses on devices that ignore `ssdp:all` or drop UDP packets.
  - Enhanced renderer detection to identify all devices exposing the `AVTransport` service even if custom device types are used (e.g. Sonos, Heos, custom streamers).
  - Wired `SimpleRegistryListener` to `MediaServerHub.setOnRenderersChangedListener()` to immediately notify `MusicMateServiceImpl` when devices are discovered, updated, or removed.
  - Added dynamic target reconciliation in `MusicMateServiceImpl` to automatically upgrade placeholder startup targets (`"Scanning for players…"`) to live discovered `DMRPlayer` objects.
  - Implemented an 8-second safety fallback timeout: if the previously selected DLNA renderer does not appear on the network within 8s, the app gracefully falls back to the local player instead of getting stuck on a ghost player.
  - Fixed `RegistrationException: URI namespace conflict` in `MediaServerConfiguration` where overriding `getDevicePath()` to return `""` collapsed all discovered devices' event callback URIs to the same path, causing the second discovered renderer (e.g. `HiBy Music HiBy MediaRender`) to be rejected when `Ropieee` was already registered.
  - Sanitized `MediaServerAddressFactory` to exclude cellular network interfaces from UPnP multicast, preventing multicast socket binding failures.
  - Added subnet & reachability validation in `isDeviceValidAndReachable()` to filter out stale renderers on disparate subnets from previous Wi-Fi connections.
- **Media Server Lifecycle Command Execution & Status Observation (`AudioHubBottomSheet.java`, `MediaServerManager.java`)**:
  - Fixed missing `observeServerStatus()` registration in `AudioHubBottomSheet.java` so Compose state immediately reflects server start/stop transitions.
  - Routed Start/Stop commands directly to the active `MusicMateServiceImpl` instance with seamless fallback to `MediaServerViewModel`.
  - Fixed detached `MediaServerManager.stopServer()` intent dispatch to use `context.startService(intent)` instead of `stopService(intent)`, ensuring `ACTION_STOP_SERVER` commands are properly handled by `onStartCommand()`.
- **MediaSession Active Sessions Self-Package Filtering (`MusicMateServiceImpl.java`, `ExternalAndroidPlayer.java`)**:
  - Guarded against `MediaSessionManager.getActiveSessions()` returning MusicMate's own package, eliminating redundant `"Music Mate • v3.18.20"` duplicate entries in player pickers.

## [3.18.20] - 2026-08-14

### Added
- **Premium Scroll & Fast-Scroller Unification (`MainActivity`, `activity_main.xml`)**:
  - Re-engineered the scroll-to-top interaction by placing a meticulously styled 36dp "Go to top" frosted glass FAB securely aligned with the right-edge `FastScroller`.
  - Color-matched the new compact FAB with the obsidian deep glass of the bottom navigation dock for a seamless visual flow.
- **Custom Native Quality Pie Chart & MPAndroidChart Removal (`QualityPieChartView`, `AboutActivity`)**:
  - Completely stripped out the heavy, unmaintained `MPAndroidChart` bloatware (~2MB savings).
  - Designed a 150-line, 0-dependency lightweight custom Android `View` (`QualityPieChartView`) utilizing direct native Canvas APIs to mathematically render the premium audiophile encoding chart on the About page.
- **Enhanced Recursive DLNA Renderer Discovery (`MediaServerHubImpl`)**:
  - Refactored UPnP service resolution to traverse deeply nested/embedded UPnP renderers.
  - Removed rigid version enforcing (e.g. `MediaRenderer:1`) to gracefully discover newer generation AVTransport (V2/V3) rendering control clients.
- **Incoming Tracks Triage (`TrackDao`, etc)**:
  - Renamed "Recently Added" to "Incoming Tracks" across the mobile app, database DAO, and WebUI / UPnP server routing to reflect unmanaged audio triage.
  - Updated DAO query ordering to group tracks naturally by artist, album, and track sequence: `ORDER BY artist ASC, album ASC, CAST(track AS INTEGER) ASC, title ASC`.
- **Music Center Server Panel UI/UX Refinements (`AudioHubBottomSheet`, `view_action_server_management_bottom_sheet.xml`)**:
  - Added direct 1-tap "Open in Browser" action (`rounded_open_in_new_24`) next to the server URL to immediately preview/control the WebUI on the host device.
  - Added dynamic streaming engine explainer captions under the `SonicNIO / CoreHTTP / Netty` toggle group describing real-time architecture advantages.
  - Added DLNA 1.5 / UPnP AV active broadcast service badge with port indication.
  - Modernized "Stop MediaServer" with Material 3 destructive tonal styling and translucent crimson background.
- **Discover Music Folders Dialog UI/UX Modernization (`MainActivity`, `view_action_directories.xml`, `view_action_listview_item.xml`)**:
  - Replaced crude default programmatic buttons with sleek Material 3 Tonal storage chips (`+ Primary`, `+ SD Card`) featuring folder icons and gold accents.
  - Replaced rigid 220dp list height with dynamic auto-sizing (`setListViewHeightBasedOnChildren`) so the list card perfectly hugs the directory items with no empty black void.
  - Enhanced directory rows with folder icons, middle-ellipsized 2-line path visibility, and red ripple delete icon buttons.
  - Polished checkboxes and action buttons ("Cancel" / "Start Scan") with gold Material 3 filled styling.
- **WebUI Now Playing Screen & Waveform Experience Upgrade (`index.html`, `MusicInfoRepository`)**:
  - Added full playback transport controls (Shuffle, Previous, large Play/Pause, Next, Repeat) directly inside the fullscreen Now Playing modal.
  - Added click-to-seek support on the waveform visualizer to scrub tracks directly by clicking the waveform bars.
  - Styled waveform with radiant gold linear gradients (`#FFE082` -> `#FFB300`) on played bars and crisp translucent white on unplayed bars.
  - Replaced raw "No artist biography found" / "No album information found" placeholders with a rich, glassmorphism **Audiophile Technical Specifications** grid (Container, Format, Resolution, Dynamic Range Score, Channel Mode, Bitrate, Track #, Source Path).
  - Made Now Playing instantly accessible by clicking the bottom bar album art, song title, artist text, or new expand button (`bi-arrows-angle-expand`), plus global hotkey `N` (open/close) and `Escape` (dismiss).
- **100% Offline Self-Contained WebUI (`index.html`, `tailwindcss.min.js`)**:
  - Bundled Tailwind CSS locally in `app/src/main/assets/webui/js/tailwindcss.min.js` and removed external CDN dependency (`https://cdn.tailwindcss.com`), allowing full WebUI functionality on standalone Wi-Fi hotspots and offline local networks without internet access.

### Fixed
- **Android 13+ Bluetooth Codec Reflection & Cache Invalidation (`AudioOutputHelper`, `MusicMateServiceImpl`)**:
  - Guarded against hidden `BluetoothA2dp.getCodecStatus()` reflection invocations that throw `SecurityException` (`CDM association / BLUETOOTH_PRIVILEGED required`) on API 33+.
  - Prevented `refreshBluetoothCodecStatus()` from clearing cached Bluetooth codec details on Android 13+, ensuring data received via `CODEC_CONFIG_CHANGED` broadcasts persists until explicit device disconnection.
  - Registered broadcast receiver with `Context.RECEIVER_EXPORTED` and attached framework `ClassLoader` to ensure safe unmarshalling of `android.bluetooth.BluetoothCodecStatus` parcelables.
- **MediaSessionManager Permission Guard (`MusicMateServiceImpl`)**:
  - Replaced naked `mediaSessionManager.getActiveSessions(null)` calls with guarded `refreshExternalPlayersSafe()` using explicit `MediaNotificationListener` component checks to prevent `SecurityException: Missing permission to control media`.
- **DLNA UPnP Auto-Rebind on Wi-Fi Roaming & Doze Wakeup (`MediaServerHubImpl`)**:
  - Implemented `onLinkPropertiesChanged` and IP change tracking (`lastBoundIp`) in `MediaServerHubImpl` to detect DHCP renewals, Wi-Fi mesh AP roaming, and Doze wakeups.
  - Added debounced auto-restart logic (`restart()`) so the jUPnP stack and HTTP Web Server seamlessly rebind to the new IP address without requiring the user to force-close and restart the app.
  - Strengthened `acquireLocks()` with `isHeld()` validation to re-acquire `WifiManager.MulticastLock` and `WifiLock` dynamically on network restoration, preventing Android from dropping SSDP multicast packets (`239.255.255.250:1900`).
- **HttpCore 5 Benign Client Disconnect Logging (`HttpCoreWebServerImpl`)**:
  - Added `isClientDisconnect()` filtering in `HttpCoreWebServerImpl` to route normal client disconnects (`Connection reset by peer`, `Broken pipe`, `ClosedChannelException` during track seeking or browser tab close) to debug logs (`Log.d`) instead of generating full error stack traces (`Log.e`).
- **Netty 4.2 Web Server 10/10 Architecture Upgrade (`NettyWebServerImpl`)**:
  - Attached `ChannelFutureListener` on zero-copy `DefaultFileRegion` stream completion to guarantee `RandomAccessFile` / `FileChannel` cleanup, eliminating file descriptor leaks during rapid scrubbing.
  - Implemented REST JSON POST/PUT command dispatch via `wsHandler.handleCommand()` with `Server: getServerSignature()` response injection.
  - Added ETag caching with HTTP `304 NOT_MODIFIED` handling for lightning-fast WebUI asset and artwork loading.
  - Filtered benign socket disconnects in `exceptionCaught` handlers across HTTP and WebSocket pipelines.
- **WebUI Now Playing Stability & Null-Safety (`index.html`)**:
  - Implemented strict null-checks (`currentPlaybackState || {}`) when interacting with the mini-player before the first WebSocket broadcast arrives, preventing `TypeError` crashes.
  - Added `e.stopPropagation()` to cover art click listeners to prevent duplicate simultaneous popups caused by nested event bubbling.
  - Fixed play/pause and repeat mode toggle visual desync by correctly mapping `shuffleMode`, `repeatMode`, and `state.state` properties.
  - Cleaned redundant `-Bit` suffixes from Audiophile resolution specs to elegantly render as `16-Bit / 44.1kHz`.

## [3.18.19] - 2026-08-14

### Added
- **Build System & Dependency Catalog Modernization (`libs.versions.toml`, `settings.gradle`, `build.gradle`)**:
  - Completely audited and pruned ~60+ lines of legacy commented-out artifacts (old Jackson, RxJava, Guava, Skydoves, old Cling/UPnP forks, unreferenced Jetty/HttpCore54 entries).
  - Cleaned `settings.gradle` by removing ~30+ lines of obsolete commented module includes.
  - Removed duplicate subproject dependency declarations in `core/build.gradle` and cleaned obsolete migration comments across all module build scripts.
  - Pruned unused transitive dependencies in `androidtagview` and `crashreporter`, modernizing `JustFLAC` and `justdsd` build scripts for faster, cleaner Gradle builds.

### Changed
- **Song Detail & Tag Editor UI/UX Hierarchy (`TagsActivity`, `activity_tags.xml`, `fragment_editor_preview.xml`)**:
  - **Dynamic Tab Visibility:** In Preview mode with the header fully expanded, `TabLayout` is cleanly hidden to prevent visual collisions with the bottom action capsule (`Delete` | `Organize` | `More...`). When tapping `Edit Song Info` or scrolling, the tabs smoothly appear at the top under the action bar.
  - **Metadata Deduplication:** Suppressed duplicate artist display when `Album Artist` equals `Artist`, cleanly rendering `Artist | Album` followed by `❖ Genre ❖` without redundant repetition.
  - **Title Scrim & Contrast:** Enhanced `shape_background_main_header.xml` with a smooth dark top-down vignette gradient for high text contrast across all album covers.

### Fixed
- **Bluetooth Codec Propagation to UI Target Badges (`AudioOutputHelper`)**:
  - Fixed `AudioOutputHelper.getOutputDevice()` to explicitly attach the cached Bluetooth codec string (`sCachedBtCodec`) to the output `Device` model, ensuring target labels accurately render `{Name} • {Codec}` (e.g. `Shanling UP4 • LDAC`).
- **Telemetry Chip Space & Layout Balancing (`sheet_now_playing_queue.xml`)**:
  - Rebalanced padding, margins, and text sizes across telemetry chips to prevent truncation of the `DIRECT` bit-perfect indicator badge on smaller screens.

## [3.18.18] - 2026-08-14

### Added
- **Audiophile Dynamic Range (DR) Metrics Chip & Direct Bit-Perfect Badge (`AudioHubBottomSheet`, `sheet_now_playing_queue.xml`)**:
  - Displays a dedicated amber Dynamic Range score chip (e.g. `[DR 14]`) on the Now Playing screen when DR mastering health data is available.
  - Displays a glowing emerald `[DIRECT]` verification badge when lossless audio streams directly to DLNA renderers or bit-perfect local outputs.
- **1-Tap 3D Flip Technical Specs Card (`AudioHubBottomSheet`, `sheet_now_playing_queue.xml`)**:
  - Tapping the album artwork or the top-right `(i)` badge triggers a 3D Y-axis card flip animation (`rotationY 90° ➔ -90° ➔ 0°`), revealing a dark glassmorphic Audio Anatomy drawer with Format, Bitrate, Dynamic Range, and physical File Size.
- **Dual-Engine True Gapless Playback (`AndroidPlayerController`, `MediaServerHubImpl`, `MusicMateServiceImpl`)**:
  - Implemented double-buffered `setNextTrack()` with `onMediaItemTransition(MEDIA_ITEM_TRANSITION_REASON_AUTO)` in ExoPlayer for 100% gapless transitions on on-device playback.
  - Unified with DLNA `SetNextAVTransportURI` preloading for Wi-Fi streamers (WiiM, Eversolo, HiBy).
- **Audiophile DSD & Integer Resampling Pipeline (`FFMpegHelper`)**:
  - Transcodes DSD (DSF/DFF) files using exact 32x integer multiples (88.2 kHz / 176.4 kHz) with an 8th-order 30 kHz lowpass filter (`-af "lowpass=30000, volume=6dB"`) to eliminate quantization noise without non-integer jitter.
- **Universal Queue Deduplication (`QueueManager`)**:
  - Enforces strict single-instance track uniqueness across `addPlayingQueue`, `addPlayNext`, `savePlayingQueue`, and `loadPlayingQueue` with index pointer synchronization.

### Fixed
- **Bluetooth A2DP Output Audio Quality (`AndroidPlayerController`, `AudioOutputHelper`)**:
  - Reverted forced float PCM in favor of auto-negotiated 16-bit / 24-bit integer PCM, eliminating distortion and crackling over Bluetooth A2DP.
  - Removed aggressive reflection-based codec overrides to allow natural, stable Bluetooth HAL profile negotiation.

## [3.18.17] - 2026-08-14

### Added
- **Automatic Bluetooth Audio Optimization & Real-Time Reflection Engine (`AudioOutputHelper`, `MusicMateServiceImpl`, `AudioHubBottomSheet`, `MainActivity`)**:
  - **Automatic Background Codec Optimization:** MusicMate automatically requests the highest possible codec (`LDAC 24-bit / 96 kHz` or `aptX HD`) silently in the background whenever Bluetooth headphones connect via reflection on `BluetoothA2dp.setCodecConfigPreference()`, eliminating manual configuration buttons.
  - **Real-Time Codec Telemetry:** Hooked `BluetoothProfile.A2DP` proxy service listener and `CODEC_CONFIG_CHANGED` / `ACTION_ACL_CONNECTED` broadcast receivers to immediately reflect active codec and sample rate telemetry across the app.
  - **Direct System Audio / Bluetooth Routing:** Tapping the Step 3 output card in the Music Center directly opens Android's native Media Output panel or Bluetooth settings with zero modal dialog friction.
  - **Compact Naming Format:** Added `AudioOutputHelper.getCompactLabel()` to unify Bluetooth device naming across the player selection dropdown, Floating Dock, and Music Center bottom sheet using `{Name} • BT ({Codec})` (e.g. `Sony WH-1000XM5 • BT (LDAC)`).
  - **Title Cased Audio Output Descriptors:** Standardized output types into clean Title Case (`"Bluetooth Audio"`, `"USB DAC"`, `"Wired Headphones"`, `"Phone Speaker"`).
- **Dual-Chunk WAV Audio Tagging (`JThinkWriter`)**:
  - Writes standard RIFF `WavInfoTag` chunks for legacy hardware / car stereos alongside standard ID3v2.4 chunks for modern audiophile software.
- **Embedded Cover Art Tag Writing (`JThinkWriter`)**:
  - Integrated `ArtworkFactory` to write embedded album art binary frames directly into audio files during tag saving.

### Changed
- **Standardized Playback Renderer Naming Pattern**:
  - Replaced legacy parenthesis notation `(Bluetooth Audio)` with unified bullet delimiter `•` across the entire app (`{Name} • BT ({Codec})`, `{Name} • USB DAC`, `{Name} • {IP}`, `{Name} • {Version}`).
- **Tag Editor Sequential Disk I/O Throttling (`TagsEditorFragment`)**:
  - Replaced unbounded parallel futures with sequential execution on background worker threads during batch tag updates to prevent micro-SD card lockups.

### Fixed
- **Audio Route Path Bluetooth Badge (`AudioHubBottomSheet`)**:
  - Fixed Step 3 (Target Audio Output) route badge to properly display `BLUETOOTH A2DP` and `Active Bluetooth A2DP Wireless Stream` with detected codec info instead of falling back to `DIRECT SYSTEM OUTPUT`.
- **Persistent "Discard changes?" Popup Bug (`TagsEditorFragment`, `TagsActivity`)**:
  - Added `isBindingInputs` guard flag to suppress `TextWatcher.afterTextChanged()` callbacks during programmatic `setText()` population on initial load and post-save refreshes.
  - Fixed duplicate `TextWatcher` attachments and ensured `tagsActivity.setDirty(false)` and `modifiedFields.clear()` are executed after saving.
- **Silent Tag Write Failures & Error Propagation (`TagWriter`, `FileRepository`)**:
  - Updated `TagWriter.writeTag()` and `FileRepository.setMusicTag()` to return `boolean`, guaranteeing that the Room database is updated only when physical disk writes succeed.
- **Multi-Value Tag Delimiter Normalization (`JThinkReader`, `StringUtils`)**:
  - Normalized mixed delimiters (`/`, `;`, `&`, `,`) to clean comma-separated strings (`", "`) on read, while preserving band names like `AC/DC`.

## [3.18.16] - 2026-08-12

### Changed
- **Now Playing Cover Art Overlay & Playing Indicators (`MusicTagAdapter`)**:
  - **Scoped Overlay Visibility:** Replaced global dark overlay on all list items with dynamic, single-track cover art overlay (`shape_now_playing_cover_overlay.xml`) rendered exclusively on the currently playing song (`isNowPlaying`). Non-playing tracks retain clean, full-brightness artwork.
  - **Animated Vector Equalizer:** Integrated real-time `AnimatedVectorDrawable` equalizer (`ic_equalizer_active`) on the cover art overlay when playing (`PLAYING`), and a Gold pause icon (`ic_baseline_pause_24`) when paused (`PAUSED`).
  - **Active Track Title Highlight:** Highlighted active song titles in `@color/colorGold` and updated `MainActivity.java` to notify the adapter on play/pause state changes.
  - **Single Indicator Surface:** Removed the duplicate `item_player` equalizer icon from the top-right status indicator bar to prevent UI clutter.
- **Artwork Gesture Overlay Feedback & Micro-Pill Telemetry (`AudioHubBottomSheet`)**:
  - **Gesture Overlay Animation:** Added central `ImageView` overlay (`sheet_gesture_feedback_icon`) on album art to flash 48dp action icons (`play`, `pause`, `skip_next`, `skip_previous`) during double-tap and horizontal fling gestures with a smooth scale-up (`0.7f` $\rightarrow$ `1.2f`) and fade-out animation.
- **Unified Sparkle (✦) Symbol Badge System for NEW Tracks (`view_list_music_tag`, `NewIndicatorView`)**:
  - Replaced yellow dot and yellow rectangular block indicators with a cohesive Sparkle/Starburst symbol badge system (`auto_awesome`).
  - **Cover Art Thumbnail:** 14dp vector badge (`ic_new_sparkle_badge.xml` in Gold for unmanaged tracks, `ic_new_download_sparkle_badge.xml` in Cyan for downloads) anchored to the top-right corner of album art.
  - **Tag Activity Header:** `NewIndicatorView` renders a `12dp` rounded pill chip containing a Sparkle icon + bold "NEW" text in dark amber (`#2E2712`) / dark cyan (`#0D2E3D`) chip backgrounds.

### Fixed
- **Player Picker Popup Menu Interleaving (`MainActivity.java`)**:
  - Fixed menu item interleaving where Group 1 utility actions (*Rescan* and *Bluetooth Setup*) appeared between Group 0 playback targets. Offset Group 1 menu order values (`baseOrder = renderers.size() + 10`) so utility actions always remain strictly at the bottom below the group divider line.
- **Cover Art Flickering During Playback Progress Updates (`MainActivity`, `MusicTagAdapter`)**:
  - Suppressed full `RecyclerView` item rebinds (`adapter.notifyItemChanged`) and redundant Coil image loading requests on 1-second progress ticks during active playback.
  - Added `imageView.setTag(song.getPath())` guards on `barAlbumArt` (bottom navbar) and `mCoverArtView` (music list) to prevent Coil from re-fetching bitmaps when the same track is already loaded.
- **VectorDrawable Property Animation Crash (`ic_equalizer_active.xml`)**:
  - Fixed runtime `java.lang.IllegalArgumentException: Property: scaleY is not supported for FullPath` by wrapping animated bar paths in individual `<group>` tags with pivot points (`pivotX`, `pivotY="12"`).

## [3.18.15] - 2026-08-11

### Added
- **UI/UX Design Principles & ADRs (`DESIGN.md`)** — Added dedicated design documentation outlining product philosophy, gesture mapping, surface menu decoupling rules, player picker UX, and Architecture Decision Records (ADRs).

### Changed
- **Music List Interaction Model** — Redesigned item interactions to align with the app's core purpose (tag management):
  - **Single tap (row)** now always opens `TagsActivity` unconditionally, regardless of playback state. Previously the behaviour was inconsistent — opening the tag editor only when no player was connected.
  - **Cover art tap** is now the dedicated quick-play trigger. When a playback device is active, tapping the cover art immediately plays the track in context. When no device is available, a toast guides the user.
  - **Cover art play indicator** — A subtle play icon overlay is shown on the album art only when a playback device is connected and active, acting as a dual signal: "tap to play" and "a player is ready".
- **Menu Rationalization** — Split the shared action menu into two purpose-built menus:
  - **`⋮` Popup (single track)** — Focuses on quick playback actions (`Play Now`, `Play Next`, `Add to Queue`) and file-level operations (`Convert Format`, `Open in External App`). The playback group is hidden entirely when no player device is active. `Song Info`, `Move`, and `Delete` removed — no longer needed since single-click opens tags directly.
  - **Long-press Action Mode (multi-select)** — Focused exclusively on batch tag and file management: `Edit Tags`, `Move Files`, `Convert Files`, `Delete`, `Select All`. Playback actions removed to reduce clutter for bulk operations.
  - Introduced `menu_track_popup.xml` as a dedicated single-item popup menu, decoupled from `menu_main_actionmode.xml`.
- **Cast / Output Device Picker UX** — Resolved mixed affordances in the player selection popup:
  - **Auto-scan on open** — M-SEARCH is triggered the moment the popup opens, so DLNA devices are already being discovered as the user reads the list. No manual tap required in the common case.
  - **Visual group divider** — A horizontal divider (API 28+) now separates selectable player targets from utility actions, making it visually clear which items switch the output and which trigger system actions.
  - **Logical item order** — "Rescan for DLNA players" moved above "Bluetooth / System Output…". Rescan adds items to the list above it; Bluetooth exits the app — these are fundamentally different and now ordered by proximity to their effect.
  - **Empty state label** — When no players are discovered yet, shows a disabled "Scanning for players…" placeholder instead of a tappable "No players discovered" item.
- **Tag Activity "More Actions" Menu UX** — Organized `tag_more_actions_menu.xml` into three functional groups with icons and visual group dividers (API 28+):
  - **Tag Automation Group:** `Auto-Tag (MusicBrainz)` and `Search & Match Tags`.
  - **Audio Analysis Group:** `Verify Lossless Quality`.
  - **File & External Tools Group:** `Show in File Manager` and `Search Song on Web`.
  - Enabled icon rendering (`UIUtils.makePopForceShowIcon`) and group dividers for instant scannability.
- **Left & Right Slide Menu Architecture (`ResideMenu`)** — Refactored XML menu files into clean functional groups and removed deprecated legacy code:
  - **Left Slide Menu (`menu_music_collection.xml`):** Dedicated to **Music Content & Library Filtering** (Browse: All/Artists/Genres, Curate: Collections, Discover: Recently Added/Similar, Quality Grade).
  - **Right Slide Menu (`menu_music_mate.xml`):** Dedicated to **App System & Controls** (Manage Library, Settings, Storage & Notification Access Permissions, Diagnostics, About).


### Fixed
- **Pre-existing `PlaybackService` API mismatches** — Corrected three stale method calls that prevented compilation:
  - `PlaybackState.isPlaying()` → `playbackState.currentState == PlaybackState.State.PLAYING`
  - `PlaybackService.pause()` → `pausePlayer()`
  - `PlaybackService.play()` → `playSong(getNowPlayingSong())`
  - `PlaybackService.skipToNext()` → `skipToNextInQueue()`

---

## [3.18.14] - 2026-08-09


### Added
- **Bluetooth Audio Playback Suite**:
  - **Live Codec & Device Telemetry:** Detects active Bluetooth A2DP & BLE codecs (**LDAC**, **aptX**, **AAC**, **SBC**) and displays the Bluetooth device product name (e.g. `Sony WH-1000XM5`, `Bose QC45`) in the Audio Route Path.
  - **1-Tap System Audio Output Switcher:** Added `Bluetooth / System Output...` action to target player selector, launching native Android Media Output panel.
  - **Auto-Pause on Disconnect:** Registered `becomingNoisyReceiver` (`ACTION_AUDIO_BECOMING_NOISY` / `ACTION_ACL_DISCONNECTED`) to automatically pause local audio playback when wireless headphones or Bluetooth receivers disconnect.
- **Audio Route Path Naming Standard**:
  - Renamed legacy "Signal Path" to **Audio Route Path** across UI strings, resource files, menus, bottom sheets, and documentation for precise end-to-end audiophile route telemetry.

### Changed
- **Dedicated 3-Tab Architecture (`AudioHubBottomSheet`)**:
  - Transitioned Music Center to a full-height **3-Tab Architecture** (**`[ Playback | Queue | Server ]`**).
  - Dedicated **`Queue`** page provides full-height viewport displaying 8–12 upcoming tracks at once, complete with total remaining playback duration (`X tracks • Y min total`), drag-to-reorder, and swipe-to-remove.
  - Dedicated **`Playback`** page gives maximum vertical space to artwork, transport controls, and Audio Route Path telemetry.
- **Comprehensive Project Documentation Update**:
  - Updated `README.md`, `USER_GUIDE.md`, and `NETWORK_RESILIENCE.md` with complete documentation for recent Music Center features, Audio Route Path telemetry, and system architecture.

### Fixed
- **DLNA Target Activation & Auto-Transfer Stream**:
  - Fixed output target switching in `switchPlayer()` when selecting a remote DLNA renderer: ensures `startServers()` is invoked to guarantee HTTP media server readiness, and automatically initiates stream transfer (`playSong(activeTrack)`) when selecting a target renderer.

---

## [3.18.13] - 2026-08-08

### Added
- **Custom Audiophile Signal Path Geometries**:
  - Added 5 custom XML shape drawables (`shape_node_source.xml`, `shape_node_transport.xml`, `shape_node_target.xml`, `shape_node_target_bitperfect.xml`, `shape_node_connector.xml`) with asymmetric curves, chamfers, and HSL glowing strokes to visually distinguish pipeline stages.

### Changed
- **Playback Center Naming Symmetry**:
  - Renamed master bottom sheet tabs to **`Audio Transport`** and **`Network Streamer`** for precise audiophile terminology.
- **Full-Width 1-Line Signal Path Flow**:
  - Redesigned the Now Playing card (`sheet_now_playing_queue.xml`) to move album art and track metadata to the top row, placing the 3-stage Signal Path widget across the full width (`100% width`) below.
  - Streamlined signal path nodes to single 1-line chips (`FLAC 24/96` ➔ `Net Streamer` / `Local Transport` ➔ `USB Bit-Perfect ▾`) with compact padding and zero text truncation.

### Removed
- **Diagnostic Copy Button**:
  - Removed the legacy "Copy Diagnostic Report" clipboard button from the Signal Path telemetry view for a cleaner UI.

---

## [3.18.12] - 2026-08-07

### Fixed
- **Playback State Persistence**: Fixed a bug where Shuffle and Repeat modes were not remembered after restarting the app by persisting them using Android `SharedPreferences`.

### Removed
- **OrmLite Database Engine**: Fully deprecated and removed the legacy `db-ormlite` module.
- **Build Flavors**: Removed the database flavor dimension. The app is now compiled exclusively with Google's modern `Room` database architecture, significantly reducing maintenance overhead, improving type safety, and reducing the final APK size.

## [3.18.11] - 2026-08-07

### Added
- **Quick Action Buttons on Collections**:
  - Replaced redundant chevron on folder cards (Artist, Genre, Playlist) with dedicated **Play** and **Add to Queue** icons for frictionless listening session management without opening folders.

### Changed
- **OLED UI Refinement & Borders Elimination**:
  - Removed chunky `MaterialCardView` borders, excessive padding, and heavy shadows across remaining dialogs (`dialog_text_input.xml`, `dialog_item_list.xml`).
  - Restyled Now Playing Mini-Player and Queue sheet clear button to seamlessly blend with the "Neon on Dark" true black UI aesthetic.
- **Audio Hub Visibility**:
  - Lowered `AudioHubBottomSheet` maximum peek height from 82% to 65% of the screen, allowing users to comfortably see and interact with 2-3 list items lingering dynamically in the background while the sheet is open.

### Fixed
- **NPE in Quality Indicator**:
  - Fixed `NullPointerException` thrown in `QualityIndicatorView.java` when attempting to tint a null drawable.

---

## [3.18.10] - 2026-08-06

### Added
- **Unified Audio Hub Bottom Sheet Architecture**:
  - Consolidated `NowPlayingQueueSheet`, `SignalPathBottomSheet`, and `MediaServerManagementSheet` into a single, high-performance `AudioHubBottomSheet`.
  - Integrated a top tab bar switcher (`MaterialButtonToggleGroup`) for instant 1-tap switching between **Now Playing & Queue**, **Audio Signal Path**, and **Media Server Management** without sheet dismiss animations.
- **Unconditional Now Playing Queue Access & Target Player Selector**:
  - Made the Audio Hub bottom sheet accessible at all times from the floating playback bar, even when no track is currently playing.
  - Relocated target player picker and signal path buttons into the Audio Hub sheet header for clean, unified playback engine management.
  - Added custom vinyl disc vector icon with gold accent ring and note (`ic_now_playing_idle.xml`) for idle playback state.
- **Now Playing File Format Badge Styling**:
  - Standardized file format badges (e.g., `FLAC`, `MP3`, `DSD`, `MQA`) on Now Playing cards to use `apincer.android.mmate.ui.view.BadgeView`.
  - Applied identical codec styling and lossy/lossless background and text colors (`TagUtils.getCodecColor`, `TagUtils.getCodecBgColor`) as displayed in the song list (`MusicTagAdapter`).
- **Custom Audiophile Navigation Icons**:
  - Created `ic_nav_collections.xml` (stacked music library cards with note emblem) for opening the left Music Collections drawer.
  - Created `ic_nav_musicmate_menu.xml` (matching card container, border, depth shadow, and 3-slider audio tools emblem) for opening the right MusicMate side menu.

### Changed
- **Premium UI/UX Dialog Standardization**:
  - Refactored all 16 dialog and bottom sheet layouts to use a unified `RelativeLayout` header.
  - Standardized dialogs with a top-center translucent drag handle (`shape_dot_indicator`) and a top-right close button.
  - Enforced strong `24dp` elevation with pure black spot/ambient shadows to give blur-background dialogs true physical depth.
- **Semantic Theme & Color Modernization**:
  - Purged hardcoded `@color/white` and `@color/grey...` attributes across non-dialog layouts, migrating them to Material 3 semantic attributes (`?attr/colorOnSurface`, `?attr/colorOnSurfaceVariant`) for flawless DayNight theme switching.
  - Upgraded Light Mode surface to crisp `#FFFFFF` (from sepia warm white) and Dark Mode surface to true OLED `#000000` (from brownish charcoal) for maximum album art contrast.
  - Sharpened the glassmorphism edge on `bg_dialog_dark_blur` with a 30% white 1dp stroke and a 15% inner sheen gradient.

### Fixed
- **Now Playing Queue List Vertical Scroll Gesture**:
  - Disabled nested scrolling on the `ViewPager2` inner `RecyclerView` in `AudioHubBottomSheet` (`child.setNestedScrollingEnabled(false);`), allowing `BottomSheetBehavior` to correctly pass vertical scroll touch events to the Queue list (`sheet_queue_list`).
- **DLNA / DMR Player Target Auto-Selection Controls**:
  - Resolved issue where auto-selected DLNA renderer targets failed to accept transport actions (Next, Prev, Play, Pause, Stop) until manually re-selected in target picker popup.
  - Updated `autoSelectBestPlayer()` and `switchPlayer()` to initialize `controlledPlayerTargetId` with `controlled = true`, and added self-healing `isControllable()` checks.
- **Now Playing Queue Auto-Scroll & Swipe-to-Remove**:
  - Added automatic scrolling to the currently playing song in the queue list upon opening the sheet or when track changes occur.
  - Fixed empty space bug after swiping to remove songs from the queue by calling `adapter.notifyItemRangeChanged()` and handling empty state transitions.
- **Lossless Quality & Audio Authenticity Engine**:
  - **Quality Badge Color Reset**: Fixed default `upscaledScore` and `resampledScore` in `IconProviders.java` returning `1`, which previously forced quality badges to display red (`quality_scale_not_matched`) for all tracks.
  - **AIFF IEEE 80-Bit Extended Precision Sample Rate Parser**: Fixed IEEE 754 80-bit float parsing in `AudioDecoder.java` to compute mantissa and exponent, correctly resolving AIFF sample rates (e.g., 44.1 kHz).
  - **Fake Hi-Res & Upscaled Audio Verification**: Updated `AudioAuthenticityAnalyzer.java` to detect tracks with sample rates > 48 kHz but spectral rolloff ≤ 24 kHz or rolloff ratio < 0.75, accurately classifying upsampled CD content as `Upscaled Content`.
  - **PCM Bit Depth Decoding Fallback**: Updated `AudioDecoder.getBitsPerSample` to safely default to 16-bit PCM rather than throwing unhandled `IllegalArgumentException` on unexpected encodings.
- **TripMate Module Exception Handling**:
  - Replaced over 200 silent empty catch blocks (`// TODO: handle exception`) and method stubs across `TripMate` activities and parsers with explicit error and warning logging (`Log.e` / `Log.w`).

---

## [3.18.9] - 2026-08-03

### Added
- **Audio Converter Sample Rate Downsampling**:
  - Added ExposedDropdownMenu for target sample rate selection in Convert Files dialog (`Original`, `96 kHz`, `48 kHz`, `44.1 kHz`).
  - Overloaded `FFMpegHelper.convert` and `FileOperationTask.encodeFiles` to support `-ar <sampleRate>` downsampling flag.

### Fixed
- **FFmpeg 24-Bit Encoding Error**:
  - Fixed invalid `-sample_fmt s24` flag by replacing it with valid `-sample_fmt s32` for 24-bit FLAC/ALAC audio conversion.
- **Converted Track Metadata Inheritance & Instant Library Indexing**:
  - Updated `FileOperationTask.java` to copy track metadata (`Title`, `Artist`, `Album`, `AlbumArtist`, `Genre`, `Track`, `Year`, `Comment`, `Composer`, `Publisher`) from source track to newly converted track.
  - Automatically writes metadata tags to disk via `TagWriter.writeTagToFile` and updates database, ensuring converted files (`_001.flac`) immediately match active Artist/Album view filters upon completion.
- **Selection State & Playback Interruption**:
  - Prevented selection mode from clearing during playback status updates or background dataset refreshes by guarding `isListFollowNowPlaying` / `scrollToSong()` with `(actionMode == null)` and preserving `SelectionTracker` selection keys.
- **Action Dialog List View Layout & Icons**:
  - Redesigned `view_action_listview_item.xml` layout to use a responsive `LinearLayout` (`layout_weight="1"` for track name with `ellipsize="end"`), fixing collapsed/hidden song names.
  - Added `getTrackDisplayName()` fallback helper (`getSimpleName()` -> `getTitle()` -> `FileUtils.getFileName()`).
  - Synchronized dialog header icons with context menu action bar icons for **Move Songs** (`rounded_drive_file_move_24`) and **Convert Files** (`rounded_swap_horiz_24`).

## [3.18.8] - 2026-08-02

### Added
- **Split-Tap Zone on Song List Items**:
  - Tapping the **Song Title / Details** plays the track immediately.
  - Tapping the **Album Cover Art Thumbnail** directly opens **`TagsActivity`** (Metadata Editor) for fast 1-tap tag editing.
- **Now Playing & Queue Bottom Sheet (`NowPlayingQueueSheet`)**:
  - Implemented modern bottom sheet showing Now Playing album art, track details, technical format badge (e.g. `FLAC 352.8 kHz / 24bit`), active player badge, and scrollable queue list with current track gold highlighting.
  - Added embedded **Previous**, **Play / Pause**, and **Next** transport controls inside the Now Playing card.
  - Added **Play All** button in queue header to instantly enqueue all currently displayed library songs into the playback queue and begin playback.
  - Added **Shuffle Toggle (🔀)** and **Repeat Mode Toggle (🔁)** controls.
  - Added dedicated gold **Signal Path icon button** (`ic_baseline_audio_path_24`) in sheet header.
  - Added **Tap-to-Scroll** list navigation: tapping the track card or any queue row dismisses the sheet and scrolls the main library list to that song's position.
- **Top Header & Selection Mode Icon Audit & Alignment**:
  - Replaced top header back button icon with standard left navigation arrow (`@drawable/ic_baseline_arrow_back_24`).
  - Aligned selection mode contextual action bar icons: **Play Now** (`▶`), **Add to Queue** (`≡+`), and **Song Info** (`📝`).
- **Top-Anchored Quick Player Picker (`showPlayerPickerPopup`)**:
  - Introduced a top-right anchored popup menu triggered by the header cast button for fast 1-tap renderer switching right beneath the tap target.
- **Dynamic Media Server Status Tint**:
  - Bound `navigation_media_server` icon in bottom navigation bar to `MediaServerManager.getServerStatus()` LiveData to dynamically display active running status (Teal tint when server is active, Muted tint when offline).

### Improved
- **Unified Floating Navigation & Playback Dock**:
  - Combined the floating playback bar and bottom navigation into a single, high-efficiency floating dock (`20dp` radius card).
  - **Idle State**: Displays Library icon, default app title, dynamic Media Server status icon, and Menu.
  - **Playback Active State**: Seamlessly expands to show mini album art, marquee track title, player target subtitle (e.g. `HiBy R3 • DLNA Renderer`), and clean gesture access.
  - **Gestures**: Tapping track title or artwork opens `NowPlayingQueueSheet`; long-pressing opens `SignalPathBottomSheet`.
  - Maximizes vertical list viewable area and eliminates double bar UI overlap.
- **Single Web Server Engine Build**:
  - Consolidated legacy Gradle `server` build flavor dimension into `src/main` with **HttpCore** as default engine. All 3 engines (`HttpCore`, `NIO`, `Netty`) remain dynamically switchable from App Settings in a single APK build.
- **Streamlined Navigation & Signal Path Access**:
  - Integrated direct transition from `NowPlayingQueueSheet` (via header icon button) to `SignalPathBottomSheet`.
  - Added long-press shortcut on Floating Playback Bar to quickly open `SignalPathBottomSheet` for audiophiles and power users.
  - Cleaned up bottom navigation bar by removing duplicate signal path label triggers.

### Added
- **Unified Player Target Display Formatting**:
  - Added centralized 2-line and 1-line player label formatters in `PlayerNameUtils`.
  - Display standardized titles and sub-metadata across Media Server Management Sheet, player selection dropdowns, and Signal Path Bottom Sheet.
  - Distinct player target type labels: **DLNA Renderer** (e.g. `HiBy R3 (192.168.1.50 • DLNA Renderer)`), **Web Streaming**, and **Android App** (e.g. `Poweramp (com.maxmpz.audioplayer • Android App)`).
- **Runtime Server Engine Selector (App Settings)**:
  - Implemented `CompositeWebServer` proxy to allow users to switch HTTP streaming web server engines at runtime (**SonicNIO**, **CoreHTTP**, or **Netty**) directly from App Settings without rebuilding or restarting the application process.
- **Full DLNA Controller (DMC) Transport Actions**:
  - Implemented `playerPause()`, `playerSeek()`, and `playerSetVolume()` in `MediaServerHubImpl` to provide full control over remote DLNA renderers (`AVTransport` + `RenderingControl` UPnP services).
- **IP Address Sanitization Utility**:
  - Added `NetworkUtils.extractIpAddress()` to extract clean IPv4 addresses by stripping schemes, ports, leading slashes, and paths.

### Improved
- **Ultra High-Res (352.8 kHz / DXD) Streaming Optimizations**:
  - Increased file streaming chunk size to **256 KB** and restored TCP socket send buffers (`SO_SNDBUF`) to **512 KB** across all engines (`SonicNIO`, `CoreHTTP`, and `Netty`) to prevent mid-track buffering and stalls during high-rate (>10 Mbps) FLAC streaming.


### Fixed
- **DLNA Renderer Name Resolution**:
  - Fixed issue where incoming HTTP stream requests for discovered DLNA Renderers (like HiBy R3) were fallback-named as generic `"Streaming Player"` or `"Mozilla"`.
  - Added automatic resolution in `MusicMateServiceImpl` to map incoming HTTP stream IP addresses to registered UPnP DMR device friendly names.
- **HiBy User-Agent Parsing**:
  - Added explicit HiBy player detection in `PlayerNameUtils.getFriendlyNameFromUserAgent()`.
  - Filtered out generic `"Mozilla"` fallback labels for embedded DAP web browser user agents.
- **DLNA Renderer Disappears After Device Reformat / Factory Reset**:
  - Fixed issue where a DLNA device (e.g. HiBy M3) would no longer appear in the player picker after being reformatted or factory reset (new UDN / new IP address assigned).
  - Added `refreshDiscovery()` to `MediaServerHub` SPI and `MediaServerHubImpl` (triggers immediate UPnP SSDP M-SEARCH).
  - Exposed `refreshPlayerDiscovery()` in `PlaybackService` interface and implemented in `MusicMateServiceImpl`.
  - Added **"🔄 Rescan for players"** action at the bottom of the Cast / Player Picker popup — triggers an immediate rescan and automatically reopens the picker after 2.5 s showing freshly discovered devices.

---

## [2026.06.0] - 2026-06-15

### Added
- **Network Resilience Layer**:
  - Automatic media server pause/resume on Wi-Fi state loss and recovery (`ConnectivityManager.NetworkCallback`).
  - Hotspot mode auto-binding (`WIFI_AP_STATE_CHANGED`).
  - Wake locks, Wi-Fi locks, and Multicast locks for continuous background streaming.

---

## [2026.05.0] - 2026-05-31

### Improved
- **CoreHTTP Engine Production-Grade Optimization**:
  - Memory footprint reduction to ~64 KB per connection using object pooling and `FileChannel.transferTo()` zero-copy streaming.
  - Enhanced WebSocket handling and GC pause optimizations (<30 ms).
