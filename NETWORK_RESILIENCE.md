# Network Resilience — WiFi Loss & Hotspot Mode

> Covers the media server engine's network-awareness layer introduced in 2026.06.
> Related classes: `MediaServerHubImpl`, `NetworkUtils`, `MediaServerAddressFactory`, `MusicMateServiceImpl`.
>
> **Engine scope:** These improvements apply to the three actively maintained engines —
> **SonicNIO** (`nio`), **CoreHTTP** (`httpcore`), and **Netty** (`netty`).
> The archived engines (`jetty`, `undertow`) are unaffected and will not be updated.

---

## Overview

The MusicMate media server (UPnP/DLNA) must remain reachable by renderers (WiiM, KEF, Roon, etc.)
even when the underlying network changes. Two scenarios are handled:

| Scenario | Trigger | Recovery |
|----------|---------|----------|
| **WiFi / Ethernet loss** | `ConnectivityManager.NetworkCallback.onLost()` | Server auto-stops; auto-restarts when network returns |
| **Hotspot mode** | `BroadcastReceiver` for `WIFI_AP_STATE_CHANGED` | Server starts/stops with the hotspot; UPnP binds to AP interface |

---

## 1 — WiFi / Ethernet Loss Recovery

### Architecture

The server runs a 4-state machine:

```
IDLE ──start()──► STARTING ──► RUNNING ──stop()──► STOPPING ──► IDLE
                                   ▲                                │
                           WiFi restored                     WiFi lost
                        evaluateNetworkState()            evaluateNetworkState()
```

### How it works

#### `startNetworkMonitoring()` — [`MediaServerHubImpl.java`](server-jupnp/src/main/java/apincer/music/server/jupnp/MediaServerHubImpl.java)

Registers a `ConnectivityManager.NetworkCallback` scoped to `TRANSPORT_WIFI` and `TRANSPORT_ETHERNET`.
The callback is registered in **`start()`**, not inside the UPnP worker thread, so it survives
the `stop() → IDLE` transition and can trigger auto-restart.

```
start() called
  └── startNetworkMonitoring()   ← registers callback here (survives stop/start)
  └── startHotspotMonitoring()
  └── [UPnP thread] acquireLocks + initUpnp + startPeriodicDiscovery
```

#### `onAvailable()` — WiFi / Ethernet connected

```java
currentNetwork = network;
wifiAvailable  = true;
scheduler.schedule(() -> evaluateNetworkState(), 1, SECONDS);  // 1 s DHCP grace period
```

#### `onLost()` — WiFi / Ethernet dropped

```java
wifiAvailable  = false;
currentNetwork = null;
evaluateNetworkState();   // immediate — no delay
```

#### `evaluateNetworkState()`

```java
boolean networkUp = wifiAvailable || hotspotAvailable;
if (networkUp  && state == IDLE)    start();
if (!networkUp && state == RUNNING) stop();
```

#### `stop()` — what happens on loss

1. Cancels periodic UPnP discovery
2. **Does NOT unregister `networkCallback`** — it must stay alive to detect recovery
3. Sends SSDP `Bye-Bye` to inform renderers
4. Shuts down `UpnpService`
5. Releases `WakeLock`, `WifiLock`, `MulticastLock`
6. Transitions to `IDLE`

> **Key design decision**: `stopNetworkMonitoring()` is only called in `release()` (full hub
> teardown), never in `stop()`. This is what enables auto-restart.

#### Locks held during streaming

| Lock | Tag | Purpose |
|------|-----|---------|
| `PARTIAL_WAKE_LOCK` | `MM:Wake` | Prevents CPU sleep; no timeout — released in `stop()` |
| `WIFI_MODE_FULL_LOW_LATENCY` | `MM:Wifi` | Keeps WiFi radio active for streaming |
| `MulticastLock` | `MM:Multicast` | Keeps SSDP/UPnP multicast packets alive |

### Full flow diagram

