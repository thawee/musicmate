# Playback Module Architecture

This document describes how playback control, queue management, Android app control, and DLNA renderer control are integrated into MusicMate.

---

## High-Level Overview

The playback module uses a **Strategy pattern** with a single service entry-point (`MusicMateServiceImpl`) that routes all commands to the correct player backend based on the current target type.

```
                        ┌──────────────────────┐
                        │      UI Layer        │
                        │  ┌────────────────┐  │
                        │  │  MainActivity  │  │
                        │  │  Player Picker │  │
                        │  │  WebUI Remote  │  │
                        │  └───────┬────────┘  │
                        └──────────┼───────────┘
                                   │ bindService()
                        ┌──────────▼───────────┐
                        │  MusicMateServiceImpl │
                        │  (PlaybackService)    │
                        │                       │
                        │  ┌─────────────────┐  │
                        │  │  QueueManager   │  │
                        │  │  shuffle/repeat │  │
                        │  └─────────────────┘  │
                        └──────────┬───────────┘
                           ┌───────┴───────┐
                           │               │
               ┌───────────▼───┐    ┌──────▼──────────────┐
               │ AndroidPlayer │    │ MediaServerHubImpl  │
               │  Controller   │    │ (UPnP Engine)       │
               │               │    │                     │
               │ ┌───────────┐ │    │ ┌─────────────────┐ │
               │ │ ExoPlayer │ │    │ │ ControlPoint    │ │
               │ │ (local)   │ │    │ │ (SSDP + SOAP)   │ │
               │ └───────────┘ │    │ └─────────────────┘ │
               │ ┌───────────┐ │    │ ┌─────────────────┐ │
               │ │ MediaSess.│ │    │ │ BaseServer      │ │
               │ │ (external)│ │    │ │ (HTTP :9000)    │ │
               │ └───────────┘ │    │ └─────────────────┘ │
               └───────────────┘    └─────────────────────┘
                      │                        │
              ┌───────┴───────┐        ┌───────┴───────┐
              │               │        │               │
         ┌────▼────┐   ┌──────▼──────┐ │  DLNA         │
         │ On-     │   │ USB Audio  │ │  Renderers    │
         │ Device  │   │ Player PRO │ │  (network)    │
         └─────────┘   └────────────┘ └───────────────┘
```

---

## Layer 1 — SPI Contracts

The SPI layer (`core/.../playback/spi/`) defines interfaces that decouple the UI from playback implementations.

### PlaybackTarget (Interface)

Abstraction for *any* playback destination:

| Method | Purpose |
|:---|:---|
| `getTargetId()` | Unique ID (UDN for DLNA, package name for apps) |
| `getDisplayName()` | Friendly name for UI |
| `getTargetType()` | `LOCAL` / `EXTERNAL_APP` / `STREAMING` |
| `isStreaming()` | `true` for DLNA renderers |
| `canReadSate()` | Whether state monitoring is supported |

Three concrete implementations:

| Class | Type | Use Case |
|:---|:---|:---|
| `LocalAndroidPlayer` | `LOCAL` | On-device playback via ExoPlayer |
| `ExternalAndroidPlayer` | `EXTERNAL_APP` | Third-party audiophile apps (UAPP, Neutron, Poweramp, etc.) |
| `DMRPlayer` | `STREAMING` | DLNA/UPnP Media Renderers on the network |
| `WebStreamingPlayer` | `STREAMING` | HTTP web clients streaming via browser |

### PlaybackService (Interface)

The master contract — every playback operation goes through this:

| Category | Methods |
|:---|:---|
| **Transport** | `playSong()`, `pausePlayer()`, `stopPlaying()`, `skipToNextInQueue()`, `skipToPrevious()` |
| **Queue** | `setNextSongInQueue()`, `setShuffleMode()`, `setRepeatMode()` |
| **Player Mgmt** | `switchPlayer()`, `getPlayer()`, `getPlaybackTargets()`, `refreshPlayerDiscovery()` |
| **State** | `subscribePlaybackState()`, `subscribeNowPlayingSong()`, `subscribePlaybackTarget()` |

### PlaybackCallback (Abstract Class)

Unified callback — both Android and DLNA backends fire these:

