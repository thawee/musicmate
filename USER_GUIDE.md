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
|  [Search / Filter Criteria]                    |
+------------------------------------------------+
|  Song List (Endless Scroll - Paged in 500s)     |
|  - Title                                       |
|  - Artist / Album                              |
|  - Quality Badge (Hi-Res / FLAC / MP3)         |
+------------------------------------------------+
|  [Bottom App Bar: Refresh / Media Server]      |
+------------------------------------------------+
```

### Key Features
* **Smart Search & Filters:** Filter your songs by Genre, Artist, Album, Year, or custom directories using the search button at the top header.
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
2. A bottom sheet displays the current server status (Running / Offline) and configuration details (URL, active connections).
3. Tap **Start / Stop** to toggle the server.
4. Keep the app open or running in the background while streaming. The event reactor reactor loop features epoll CPU spin protection and automated socket teardowns to ensure stability during long-running sessions.

---

## 5. Best Practices & Tips

* **Smart Casing:** Use the **Reformat** button in the editor tab to clean up punctuation or inconsistent spacing in artist lists instantly.
* **Navigating Back:** To discard changes safely, simply press the system **Back** key or navigation swipe gesture to immediately return to the main screen. If you have edited metadata, remember to click **Save (💾)** to persist your edits before leaving.
* **Scroll-Friendly Editing:** You can safely edit files located deep down in your list. When you save and return, the list will refresh its values but maintain your scroll context exactly where you left off.
