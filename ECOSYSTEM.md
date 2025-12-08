# MusicMate Ecosystem

### The New MusicMate Ecosystem Architecture

#### 1. MusicMate Core (The Server)
This is the heart of the system, running as a background service inside the MusicMate app on a single Android Device. It hosts two servers simultaneously:

-   **DLNA Digital Media Server (DMS):**
    -   **Purpose:** Compatibility.
    -   **Function:** Scans the **Music Library** and advertises it using standard UPnP protocols.
    -   **Technology:** `jupnp`.
    -   **Result:** Any DLNA player on the network (e.g., RopieeeXL, Volumio, a smart TV) can see and play music from it.

-   **Web Server (HTTP Server):**
    -   **Purpose:** Control.
    -   **Function:** Serves a modern web application that acts as the **MusicMate Remote**.
    -   **Technology:** `jetty-server12` is perfect for embedding a powerful web server directly in the app.

#### 2. The Players (The "Endpoints" / DMRs)
-   These can be any standard DLNA Digital Media Renderer on the network.
-   The **MusicMate Core** will discover these devices using UPnP discovery and maintain a list of available players.
-   **Examples include:** **RoPieeeXL** (Raspberry Pi endpoints), **Volumio**, **WiiM**, **Sonos** (DLNA mode), **HEOS** devices, and Smart TVs.

#### 3. The Controllers (DMCs)
The system supports two types of Digital Media Controllers:

*   **Standard DLNA Controllers:** Any standard UPnP/DLNA controller app (e.g., **mConnect**, **JPLAY**, **BubbleUPnP**) can browse the MusicMate library and control playback on renderers.
*   **MusicMate Web Remote:** A built-in, lightweight web application served directly by the **MusicMate Core**.
    *   **Purpose:** Provides a "Roon-like" remote control experience in any web browser.
    *   **Scope:** A minimal DMC implementation optimized specifically for the internal MusicMate DMS server.
    *   **Access:** Open `http://<device-ip>:9000/` on any phone, tablet, or laptop on the network.
 
### Architecture Diagrams

#### System Overview
```
 ┌───────────────────────────┐
 │  MusicMate Remote         │
 │  (Web Controller)         │
 │  - Device list            │
 │  - Play queue             │
 │  - Library browser        │
 │  - Playback controls      │
 └─────────────▲─────────────┘
               │ WebSocket / HTTP
               │
 ┌─────────────┴───────────────────┐
 │  MusicMate Core                 │
 │  (Server & DMS)                 │
 │                                 │
 │  ┌───────────────────────────┐  │
 │  │  DMS (Digital Media Serv.)│  │
 │  │  - Serves music files     │  │
 │  │  - Metadata & album art   │  │
 │  └───────────────────────────┘  │
 │                                 │
 │  ┌───────────────────────────┐  │
 │  │  DMC (Digital Media Ctrl.)│  │
 │  │  - Discover renderers     │  │
 │  │  - Send AVTransport cmds  │  │
 │  │  - Volume/seek control    │  │
 │  │  - Event subscriptions    │  │
 │  └───────────────────────────┘  │
 │                                 │
 └─────────────────────────────────┘
               │
               │ UPnP / DLNA network commands
               ▼
 ┌───────────────────────────┐
 │  DLNA Renderers (DMR)     │
 │  - MPD DLNA renderer      │
 │  - Smart speakers         │
 │  - TVs                    │
 └───────────────────────────┘
```

#### Metadata Enrichment Architecture
```
           ┌─────────────────────────────┐
           │   Local Music Library       │
           │   (FLAC, MP3, etc.)         │
           └──────────────┬──────────────┘
                          │
                 [1] Scan & Extract Tags
                          │
               ┌──────────▼──────────┐
               │  Tag Extractor      │
               │  (jaudiotagger)     │
               └──────────┬──────────┘
                          │
                [2] Match with APIs
                          │
         ┌────────────────┼─────────────────────┐
         │                │                     │
┌────────▼────────┐┌──────▼─────────┐ ┌─────────▼─────────┐
│ MusicBrainz API ││ Discogs API    │ │ Last.fm API       │
│ - IDs           ││ - Credits      │ │ - Bios, Tags      │
│ - Release Data  ││ - Labels       │ │ - Similar Artists │
└────────┬────────┘└──────┬─────────┘ └─────────┬─────────┘
         │                │                     │
         └────────────────┴─────────────────────┘
                          │
              [3] Consolidate Metadata
                          │
               ┌──────────▼──────────┐
               │ Metadata Store      │
               │ (OrmLite/DB)        │
               └──────────┬──────────┘
                          │
                [4] Serve to Ecosystem
                          │
                          │
               ┌──────────▼──────────┐
               │ MusicMate Core      │
               │ (DLNA DMS)          │
               └──────────┬──────────┘
                          │                                                                             
     ┌────────────────────┼─────────────────────┐
     │                    │                     │
┌────▼──────────┐         │           ┌─────────▼──────────┐
│ DLNA DMC      │         │           │ MusicMate Remote   │                         
│ (Basic Info)  │         │           │ (Roon-like views)  │
└────┬──────────┘         │           └────────┬───────────┘
     │                    │                    │
     └────────────────────┼────────────────────┘
                          │
               ┌──────────▼──────────┐
               │   DLNA Renderer     │
               └─────────────────────┘
               
```