```
WiFi drops
  → onLost()
    → wifiAvailable = false
    → evaluateNetworkState()
      → state == RUNNING → stop()
        → SSDP Bye-Bye
        → UpnpService.shutdown()
        → releaseLocks()
        → state = IDLE
        [networkCallback still alive]

WiFi returns (1 s later)
  → onAvailable()
    → wifiAvailable = true
    → [1 s delay] evaluateNetworkState()
      → state == IDLE → start()
        → acquireLocks()
        → initUpnp() + sendAlive()
        → state = RUNNING
```

---

## 2 — Hotspot (AP) Mode Support

### What hotspot mode is

When the Android device creates a WiFi hotspot, it acts as the **access point**. It is not a
WiFi *client*, so `ConnectivityManager.NetworkCallback` with `TRANSPORT_WIFI` will never fire.
The hotspot interface is typically `ap0` (Pixel/AOSP) or `swlan0` (Samsung), not `wlan0`.

### How it works

#### Interface name recognition — [`NetworkUtils.java`](core/src/main/java/apincer/music/core/utils/NetworkUtils.java)

```java
public static boolean isHotspotInterfaceName(String name) {
    // ap*    — Pixel / AOSP
    // swlan* — Samsung
    // wlan*  — Qualcomm (concurrent AP+STA reuses wlan0)
    return name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("wlan");
}
```

#### Hotspot detection — `isHotspotActive()`

Scans live network interfaces for an `ap*` or `swlan*` interface with a valid IPv4 address.
No `TetheringManager` API needed — pure `java.net.NetworkInterface`.

```java
for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
    if (!ni.isUp() || ni.isLoopback()) continue;
    String name = ni.getName();
    if (!(name.startsWith("ap") || name.startsWith("swlan"))) continue;
    // check for a valid IPv4 → return true
}
```

#### Hotspot monitoring — `startHotspotMonitoring()`

A `BroadcastReceiver` listens for `android.net.wifi.WIFI_AP_STATE_CHANGED`.
When fired, `isHotspotActive()` re-checks the interfaces and calls `evaluateNetworkState()`.

```
Hotspot enabled
  → WIFI_AP_STATE_CHANGED broadcast
    → isHotspotActive() → true
    → hotspotAvailable = true
    → [2 s delay] evaluateNetworkState()   ← delay lets ap0 fully come up
      → state == IDLE → start()
        → UPnP binds ap0 (192.168.43.1)
        → SSDP Alive sent
        → state = RUNNING

Hotspot disabled
  → WIFI_AP_STATE_CHANGED broadcast
    → isHotspotActive() → false
    → hotspotAvailable = false
    → evaluateNetworkState()  ← immediate
      → state == RUNNING → stop()
```

> The 2-second delay on hotspot start ensures the `ap0`/`swlan0` interface is fully assigned
> an IP before `UPnPServiceImpl` tries to bind to it.

#### UPnP interface binding — [`MediaServerAddressFactory.java`](server-jupnp/src/main/java/apincer/music/server/jupnp/MediaServerAddressFactory.java)

```java
return NetworkUtils.isOnWifiNetwork(networkInterface, address)   // wlan*
    || NetworkUtils.isOnHotspotInterface(networkInterface, address) // ap*, swlan*
    || NetworkUtils.isOnCellularNetwork(networkInterface, address); // rmnet*
```

Without this, jUPnP would refuse to bind to `ap0`/`swlan0` and the server would be
unreachable by devices connected to the hotspot.

#### Server start guard — [`MusicMateServiceImpl.java`](app/src/main/java/apincer/android/mmate/service/MusicMateServiceImpl.java)

```java
// Before (blocked hotspot):
if (!NetworkUtils.isWifiConnected(this)) { return; }

// After (allows hotspot):
if (!NetworkUtils.isServerNetworkAvailable(this)) { return; }
// isServerNetworkAvailable = isWifiConnected || isHotspotActive
```

### IP address resolution

`NetworkUtils.getIpAddress()` step 1 now includes `ap*`/`swlan*` alongside `wlan*`:

```java
if (ni.isUp() && isHotspotInterfaceName(ni.getName())) {
    // return first valid IPv4 — typically 192.168.43.1 for hotspot
}
```