| Method | When |
|:---|:---|
| `onMediaTrackChanged(Track)` | New track started |
| `onMediaTrackChanged(title, artist, album, duration)` | Metadata from external app MediaSession |
| `onPlaybackStateChanged(PlaybackState)` | State transition (PLAYING / PAUSED / STOPPED) |
| `onPlaybackStateTimeElapsedSeconds(long)` | Position update for progress bar |
| `onPlaybackTargetChanged(PlaybackTarget)` | Active player changed |

---

## Layer 2 — Service Orchestrator (`MusicMateServiceImpl`)

*   **Path:** `app/src/.../service/MusicMateServiceImpl.java`
*   **Extends:** `android.app.Service`
*   **Implements:** `PlaybackService`
*   **Injected via:** Hilt (`@AndroidEntryPoint`)

### Initialization Flow

```
onCreate()
 ├── Inject QueueManager (Hilt @Singleton)
 ├── new AndroidPlayerController(context, MediaSessionManager)
 ├── Inject MediaServerHubImpl (Hilt → boots UPnP + HTTP server)
 ├── Register shared PlaybackCallback
 ├── discoverExternalPlayers() via MediaSessionManager
 ├── Load persistent queue from DB
 └── autoSelectBestPlayer()
         Priority: DMR > WebStream > ExternalApp > Local
```

### Command Routing (Strategy Pattern)

All playback commands follow the same type-check dispatch:

```
                           ┌──────────────────────┐
                           │  playSong(track)      │
                           └──────────┬───────────┘
                                      │
                              ┌───────▼───────┐
                              │ isControllable │
                              │ (streaming +   │
                              │  controlled)?  │
                              └───────┬───────┘
                              yes     │     no
                         ┌────────────┴────────────┐
                         ▼                         ▼
              mediaHub.playerPlaySong()   androidPlayer.play()
              (UPnP SOAP to renderer)    (ExoPlayer or MediaController)
```

This pattern applies identically to `pause`, `stop`, `skipToNext`, and `skipToPrevious`.

### Player Switching (`switchPlayer()`)

1. **Resolve** streaming proxy targets (match IP to DMR)
2. **Deactivate** old player if different (`mediaHub.playerDeactivate()` or `androidPlayer.unregisterCallback()`)
3. **Activate** new player:
    *   `ExternalAndroidPlayer` → `androidPlayer.registerCallback(ext, callback)`
    *   DLNA Streaming → `mediaHub.playerActivate(udn, callback)`
4. **Update** `currentPlayerFlow` StateFlow → triggers UI update
5. **Update** foreground notification

### Observable State Flows

| StateFlow | Type | Consumers |
|:---|:---|:---|
| `currentPlayerFlow` | `MutableStateFlow<Optional<PlaybackTarget>>` | Cast icon tint, active player display |
| `playbackStateFlow` | `MutableStateFlow<PlaybackState>` | Notification, WebUI, progress bar |
| `currentTrackFlow` | `MutableStateFlow<Optional<Track>>` | Now-playing display |
| `playingQueueFlow` | `MutableStateFlow<List<Track>>` | Queue sheet UI |

---

## Layer 3A — Android Player Controller

*   **Path:** `app/src/.../service/AndroidPlayerController.java`

Handles **on-device** playback in two modes:

### Local Playback (ExoPlayer)

*   `playLocal(Track)` → Plays audio file using ExoPlayer
*   `pauseLocal()` / `resumeLocal()` / `stopLocal()` / `seekLocal()`
*   ExoPlayer's `Player.Listener` fires `PlaybackCallback` events

### External App Bridge (MediaSession)

*   **Discovery:** Uses `MediaSessionManager.getActiveSessions()` to find running audiophile apps
*   **Supported Apps:** HiBy Music, NePLAYER Lite, Neutron, USB Audio Player PRO, Foobar2000, Poweramp
*   **Control:** `registerCallback()` attaches a `MediaController.Callback` that translates external app state changes → `PlaybackCallback`
*   **Playback routing:**
    *   Neutron → Custom intent via `MusicFileProvider.getUriForFile()`
    *   Poweramp → Custom intent via `MusicFileProvider.getUriForFile()`
    *   Others → `MediaController.getTransportControls().playFromUri()` or `ACTION_VIEW` intent
