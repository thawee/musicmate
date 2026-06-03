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
