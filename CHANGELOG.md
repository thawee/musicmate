# CHANGELOG

All notable changes to the **MusicMate** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.19.5] - 2026-09-10

### Added
- **Pillar 1: Responsive Badge Synthesis & Screen Density (`AudioBadges.kt`, `DynamicRangeMeters.kt`, `TrackListItem.kt`)**:
  - Implemented `UnifiedAudioBadge` fusing format tier, bit depth, and sampling rate into a single compact micro-capsule (`[● HI-RES 24/96]`, `[● CD 16/44.1]`, `[● DSD64]`, `[● 320k]`).
  - Added `compact: Boolean = false` mode to `DynamicRangeMeter` to collapse bar graphics into clean text (`DR12`).
  - Added responsive screen width detection (`screenWidthDp < 390`) in `TrackListItem`: automatically toggles unified badges and compact DR meter on compact viewports, liberating ~40dp of horizontal breathing room and completely eliminating title/artist ellipses truncation.
- **Pillar 2: Dual Persona "Curator vs. Listener" Interaction Modes (`Settings.java`, `Constants.java`, `SettingsScreen.kt`, `MainActivity.java`)**:
  - Added `PREF_TAP_ACTION_MODE` preference (`"listen"` vs `"curate"`).
  - Added "INTERACTION MODE" card in `SettingsScreen.kt` with segmented switcher between `🎧 Listener Mode` and `🏷 Curator Mode`.
  - In `Listener Mode`: single-tap row starts instant playback, long-press opens tag editor (`TagsActivity`).
  - In `Curator Mode`: single-tap row opens `TagsActivity` (preserving existing power-curator workflows), while tapping cover art starts playback.
- **Pillar 3: Visual "Audiophile Query Studio" (Custom Smart Playlists) (`CreateSmartPlaylistDialog.kt`, `PlaylistRepository.java`, `MainScaffold.kt`)**:
  - Built interactive Compose query builder dialog with real-time matching track count & storage telemetry (`⚡ Live Match: X tracks • Y GB`), Dynamic Range (DR) threshold slider ($0\dots 16$), and audio tier chips (`Hi-Res`, `Lossless`, `DSD`, `24-bit Studio`).
  - Added custom smart playlist creation, editing, and deletion in `PlaylistRepository` backed by `custom_playlists.json` persistence.
  - Added "New Smart Playlist" top bar action button (`rounded_playlist_add_24.xml`) in `MainScaffold` when browsing playlists.
- **Pillar 4: Vintage Analog Needle VU Meter & Studio Telemetry (`AnalogVUMeter.kt`, `NowPlayingPage.kt`)**:
  - Built `AnalogVUMeter` in Compose Canvas with dual stereo channel dials (Left & Right), calibrated $-20\text{ dB} \dots +3\text{ dB}$ logarithmic arc scale, and red overload zone.
  - Implemented ANSI standard ballistic spring-damper physics (300ms rise, ~1.5% overshoot) with stereo phase decorrelation, driven by playback state, track dynamic range (DR), volume, and ReplayGain true-peak telemetry.
  - Supported 3 legendary audiophile color themes switchable by tapping the meter chassis: Accuphase Champagne Gold, McIntosh Ocean Blue, and Studio Slate Reference.
  - Embedded into the Audio Anatomy flip card in `NowPlayingPage.kt`.
- **Unit Test Coverage (`VUMeterAndBadgeTest.kt`)**:
  - Added unit test suite verifying `VUMeterTheme` catalog and `getUnifiedBadgeText` formatting across Hi-Res FLAC, CD Lossless, DSD, and MP3.

### Fixed
- **Cover Art Gesture Collision & Pager Swiping Conflict (`NowPlayingPage.kt`)**:
  - Removed conflicting horizontal drag gesture detector (`detectDragGestures`) from the playback screen's album art container.
  - Eliminated accidental track skips caused by minute finger rolls during taps and resolved touch event cancellation for 3D card flips (`onTap`) and play/pause (`onDoubleTap`).
  - Restored frictionless horizontal swiping across the entire playback viewport to navigate between the `[Playback]`, `[Queue]`, and `[Server]` tabs in `AudioHubSheet`'s `HorizontalPager`.