---

## 3 — Combined Network State Matrix

| WiFi client | Hotspot | `networkUp` | Server action |
|:-----------:|:-------:|:-----------:|---------------|
| ✅ | ❌ | ✅ | Runs, binds `wlan0` |
| ❌ | ✅ | ✅ | Runs, binds `ap0`/`swlan0` |
| ✅ | ✅ | ✅ | Runs, binds both |
| ❌ | ❌ | ❌ | Stopped |

---

## 4 — Callback Lifecycle

```
hub.start()
  ├── startNetworkMonitoring()   registers ConnectivityManager.NetworkCallback
  └── startHotspotMonitoring()   registers BroadcastReceiver

hub.stop()                       ← triggered by network loss OR user action
  └── [callbacks remain alive]   ← key: allows auto-restart

hub.release()                    ← only on service destroy
  ├── stopNetworkMonitoring()    unregisters NetworkCallback
  └── stopHotspotMonitoring()    unregisters BroadcastReceiver
```

---

## 5 — Permissions Required

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

No special permission is required for reading `NetworkInterface` list or receiving
`WIFI_AP_STATE_CHANGED` (it is a normal broadcast, not a protected one).

---

## 6 — Player Target Resolution & Unified Display Formatting

> Added in 2026.08 (`PlayerNameUtils`, `MusicMateServiceImpl`, `MediaServerManagementSheet`, `SignalPathBottomSheet`).

### IP Address Extraction & Resolution
When incoming HTTP playback requests arrive at `BaseServer`, the client IP/address (e.g. `"/192.168.1.50:53210"`) is sanitized using `NetworkUtils.extractIpAddress()`, stripping schemes, ports, leading slashes, and paths.

If an incoming HTTP streaming target matches a discovered UPnP `DMRPlayer` target by IPv4 address, `resolveStreamingPlayerTarget()` automatically resolves the stream target to the discovered renderer (e.g., **"HiBy R3"**).

### Standardized Format Across All Player Types
All player targets (`DMRPlayer`, `WebStreamingPlayer`, `ExternalAndroidPlayer`) use centralized formatters in `PlayerNameUtils`:

* **Two-Line Format (`PlayerNameUtils.getTwoLinePlayerLabel`)**: Used in Management Sheets and Audio Route Path steps.
  - **DLNA Renderer:** `HiBy R3` \n `(192.168.1.50 • DLNA Renderer)`
  - **Web Streaming:** `Web Streaming` \n `(192.168.1.100 • Web Streaming)`
  - **Android App:** `Poweramp v975` \n `(com.maxmpz.audioplayer • Android App)`

* **Single-Line Format (`PlayerNameUtils.getDropdownPlayerLabel`)**: Used in target selection dropdown popups.
  - **DLNA Renderer:** `HiBy R3 • 192.168.1.50`
  - **Web Streaming:** `Web Streaming • 192.168.1.100`
  - **Android App:** `Poweramp v975 • Android App`

---

## 7 — Runtime Server Engine Switching & Ultra High-Res Streaming Tuning

> Added in 2026.08 (`CompositeWebServer`, `NioHttpServer`, `HttpCoreWebServerImpl`).

### Dynamic Engine Proxy (`CompositeWebServer`)
The `CompositeWebServer` class acts as a dynamic proxy for the web server layer:
* Reads `preference_media_server_engine` from `SharedPreferences` (`"nio"`, `"httpcore"`, or `"netty"`).
* Instantiates and delegates calls (`initServer`, `stopServer`, `restartServer`) to the selected engine via reflection.
* Allows hot-swapping server engines at runtime without restarting the Android application process.

