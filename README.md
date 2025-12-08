# Music Mate

**Music Mate** is a comprehensive High-Resolution Audio management and streaming application for Android. It transforms your mobile device into a powerful **DLNA/UPnP Media Server**, allowing you to stream your local high-fidelity music collection to any compatible renderer (Hi-Fi streamers, Smart TVs, AV Receivers) or play it locally with audiophile-grade quality.

---

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

Music Mate follows a modular, clean architecture using Android Jetpack components.

### Modular Server Design
The application features a unique pluggable server architecture, allowing the underlying HTTP transport engine to be swapped at compile time via Gradle flavors.

### Module Details

#### 🔹 MusicMate Core (The Backend)
The central nervous system of the application, running as a background service on Android.
*   **Responsibilities:**
    *   **DLNA/UPnP Server:** Advertises the library to the network, handles browsing requests, and streams audio.
    *   **Web Server:** Hosts the **MusicMate Remote** web application.
    *   **Metadata Manager:** Scans files, extracts tags (ID3, FLAC, Vorbis), and enriches them with high-quality metadata.
    *   **Playback Control:** Manages the "Now Playing" queue and sends commands to DLNA renderers.
*   **Key Technologies:** Java, jUPnP (Cling), Jetty 12 / Netty 4, RxJava.
*   **Server Implementations:**
    *   **Jetty (`server-jupnp-jetty`):** **(Recommended)** The robust, production-ready implementation.
    *   **Netty (`server-jupnp-netty`):** High-performance, low-memory NIO implementation (Experimental).
    *   **NIO (`server-jupnp-nio`):** Lightweight, pure Java NIO implementation.

#### 🔹 MusicMate Web Remote (The Frontend)
The user interface for controlling the system, accessible via any web browser.
*   **Responsibilities:**
    *   **Library Browsing:** Provides a rich, visual interface for navigating Artists, Albums, and Genres.
    *   **Playback Control:** Play, pause, skip, seek, and volume control for the active renderer.
    *   **Queue Management:** View and edit the upcoming song list.
    *   **Visuals:** Displays high-resolution album art and artist imagery.
*   **Key Technologies:** HTML5, CSS3, JavaScript, WebSockets for real-time updates.

#### 🔹 Music Library (The Data)
The organized collection of your audio files and their associated metadata.
*   **Responsibilities:**
    *   **Storage:** Abstraction over the local file system (Internal Storage, SD Card).
    *   **Database:** High-performance index of tracks, optimized for fast searching and filtering.
    *   **Tagging:** Support for reading and writing standard audio tags.
*   **Key Technologies:** OrmLite (Database), JAudiotagger (Tag Parsing), FFmpeg (Analysis).

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

---

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

## 🌐 MusicMate Ecosystem

For a detailed overview of the MusicMate architecture, players, and controllers, please see the [MusicMate Ecosystem Guide](ECOSYSTEM.md).


---

## 🎼 Music Quality Guide



For details on Bit Depth, Dynamic Range, and what the numbers mean for your listening experience, please see the [Music Quality Guide](MUSIC_QUALITY_GUIDE.md).