- **Premature Track Skip at ~58s & DLNA Stream Truncation (`PartialFileProducer.java`, `MediaServerHubImpl.java`)**:
  - Eliminated the 4MB in-memory buffer splicing bug in HttpCore's `PartialFileProducer` that caused HTTP streaming connections to abort prematurely at 4MB (which equals ~58 seconds of playback at typical bitrates), causing DLNA renderers to run out of data and skip.
  - Re-architected `PartialFileProducer` to stream directly from `FileChannel` in 64KB chunks with OS kernel page cache warming via `AudioStreamCacheManager.preloadTrack()`.
  - Corrected DIDL-Lite metadata track duration calculation in `MediaServerHubImpl`: converted `song.getAudioDuration()` (seconds) to milliseconds before calling `formatDurationForDidl()`, eliminating incorrect sub-second durations (`0:00:00.238` ➔ `0:03:58.000`).
  - Added robust parsing for millisecond fractions (`HH:MM:SS.mmm`) and 2-part formats (`MM:SS`) in `parseTimeToSeconds()`, preventing `NumberFormatException` during renderer status polling.
  - Added unit test suite `MediaServerHubTimeParsingTest` in `:server-jupnp`.

## [3.19.4] - 2026-09-10

### Added
- **Active ReplayGain 2.0 / EBU R128 Playback Leveling Engine (`ReplayGainManager.java`, `AndroidPlayerController.java`)**:
  - Real-time loudness normalization during local on-device playback using ExoPlayer linear volume scaling ($10^{\frac{\text{gainDb} + \text{preAmpDb}}{20}}$).
  - Configurable playback modes: `Track Gain` (standard loudness matching), `Album Gain` (preserves album master volume progression), and `Off`.
  - Configurable pre-amp gain $(-12 \text{ dB} \dots +12 \text{ dB})$ and anti-clipping true-peak limiter safeguard preventing digital clipping distortion ($scalar \times peak \le 1.0$).
  - Seamless gapless audio transition support: ReplayGain volume scaling recalculates and applies dynamically on `onMediaItemTransition()`.
  - Universal external player tag interoperability: writes standardized Vorbis Comments (`REPLAYGAIN_TRACK_GAIN`, `REPLAYGAIN_TRACK_PEAK`), ID3v2 TXXX, and MP4 tags to disk files, enabling Poweramp, Foobar2000, USB Audio Player PRO (UAPP), and Neutron to read loudness tags.
- **Audiophile Playback Settings Card (`SettingsScreen.kt`, `SettingsActivity.kt`)**:
  - Modern Jetpack Compose controls for ReplayGain mode selector, pre-amp slider steps, and anti-clipping true-peak limiter toggle.
- **Audio Anatomy ReplayGain Telemetry (`NowPlayingPage.kt`, `TagsTechnicalPage.kt`, `NowPlayingState.kt`)**:
  - Dynamic ReplayGain status pill (`RG -4.2 dB`) displayed on the Audio Anatomy flip card and dedicated technical diagnostics card in the tag editor.
- **Dynamic Smart Playlists Engine (DR & Authenticity) (`PlaylistEntry.java`, `PlaylistRepository.java`, `playlists.json`)**:
  - Dynamic rule matching engine evaluating on-device audio telemetry (`minDrScore`, `hiresOnly`, `dsdOnly`, `losslessOnly`, `minBitDepth`, `minSampleRate`).
  - Added 4 flagship built-in audiophile smart playlists:
    1. *Audiophile Sanctuary (DR12+)*: High dynamic range uncompressed masterings (DR12 and above).
    2. *Studio Masters (Hi-Res)*: 24-bit studio quality and high sample rate masters (>= 24-bit / 48kHz).
    3. *Pure DSD Archive*: 1-bit Direct Stream Digital recordings (DSD64, DSD128, DSD256).
    4. *Lossless Master Vault*: Bit-perfect lossless CD audio and studio recordings (FLAC, ALAC, WAV, AIFF, DSD).
  - Cross-platform availability: automatically evaluated across the Native Android UI, DLNA/UPnP Media Server, Web Remote UI, and M3U playlist exports.
- **Unit Test Suite Expansion (`PlaylistEntrySmartTest.java`, `ReplayGainManagerTest.java`)**:
  - Added unit test suites verifying smart playlist rules matching and ReplayGain dB-to-linear conversion with peak limiter clamping.

### Changed
- **Playlist Card Typography & Layout Density (`FolderListItem.kt`)**:
  - Expanded playlist titles and descriptions to `maxLines = 2` with balanced line heights to eliminate aggressive ellipses truncation (`"Audiophile Sanct..."`, `"Lossless Master ..."`).
  - Compacted action buttons to 36dp with 18dp icons to provide full horizontal breathing room.
- **Audiophile Insignia Cover Art for Smart Playlists (`FolderListItem.kt`)**:
  - Replaced plain placeholder grey boxes with custom gradient-rendered insignias and luxury typographic badges (`DR 12+ / AUDIOPHILE`, `24-BIT / STUDIO`, `DSD / 1-BIT DIRECT`, `VAULT / LOSSLESS`, `CLASSICAL / HERITAGE`, `REFERENCE / ARCHIVE`).