### High-Res (352.8 kHz / DXD) Streaming Optimizations
To support seamless high-bitrate streaming (>10 Mbps) to DAPs (e.g. HiBy R3) over Wi-Fi without buffer underruns:
* **High-Rate Buffer Allocation:** Restored hardcoded `SO_SNDBUF` (512 KB) in SonicNIO, Netty, and CoreHTTP, as relying on OS-level TCP Auto-tuning proved to aggressively shrink windows on poor Wi-Fi networks causing mid-track DLNA buffering.
* **Large File Streaming Chunks:** Increased payload chunk size to **256 KB** across all engines (`SonicNIO`, `CoreHTTP`, and `Netty`) to reduce application-level overhead and minimize NIO selector iterations during high-rate (>10 Mbps) FLAC streaming, eliminating mid-track freezes.
* **Socket Timeouts:** Increased `soTimeout` and `keepAliveTimeout` from 30s to **120s** to tolerate longer latency spikes and prevent premature stream disconnections.

### Port & HTTP Endpoint Specification
All server engines (`SonicNIO`, `CoreHTTP`, `Netty`) standardize on port **`9000`** and expose the following endpoint contract:

| Endpoint Type | Constant | Path Structure | Description |
| :--- | :--- | :--- | :--- |
| **HTTP Port** | `BaseServer.WEB_SERVER_PORT` | `9000` | Fixed HTTP listener port |
| **Audio Stream** | `BaseServer.CONTEXT_PATH_MUSIC` | `/music/<id>/file.<ext>` | Audio file payload endpoint |
| **Cover Art** | `BaseServer.CONTEXT_PATH_COVERART` | `/coverart/<albumKey>` | Embedded/cached cover artwork endpoint |
| **WebSocket** | `BaseServer.CONTEXT_PATH_WEBSOCKET` | `/ws` | WebSocket protocol for remote control & status |
| **Web UI Root** | `BaseServer.CONTEXT_PATH_ROOT` | `/` | Web dashboard root (`/index.html`) |

---

## 8 — DLNA Controller (DMC) Transport Actions & Remote Control

> Added in 2026.08 (`MediaServerHubImpl`, `MusicMateServiceImpl`).

### Production-Ready Action Set
MusicMate operates as a full **Digital Media Controller (DMC)** using UPnP `AVTransport` and `RenderingControl` services:

| Controller Action | UPnP Service | Method | Function |
| :--- | :--- | :--- | :--- |
| **Discovery** | SSDP (`M-SEARCH`) | `RegistryListener` | Auto-detects DMR devices on Wi-Fi/Hotspot |
| **Cast / Play** | `AVTransport` | `SetAVTransportURI` + `Play` | Pushes DIDL-Lite metadata and initiates streaming |
| **Pause** | `AVTransport` | `Pause` | Pauses playback on target DMR device (`playerPause`) |
| **Stop** | `AVTransport` | `Stop` | Stops playback on target DMR device (`playerStop`) |
| **Seek** | `AVTransport` | `Seek` | Scrubs position using formatted `H:MM:SS.ms` time (`playerSeek`) |
| **Gapless Queue** | `AVTransport` | `SetNextAVTransportURI` | Pre-queues next track in queue before current finishes |
| **Volume Control** | `RenderingControl` | `SetVolume` | Controls master volume (0–100) (`playerSetVolume`) |
| **Position Sync** | `AVTransport` | `GetPositionInfo` | Periodic polling syncs UI seek bar with DMR progress |

---

## 9 — Multi-Target SSDP Bursts, Subnet Sanitation & Target Reconciliation

> Added in 2026.08 (`MediaServerHubImpl`, `SimpleRegistryListener`, `MusicMateServiceImpl`, `MediaServerAddressFactory`).

### Multi-Target SSDP Search Bursts
Rather than relying only on `ssdp:all` (`STAllHeader`), `MediaServerHubImpl.triggerMultiSearch()` broadcasts three query headers concurrently:
1. `STAllHeader` (broad query for all UPnP root devices and services)
2. `UDADeviceTypeHeader("MediaRenderer")` (targeted query for media renderers like WiiM, KEF, Eversolo, Sony)
3. `UDAServiceTypeHeader("AVTransport")` (targeted query for active transport controllers)

In `refreshDiscovery()`, searches are fired in 2 pulses (0s and 1.5s) with MX=4 to compensate for UDP packet drops on congested Wi-Fi networks.

