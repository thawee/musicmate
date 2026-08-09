# MusicMate User Guide

Welcome to the **MusicMate** user guide! MusicMate is a high-performance Android application designed for organizing, tagging, and streaming your music collection. Whether you need to fix metadata, embed cover art, or run a DLNA media server to stream audio across your local network, MusicMate has you covered.

---

## Table of Contents
1. [Getting Started & Permissions](#1-getting-started--permissions)
2. [Exploring Your Music (MainActivity)](#2-exploring-your-music-mainactivity)
3. [Editing Tags & Technical Details (TagsActivity)](#3-editing-tags--technical-details-tagsactivity)
4. [DLNA Media Server Management](#4-dlna-media-server-management)
5. [Best Practices & Tips](#5-best-practices--tips)

---

## 1. Getting Started & Permissions

To manage your music files directly, MusicMate requires specific permissions depending on your Android version:
* **All Files Access Permission:** Required on Android 11 (API 30) and higher to modify ID3 tags, rename files, and organize music folders.
* **Notification Listener Access:** Required to integrate with system audio playbacks and display lock screen media controls.

When you first open the app, the **Permission Screen** will guide you through granting these settings if they are missing.

---

## 2. Exploring Your Music (MainActivity)

The main dashboard is optimized for handling extremely large music libraries.

```
+------------------------------------------------+
|  [Search]                     [Cast / Output]  |
+------------------------------------------------+
|  Song List (Endless Scroll - Paged in 500s)     |
|  - Title                                       |
|  - Artist / Album                              |
|  - Quality Badge (Hi-Res / FLAC / MP3)         |
+------------------------------------------------+
|  [Unified Floating Dock - Card 20dp Teal]      |
|  (Lib | Art Track & Target | Prev Play Next | Server Menu) |
+------------------------------------------------+
```

### Key Features & Controls
* **Smart Search & Quick Cast:** Search your library or tap the cast icon (`rounded_music_cast_24`) in the top header for instant 1-tap renderer switching.
  - **Dynamic Status Tinting:** Tints **Gold** (`#FFC107`) when casting to a remote DLNA renderer, and default theme tint when playing locally.
  - **Device Type & App Icons:** Target selection menu displays official **DLNA logo icons** for network renderers, **actual installed app icons** for Android music apps (Poweramp, UAPP, Foobar2000, etc.), version numbers (e.g. `Poweramp • v935`), and active checkmarks (`✓`).
* **Smart Library Song List:**
  - **Single Tap (Song Title / Details):** Opens **`TagsActivity`** (Metadata Editor) for 1-tap tag editing.
  - **Single Tap (Album Cover Art Thumbnail):** Instantly enqueues and starts playing the track to the active player.
  - **Long Press:** Activates Contextual Selection Mode for bulk editing, queue management, or file deletion.
* **Unified Floating Navigation & Playback Dock (Card 20dp):**
  - **Idle State:** Displays Library icon, app title ("MusicMate"), Media Server status icon, and Menu.
  - **Playing State:** Dynamically embeds mini album art, scrolling track title, and player target subtitle (e.g. `HiBy R3 • DLNA Renderer`).
  - **Single Tap (Title/Art):** Opens the **Now Playing & Queue Sheet** (`NowPlayingQueueSheet`).
  - **Long Press (Title/Art):** Opens the **Audio Route Path Bottom Sheet** (`AudioHubBottomSheet`) for real-time audio pipeline diagnostics.
* **Now Playing & Queue Sheet (`NowPlayingQueueSheet`):**
  - Shows expanded album artwork, technical format specs (e.g. `FLAC 352.8 kHz / 24bit`), active player badge, and scrollable playing queue with current track gold highlighting.
  - **Embedded Transport Controls:** Features full **Previous**, **Play / Pause**, and **Next** transport control buttons directly inside the Now Playing track card.
  - **Audio Route Path Quick Access:** Includes a dedicated gold Audio Route Path icon button (`ic_baseline_audio_path_24`) in the top-right header to quickly jump to audio pipeline diagnostics.
  - **Tap-to-Scroll Navigation:** Tapping the track info card or any queue track row dismisses the sheet and automatically scrolls the main library list to that song's position.
  - **Queue Controls:** Includes **Play All**, **Shuffle Toggle (🔀)**, **Repeat Mode Toggle (🔁)**, **Clear Queue**, and **Stop Playback** actions.
* **Bottom Navigation Dock (Card Shape 16dp):**
  - **Media Server Icon:** Features live status tinting (Teal tint when DLNA server is active/running, Muted tint when offline). Tapping opens `MediaServerManagementSheet`.
* **Bluetooth Audio Playback Suite:**
  - **Live Bluetooth Codec Telemetry:** Displays live Bluetooth A2DP audio codec specs (`BT • LDAC`, `BT • aptX`, `BT • AAC`, `BT • SBC`) with a dedicated Bluetooth icon in the **Audio Route Path** telemetry.
  - **1-Tap System Audio Output Switcher:** Accessible directly from the target player picker popup (`Bluetooth / System Output...`), launching the native Android Media Output panel to quickly pair or switch Bluetooth devices.
  - **Auto-Pause on Disconnect:** Automatically pauses local audio playback when headphones, Bluetooth DACs, or car receivers disconnect (`ACTION_AUDIO_BECOMING_NOISY`), preventing accidental loudspeaker blaring.
* **High-Performance Pagination:** To prevent application lag and save memory, songs are loaded in chunks of **500 items**. As you scroll to the bottom, the next page loads automatically.
* **Scroll Memory & State Context:** When you click on a song to view or edit tags and then return to the main list, the app intelligently remembers your precise scroll position, even if you are scrolled past 500+ items.

---

## 3. Editing Tags & Technical Details (TagsActivity)

Selecting a song from the list opens the **Tags Editor**. The interface contains a collapsing cover art panel at the top and two main tabs at the bottom.

### Tab A: Song Info (Metadata Editor)
This tab allows you to edit standard fields including:
* **Text Inputs:** Title, Artist, Album, Album Artist, Track number, Year, Genre, Style, Origin, Mood, and Publisher.
* **Bulk Editing:** If you select multiple files from the main list, you can edit shared attributes simultaneously. Divergent values are marked with a `[Multi Values]` placeholder.
* **Action Menu Options (Editor Mode):**
  * ✨ **Reformat (Auto-Awesome):** Automatically formats tags to match standard title casing and clean up artist list separator styles.
  * 📝 **Read Tag Preview:** Reads tags directly from the underlying file structure to preview proposed updates.
  * 💾 **Save Changes:** Persists modifications to both the local database and the file's ID3/FLAC metadata blocks.

### Tab B: Tech Info (Technical Details)
This tab provides a deep-dive read-only view of file parameters, including:
* **Path Information:** Current absolute path and proposed target collection path.
* **FFmpeg Stream Metadata:** Direct output details such as sample rate, bit depth, codec, channel layout, and bitrate.
* **Action Menu Options (Tech Mode):**
  * 🔄 **Reload Tag:** Reloads metadata directly from the file to resolve sync issues.
  * 🖼️ **Extract Cover Art:** Extracts embedded artwork and exports it to your gallery.
  * 🗑️ **Remove Cover Art:** Strips embedded cover art blocks to reduce file size.

---

## 4. DLNA Media Server Management

MusicMate features an embedded Java NIO-based DLNA Media Server allowing you to stream music to smart TVs, network speakers, or computers.

1. Click the **Media Server** icon in the bottom menu.
2. A bottom sheet displays the current server status (Running / Offline), configuration details (URL, active connections), and currently active playback player.
3. Tap **Start / Stop** to toggle the server or tap **Select Player** to switch between active targets.
4. **Standardized Player Target Displays:**
   - **DLNA Renderers:** Displays device friendly name and IP address (e.g. `HiBy R3 (192.168.1.50 • DLNA Renderer)`).
   - **Web Streaming:** Displays stream client IP and protocol details (e.g. `Web Streaming (192.168.1.100 • Web Streaming)`).
   - **Android Player Apps:** Displays local app title, package/version, and app type (e.g. `Poweramp (com.maxmpz.audioplayer • Android App)`).
5. **Runtime Server Engine Switching (App Settings):**
   - Switch web server engines dynamically under **App Settings -> Server Engine** without restarting the app:
     * **SonicNIO (Default · Balanced / Zero-Copy):** Low CPU wake-ups with Java NIO non-blocking I/O.
     * **CoreHTTP (Ultra-Low Memory):** Apache HttpCore engine optimized for minimal RAM footprint.
     * **Netty (High Throughput):** Event-driven asynchronous network engine for high concurrent streaming.
6. **Server Port & Endpoints Reference:**
   - **Default Server Port:** `9000` (HTTP)
   - **Endpoints Table:**
     | Endpoint | Path Template | Description |
     | :--- | :--- | :--- |
     | **Audio Stream** | `http://<ip>:9000/music/<id>/file.<ext>` | Audio file streaming URL for DLNA renderers |
     | **Cover Art** | `http://<ip>:9000/coverart/<albumKey>` | Album artwork image endpoint |
     | **WebSocket** | `ws://<ip>:9000/ws` | Real-time playback control & status stream |
     | **Web UI Root** | `http://<ip>:9000/` | Web player dashboard (`/index.html`) |
7. Keep the app open or running in the background while streaming. The event reactor loop features epoll CPU spin protection and automated socket teardowns to ensure stability during long-running sessions.

---

## 5. Best Practices & Tips

* **Smart Casing:** Use the **Reformat** button in the editor tab to clean up punctuation or inconsistent spacing in artist lists instantly.
* **Navigating Back:** To discard changes safely, simply press the system **Back** key or navigation swipe gesture to immediately return to the main screen. If you have edited metadata, remember to click **Save (💾)** to persist your edits before leaving.
* **Scroll-Friendly Editing:** You can safely edit files located deep down in your list. When you save and return, the list will refresh its values but maintain your scroll context exactly where you left off.