### Fixed
- **Top Header Collection Counter Unit (`MainActivity.java`)**:
  - Corrected header subtitle to dynamically display `"10 Playlists"` (or `"Artists"`, `"Genres"`) instead of erroneously defaulting to `"10 Tracks"` when viewing category collections.
- **Empty Playlist Track Count Subtitle (`FolderListItem.kt`)**:
  - Provided a clean `"0 tracks"` fallback instead of rendering an empty subtitle line for unpopulated smart playlists.

## [3.19.3] - 2026-09-08

### Added
- **Local ExoPlayer Precision Seeking (`AndroidPlayerController.java`, `MusicMateServiceImpl.java`)**:
  - Implemented `seekTo(positionMs)` with main thread dispatch in `AndroidPlayerController`, connecting the UI seekbar to local audio playback.
- **Defensive Seek Clamping (`MusicMateServiceImpl.java`)**:
  - Bound seek requests between `0` and current track duration in milliseconds, preventing negative offsets or decoder overrun errors.
- **ExoPlayer Gapless Preload & Auto-Advance Handover (`AndroidPlayerController.java`, `MusicMateServiceImpl.java`)**:
  - Wired `setNextTrack()` directly into ExoPlayer's secondary media item queue for seamless on-device gapless playback; handled `MEDIA_ITEM_TRANSITION_REASON_AUTO` smoothly without redundant manual skip triggers.
- **Lifecycle-Safe Activity State Subscriptions (`MainActivity.java`)**:
  - Added `playbackStateSubscription` lifecycle tracking, properly closing scheduled 500ms executor subscriptions on rotation, service reconnect, and Activity destruction to prevent memory and scheduler leaks.
- **Unit Test Coverage for Queue Resilience (`QueueManagerTest.java`)**:
  - Added test cases covering shuffle stability across track changes, drag-and-drop index synchronization, in-place queue track selection, and queue clearing.

### Changed
- **Sound Grade & Codec Query Alignment (`RoomDbHelper.java`)**:
  - Aligned dynamic SQL `buildWhereClause()` with `TrackDao` Room queries using `LOWER(audioEncoding)` and full support for all formats (`dsd`, `dsf`, `dff`, `sacd`, `aac`, `mpeg`, `mp3`, `m4a`, `ogg`, `opus`, `wma`, `flac`, `alac`, `aiff`, `aif`, `wave`, `wav`).
  - Ensured accurate track counts and total durations across all sound grade categories.
- **Directory Path Filtering & Normalization (`RoomDbHelper.java`)**:
  - Normalized directory queries with trailing slash enforcement in `findInPath()` and mapped folder path filtering under `LIBRARY` criteria to prevent partial prefix sibling folder collisions and full-library query fallbacks.

### Fixed
- **Queue Track Rip-and-Append (`MusicMateServiceImpl.java`, `QueueManager.java`)**:
  - Fixed issue where playing a track already in the queue removed it from its current position and re-appended it to the end; now selects track in-place via `setCurrentTrack()`, maintaining album sequence integrity.
- **Pause / Resume State Preservation (`AndroidPlayerController.java`, `MediaServerHubImpl.java`, `MusicMateServiceImpl.java`, `MainActivity.java`)**:
  - Added `resume()` to `AndroidPlayerController` (`internalExoPlayer.play()`) and `playerResume()` to DLNA streamer, preserving playback progress instead of restarting tracks from 0:00.
- **Atomic File Move Backup Check (`FileSystem.java`)**:
  - Guarded backup creation in `safeMove()` with `targetFile.exists()`, preventing file moves to new paths from failing due to nonexistent targets.
- **Queue Shuffle Order Invariance (`QueueManager.java`)**:
  - Preserved randomized `shuffleOrder` across track transitions instead of re-shuffling remaining tracks on every song change.
- **Queue Drag-and-Drop Index Desynchronization (`QueueManager.java`)**:
  - Synchronized `currentIndex` and `playbackIndex` to follow the active track during drag-and-drop reordering.
- **Notification Play/Pause Button State (`MediaNotificationBuilder.java`, `MusicMateServiceImpl.java`)**:
  - Fixed notification playback state synchronization to correctly display Pause when playing and Play when paused during local and casting sessions.
- **ID3 Custom Taxonomy Writing for MP3 & DSF (`JThinkWriter.java`)**:
  - Added `AbstractID3v2Tag` handling and modernized `addTxxx()` to write `STYLE`, `MOOD`, and `ORIGIN` frames across ID3v2 versions, ensuring edits to MP3 and DSF files persist correctly.