*   **Progress polling:** Schedules 1s `Handler` polling for position updates via `MediaController.getPlaybackState()`

```
    MediaSessionManager
           │
           │ getActiveSessions()
           ▼
    ┌──────────────┐     ┌──────────────────┐
    │ MediaController│────▶│ ExternalAndroidPlayer │
    │   .Callback    │     │ (PlaybackTarget)      │
    └──────┬─────────┘     └───────────────────────┘
           │ onMetadataChanged()
           │ onPlaybackStateChanged()
           ▼
    PlaybackCallback → MusicMateServiceImpl
```

---

## Layer 3B — DLNA Engine (`MediaServerHubImpl`)

*   **Path:** `server-jupnp/src/.../MediaServerHubImpl.java`
*   **Framework:** jUPnP (fork of Cling)

This is a **dual-mode UPnP engine**:

### Mode 1: Digital Media Server (DMS)

*   Advertises the local music library to the network
*   Other DLNA control points can browse and play music from this device
*   Powered by `BaseServer` HTTP server on port 9000
*   Generates DIDL-Lite XML metadata for UPnP compliance

### Mode 2: Control Point (CP)

*   Discovers DLNA Media Renderers via SSDP M-SEARCH
*   Controls renderers via AVTransport SOAP actions
*   Monitors renderer state via GENA subscriptions + polling

### Discovery Flow

```
    MusicMate                         Network                     DLNA Renderer
       │                                │                              │
       │──── M-SEARCH (multicast) ─────▶│                              │
       │                                │──── M-SEARCH ──────────────▶│
       │                                │◀─── SSDP Response ─────────│
       │◀─── remoteDeviceAdded() ──────│                              │
       │                                │                              │
       │   (Registry stores device)     │                              │
       │                                │                              │
       │  [Repeats every 30 seconds]    │                              │
```

*   **Periodic:** `scheduleWithFixedDelay` every 30 seconds with default `MX=3`
*   **Manual rescan:** `refreshDiscovery()` uses `MX=5` (longer window for slow devices)

### AVTransport Control (SOAP)

```
    MusicMateServiceImpl          MediaServerHubImpl              DLNA Renderer
           │                            │                              │
           │── playerPlaySong(track) ──▶│                              │
           │                            │── Build stream URL ─────────│
           │                            │   + DIDL-Lite XML            │
           │                            │                              │
           │                            │── SetAVTransportURI ───────▶│
           │                            │◀── OK ─────────────────────│
           │                            │── Play() ──────────────────▶│
           │                            │◀── OK ─────────────────────│
           │                            │                              │
           │                            │       ┌──────────────────┐   │
           │                            │       │ Renderer fetches │   │
           │                            │       │ audio via HTTP   │   │
           │                            │       │ GET :9000/music/ │   │
           │                            │       └──────────────────┘   │
```

Other transport commands follow the same SOAP pattern:
*   `playerPause()` → `Pause` action
*   `playerResume()` → `Play` action
*   `playerStop()` → `Stop` action
*   `playerSeek()` → `Seek(REL_TIME, ...)` action
*   `playerSetVolume()` → `SetVolume` on RenderingControl service

### State Monitoring (GENA + Polling Hybrid)

| Mechanism | What | Frequency | Purpose |
|:---|:---|:---|:---|
| **GENA Subscription** | `LastChange` XML events from AVTransport | Event-driven | Detect PLAYING→PAUSED→STOPPED transitions |
| **Position Polling** | `GetPositionInfo` action | 1–3 seconds (adaptive) | Accurate playback position for progress bar |
| **Fallback Monitor** | Checks `lastEventTime` staleness | Periodic | Switches to polling if GENA stalls (>4s no events) |

*   **GENA subscription** on `AVTransport` with 600s lease — parses `LastChange` XML for `TransportState` and `RelativeTimePosition`
*   **Adaptive polling interval:** 1s when GENA events are recent, up to 3s when events are stale
*   **Stagnant recovery:** If position hasn't changed for 8+ polls, triggers recovery (`Stop` + `Play`)

### Gapless Playback

1. When a track starts, `handleTrackStartEvent()` calls `preloadNextTrackSafe()`
2. `preloadNextTrackSafe()` gets next from `QueueManager` and calls `mediaHub.setNextTrack()`
3. `setNextTrack()` sends UPnP `SetNextAVTransportURI` action to the renderer
4. A fallback timer at ~97% of track duration ensures auto-advance if the renderer fails to transition

