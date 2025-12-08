# Music Mate

**Music Mate** is a comprehensive High-Resolution Audio management and streaming application for Android. It transforms your mobile device into a powerful **DLNA/UPnP Media Server**, allowing you to stream your local high-fidelity music collection to any compatible renderer (Hi-Fi streamers, Smart TVs, AV Receivers) or play it locally with audiophile-grade quality.

## 🚀 Key Features

### 🎧 High-Res Audio Support
*   **MQA Identification:** Automatically detects Master Quality Authenticated (MQA) tracks and displays sample rates.
*   **Audiophile Analysis:** Reads Dynamic Range (DR) and bit-depth to help identify "bad" or up-sampled music files.
*   **Format Support:** FLAC, WAV, AIFF, ALAC, AAC, MP3, and DSD (DoP).

### 📡 Advanced Media Server
*   **DLNA/UPnP 1.0 Compliant:** Functions as a Digital Media Server (DMS) compatible with control points like mConnect, BubbleUPnP, and RoPieeeXL.
*   **Transcoding-Free Streaming:** Delivers bit-perfect audio streams directly to renderers.
*   **Rich Metadata:** Serves extensive metadata including Album Art, Artist, Genre, and technical details (Bitrate, Sample Rate).

### 📂 Library Management
*   Fast scanning using `jaudiotagger`.
*   Organized browsing by Album, Artist, Genre, and Folder.

---

## 🏗 System Architecture

Music Mate follows a modular, clean architecture using Android Jetpack components:
`UI (Activity/Fragment) -> ViewModel -> Repository -> Data Provider (OrmLite / File System)`

### Modular Server Design
The application features a unique pluggable server architecture, allowing the underlying HTTP transport engine to be swapped at compile time via Gradle flavors.

*   **Core Module (`server-jupnp`):** Contains the UPnP logic, Device/Service definitions, and Content Directory Service (CDS) implementation based on the `jupnp` library.
*   **Transport Modules:**
    *   **Jetty (`server-jupnp-jetty`):** **(Recommended)** The robust, production-ready implementation.
    *   **Netty (`server-jupnp-netty`):** High-performance, low-memory NIO implementation (Experimental).
    *   **NIO (`server-jupnp-nio`):** Lightweight, pure Java NIO implementation.

---

## 📐 Application Design

Music Mate is built on a robust, modern Android architecture designed for scalability, testability, and separation of concerns.

### Architecture Patterns
*   **MVVM (Model-View-ViewModel):** The core architectural pattern.
    *   **View (UI):** Activities and Fragments (e.g., `MainActivity`, `LibraryFragment`) that observe data and handle user interactions. They contain no business logic.
    *   **ViewModel:** (e.g., `MainViewModel`, `MediaServerViewModel`) Acts as a bridge between the UI and data layers. It survives configuration changes, manages UI-related data using `LiveData`, and launches asynchronous operations.
    *   **Model:** Represents the data and business logic, encapsulated in Repositories and domain objects.
*   **Repository Pattern:** A unified abstraction layer (`TagRepository`, `FileRepository`) that mediates between different data sources (database, file system, network). The UI and ViewModels are agnostic to the origin of the data.
*   **Dependency Injection (Hilt):** Used extensively to provide dependencies (Repositories, Services, Contexts) to classes, ensuring loose coupling and easier testing.

### Key Components
*   **Core Module (`:core`):** Contains the shared business logic, data models (`MusicTag`), database access (OrmLite), and utility classes. This clean separation ensures that the UI module (`:app`) depends only on stable abstractions.
*   **UI Module (`:app`):** Handles all user-facing components. It observes `LiveData` from ViewModels to update the UI reactively without manual refreshes.
*   **Media Server (`:server-*`):** Isolated modules for UPnP/DLNA functionality, allowing the server engine to be swapped without affecting the rest of the app.