- **Non-Interactive FFmpeg File Overwrite (`FFMpegHelper.java`, `FFMpegWriter.java`)**:
  - Added global `-y` flag across all FFmpeg cover art extractions, removals, format conversions, and tag updates to prevent background process hangs on pre-existing files.
- **Playing Queue Clearance & Thread Safety (`RoomDbHelper.java`, `QueueManager.java`)**:
  - Synchronized `emptyPlayingQueue()` and reset shuffle/playback indices to prevent stale state retention.

## [3.19.2] - 2026-08-30

### Added
- **Centralized Design Tokens Architecture (`MusicMateDesignTokens.kt`, `MusicMateTheme.kt`)**:
  - Centralized design tokens across Surfaces (Obsidian `#121212`, Charcoal `#1E1E1E`, Elevated `#2C2C2C`, Glassmorphic Cards `#D9101010`), Brand Accents (Gold `#FFD700`, Warm Amber `#FFB300`, Acoustic Teal `#80CBC4`), Audio Provenance (DSD Cyan, Hi-Res Gold, MQA Magenta, CD Sky Blue, Lossy Slate), Dynamic Range Temperature Spectrum, and standard geometry tokens.
- **Adaptive Chromatic Player & Tactile Haptics (`NowPlayingPage.kt`, `AudioBadges.kt`)**:
  - Enhanced seekbar with dynamic active track ambient glow derived from the current album art palette with a dual-ring glowing halo thumb (colored halo + high-contrast white core).
  - Added tactile micro-haptic feedback (`LocalHapticFeedback`) to Quick-Fix chips, seekbar scrubbing, and volume adjustment.
- **Brand Identity & Network Asset Harmonization (`iconpng64.png`, `iconpng128.png`, `ic_notification_default.png`, `mipmap-*/ic_launcher.png`)**:
  - **DMS Server DLNA/UPnP Icons:** Generated high-resolution 64×64 and 128×128 PNGs featuring the official Golden "M" emblem on dark radial obsidian, replacing legacy orange flame assets across all network control points (WiiM, BubbleUPnP, mconnect, Foobar2000).
  - **Material 3 Status Bar Notification:** Replaced dated raster glyph with a crisp, pure white "M" brand silhouette on transparent background (`ic_notification_default.png`).
  - **Legacy Launcher Fallbacks:** Re-rendered all mipmap densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi) to guarantee 100% brand consistency on legacy launchers and dialogs.
- **Unified Immersive Hero Cover Art Layout (`activity_tags.xml`, `TagsActivity.java`, `shape_bottom_cover_scrim.xml`)**:
  - Transformed the Tag Preview header to match the Now Playing playback experience with a clean 1:1 square artwork viewport free of top scrims.
  - Grouped Song Title (`panel_title`) and dynamically formatted subtitle (`{Artist} • {Album}`) at the bottom of the cover art over a smooth cinematic dark gradient scrim (`shape_bottom_cover_scrim.xml`).
  - Completely eliminated the redundant split `[ Artist | Album ]` two-column layout (`fragment_editor_preview.xml`) and disruptive text click listeners.
- **4-Tier Audiophile Header Hierarchy & Telemetry Footer Strip (`AudioBadges.kt`, `RelatedTracksSheet.kt`, `TagsViewModel.kt`, `DialogInterop.kt`)**:
  - Reorganized the Tag Preview Header into a clear 4-tier narrative: **Tier 1 (Fidelity Badges)** ➔ **Tier 2 (2-Line Provenance Capsules)** ➔ **Tier 3 (Musical Taxonomy Chips)** ➔ **Tier 4 (Technical Telemetry Footer)**.
  - Relocated the monospace specs strip (`FLAC • 24/96 • 4608 kbps • Stereo • 05:54 • 198 MB`) to the bottom of the card with dedicated breathing room (`7.dp`), acting as a clean technical grounding baseline.
  - Embedded a structured 2-line Studio Provenance block on `TagPreviewHeader`: Row 1 = `[ 👤 {Artist} • N ❯ ]` (Gold) + `[ 💿 {Album} • N ❯ ]` (Teal), Row 2 = `[ 📁 {Folder} • N ❯ ]` (Slate Blue) with live database track counts and tactile haptic feedback.
  - Positioned a dedicated **Musical Taxonomy Tier** directly underneath Provenance with intuitive category glyphs: `[ 🎸 Genre ]`, `[ 🎭 Mood ]`, `[ 🎨 Style ]`, and `[ 🌏 Origin ]`.
  - Implemented `RelatedTracksSheet` Compose modal bottom sheet for seamless, in-place discography browsing without exiting the tag editor, featuring real-time audiophile tier badges, 1-tap playback, `Play All`, `Queue All`, and `View in Library` actions.
  - Fixed database scoping for `FILTER_TYPE_PATH`, `FILTER_TYPE_ARTIST`, `FILTER_TYPE_ALBUM` across `RoomDbHelper.buildWhereClause()` and `TagRepository.findByCriteria()`, and introduced `findByAlbum(album)` sorted by track sequence.