### Subnet Reachability & Ghost Target Elimination
When UPnP devices are enumerated in `getMediaRenderers()`, `isDeviceValidAndReachable()` compares the host IP of the descriptor URL with the bound IP (`lastBoundIp`). Devices on non-matching subnets (e.g. from previous Wi-Fi networks) are immediately filtered out. In addition, `MediaServerAddressFactory` excludes cellular interfaces (`rmnet*`) from multicast binding.

### Dynamic Placeholder Reconciliation & 8-Second Startup Fallback
* When restoring a previously selected DLNA renderer (`uuid:...`) on startup, a placeholder target (`"Scanning for players…"`) is created in `currentPlayerFlow`.
* When the live renderer is discovered, `MusicMateServiceImpl.handleDiscoveredRenderers()` immediately reconciles the placeholder to the live device with its active IP and friendly name.
* If the renderer does not appear within **8 seconds** (e.g. powered off or user changed networks), `dmrStartupTimeoutTask` automatically falls back to `localTarget` (Android Player / DAC / BT), eliminating ghost players.

---

## 10 — Seamless Playback Handoff & Non-Destructive Session Adoption

> Added in 2026.08 (`MediaServerHubImpl`, `MusicMateServiceImpl`).

### Non-Destructive Session Adoption
When switching to a DLNA renderer that is already playing an active stream:
1. `GetTransportInfo` and `GetMediaInfo` read `CurrentTransportState`, `CurrentURI`, and DIDL-Lite XML (`dc:title`, `upnp:artist`, `upnp:album`).
2. MusicMate attaches to the live session without issuing `Stop` or resetting the track, adopting track metadata and elapsed time seamlessly.

### Precision Position Handoff (Local ➔ DLNA)
When transferring an actively playing track from local headphones/speakers to an idle DLNA renderer:
1. `switchPlayer()` captures the exact elapsed timestamp (`currentPositionMs`).
2. `playerActivateWithHandoff()` sets transport URI, initiates `Play`, and schedules a precision UPnP `Seek(HH:MM:SS)` 300ms post-play to resume playback at the exact elapsed second.

### 5-Second Gapless Preload Stabilization
To prevent hardware DAC FIFO buffer acquisition stalls on renderers (e.g. HiBy R3, Eversolo, WiiM), `SetNextAVTransportURI` is dispatched with a 5-second post-start stabilization delay rather than at $t = 0$, ensuring jitter-free track start and rolling gapless transitions.

---

## 11 — RFC 7233 Range Streaming & Natural Track Completion Latching

> Added in 2026.09 (`HttpCoreWebServerImpl`, `PartialFileProducer`, `MediaServerHubImpl`, `AudioStreamCacheManager`).

### Zero-Cliff Direct Streaming (`PartialFileProducer`)
* Eliminated in-memory 4MB buffer splicing, streaming directly from `FileChannel` in 64KB chunks with immediate file descriptor recycling upon EOF or cancellation.
* Eliminates the premature ~58-second stream abort experienced on low-power DLNA renderers.

### RFC 7233 Range Specification Compliance
* **End Position Clamping:** Clamps open-ended client range requests (e.g. `bytes=0-2147483647`) to `fileLength - 1`, preventing false multi-gigabyte `Content-Length` headers and premature stream truncation errors.
* **416 Status Guard:** Returns HTTP `416 Range Not Satisfiable` with `Content-Range: bytes */fileLength` when requested start offsets exceed file length.
* **Suffix Range Resolution:** Accurately resolves suffix ranges (e.g. `bytes=-500`).
* **HEAD Response Header Parity:** Sends `Content-Length` and `Content-Type` headers without attaching an entity body stream.

### Natural Track Completion Latching
* When the transport elapsed position reaches track duration (`position >= duration`) or when a `STOPPED` GENA event arrives, `stopPolling()` is called and `isUserInitiatedStop = true;` is latched immediately before notifying `playbackCallback.onPlaybackCompleted()`.
* Guarantees that recurring polling ticks and duplicate renderer GENA packets cannot trigger double track skips.