### Data Flow
1.  **User Action:** A user interacts with the UI (e.g., searches for a song).
2.  **ViewModel Trigger:** The View calls a method in the `ViewModel` (e.g., `MainViewModel.loadMusicItems()`).
3.  **Repository Fetch:** The `ViewModel` delegates the request to the appropriate `Repository` on a background thread.
4.  **Data Source:** The Repository fetches data from the local OrmLite database or scans the file system.
5.  **Reactive Update:** The data is posted to a `LiveData` object.
6.  **UI Refresh:** The View, observing this `LiveData`, automatically receives the new data and updates the screen.

---

## ⚙️ Server Implementation Details

The **Jetty 12** implementation (`JettyUPnpServerImpl` & `JettyWebServerImpl`) is highly optimized for Android devices:

*   **Engine:** Built on **Eclipse Jetty 12.1.2**.
*   **Performance Optimizations:**
    *   **Dynamic Threading:** Thread pool automatically scales based on the device's CPU cores (`Math.max(4, cores * 4)`), ensuring responsiveness on both low-end and flagship devices.
    *   **Non-Blocking I/O:** Uses Jetty's asynchronous I/O capabilities for handling UPnP requests and media streaming, minimizing thread blocking and memory footprint.
    *   **Adaptive Buffering:** Media stream buffers are dynamically calculated based on the device's available RAM to prevent Out-Of-Memory (OOM) errors while maintaining high throughput.
    *   **Zero-Copy Streaming:** Utilizes memory-mapped files (where supported) to stream audio directly from storage to the network interface.
    *   **Efficient Notifications:** Uses a shared `ExecutorService` for playback state notifications to eliminate thread churn during song changes.
*   **Interoperability:**
    *   Implements specific DLNA headers (`transferMode.dlna.org`, `contentFeatures.dlna.org`) to ensure compatibility with strict hardware renderers (e.g., Sony, Denon, Onkyo).
    *   Supports `GetMediaInfo`, `GetTransportInfo`, and `GetPositionInfo` for accurate playback status synchronization.

---

## 🛠 Tech Stack

*   **Language:** Java 17 / Kotlin
*   **Dependency Injection:** Hilt
*   **Async/Reactive:** RxJava 3
*   **Server Engines:** Eclipse Jetty 12, Netty 4
*   **UPnP Framework:** jUPnP (fork of Cling)
*   **Tagging:** JAudiotagger
*   **Media Processing:** FFmpeg (File Conversion & Analysis)
*   **Database:** OrmLite

## 📋 Pre-requisites

*   **Android OS:** Android 14 (API 34) or higher is required due to Jetty 12 dependencies.

## 📄 License

Copyright 2014-2025 The Android Open Source Project, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

---

## 🌐 Ecosystem Plan

### The New MusicMate Ecosystem Architecture

#### 1. The MusicMate Server (The "Core")
This is the heart of the system, running as a background service inside the MusicMate app on a single Android Device. It hosts two servers simultaneously:

-   **DLNA Digital Media Server (DMS):**
    -   **Purpose:** Compatibility.
    -   **Function:** Scans the music library and advertises it using standard UPnP protocols.
    -   **Technology:** `jupnp`.
    -   **Result:** Any DLNA DMC or renderer on the network (e.g., RopieeeXL, Volumio, a smart TV) can see and play music from it.

-   **Web Server (HTTP Server):**
    -   **Purpose:** Control.
    -   **Function:** Serves a modern web application that will act as the "Roon-like" remote.
    -   **Technology:** `jetty-server12` is perfect for embedding a powerful web server directly in the app.

#### 2. The Players (The "Endpoints" / DMRs)
-   These can be any standard DLNA Digital Media Renderer on the network.
-   The MusicMate Server will discover these devices using UPnP discovery and maintain a list of available players.
-   **Examples include:** **RoPieeeXL** (Raspberry Pi endpoints), **Volumio**, **WiiM**, **Sonos** (DLNA mode), **HEOS** devices, and Smart TVs.

#### 3. The Remote Control (The "DMC")
The system supports two types of Digital Media Controllers:

*   **Standard DLNA Controllers:** Any standard UPnP/DLNA controller app (e.g., **mConnect**, **JPLAY**, **BubbleUPnP**) can browse the MusicMate library and control playback on renderers.
*   **MusicMate Web UI:** A built-in, lightweight web application served directly by the MusicMate app.
    *   **Purpose:** Provides a "Roon-like" remote control experience in any web browser.
    *   **Scope:** A minimal DMC implementation optimized specifically for the internal MusicMate DMS server.
    *   **Access:** Open `http://<device-ip>:9000/` on any phone, tablet, or laptop on the network.

### Architecture Diagrams

#### System Overview
```
 ┌───────────────────────────┐
 │  MusicMate DMC WebUI      │
 │  - Device list            │
 │  - Play queue             │
 │  - Library browser        │
 │  - Playback controls      │
 └─────────────▲─────────────┘
               │ WebSocket / HTTP
               │
 ┌─────────────┴───────────────────┐
 │  MusicMate MediaServer          │
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
               │   Metadata Store    │
               │   (OrmLite/DB)      │
               └──────────┬──────────┘
                          │
                [4] Serve to Ecosystem
                          │
     ┌────────────────────┼────────────────────┐
     │                                         │
┌────▼──────────┐                        ┌─────▼──────────────┐
│ DLNA DMS/DMC  │                        │ WebUI Control      │                         
│ (Basic Info)  │                        │ (Roon-like views)  │
└────┬──────────┘                        └─────┬──────────────┘
     │                                         │
     └────────────────────┬────────────────────┘
                          │
               ┌──────────▼──────────┐
               │   DLNA Renderer     │
               │   (OrmLite/DB)      │
               └─────────────────────┘

```

---

## 🎼 Music Quality Guide

### Bit Depth & Dynamic Range
**Bit depth** determines the **dynamic range** (difference between softest and loudest sounds) in your music:

 - **16-bit audio:** 96dB range (CD quality)
 - **24-bit audio:** 144dB range (Studio quality)
 - **Human hearing:** ~90dB range

Higher bit depth means clearer quiet passages, smoother transitions between soft and loud moments, and more authentic sound reproduction.

### Dynamic Range Meter
Dynamic Range measures the contrast between the quietest and loudest parts of a song. Think of it as the music's ability to "breathe" – to whisper intimately one moment and then deliver powerful crescendos the next.

#### What the Numbers Mean for Your Listening Experience

**Low DR (1-6 dB): "The Wall of Sound"**
 - **What it sounds like:** Consistently loud throughout, with little difference between verses, choruses, and solos
 - **Common in:** Modern pop, EDM, heavy metal, commercial radio hits
 - **Listening experience:** Energetic but can become fatiguing during longer listening sessions
 - **Examples:** Most Billboard Hot 100 hits since 2000, mainstream EDM

**Medium DR (7-12 dB): "The Sweet Spot"**
 - **What it sounds like:** Good contrast between sections while maintaining cohesion
 - **Common in:** Well-produced rock, pop from the 80s-90s, modern jazz, quality hip-hop
 - **Listening experience:** Engaging, with enough variation to keep interest without quiet parts disappearing
 - **Examples:** Michael Jackson's "Thriller," Steely Dan, well-mastered modern indie music

**High DR (13-18 dB): "The Emotional Journey"**
 - **What it sounds like:** Distinct quiet passages building to powerful climaxes
 - **Common in:** Jazz, acoustic performances, film scores, pre-1980s rock and soul
 - **Listening experience:** Emotionally engaging, rewards active listening
 - **Examples:** Pink Floyd's "Dark Side of the Moon," Miles Davis, Adele's ballads

**Very High DR (19+ dB): "The Audiophile's Delight"**
 - **What it sounds like:** Full preservation of natural instrument dynamics from whisper-quiet to full orchestra
 - **Common in:** Classical music, audiophile jazz recordings, live acoustic performances
 - **Listening experience:** Immersive, revealing, closest to live performance