- **Expanded "More..." Power Menu & Quick-Fix Actions (`tag_more_actions_menu.xml`, `AudioBadges.kt`, `DialogInterop.kt`)**:
  - Added grouped Material 3 items across Playback (`Play Track Now`, `Add to Queue`), Tag Curation (`Auto-Tag MusicBrainz`, `Search Online`, `Smart Clean & Format`), Audio Auditing (`Lossless Spectrum Verifier`), and File Utilities (`Open in File Manager`, `Search on Web`, `Share Audio File`, `Reload Raw Tags`).
  - Enabled expanded audiophile quality tier badges (`[● CD QUALITY]`, `[● HI-RES LOSSLESS]`, `[● 24-BIT STUDIO]`, `[● DSD AUDIO]`, `[● MQA MASTER]`) on the Tag Preview Header to achieve 100% visual parity with the Now Playing playback sheet.
- **Command Bar Micro-Labels & Accessibility Tooltips (`activity_tags.xml`, `strings.xml`, `TagsActivity.java`)**:
  - Added clear text labels alongside vector icons on Row 2 (`[✨ Format]`, `[📄 From File]`, `[💾 Save]`, `[🔄 Reload]`, `[🖼️ Extract]`, `[🗑️ Remove Art]`) paired with descriptive `TooltipCompat` tooltips.
- **Tactile Micro-Haptics & Pro Long-Press Shortcuts (`TagsActivity.java`)**:
  - Added `performHapticFeedback` on all button taps and long-presses.
  - Long-pressing `[Format]` triggers the **Full Clean Pipeline** (Junk noise removal + Title Case + Thai encoding repair in a single pass); long-pressing `[Save]` triggers **Save & Close**.
- **Dynamic Multi-Track Batch Badging (`TagsActivity.java`)**:
  - Dynamically displays selected track counts on buttons (`Delete (N)`, `Organize (N)`, `Save (N)`) when editing batches of songs.
