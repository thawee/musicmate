# MusicMate User Guide

Welcome to the **MusicMate** user guide! MusicMate is a high-performance Android application designed for organizing, tagging, and streaming your music collection. Whether you need to fix metadata, embed cover art, or run a DLNA media server to stream audio across your local network, MusicMate has you covered.

## Smart Queue Sources

Open **Music Center → Queue** and select from the tactile micro-capsule strip:
- **Manual:** Keep the current list and stop automatic additions.
- **New:** Automatically add tracks in MusicMate's existing New category (unorganized tracks, not a date-based arrival filter).
- **Downloads:** Automatically add all tracks matching the existing Downloads classification, including tracks you have already heard. The current classifier includes paths outside `/Music/` and paths containing `/Telegram/`.
- **Unplayed Discoveries:** Automatically add tracks with zero completed listens recorded by MusicMate (tracking begins with this update; does not infer historical listens prior to tracking).
- **Rediscover:** Automatically add tracks that were completed previously and have not been played for at least 30 days, queued oldest-last-played first.
- **Playlist ▾:** Select any built-in smart playlist (e.g. *Audiophile Sanctuary DR12+*, *Studio Masters Hi-Res*, *Pure DSD*, *Lossless Vault*) or custom user playlist to auto-fill unplayed matching tracks up to the 20-track lookahead buffer.

### Loading Playlists into the Queue
Tap the **`[📋]` Load Playlist** icon in the compact header bar or the **`Playlist ▾`** capsule to open the playlist picker:
- **Play All (▶):** Replaces the current queue with all playable tracks from the chosen playlist and starts playing track 1 immediately.
- **+ Queue (+):** Appends all tracks from the playlist to the end of the upcoming queue without interrupting the currently playing song.
- **✦ Auto (✦):** Sets the playlist as the active smart source to continuously refill the upcoming queue.

Smart sources append suggestions in library order without interrupting playback or replacing queued tracks. They check for newly indexed matches every 15 seconds while the service is running and maintain up to 20 upcoming tracks. Selecting a source also refreshes immediately; tap a track to begin playback if nothing is playing.

**Play Next** and manual additions take priority over automatic suggestions. Removed or previously supplied tracks are not automatically added again in the same session, including after restarting the app. The selected source, active playlist, queue, and current-track anchor are restored; this does not automatically resume playback or restore a seek position.

Select **Manual** to keep a queue you like and enable shuffle/repeat. Smart sources keep shuffle and repeat off for predictable, non-repeating playback. **Clear Queue** or replacing the queue from a library selection ends the smart session. Changing sources preserves the existing list and exclusions; the new source supplies subsequent additions. When there are no more suggestions, the queue shows a caught-up message instead of looping.

---