### Network Resilience

*   **WiFi lock** + **Multicast lock** + **PowerManager WakeLock** for background stability
*   `ConnectivityManager.NetworkCallback` monitors WiFi/Ethernet changes
*   On network change → restarts UPnP service + re-triggers discovery

---

## Queue Management (`QueueManager`)

*   **Path:** `core/src/.../repository/QueueManager.java`
*   **Scope:** `@Singleton` (Hilt)
*   **Storage:** Thread-safe `CopyOnWriteArrayList<Track>`, persisted via Room `PlayingQueue` model

### Core Operations

| Method | Purpose |
|:---|:---|
| `loadPlayingQueue()` | Load persistent queue from DB |
| `savePlayingQueue(List<Track>)` | Persist queue to DB |
| `setPlaybackTrack(Track)` | Set current playing index |
| `getNextTrack()` | Get next track (respects shuffle/repeat) |
| `getPreviousTrack()` | Get previous track |
| `addPlayingQueue(trackId)` | Add track to queue |
| `emptyPlayingQueue()` | Clear queue |
| `setRepeatMode(mode)` | `OFF`, `ONE`, `ALL` |
| `setShuffleMode(enabled)` | Toggle shuffle + rebuild shuffle order |

### Shuffle Implementation

*   `shuffleOrder` list maps physical indices to randomized positions
*   Current track is always placed at index 0 in shuffle sequence
*   `updateShuffleOrder()` rebuilds on toggle or track change using `Collections.shuffle()`

### Queue ↔ Playback Integration

```
    PlaybackCallback                MusicMateServiceImpl             QueueManager
         │                                  │                            │
         │── onPlaybackComplete() ─────────▶│                            │
         │                                  │── setPlaybackTrack(cur) ──▶│
         │                                  │── getNextTrack() ─────────▶│
         │                                  │◀── nextTrack ─────────────│
         │                                  │                            │
         │                                  │── play(nextTrack) on       │
         │                                  │   active player            │
```

---

## UI Binding (`MainActivity`)

### Service Connection

```java
// In onCreate():
bindService(intent, serviceConnection, BIND_AUTO_CREATE);

// ServiceConnection.onServiceConnected():
playbackService = binder.getPlaybackService();
isPlaybackServiceBound = true;
adapter.setPlaybackService(playbackService);

// Subscribe to state updates:
playbackService.subscribePlaybackState(
    state -> setNowPlaying(playbackService.getNowPlayingSong(), state),
    throwable -> Log.e(TAG, "Error", throwable));
```

### Player Picker

The cast button (`headerCastBtn`) triggers `showPlayerPickerPopup()`:

1. Reads `playbackService.getPlaybackTargets()` (local + DLNA combined)
2. Shows popup with `📱` prefix for local players, `📻` for remote
3. `✓` marks the currently active player
4. **Rescan** option triggers `refreshPlayerDiscovery()` with smart polling (reopens when players appear, up to 6s)
5. On selection → `playbackService.switchPlayer(target, true)`

---

## Module Map

| Module | Key Files | Role |
|:---|:---|:---|
| `:core` | `playback/spi/PlaybackTarget.java` | Target interface |
| `:core` | `playback/spi/PlaybackService.java` | Service interface |
| `:core` | `playback/spi/PlaybackCallback.java` | Callback contract |
| `:core` | `playback/DMRPlayer.java` | DLNA renderer target |
| `:core` | `playback/ExternalAndroidPlayer.java` | External app target |
| `:core` | `playback/WebStreamingPlayer.java` | Web client target |
| `:core` | `server/spi/MediaServerHub.java` | UPnP engine interface |
| `:core` | `server/BaseServer.java` | HTTP streaming server |
| `:core` | `repository/QueueManager.java` | Queue management |
| `:server-jupnp` | `MediaServerHubImpl.java` | UPnP engine (jUPnP) |
| `:app` | `service/MusicMateServiceImpl.java` | Central orchestrator |
| `:app` | `service/AndroidPlayerController.java` | Local + external player bridge |
| `:app` | `ui/MainActivity.java` | UI binding, player picker |