- **Embedded Artwork & Metadata Health Inspector (`TagsTechnicalPage.kt`)**:
  - Added real-time artwork dimension readout (e.g. `1400x1400 px`), MIME type, file size in KB, and visual UHD / HD / Low-Res rating badge.
  - Added an 8-point quality standard assessment (Title, Artist, Album, Year, Genre, Track#, Artwork, Lossless) with percentage health score.
- **Direct Cover Art Interaction (`TagsActivity.java`)**:
  - Added tap-on-artwork action sheet (Online Search, Gallery Photo Picker via `ActivityResultLauncher`, Extract to Folder, Remove Art).

### Changed
- **Accessible 2-Row Bottom Command Bar Architecture (`activity_tags.xml`, `TagsActivity.java`)**:
  - Structured the bottom dock into two distinct functional tiers: Row 1 for permanent, high-frequency file operations (`[Delete]`, `[Organize]`, `[More...]`), and Row 2 for active fragment actions (`[Edit Song Info] | [Save]` in preview, `[Format] | [From File] | [Save]` in editor, `[Reload] | [Extract] | [Remove Art]` in tech info), maximizing thumb reach and touch accessibility.
- **Unified Obsidian Preview Header (`TagPreviewHeader`, `AudioBadges.kt`)**:
  - Replaced fragmented XML TextViews with a pure Compose obsidian header combining `QualityBadge`, `ResolutionBadge`, `DynamicRangeMeter`, `RatingBadge`, interactive Tag Pills (Origin, Genre, Mood, Style), and tabular monospace telemetry strip (`FLAC • 24/96 • 1411 kbps • Stereo • 04:23 • 45.2 MB`).
- **Asynchronous Technical Diagnostics Extraction (`TagsTechnicalPage.kt`)**:
  - Offloaded synchronous `TagReader.readFullTag`, `FFMPegReader.extractTagFromFile`, and reflection fields to `Dispatchers.IO` with `produceState`, eliminating UI frame drops during tab transitions.
- **Elimination of On-Screen Volume Slider for Audiophile Bit-Perfect Clarity (`NowPlayingPage.kt`, `DESIGN.md`)**:
  - Removed persistent on-screen horizontal volume slider row, preventing accidental seek touch collisions and UPnP SOAP volume command flooding on single-threaded DAPs.
  - Reclaimed 40dp+ vertical viewport space for Album Artwork, chromatic glowing Seekbar, and primary transport controls. Local playback uses phone hardware volume buttons; streaming uses physical DAC/DAP analog dials for 100% bit-perfect output.
- **Song Info Editor Reactive State Synchronization (`TagsEditorPage.kt`, `TagsEditorFragment.kt`, `TagsTechnicalFragment.kt`)**:
  - Removed redundant `PREVIEW -> Unknown Title` box from `TagsEditorPage.kt` and wired reactive `StateFlow` collection with `LaunchedEffect` to populate all form fields immediately upon background database load.

### Fixed
- **DLNA / UPnP DMR Seeking & Position Scrubbing on HiBy R3 (`MediaServerHubImpl.java`)**:
  - Replaced millisecond duration formatting (`%d:%02d:%02d.%03d`) with strict standard `HH:MM:SS` format (`%02d:%02d:%02d`) for UPnP `Seek` actions, resolving seek failures and SOAP errors on HiBy R3 / HiBy OS and embedded DAP renderers.
  - Resolved renderer lookup across UDN formats and prefix variations with `resolveRenderer()`.
  - Maintained active 1-second position polling throughout active playback (`serverStatus == CAST`) and removed premature polling termination on sporadic GENA events.
- **Safe-by-Default DLNA Queue Preload Allowlist (`MediaServerHubImpl.java`, `DMRPlayer.java`, `MusicMateServiceImpl.java`)**:
  - Configured DLNA gapless preload (`SetNextAVTransportURI`) to be **opt-in only for verified hardware streamers** (`WiiM`, `Linkplay`, `Eversolo`, `Zidoo`, `Linn`, `Auralic`).
  - All generic DLNA renderers, DAPs (`HiBy`, `Shanling`, `FiiO`, `Astell&Kern`), Sonos, and smart TVs default to `supportsPreload = false`, leveraging MusicMate's ultra-reliable RAM pre-caching (`AudioStreamCacheManager`) and event-driven discrete handover to eliminate decoder buffer lockups across all consumer renderers.
  - Added natural track completion detection when renderers transition to `STOPPED` at end-of-track, immediately notifying `playbackCallback.onPlaybackCompleted()` to advance to the next track in the queue with a `durationMs + 1.5s` safety fallback timer.
- **Lossless Spectrogram Resampling & Ultrasonic Nyquist Preservation (`SpectrogramGenerator.java`)**:
  - Removed hardcoded `-ar 48000` downsampler that artificially truncated genuine 96kHz and 192kHz Studio Masters at 24kHz, preserving full ultrasonic frequencies up to 48kHz.
- **Spectrogram Cache Collision & Concurrency Race Condition (`SpectrogramGenerator.java`)**:
  - Replaced static `/spectrogram.jpg` path with dynamic per-track timestamped hash caching (`spectrogram_<hash>.jpg`) and automated stale file eviction.
- **Case-Insensitive DSD/DSF Query Integration (`TrackDao.java`)**:
  - Updated Room SQL queries to `LOWER(audioEncoding) IN ('dsd', 'dsf', 'dff', 'sacd')`, ensuring `.dsf` tracks (90%+ of DSD libraries) and mixed-case tags are correctly loaded in DSD smart playlists and duration/track counts.
- **Room DAO Format Coverage & Case Normalization (`TrackDao.java`)**:
  - Standardized Hi-Res (`alac`, `flac`, `aiff`, `aif`, `wave`, `wav`) and Compressed (`aac`, `mpeg`, `mp3`, `m4a`, `ogg`, `opus`, `wma`) queries to prevent undercounting.
- **Unsaved Edits Back-Press Discard Dialog Sync (`TagsActivity.java`, `TagsEditorFragment.kt`)**:
  - Connected Compose editor modification state with Activity `handleOnBackPressed()` to prevent accidental loss of user edits without confirmation.
- **Redundant Duplicate Genre Display (`fragment_editor_preview.xml`, `TagsActivity.java`)**:
  - Removed legacy XML `panel_genre` so `TagPreviewHeader`'s interactive Compose Genre pill is the single source of truth.
- **Soft-Keyboard IME Insets & Scrolling Bottom Occlusion (`TagsEditorPage.kt`)**:
  - Added `Modifier.imePadding()` and a bottom content spacer (`Spacer(modifier = Modifier.height(72.dp))`) to prevent form inputs from being hidden under the keyboard.
- **Batch Multi-Value Placeholder Usability (`TagsEditorPage.kt`)**:
  - Automatically clears `" - "` placeholder on field focus and edit to prevent accidental overwriting with literal placeholder text.
- **Streaming Engine Zero Descriptor Leaks (`PartialFileProducer.java`)**:
  - Enforced immediate closure of `RandomAccessFile` and `FileChannel` upon end-of-stream and EOF, preventing open file descriptor leaks over multi-hour playback sessions.
- **Rapid Skip Cache Eviction (`AudioStreamCacheManager.java`)**:
  - Added atomic task tracking in `AudioStreamCacheManager` to cancel obsolete background file reads when users quickly skip tracks in a queue.
- **Queue Duplicate Key Crash Guard (`QueuePage.kt`)**:
  - Replaced bare `uniqueKey` in `LazyColumn` with position-indexed composite key (`${index}_${track.uniqueKey ?: track.id}`), preventing app crashes when identical songs are enqueued multiple times.
- **Duration Formatting Standardization for Long Audio (`NowPlayingPage.kt`, `QueuePage.kt`)**:
  - Replaced raw integer division with `StringUtils.formatDuration(seconds, false)`, correctly rendering tracks, DJ sets, and mixes longer than 1 hour (e.g. `01:15:00` instead of `75:00`).
- **Hardware Volume Routing to DLNA Renderers (`PlaybackService.java`, `MusicMateServiceImpl.java`, `MainActivity.java`)**:
  - Implemented `setVolume(volumePercent)` and `adjustVolume(direction)` in `PlaybackService` and `MusicMateServiceImpl`.
  - Routed Audio Hub volume gestures and sliders to UPnP `mediaHub.playerSetVolume` when streaming to remote DMR devices, with graceful fallback to local Android `AudioManager`.
- **DLNA Position Polling Runaway Loop & Logcat Spam Fix (`MediaServerHubImpl.java`)**:
  - Gated position polling strictly to active `CAST` state and terminated runaway 1-second loops when remote renderers stop responding, disconnect, or return SOAP 701 errors (`Current state of service prevents invoking that action`).
  - Added failure counter with exponential backoff (2.5s) and automatic termination after 3 consecutive failures, transitioning server status to `RUNNING` and eliminating hundreds of failed SOAP queries per minute.
- **Defensive Non-Null Collections in Repository (`TagRepository.java`)**:
  - Guaranteed non-null empty list returns in `findByCriteria` when querying paths.

## [3.19.1] - 2026-08-28

### Added
- **Spotify Connect-Style Seamless DLNA Position Handoff (`MusicMateServiceImpl.java`, `MediaServerHubImpl.java`, `MediaServerHub.java`)**:
  - Implemented smart elapsed position capture on target switching (`currentPositionMs`).
  - Added `playerActivateWithHandoff(...)` and precision post-play UPnP `Seek(HH:MM:SS)` execution to resume playback at the exact elapsed second when transferring an active song to an idle renderer.
  - Added continuous playback handoff when switching from DLNA back to local Android audio output.
- **Non-Destructive Live DLNA Session Adoption (`MediaServerHubImpl.java`, `MusicMateServiceImpl.java`)**:
  - Selecting an active DLNA renderer now connects to the live stream, parses DIDL-Lite metadata (`Title`, `Artist`, `Album`), and adopts track progress and duration without stopping or restarting the song.

### Fixed
- **DLNA Playback Stutter & Audio Interruption on Track Start (`MusicMateServiceImpl.java`, `MediaServerHubImpl.java`)**:
  - Implemented 5-second post-start stabilization window for `SetNextAVTransportURI` gapless preloading, preventing FIFO buffer acquisition collisions on hardware DACs/renderers.
  - Added DMR player collision guard in `switchPlayer()` to prevent uncontrolled incoming HTTP stream requests from resetting active DLNA sessions.
  - Corrected stuck-playback recovery logic in `getAvTransportPosition()` to ignore initial 0-second buffer states and require $\ge 15$ stagnant polls before issuing recovery commands.
- **Audio Anatomy Screen Refinement & Expanded Quality Badges (`NowPlayingPage.kt`, `AudioBadges.kt`)**:
  - Enhanced the 3D flip Audio Anatomy card with comprehensive song metadata: Track Duration (`04:23`), Audio Channels (`Stereo (2.0)`, `Mono`, `5.1 Surround`), Dynamic Range, File Size, and Track # • Year • Genre telemetry.
  - Removed redundant player/output device info from the flip side, consolidating output target management exclusively onto the front target pill and top bar cast picker.
  - Upgraded front card quality indicator to an expanded, streaming-tier glass pill (`[● HI-RES LOSSLESS]`, `[● CD QUALITY]`, `[● 24-BIT STUDIO]`, `[● DSD AUDIO]`, `[● MQA MASTER]`, `[● STANDARD QUALITY]`) and removed duplicate codec/resolution strings from the front overlay.
- **Queue Synchronization & Music Folder Enqueueing Fix (`MainActivity.java`, `MainViewModel.kt`, `QueueState.kt`, `TagRepository.java`)**:
  - Fixed disconnect where Compose `QueueState` was never updated from `QueueManager` upon enqueueing, track change, removal, or playback.
  - Resolved music folder / directory collection tracks querying in `playCollection` via `findInPath`, ensuring folder enqueueing and playback populate all songs.
  - Connected reactive queue syncing across service connection, `setNowPlaying`, single-track popup "Add to Queue", "Play Next", swipe-to-dismiss, and queue clear.
- **Repeat Mode & Audio Control Polish (`MainActivity.java`, `MusicMateServiceImpl.java`)**:
  - Fixed Repeat Mode toggle by mapping string integers (`0`, `1`, `2`) to enum names (`OFF`, `ALL`, `ONE`) and adding tolerant numeric parsing in `MusicMateServiceImpl.setRepeatMode()`.
  - Initialized shuffle and repeat states in Compose `NowPlayingState` upon service connection.
  - Implemented `onAudioHubVolumeDown()`, `onAudioHubVolumeUp()`, and `onAudioHubVolumeChanged()` using system `AudioManager` (`STREAM_MUSIC`).
- **Runtime Crashes & UI Polish**:
  - Fixed `TagsActivity` inflation crash by replacing legacy `ReflectionContainer` with `FrameLayout`.
  - Fixed `TagsViewModel` background thread assertion crash by switching LiveData mutations to `postValue()`.
  - Corrected `mqaSampleRate` parameter passed to `TagUtils.formatResolution()`.
  - Tuned marquee edge fading widths in `NowPlayingPage.kt` and `MainScaffold.kt`.
- **Codebase & Library Pruning**:
  - Pruned unused legacy library modules (`paralloid`, `placesAPI`, `slideDateTimePicker`, `spacetablayout`) and dead layout resources.

## [3.19.0] - 2026-08-27

### Added
- **100% Pure Jetpack Compose Architecture**:
  - Completed total migration from legacy XML views to pure Jetpack Compose across the entire application ecosystem (`MainActivity`, `AboutActivity`, `SettingsActivity`, `PermissionActivity`, and `TagsActivity`).
  - Implemented `AboutScreen.kt` with dynamic audio tier donut distribution chart and interactive technical specs reference.
  - Implemented `SettingsScreen.kt` with segmented streaming engine selector (`CoreHTTP`, `SonicNIO`, `Netty`) and library preferences.
  - Implemented `PermissionScreen.kt` with audiophile onboarding cards and status chips.
  - Replaced legacy `TagsActivity` header layout with `TagHeaderBadges` ComposeView.
  - Safely removed legacy custom views (`BadgeView`, `DurationView`, `ResolutionView`, `QualityIndicatorView`, `DynamicRangeView`, `RatingIndicatorView`, `NewIndicatorView`, `TriangleLabelView`, `ReflectionContainer`) and 7 obsolete XML layout files.
- **10/10 Flagship Audiophile UI/UX Elevation**:
  - **Fading Edge Alpha Masks (`FadingEdge.kt`, `MainScaffold.kt`, `NowPlayingPage.kt`)**: Added smooth 8–10dp horizontal gradient alpha fade masks on scrolling marquee titles and artists, eliminating harsh text cutoff edges.
  - **Dual-Layer Breathing Ambient Artwork Glow (`NowPlayingPage.kt`)**: Enhanced cover art backdrop with animated pulse scale and alpha oscillation driven by real-time `Palette` color extraction.
  - **Rich Animated Empty States (`MusicListScreen.kt`, `QueuePage.kt`)**: Designed animated radar/pulse empty state with gold insignia and `"Refresh Library"` button for music lists, and interactive guidance card for empty playback queues.
  - **Tactile Micro-Haptics (`NowPlayingPage.kt`)**: Integrated `LocalHapticFeedback` on transport buttons, volume step controls, and scrubber milestones.
  - **Spring Motion Physics**: Tuned `Spring.DampingRatioMediumBouncy` on 3D perspective card flips and modal dialogs.
- **Reactive Media Server Status & QR Synchronization (`MainActivity.java`, `BitmapHelper.java`)**:
  - Added live observation of `MediaServerHub.ServerStatus` LiveData in `MainActivity`.
  - Added on-the-fly QR code bitmap generation via ZXing in `BitmapHelper.generateQRCode()`.
  - Connected immediate reactive UI feedback when starting, stopping, or switching streaming server engines.

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