## Table of Contents
1. [Getting Started & Permissions](#1-getting-started--permissions)
2. [Exploring Your Music (MainActivity)](#2-exploring-your-music-mainactivity)
   - [Gestures & Interactions](#gestures--interactions)
   - [⋮ Single-Track Popup Menu](#-single-track-popup-menu)
   - [Long-Press Action Mode](#long-press-action-mode-multi-select)
3. [Editing Tags & Technical Details (TagsActivity)](#3-editing-tags--technical-details-tagsactivity)
4. [DLNA Media Server Management](#4-dlna-media-server-management)
5. [Best Practices & Tips](#5-best-practices--tips)

---

## 1. Getting Started & Permissions

To manage your music files directly, MusicMate uses two independent Android special-access settings:
* **Full Storage Access:** Required to scan direct file paths, modify tags, rename files, and organize music folders.
* **External Player Access:** Optional. It lets MusicMate discover and control playback sessions from supported music apps; it does not control whether MusicMate may post its own notifications.

MusicMate checks these settings silently when the app starts or resumes. It does not open Android Settings automatically. Choose **System Access** from the MusicMate menu, start a folder scan, or use the optional action in the player picker when you want to enable missing access. Returning from Settings updates the status immediately.

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
  - **Device Type & App Icons:** Target selection menu displays official **DLNA logo icons** for network renderers, **actual installed app icons** for Android music apps (Poweramp, UAPP, Foobar2000, etc.) shown by name, and active checkmarks (`✓`).
* **Play / Shuffle:** The track count line ("35 Tracks • 1.51 GB") has **Play** and **Shuffle** buttons that put every track in the current list or search results into the queue, in order or shuffled. They are hidden for artist, genre and playlist overviews.
### Gestures & Interactions

MusicMate uses a deliberate, purpose-built interaction model — every gesture has exactly one job:

| Gesture | Target | Action |
|---|---|---|
| **Single tap** | Song row (title / info area) | Opens **TagsActivity** — always, unconditionally |
| **Single tap** | Album cover art thumbnail | **Quick play** — starts playback in the active player |
| **Long press** | Song row | Enters **multi-select mode** for batch operations |
| **Tap `⋮`** | Per-item more button | Opens single-track **context popup** |

> [!NOTE]
> The cover art play indicator (▶ overlay) is only visible when a playback device is connected and active. When no player is available, tapping cover art shows a guidance toast.

### `⋮` Single-Track Popup Menu

Focused on **quick actions for one track**. The playback group is automatically hidden when no player device is active.

| Action | Shown when |
|---|---|
| ▶ **Play Now** | Player active |
| ⏭ **Play Next** | Player active |
| ➕ **Add to Queue** | Player active |
| 🎶 **Add to Playlist…** | Always |
| 👤 **Go to Artist** | Track has an artist |
| 💿 **Go to Album** | Track has an album |
| 🔁 **Convert Format** | Always |
| 🔗 **Open in External App** | Always |

### Long-Press Action Mode (Multi-select)

Focused exclusively on **batch tag management and file operations**. Playback actions are intentionally excluded to keep bulk operations clean.

| Action | Description |
|---|---|
| 🏷 **Edit Tags** | Open TagsActivity with all selected tracks |
| 🎶 **Add to Playlist** | Add all selected tracks to one of your playlists |
| 📁 **Move Files** | Relocate selected files to another folder |
| 🔁 **Convert Files** | Re-encode selected files to a target format |
| 🗑 **Delete** | Permanently delete selected files |
| ☑ **Select All** | Toggle select/deselect all visible items |

* **Unified Floating Navigation & Playback Dock (Card 20dp):**
  - **Idle State:** Displays Library icon, app title ("MusicMate"), Media Server status icon, and Menu.
  - **Playing State:** Dynamically embeds mini album art, scrolling track title, and player target subtitle (e.g. `HiBy R3 • DLNA Renderer`).
  - **Single Tap (Title/Art):** Opens the **Music Center** sheet (`AudioHubSheet.kt`) at the last-viewed tab.
  - **Long Press (Title/Art):** Scrolls the main library list to the currently playing track (haptic feedback). It does not open a separate sheet.
* **Music Center (`AudioHubSheet.kt`) — 3 Tabs:**
  - The Music Center is a single master bottom sheet with three full-height tabs: **Playback**, **Queue**, and **Server**. Swipe between tabs or tap a tab label. The last-viewed tab is remembered for the current session. Older documentation refers to this surface as `NowPlayingQueueSheet` or `AudioHubBottomSheet` — those separate sheets were consolidated and no longer exist.
  - **Playback Tab:** Shows expanded album artwork, technical format specs (e.g. `FLAC 352.8 kHz / 24bit`), active player badge, and an embedded transport row (**Previous**, **Play / Pause**, **Next**). Tap the artwork or card to flip between album art and the audio-anatomy spec sheet.
  - **Queue Tab:** Scrollable playing queue with current track gold highlighting, drag-to-reorder handles, and swipe-to-remove. Includes **Play All**, **Shuffle Toggle (🔀)**, **Repeat Mode Toggle (🔁)**, **Clear Queue**, and **Stop Playback** actions.
  - **Audio Route Path:** The real-time 3-stage pipeline telemetry (Source ➔ Transport ➔ Target) is shown on the Playback tab rather than behind a separate sheet or header icon.
  - **Tap-to-Scroll Navigation:** Tapping the track info card or any queue track row dismisses the sheet and automatically scrolls the main library list to that song's position.
* **Bottom Navigation Dock (Card Shape 16dp):**
  - **Media Server Icon:** Features live status tinting (Teal tint when DLNA server is active/running, Muted tint when offline). Tapping opens `MediaServerManagementSheet`.
* **Bluetooth Audio Playback Suite:**
  - **Live Bluetooth Codec Telemetry:** Displays live Bluetooth A2DP audio codec specs (`BT • LDAC`, `BT • aptX`, `BT • AAC`, `BT • SBC`) with a dedicated Bluetooth icon in the **Audio Route Path** telemetry.
  - **1-Tap System Audio Output Switcher:** Accessible directly from the target player picker popup (`Bluetooth / System Output...`), launching the native Android Media Output panel to quickly pair or switch Bluetooth devices.
  - **Auto-Pause on Disconnect:** Automatically pauses local audio playback when headphones, Bluetooth DACs, or car receivers disconnect (`ACTION_AUDIO_BECOMING_NOISY`), preventing accidental loudspeaker blaring.
* **High-Performance Pagination:** To prevent application lag and save memory, songs are loaded in chunks of **500 items**. As you scroll to the bottom, the next page loads automatically.
* **Collections & Filters:** In the Playlists overview, tap **New Smart Playlist** even if you have not created a playlist yet. Opening another library category clears an earlier related-track filter; use Back on a filtered list to remove the filter without changing categories.
* **Music Center & Accessibility:** Tap the mini-player artwork or track title to open Music Center. From an empty Queue, **Browse Library** closes the sheet and opens All Songs. TalkBack exposes actions to open Music Center and flip Now Playing between cover art and audio details; the Studio Console seek and volume rails support accessibility adjustments and Left/Right keyboard keys.

* **MQA tracks:** Compact song rows show `MQA` with the encoded file resolution. Expanded badges and the Studio Console distinguish `MQA MASTER` from `MQA STUDIO`, using a magenta accent. Audio details show the encoded resolution and, when available, a separately labeled original sample rate. TalkBack includes that original rate in badge descriptions. The original rate comes from file metadata; it does not confirm the decoder or DAC's current output rate.
* **Scroll Memory & State Context:** When you click on a song to view or edit tags and then return to the main list, the app intelligently remembers your precise scroll position, even if you are scrolled past 500+ items.

---

## 3. Editing Tags & Technical Details (TagsActivity)

Selecting a song from the list opens the **Tags Editor**. The expanded preview has **Back** (top-left) and an icon-only **Change Cover** control (top-right), with the title on a separate surface below the artwork, followed by quality badges, a Genre chip when available, and artist/album/folder provenance. A gold **Play** button at the cover's lower right plays the song; long-press it to play the song next. The bottom dock has **Organize** and **More…**; **More…** holds **Add to Playlist…**, tag tools, file utilities and, last in red, **Delete**, which asks before deleting.

### Your Own Playlists
Choose **Add to Playlist…** from a song's `⋮` menu, from **More…** on the song page, or from multi-select. Pick one of your playlists, or **New playlist…** to name one and add the song to it. Your playlists appear under **Playlists** with the built-in ones; songs are matched by title and artist, so a song stays in the playlist after you move or re-encode the file. Scrolling down (or tapping **Edit Song Info**) opens the detail workspace with the **Song Info / Tech Info** switcher. **Save** dims when there are no unsaved changes.

### Tab A: Song Info (Metadata Editor)
This tab allows you to edit standard fields including:
* **Text Inputs:** Title, Artist, Album, Album Artist, Track number, Year, Genre, Style, Origin, Mood, and Publisher.
* **Bulk Editing:** If you select multiple files from the main list, you can edit shared attributes simultaneously. Divergent values are marked with a `[Multi Values]` placeholder.
* **Action Menu Options (Editor Mode):**
  * 🔍 **Search & Match Tags:** Connects to MusicBrainz online database via a modernized Compose dialog to look up accurate track metadata and release artwork with 1-tap apply.
  * **Unsaved matches:** Applying a match or auto-tag result changes the draft; tap **Save** to persist it. Back asks for confirmation before discarding unsaved changes.
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
   - **Live activity:** While the server runs, the **Server** tab in Music Center shows what it is doing, for example `2 streams • 9.0 MB/s • 1,204 requests`. If a renderer was cut off to make room for another stream, or requests were turned away, the line adds `evicted`, `refused` or `rate-limited` counts.
   - **Stop persists:** After you tap Stop, reopening or recreating the main screen does not restart the server. Tap Start to enable automatic startup again; choosing a streaming target can intentionally start the server for playback.
4. **Standardized Player Target Displays:**
   - **DLNA Renderers:** Displays device friendly name and IP address (e.g. `HiBy R3 (192.168.1.50 • DLNA Renderer)`).
   - **Web Streaming:** Displays stream client IP and protocol details (e.g. `Web Streaming (192.168.1.100 • Web Streaming)`).
   - **Android Player Apps:** Displays local app title, package/version, and app type (e.g. `Poweramp (com.maxmpz.audioplayer • Android App)`).
5. **Streaming Engine:**
   - MusicMate streams with its built-in **SonicNIO** engine: Java NIO non-blocking I/O with low CPU wake-ups and true zero-copy `FileChannel.transferTo()` streaming. There is no engine setting to change.
   - **Header note:** Every stream carries the `X-Audio-*` audiophile headers (`X-Audio-Sample-Rate`, `X-Audio-Bit-Depth`, `X-Audio-Bitrate`, `X-Audio-Format`, `X-Audio-Bit-Perfect`).
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
* **Navigating Back:** Press the system **Back** key or navigation swipe gesture to leave the editor. If you have unsaved metadata changes, including a Search & Match or auto-tag result, confirm Discard or choose Cancel and **Save (💾)** first.
* **Scroll-Friendly Editing:** You can safely edit files located deep down in your list. When you save and return, the list will refresh its values but maintain your scroll context exactly where you left off.
