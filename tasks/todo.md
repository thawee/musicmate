# External Android Music Player Companion Controller Architecture Plan

## Status: 🟢 Completed

### Objectives
Transition MusicMate's external Android music app integration (Poweramp, UAPP, Neutron, HiBy Music, etc.) to a true **Companion Controller / MediaSession IPC** architecture. Eliminate track-by-track URL pushes, window/focus theft, background activity restrictions, and audio engine interruptions, while retaining one-time explicit handoff when a user taps a song to play in an external app.

---

### Master Checklist

- [x] **Phase 1: MediaSession IPC Event Routing (`AndroidPlayerController.java`)**
  - [x] Remove track-end heuristic `playbackCallback.onPlaybackCompleted()` in `mediaCallback.onPlaybackStateChanged()` to prevent external app track ends from triggering unwanted URL pushes
  - [x] Update transport controls (`skipToNext`, `skipToPrevious`, `pause`, `resume`, `seekTo`, `stopPlaying`) to re-acquire `mediaController` if null when targeting external apps

- [x] **Phase 2: Queue & Transport Orchestration (`MusicMateServiceImpl.java`)**
  - [x] Update `skipToNextInQueue()`: route external players to `androidPlayer.skipToNext()` (MediaSession IPC) instead of advancing MusicMate's queue and firing `ACTION_VIEW`
  - [x] Update `skipToPrevious()`: route external players to `androidPlayer.skipToPrevious()` (MediaSession IPC)
  - [x] Guard `scheduleFallback()`: restrict fallback timers strictly to controllable DLNA/DMR streaming players (`activePlayer.isStreaming() && isControllable(activePlayer)`), bypassing Local ExoPlayer and external apps

- [x] **Phase 3: Verification & Documentation**
  - [x] Run `./gradlew compileDebugSources testDebugUnitTest`
  - [x] Document architectural patterns and lessons learned in `tasks/lessons.md`
  - [x] Complete `tasks/todo.md` review section

- [x] **Phase 4: Design Doc Expansion - Dual-Mode Network Streaming Architecture (`DESIGN.md`)**
  - [x] Add Section 4.D detailing Dual-Mode Network Streaming (Mode A: DMS + DMC vs. Mode B: DMS Only with External Controller)
  - [x] Detail UPnP `ContentDirectory` hierarchy (`AlbumsBrowser`, `ArtistsBrowser`, `GenresBrowser`, `CollectionsBrowser`, `SourcesBrowser`)
  - [x] Detail RFC 7233 byte-range clamping and embedded NIO HTTP streaming engine
  - [x] Detail passive stream observation and non-collision guard (`onAccessMediaTrack`)
  - [x] Renumber Player Picker to Section 4.E
  - [x] Update ADR-026 to explicitly cross-reference dual streaming modes and collision guards

- [x] **Phase 5: Decouple UI/UX Design System into dedicated `UI.md`**
  - [x] Create `UI.md` containing UI Philosophy, Gestures, Menu Architecture, Obsidian-Glass System, Color Tokens, Layouts, Docks, Dialogs, Touch/a11y, and UI ADRs
  - [x] Refocus `DESIGN.md` as the Technical & System Architecture reference (System topology, Playback Domains, Audio Engine, UPnP/DLNA Streaming, System ADRs, Non-Goals, and UI cross-references)
  - [x] Verify cross-links, formatting, and file consistency
  - [x] Update `tasks/todo.md` with completion and review notes

---

## Review & Verification

### Verification Summary
- Executed `./gradlew compileDebugSources testDebugUnitTest`: **BUILD SUCCESSFUL** across 235 tasks with 0 errors.
- Decoupled UI/UX design specifications from system architecture:
  - Created [`UI.md`](file:///Users/thawee.p/Workspaces/github/musicmate/UI.md) (716 lines) as the authoritative UI/UX Design System & Human Interface Guidelines.
  - Refocused [`DESIGN.md`](file:///Users/thawee.p/Workspaces/github/musicmate/DESIGN.md) (359 lines) as the Technical & System Architecture Specification.
  - Updated documentation links in [`README.md`](file:///Users/thawee.p/Workspaces/github/musicmate/README.md) and [`tasks/lessons.md`](file:///Users/thawee.p/Workspaces/github/musicmate/tasks/lessons.md).

### Architectural Improvements Delivered
1. **Zero UI/Focus Theft**: External music apps are never re-launched with `ACTION_VIEW` when songs change in the background. Transport skip events use standard Binder IPC (`MediaController.getTransportControls().skipToNext()`).
2. **Audio Engine & DAC Integrity**: External audiophile players maintain their hardware USB DAC locks without stream interruption, sample rate re-negotiation, or DAC clicks/pops.
3. **Target-Isolated Fallback Timers**: Guarded `scheduleFallback()` to run only for controllable DLNA/UPnP renderers, preventing unwanted track-skip timers from interrupting local ExoPlayer or external Android music player sessions.
4. **Resilient Dynamic Controller Binding**: Added `ensureMediaController()` across all transport commands (`pause`, `resume`, `seekTo`, `skipToNext`, `skipToPrevious`, `stopPlaying`) to dynamically re-bind active `MediaController` instances if dropped or lazily initialized.
5. **Dual-Mode Streaming Documentation**: Fully documented UPnP AV / DLNA topology across Mode A (Integrated DMS + DMC) and Mode B (Standalone DMS with third-party DMCs like BubbleUPnP, mconnect, WiiM, Audirvana), the `ContentDirectory` browser tree, and `onAccessMediaTrack()` collision guards.
6. **Modular Documentation Decoupling**: Successfully separated UI design tokens, layout hierarchies, and interaction models (`UI.md`) from system topology, audio pipelines, streaming protocols, and backend ADRs (`DESIGN.md`), with comprehensive bi-directional cross-references.


