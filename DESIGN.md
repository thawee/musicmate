# MusicMate UI/UX Design System & Architectural Principles

> **Last Updated:** 2026-08-27 · **Owner:** @thawee
>
> **Scope:** This document is authoritative for UI/UX, gestures, menus, theming, and modal surfaces. For playback engine internals see `PLAYBACK_ARCHITECTURE.md`; for the web interface see `WEBUI.md`; for the WebSocket protocol see `WEBSOCKET_API.md`.

This document serves as the authoritative reference for **MusicMate's** interaction design, gesture mapping, menu architecture, and UI component decisions. All future developments and AI-assisted workflows must respect the principles and decision records detailed here.

---

## 1. Core Product Philosophy

MusicMate is fundamentally a **music library organization and tag management application**, integrated with high-fidelity local & network playback capabilities (DLNA/UPnP, Bluetooth, native Android player integrations).

### Design Principles:
1. **Primary Purpose First:** The most prominent, lowest-friction interactions (e.g., single tap on a song) must lead directly to tag management.
2. **Single Responsibility per Gesture:** Every touch target and gesture must map to exactly one primary user intent.
3. **Context-Driven Affordances:** UI elements (such as play buttons and menu groups) should dynamically reflect system capabilities and active device states rather than presenting static or broken actions.
4. **Surface Decoupling:** Menu structures must never be lazily shared between single-item context popups and batch selection toolbars.

---

## 2. Interaction Model & Gestures

```
+-----------------------------------------------------------------------+
|  [Art (Tap: Quick Play)]   Title / Artist / Album (Tap: Open Tags)  [⋮] |
+-----------------------------------------------------------------------+
```

| Gesture | Target | Destination / Action | State Dependency & Feedback |
|---|---|---|---|
| **Single Tap** | Song Row (Title, Subtitle, Info) | Open **`TagsActivity`** | Unconditional — always opens tag editor |
| **Single Tap** | Cover Art Thumbnail | **Quick Play** track | If active player present: plays immediately.<br>If no player: art stays tappable but shows guidance Toast (`"No active player — connect a device first"`). |
| **Long Press** | Song Row | Enter **Multi-Select Action Mode** | Shows selection checkboxes & top action bar |
| **Tap `⋮`** | Row More Button | Open **Single-Track Popup** | Displays context actions for that specific file |

### Now Playing Cover Art Overlay & Playing Indicators
- **Scoped Dynamic Visibility:** Cover art dark overlays (`shape_now_playing_cover_overlay.xml`) are hidden (`GONE`) for all non-playing tracks, leaving library artwork clean, bright, and un-obscured. Overlays are scoped strictly to the currently playing song (`tag.equals(playbackService.getNowPlayingSong())`).
- **Animated Vector Equalizer & Pause State:**
  - **Playing State:** When actively playing (`state == PLAYING`), renders an animated vector equalizer (`ic_equalizer_active`) tinted in Gold (`@color/colorGold`) over the cover art.
  - **Paused State:** When paused (`state == PAUSED`), displays a static Gold pause icon (`ic_baseline_pause_24`).
- **Active Track Highlight:** The title text (`item_title`) of the currently active track is highlighted in Gold (`@color/colorGold`) with bold typography to maintain clear visual hierarchy while scrolling.
- **Single-Source Indicator Rule:** Duplicate equalizer icons in secondary locations (such as the top-right status indicator bar) are removed to prevent visual clutter, establishing the cover art overlay as the single authoritative playing indicator on list items.
- **Gesture Visual Feedback Overlay:** Double-tapping or horizontal flinging album art in Music Center flashes a central action icon (`ic_baseline_play_arrow_48`, `ic_baseline_pause_48`, `ic_baseline_skip_next_48`, `ic_baseline_skip_previous_48`) with a smooth 2-stage scale-up (`0.7f` $\rightarrow$ `1.2f`) and fade-out animation.
- **Micro-Pill Telemetry Badges:** Audio route source specs (`FLAC 24/96`) and target renderers are styled as micro-pill badges (`shape_telemetry_chip_source.xml`, `shape_telemetry_chip_target.xml`) with `12dp` rounded corners and colored borders. Each badge uses a dedicated vector icon (`ic_round_audio_file_24` tinted in Gold for Source input format, and `ic_round_speaker_24` tinted in Cyan for Target output player) instead of raw text arrows. Target renderer labels are dynamically formatted using `PlayerNameUtils.getDropdownPlayerLabel(...)` or `AudioOutputHelper.getCompactLabel()` to include rich device metadata (e.g. `Sony WH-1000XM5 • BT (LDAC)`, `HiBy R3 • 192.168.1.50`), matching the player selection popup.
- **Unified Sparkle (✦) Symbol Badge System for NEW Tracks:** Replaced yellow dot/banner overlays and yellow rectangular blocks with a cohesive Sparkle/Starburst symbol system across the application:
  - **Cover Art Thumbnail Overlay:** Uses a precision 14dp vector badge (`ic_new_sparkle_badge.xml` in Gold for unmanaged tracks, and `ic_new_download_sparkle_badge.xml` in Cyan for newly downloaded tracks) anchored to the top-right corner of the cover art thumbnail.
  - **Tag Activity Header Chip:** `NewIndicatorView` renders a rounded pill chip (`12dp` radius) featuring a Sparkle vector icon (`auto_awesome`) alongside bold Oswald typography ("NEW"), styled in dark gold (`#2E2712`) / dark cyan (`#0D2E3D`) chip backgrounds to replace yellow text blocks.

---

## 3. Menu Architecture & Rationalization

### Surface Decoupling Architecture
Previously, `menu_main_actionmode.xml` was shared between the single-track `⋮` popup menu and the multi-select `ActionMode` toolbar. This created UX clutter and required code hacks (like hiding `Select All` at runtime).

The menu architecture is now strictly decoupled into two dedicated menus:

```
                  ┌───────────────────────────────┐
                  │    Music List Interactions    │
                  └──────────────┬────────────────┘
                                 │
                 ┌───────────────┴───────────────┐
                 ▼                               ▼
       [Single Item: ⋮ Popup]       [Multi-Select: Action Mode]
     menu_track_popup.xml            menu_main_actionmode.xml
```

---

### A. Single-Track Popup Menu (`menu_track_popup.xml`)

Focused strictly on **quick playback actions** and **file-level utility** for one track.

```
┌─────────────────────────────────────────┐
│  ▶  Play Now            ┐               │
│  ⏭  Play Next           │ Playback Group│
│  ➕  Add to Queue        ┘               │
│  ───────────────────────────────────────│
│  🔁  Convert Format     ┐ File Ops      │
│  🔗  Open in External   ┘ Group         │
└─────────────────────────────────────────┘
```

- **Dynamic Visibility:** The playback group (`R.id.group_playback`) is hidden dynamically (`setGroupVisible(false)`) when no playback service/device is bound.
- **Excluded Items:**
  - `Song Info / Edit Tags`: Single tap on the row already opens `TagsActivity`.
  - `Move Files` & `Delete`: Prevents destructive mis-taps on single tracks; these belong in batch mode or dedicated flows.
  - `Select All`: Irrelevant for single-item popups.

---

### B. Multi-Select Action Mode (`menu_main_actionmode.xml`)

Focused exclusively on **batch tag management and file operations**.

```
+-----------------------------------------------------------------+
| [✓ 5 selected]    [🏷 Edit]  [📁 Move]  [🔁 Convert]  [🗑]  [☑] |
+-----------------------------------------------------------------+
```

- **Action Items:**
  1. 🏷 **Edit Tags** (`action_edit_metadata`): Open `TagsActivity` in bulk edit mode for all selected items.
  2. 📁 **Move Files** (`action_transfer_file`): Relocate selected media files.
  3. 🔁 **Convert Files** (`action_encoding_file`): Batch audio re-encoding.
  4. 🗑 **Delete** (`action_delete`): Batch file deletion with confirmation.
  5. ☑ **Select All** (`action_select_all`): Select / clear selection toggle.
- **Excluded Items:** Playback controls (`Play Now`, `Play Next`, `Add to Queue`). Batch queueing is handled within the dedicated Queue Sheet (`AudioHubBottomSheet` / `NowPlayingQueueSheet`).

---

### C. Tag Activity "More Actions" Menu (`tag_more_actions_menu.xml`)

Organized into four functional groups with Material vector icons and visual group dividers (API 28+):

```
┌─────────────────────────────────────────┐
│  ▶  Play Track Now          ┐ Playback  │
│  ➕  Add to Playing Queue    ┘ & Queue   │
│  ───────────────────────────────────────│ ← Group Divider
│  ✨  Auto-Tag (MusicBrainz)  ┐ Metadata  │
│  🔍  Search & Match Tags    │ Automation│
│  🪄  Smart Clean & Format   ┘ & Cleanup │
│  ───────────────────────────────────────│ ← Group Divider
│  🎼  Lossless Spectrum Verifier (Audio) │
│  ───────────────────────────────────────│ ← Group Divider
│  📁  Show in File Manager    ┐ File &   │
│  🌐  Search Song on Web      │ Sharing  │
│  📤  Share Audio File        ┘          │
└─────────────────────────────────────────┘
```

1. **Playback & Queue Group (`group_playback`):**
   - ▶ `Play Track Now` (`action_play_now`): Immediate direct playback via `PlaybackService`.
   - ➕ `Add to Playing Queue` (`action_add_to_queue`): Append track(s) to active playing queue.
2. **Metadata Automation & Curation Group (`group_tag_automation`):**
   - ✨ `Auto-Tag (MusicBrainz)` (`action_auto_tag`): Automated online metadata fetching and audio fingerprinting (AcoustID).
   - 🔍 `Search & Match Tags` (`action_search_match_tags`): Text-based search and tag matching dialog.
   - 🪄 `Smart Clean & Format` (`action_smart_clean_format`): 1-tap master pipeline combining junk noise stripping, title casing, and Thai encoding recovery.
3. **Audio Analysis & Verification Group (`group_audio_analysis`):**
   - 🎼 `Lossless Spectrum Verifier` (`action_spectrum`): High-resolution spectrogram analysis up to 48kHz frequency ceiling.
4. **File System & Sharing Utilities (`group_file_utils`):**
   - 📁 `Show in File Manager` (`action_open_folder`): Launch system file manager at file location.
   - 🌐 `Search Song on Web` (`action_web_search`): Search song online in default web browser.
   - 📤 `Share Audio File` (`action_share`): Direct system share sheet for single or batch audio files via `MusicFileProvider`.

---

### D. Left & Right Slide Menu Architecture (`ResideMenu`)

MusicMate employs a dual sliding menu design (`ResideMenu`) with a strict separation of concerns:

```
┌──────────────────────────────┐              ┌──────────────────────────────┐
│   LEFT SLIDE MENU            │              │   RIGHT SLIDE MENU           │
│   (ResideMenu.DIRECTION_LEFT)│              │   (ResideMenu.DIRECTION_RIGHT)│
├──────────────────────────────┤              ├──────────────────────────────┤
│  Data & Library Navigation   │              │  App System & Settings       │
│  - Storage & Collection Stats│              │  - Manage Library Folders    │
│  - Browse (All, Artist, Genre)│              │  - App Settings & Features   │
│  - Curate (Collections)      │              │  - Permissions & Storage     │
│  - Discover (Incoming Tracks)│              │  - Diagnostics & About       │
│  - Quality Grade Filtering   │              │                              │
│  menu_music_collection.xml   │              │  menu_music_mate.xml         │
└──────────────────────────────┘              └──────────────────────────────┘
```

1. **Left Slide Menu — Music Content & Filtering (`menu_music_collection.xml`):**
   - **Header (`view_header_left_menu.xml`):** Displays total song count, total library duration, and a visual multi-storage space usage bar (`UIUtils.buildStoragesStatus`).
   - **Browse Group:** All Songs, Artists, Genres.
   - **Curate Group:** Collections / Folders.
   - **Discover Group:** Incoming Tracks, Similar Songs.
   - **Audiophile Group:** Quality Grade (Hi-Res, FLAC, Lossy classification).

2. **Right Slide Menu — App Control & Diagnostics (`menu_music_mate.xml`):**
   - **System Management Group:** Manage Library Folders, App Settings.
   - **System Permissions Group:** Storage Access status, Notification Access status.
   - **System Diagnostics & Info:** Crash Diagnostics, About MusicMate.

---

## 4. Cast / Output Device Picker Design (`showPlayerPickerPopup`)

The player selection popup accessible from the top bar cast button (`rounded_music_cast_24`) manages output target selection across Local Audio, Installed Android Player Apps, and DLNA/UPnP Renderers.

### Menu Structure & Hierarchy

```
┌───────────────────────────────────────────────────────────┐
│  🔊  Local Player (Phone Speaker)                ✓       │ ┐
│  📻  HiBy R3 (192.168.1.50 • DLNA Renderer)              │ │ Group 0:
│  🎵  Poweramp (v935 • Android App)                       │ ┘ Targets
│  ─────────────────────────────────────────────────────────│ ← API 28+ Divider
│  🔄  Rescan for DLNA players                             │ ┐ Group 1:
│  🔵  Bluetooth / System Output…                          │ ┘ Utilities
└───────────────────────────────────────────────────────────┘
```

### Key UX Decisions:
1. **Zero-Latency Auto-Discovery:** On opening the popup, `refreshPlayerDiscovery()` is immediately triggered in the background. Devices begin populating without requiring an explicit manual tap.
2. **Visual Group Separation:** Targets (Group 0) and Utilities (Group 1) are visually divided via `setGroupDividerEnabled(true)` (Android P+ / API 28+). On older devices the groups render as a flat list without dividers — item ordering alone preserves the grouping, so no functional fallback is required.
3. **Ordering by Proximity to Effect:**
   - **Rescan** is placed directly below the player target list because its action directly modifies the list above it.
   - **Bluetooth / System Output…** is placed at the bottom because selecting it navigates away from the app into Android System Settings / Output Panel.
4. **Informative Empty State:** When no remote renderers are found, displays a disabled `"Scanning for players…"` placeholder item rather than an interactive/confusing error item.

---

## 5. Style, Theme & Color System

MusicMate follows a modern Material 3 design foundation, tailored with a custom **Audiophile Dark/Light Palette** and specialized visual telemetry chips.

### A. Color Palette & Adaptive Theming

| Token | Light Theme | Dark Theme (OLED) | Purpose / Application |
|---|---|---|---|
| `colorPrimary` | `#F57C00` (Warm Orange) | `#FFB74D` (Sunset Amber/Gold) | Primary branding, active buttons, selection highlights |
| `colorSurface` | `#FFFFFF` (Crisp White) | `#000000` (True OLED Black) | Base app background, card surface |
| `colorSurfaceVariant`| `#F5F5F5` | `#121212` (Dark Charcoal) | Secondary cards, lists, sheet backgrounds |
| `colorSecondary` | `#E64A19` (Coral) | `#FF8A65` (Peach Coral) | Accent badges, subtle warnings |
| `bt_accent` | `#00B8D4` (Cyan) | `#00B8D4` (Cyan) | Bluetooth / System audio route highlights |
| `colorPlayingIndicator`| `#1E88E5` (Blue) | `#FFA726` (Gold) | Active track playing indicator |

### B. Dynamic Context Tinting Rules

To give immediate visual feedback without clogging the screen with text, state colors are dynamically applied across the interface:

```
┌────────────────────────────────────────────────────────────────────────┐
│  State                        │ Visual Color Tint                      │
├───────────────────────────────┼────────────────────────────────────────┤
│ Remote DLNA Cast Active       │ 🟨 Gold (#FFC107) on header cast icon  │
│ Local Audio / Android App     │ 🟦 Primary Theme Tint                  │
│ DLNA Media Server Running     │ 🟩 Teal/Cyan (#00B8D4) on server icon  │
│ DLNA Media Server Stopped     │ ⬜ Muted Grey (#7A6D67)                │
│ Active Bluetooth Audio Codec  │ 🔷 Cyan Badge (LDAC / aptX / AAC / SBC)│
└────────────────────────────────────────────────────────────────────────┘
```

> **Amber Disambiguation:** Three near-identical warm tones carry distinct semantics and never appear on the same element: `#FFB74D` (dark-theme *primary*, branding/buttons), `#FFC107` (*cast icon* gold, remote DLNA session active), and `#FFA726` (*playing indicator* gold, marks the active track row in dark theme). Users distinguish them by **location**, not hue — keep these roles fixed when introducing new gold accents.

---

### C. Audiophile Node Geometries & Telemetry Chips

The **Audio Route Path** telemetry widget (`AudioHubBottomSheet` & Now Playing card) uses asymmetric shape drawables to visually differentiate pipeline stages:

- **Source Node (`shape_node_source.xml`):** Asymmetric curved chip displaying audio format specs (`FLAC 24/96`, `DSD128`, `MQA`).
- **Transport Node (`shape_node_transport.xml`):** Hexagonal / chamfered node representing pipeline routing (`SonicNIO Engine`, `Local Transport`).
- **Target Node (`shape_node_target.xml` / `shape_node_target_bitperfect.xml`):** HSL glowing stroke chip highlighting output targets (`USB Bit-Perfect`, `HiBy R3 DLNA`, `Bluetooth LDAC`).
- **Connector (`shape_node_connector.xml`):** Linear flow arrow connecting the 1-line 3-node path: Source ➔ Transport ➔ Target.

---

### D. Quality & Dynamic Range (DR) Badges

File metadata badges use color-coded pill drawables to convey audio quality at a glance:

- **Hi-Res Audio:** Gold background (`shape_background_hires.xml`).
- **Lossless (FLAC/ALAC):** Green tint (`shape_background_lossless.xml`).
- **Lossy (MP3/AAC):** Neutral dark grey tint (`shape_background_lossy.xml`).
- **Dynamic Range (DR):**
  - **High DR (≥12):** Green (`#388E3C`)
  - **Medium DR (8–11):** Amber (`#FBC02D`)
  - **Low DR (≤7):** Red (`#D32F2F`)

---

## 6. Layout & Sheet Architecture

MusicMate's layout hierarchy is anchored by a persistent main list paired with floating cards and bottom sheets:

```
┌─────────────────────────────────────────────────────────┐
│  Top Bar: Search & Cast Icon (Dynamic Gold Tint)       │
├─────────────────────────────────────────────────────────┤
│  Song Library RecyclerView (Paged 500s, Scroll Memory) │
│  - Single tap row ➔ TagsActivity                        │
│  - Single tap art ➔ Quick Play                          │
├─────────────────────────────────────────────────────────┤
│  [ Unified Floating Navigation & Playback Dock Card ]   │
│  (Idle: App Logo & Server Icon | Playing: Mini Art + Track)
└─────────────────────────────────────────────────────────┘
```

- **Paged Loading:** The library list loads in incremental pages of **500 tracks** (`MainViewModel.PAGE_SIZE`) to keep initial render fast on large libraries.
- **Scroll Memory:** Before the adapter is repopulated (refresh, filter change), the `LayoutManager` state is saved via `onSaveInstanceState()` and restored afterward, so list updates never jump the user's scroll position. Active multi-select selections are likewise preserved across data reloads.

### A. Unified Floating Dock (`CardView 20dp` Corner Radius)
- **Geometry:** `MaterialCardView` with `20dp` corner radius, `12dp` horizontal / `8dp` bottom margins so the dock floats cleanly above the list edge with insets margin (`systemBars.bottom + 8dp`).
- **Layout Architecture (Left-to-Right Hierarchy):**
  - **Far Left:** Mini Album Artwork thumbnail (`bar_album_art`, 44dp × 44dp), serving as the primary visual anchor for the playing track (tap opens Music Center).
  - **Center:** Docked Playback Bar with marquee scrolling track title (`bar_track_title`), target output player subtitle (`bar_target_subtitle`), and Play/Pause & Next transport controls.
  - **Far Right:** Collections / Navigation Menu button (`navigation_collections`, 48dp × 48dp), positioned on the right edge in the primary reach zone for effortless one-handed thumb navigation.
- **Idle State:** Displays idle audio icon, app title ("MusicMate"), and target player prompt.
- **Playing State:** Dynamically embeds live album artwork, marquee scrolling title, and target player subtitle (e.g. `HiBy R3 • DLNA Renderer`).
- **Interactions:**
  - **Single Tap (Title/Art):** Opens the 3-Tab **`AudioHubBottomSheet`** at the last-viewed tab (sticky session state, see §8C).
  - **Single Tap (Menu Button):** Opens the Library Collections drawer / navigation sheet (`doShowLeftMenus()`).

### B. Tag Activity Accessible 2-Row Bottom Action Dock (`shape_bottom_frosted_panel`)
- **Geometry:** Edge-to-edge true bottom anchor (`0dp` corner radius, `0dp` margins), pinned flush to the window bottom (`bottomMargin = 0`), extending the frosted obsidian background (`shape_bottom_frosted_panel`) to the physical screen edge with dynamic system navigation bar insets applied as bottom padding.
- **2-Tier Functional Architecture:**
  - **Row 1 (Static Global File Tier):** Permanent file operations (`[Delete]` in subtle error tone, `[⭐ Organize]` in primary Gold tonal pill, `[More... ⋯]`). Consistently accessible across Preview, Song Info, and Tech Info tabs.
  - **Row 2 (Dynamic Active Fragment Tier):** Contextual workflows populated based on the active viewport:
    - *Preview Mode:* `[✏️ Edit Song Info]` | `[💾 Save]` (Gold tonal pill). Always exposes an immediate Save button in preview mode so users can commit edits made via the "More..." power menu without switching tabs.
    - *Song Info Editor Tab:* `[✨ Format]` | `[📄 From File]` | `[💾 Save]` (Gold tonal pill).
    - *Tech Info Tab:* `[🔄 Reload]` | `[🖼️ Extract]` | `[🗑️ Remove Art]`.
- **Unified Immersive Hero Cover Art Layout (`activity_tags.xml`):**
  - **Clean Cover Artwork Viewport:** Full 1:1 aspect ratio album artwork (`AspectRatioPhotoView`) free of top scrims or distracting visual clutter, keeping 90%+ of the album art 100% visible.
  - **Cinematic Bottom Gradient Scrim (`shape_bottom_cover_scrim`):** Groups the atomic Track Identity at the base of the artwork:
    - **Title (`panel_title`):** Prominent bold 18sp white title text with clean ellipsis protection (`maxLines = 2`).
    - **Subtitle (`panel_artist`):** Clean 13.5sp `#DDDDDD` subtitle formatted dynamically as `{Artist} • {Album}` (or single fallback if one is missing).
  - **Eliminated Redundancy:** Completely removed the legacy split `[ Artist | Album ]` two-column box and top title scrim, unifying visual parity with the Now Playing playback viewport.
- **Unified 4-Tier Audiophile Header Hierarchy (`TagPreviewHeader` in Compose):**
  1. **Tier 1 — Quality Tier & Visual Badges:** Expanded audiophile quality tier badges (`[● CD QUALITY]`, `[● HI-RES LOSSLESS]`, `[● 24-BIT STUDIO]`, `[● DSD AUDIO]`, `[● MQA MASTER]`, `[● STANDARD QUALITY]`) identical to the Now Playing playback sheet, paired with `ResolutionBadge` (`[16/44.1]`, `[24/96]`, `[DSD64]`), `DynamicRangeMeter` (`[DR 11]`), star rating, and New status badge.
  2. **Tier 2 — 2-Line Studio Provenance Capsules:** Frosted obsidian discovery micro-capsules with live track counts and haptic ripples:
     - **Row 1 (Music Discography):** `[ 👤 {Artist} • N ❯ ]` (Gold `#FFD700`) & `[ 💿 {Album} • N ❯ ]` (Teal `#80CBC4`) with flex width and ellipsis protection.
     - **Row 2 (Storage Location):** `[ 📁 {Folder} • N ❯ ]` (Slate Blue `#90CAF9`) centered underneath.
     - *In-Place Discography Sheet (`RelatedTracksSheet.kt`):* Tapping any capsule opens an in-place frosted modal sheet with live track list, audiophile badges, 1-tap playback, `[ ▶ Play All ]`, and `[ ➕ Queue All ]` without closing the tag editor.
  3. **Tier 3 — Musical Taxonomy & Character Chips:** Positioned cleanly below provenance to establish a natural narrative (Fidelity ➔ Provenance ➔ Taxonomy), featuring semantic emoji glyphs: `[ 🎸 Genre ]`, `[ 🎭 Mood ]`, `[ 🎨 Style ]`, and `[ 🌏 Origin ]`.
  4. **Tier 4 — Grounding Technical Telemetry Footer Strip:** Positioned at the very base of the header card with dedicated top breathing space (`7.dp`), rendering a monospace specs baseline: `FLAC • 24/96 • 4608 kbps • Stereo • 05:54 • 198 MB`.
- **Micro-Labels & Icon Styling:** Row 2 buttons feature compact, scannable text labels alongside Material vector icons (`minWidth="0dp"`, `10dp`–`16dp` horizontal touch padding) to eliminate icon-only ambiguity.
- **Tactile Micro-Haptics & Tooltips:**
  - `performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)` on all button clicks and `HapticFeedbackConstants.LONG_PRESS` on long presses.
  - Comprehensive `TooltipCompat.setTooltipText` on every action across Row 1 and Row 2.
- **Dynamic Multi-Track Batch Badging:** Buttons dynamically display active selection counts (`Delete (N)`, `Organize (N)`, `Save (N)`) when editing batches of songs.
- **Pro Long-Press Shortcuts:**
  - Long-press `[✨ Format]` ➔ Executes the **Full Clean Pipeline** (Junk noise removal + Title Case + Thai encoding repair in a single pass).
  - Long-press `[💾 Save]` ➔ Executes **Save & Close** (commits metadata and returns to track list).

### C. Dedicated 3-Tab Architecture (`AudioHubBottomSheet` in Jetpack Compose)
Transitioned from a single congested bottom sheet to a full-height **3-Tab Viewport**:

1. **`[ Playback ]` Tab (`NowPlayingPage.kt`):** Edge-to-edge album artwork, bottom overlay title, expanded streaming tier quality badge (`[● HI-RES LOSSLESS]`, `[● CD QUALITY]`, `[● 24-BIT STUDIO]`, `[● DSD AUDIO]`, `[● MQA MASTER]`, `[● STANDARD QUALITY]`), interactive output target selector pill, glowing seekbar, full transport controls, and interactive 3D Y-axis flip card revealing the **Audio Anatomy** spec sheet (with gold `ic_round_info_24` badge, Codec, Resolution, Bitrate, Duration, Dynamic Range, Audio Channels, File Size, and Track #/Year/Genre in compact scrollable rows).
2. **`[ Queue ]` Tab (`QueuePage.kt`):** Dedicated full-height list of upcoming tracks, total remaining duration header (`X min total`), drag-to-reorder handles, and swipe-to-remove. The tab label shows a live count (`Queue (12)`).
3. **`[ Server ]` Tab (`MediaServerPage.kt`):** Jetpack Compose Media Server management featuring a top Hero Status Card (live Wi-Fi SSID chip, status LED, Gold Start / Crimson Stop power action button), 1-tap WebUI actions, tap-to-enlarge high-contrast QR code modal, and segmented engine switcher (`SonicNIO` / `CoreHTTP` / `Netty`) with dynamic architecture descriptions.

### D. Contextual Navigation
- **Queue Tab row tap:** Immediately **plays the tapped track** — the queue is a playback surface, not a navigation surface.
- **Now Playing card tap (Playback tab):** Dismisses the sheet and smoothly scrolls the main library list to the currently playing song's position.

---

## 7. Touch Targets, Accessibility (a11y) & Feedback

### A. Minimum Touch Target Boundaries
- All interactive action icons (`⋮` overflow button, play overlay, transport buttons) strictly enforce a minimum touch target area of **48dp × 48dp**, regardless of icon graphic size (e.g., 24dp icon padded to 48dp container).

### B. Dynamic `contentDescription` Telemetry
Interactive stateful views update their accessibility labels in real time:
- Play/Pause Button: Toggles dynamically between `"Play"` and `"Pause"`.
- Cover Art Play Overlay: `android:contentDescription="@string/cd_quick_play"`.
- Media Server Dock Icon: Toggles accessibility text based on state (`"DLNA Server Active"` vs `"DLNA Server Stopped"`).

### C. System Event Feedback & Audio Safeguards
- **Auto-Pause on Disconnect:** Registered `ACTION_AUDIO_BECOMING_NOISY` / `ACTION_ACL_DISCONNECTED` receiver automatically pauses playback when headphones or Bluetooth receivers disconnect, preventing unexpected speaker blaring.
- **Guidance Toasts:** When performing actions without prerequisite services (e.g., tapping quick play without an active player), non-blocking Toast alerts provide instant feedback (`"No active player — connect a device first"`).

### D. Dropdown & Selection Popups (`item_dropdown_dark.xml`)
- **Single-Line Formatting:** Dropdown text views enforce `android:maxLines="1"`, `android:singleLine="true"`, and `android:ellipsize="end"` with `TextAppearance.Material3.BodyMedium` (14sp) and compact padding (`10dp` vertical / `14dp` horizontal).
- **Dynamic Popup Sizing (`WRAP_CONTENT`):** Dropdowns configure `input.setDropDownWidth(ViewGroup.LayoutParams.WRAP_CONTENT)` so popups expand to fit long labels on a single line rather than being artificially clamped to narrow half-screen input fields.
- **Classification Card Form Hierarchy:** To eliminate text truncation on variable-length music taxonomy:
  - **Genre & Style:** Positioned as full-width (`match_parent`) single-column fields to accommodate compound genre/style names (*Contemporary R&B*, *Electronic / Dance*, *Synth-based Pop*).
  - **Origin & Mood:** Positioned as a 2-column side-by-side pair (`layout_weight="1"` each), cleanly grouping compact contextual attributes without vertical space waste.

---

## 8. Dialog & Bottom Sheet Design System

MusicMate standardizes all modal overlays into two formal surfaces: **Material Action Dialogs** and **Music Center Bottom Sheets**.

```
┌─────────────────────────────────────────┐
│ [Icon]  Action Dialog Title         [✕] │
│ ─────────────────────────────────────── │
│  Item Preview List / Configuration      │
│  - Track 1 (FLAC 24/96 • 45 MB)         │
│  - Track 2 (FLAC 16/44.1 • 28 MB)       │
│ ─────────────────────────────────────── │
│          [CANCEL]   [PRIMARY CONFIRM]   │
└─────────────────────────────────────────┘
```

### A. Material Action Dialog Standards (`MaterialAlertDialogBuilder`)
- **Theme Definition:** All application dialogs must use `MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)`.
- **Corner Geometry:** `24dp` rounded corners (`shape_bottom_background_cut_corner.xml` / `MaterialShapeDrawable`).
- **Header & Close Button Standard:** Every action dialog features a header bar with a contextual icon, title, and a **top-right Close icon (✕)** (`48dp × 48dp` target with `12dp` padding, ID `btn_close` / `btn_close_*`). Tapping the top-right `✕` dismisses the dialog immediately.
- **Bottom Action Buttons Standard:** Every action dialog (except information-only overlays and the unique 3-tab Music Center bottom sheet) must present both:
  - **Positive / Primary Action Button:** (`button_ok`, `button_search`, `button_move_to_trash`, `button_encode_file`, etc.) styled as a Material3 Tonal or Filled Button.
  - **Negative / Cancel Action Button:** (`button_cancel`, `button_cancel_trash`, etc.) styled as a `Material3 TextButton` with label `"Cancel"`.
- **Button Type Integrity:** All dialog action buttons defined in XML as `com.google.android.material.button.MaterialButton` must be cast to `MaterialButton` in code (never `TextView`), ensuring proper state handling (`setEnabled(false)`, ripples, disabled alpha).
- **Auto-Dismiss Requirement:** Long-running file operation dialogs (`Delete`, `Move`, `Convert`) must automatically call `alert.dismiss()` upon operation completion (`onComplete()`).
- **Button Styling & Color Canon:**
  - **Primary / Constructive Actions (Save, Move, Convert, Scan):** Tonal or Filled Material Button (`R.color.teal_200` or Warm Gold `#F57C00`).
  - **Destructive Actions (Delete, Trash):** Red Alert (`colorError` / `colorOnError`).
  - **Cancel / Negative Actions:** Text Button (`button_cancel`) with white/onSurface text.

### B. File & Item Preview Requirement
For file-altering operations (`Delete`, `Move Files`, `Convert Format`), dialogs embed a custom scrollable item preview (`view_action_files.xml`, `view_action_directories.xml`, `view_action_encoding_files.xml`).
- **Design Rule:** Never present a confirmation dialog for batch file operations without displaying a preview of the affected track list and total data volume.

---

### C. Music Center Bottom Sheets (`AudioHubBottomSheet`)

```
┌─────────────────────────────────────────────────────────┐
│ [ Playback ]        [ Queue (12) ]       [ Server 🟢 ]  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│          Fixed 65%-Height Viewport Content              │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

- **Top Edge Geometry:** `24dp` top corner radius.
- **Viewport Sizing:** The sheet opens fully expanded at a **fixed 65% of screen height** (clamped between min/max bounds), with `12dp` side margins — a single predictable size for all three tabs rather than per-tab expansion modes.
- **Sticky Session State:** Selected tab position (`Playback`, `Queue`, or `Server`) remains sticky when closing and reopening the Music Center during a session (`AudioHubBottomSheet.sLastSelectedTab`).

---

## 9. Architectural Decision Records (ADRs)

> **Convention:** Each ADR records **Status** (`Proposed` / `Accepted` / `Superseded by ADR-XXX`), **Date**, **Context**, **Decision**, and **Consequences**. Never delete a superseded ADR — mark it `Superseded` and link its replacement so decision history is preserved.

### ADR-001: Unconditional Single-Tap to Tag Editor
- **Status:** Accepted
- **Date:** 2026-08
- **Context:** Previously, tapping a song row conditionally opened `TagsActivity` only if the playback service was not connected; otherwise, it played the track. This caused unpredictable navigation behavior when a player connected in the background.
- **Decision:** Row single-tap always opens `TagsActivity`. Tapping cover art handles quick play.
- **Consequences:** Consistent mental model; users can reliably edit tags at any time.

### ADR-002: Decoupled Menu Definitions
- **Status:** Accepted
- **Date:** 2026-08
- **Context:** A single `menu_main_actionmode.xml` was used for both popup menus and action mode, forcing runtime code modifications and mixing batch ops with single-track actions.
- **Decision:** Split into `menu_track_popup.xml` (single track) and `menu_main_actionmode.xml` (action mode).
- **Consequences:** Cleaner XML files, simpler Kotlin/Java handlers, zero runtime menu mutation hacks.

### ADR-003: Auto-Discovery & Divided Player Picker
- **Status:** Accepted
- **Date:** 2026-08
- **Context:** The cast selection popup mixed selection targets and utility actions in a flat list, requiring manual rescan taps to discover network renderers.
- **Decision:** Auto-fire M-SEARCH on popup launch and group target renderers separately from system/utility actions with visual dividers.
- **Consequences:** Faster device switching experience with clear visual affordances.

### ADR-004: 3-Tab AudioHubBottomSheet
- **Status:** Accepted
- **Date:** 2026-08
- **Context:** A single bottom sheet crammed transport controls, the upcoming queue, and DLNA server status into one congested scroll surface, leaving no room for the queue list or server telemetry.
- **Decision:** Restructure into a 3-tab viewport (`Playback` / `Queue` / `Server`) sized at a fixed 65% of screen height, with sticky per-session tab selection.
- **Consequences:** Each concern gets a dedicated viewport; queue management (reorder, swipe-remove) becomes practical; server diagnostics no longer compete with playback controls.

### ADR-005: Unified Floating Dock
- **Status:** Accepted
- **Date:** 2026-08
- **Context:** Navigation affordances and now-playing state previously occupied separate UI regions, wasting vertical space and splitting user attention between two surfaces.
- **Decision:** A single floating `CardView` dock (20dp radius) anchors the bottom of the main screen, morphing between an idle state (logo, title, server icon, menu) and a playing state (mini art, marquee title, target player subtitle). Tap opens `AudioHubBottomSheet` at the last-viewed tab.
- **Consequences:** One persistent anchor for both navigation and playback; state morphing keeps the layout footprint constant. A long-press deep-link to route diagnostics was considered and dropped — the route widget is already one tap away on the Playback tab, and an invisible duplicate gesture added no value.

### ADR-006: Standardized Action Dialog Controls & Dual Dismiss Affordance
- **Status:** Accepted
- **Date:** 2026-08
- **Context:** Action dialogs across the app had inconsistent control affordances — some lacked top-right close buttons, others missed bottom cancel buttons, and file operations left dialogs open after completion.
- **Decision:** Standardize all modal action dialogs to provide dual dismiss affordances (top-right `✕` icon + bottom `button_cancel` text button) and primary confirmation (`button_ok`), with auto-dismiss on operation completion. The `AudioHubBottomSheet` (Music Center) retains its unique 3-tab layout design.
### ADR-007: Robust Audio Tag Read/Write & Dual-Chunk WAV Support
- **Status:** Accepted
- **Date:** 2026-08
- **Context:** Previously, tag write exceptions in `JThinkWriter` were swallowed and returned `void`, causing the Room database to update and report success even when storage writes failed. In addition, multi-value delimiters were inconsistent between reader/writer, WAV files lacked standard RIFF INFO chunks for legacy stereos, and batch I/O wasn't throttled.
- **Decision:** 
  1. `TagWriter.writeTag()` and `FileRepository.setMusicTag()` return `boolean`, updating the Room DB and displaying success only when physical disk commit succeeds.
  2. Normalize multi-value delimiters (`/`, `;`, `&`, `,`) to clean comma-separated strings (`", "`) on read, preserving band names like `AC/DC`.
  3. Support dual-chunk WAV writing: write standard `WavInfoTag` (RIFF INFO chunk) for legacy car stereos alongside `ID3Tag` (ID3v2 chunk) for modern audiophile software.
  4. Enable embedded artwork writing via `ArtworkFactory` in `JThinkWriter`.
  5. Throttle bulk save operations sequentially on `MusicMateExecutors.getExecutorService()` to avoid I/O lockup on slow micro-SD cards.
- **Consequences:** Safe and predictable file persistence, zero silent data loss, universal WAV compatibility, and resilient bulk editing.

### ADR-008: Automatic Bluetooth Audio Optimization & Real-Time Codec Telemetry
- **Status:** Accepted
- **Date:** 2026-08-14
- **Context:** Output device labels were inconsistent across surfaces: Bluetooth devices used verbose parenthesis formatting (`Sony WH-1000XM5 (Bluetooth Audio)`), while DLNA renderers used bullet formatting (`HiBy R3 • 192.168.1.50`) and Android apps used versions (`Poweramp • v935`). Bluetooth devices lacked dynamic codec detection, and the signal path target badge incorrectly fell back to `DIRECT SYSTEM OUTPUT`. Furthermore, users playing 24/96 Hi-Res files over Bluetooth had no automatic way to request LDAC / aptX HD.
- **Decision:**
  1. Adopt the compact **`{Name} • BT ({Codec})`** / **`{Name} • BT`** format for Bluetooth devices (e.g. `Sony WH-1000XM5 • BT (LDAC)`), saving horizontal space and keeping brand names visible on mobile displays.
  2. Implement `AudioOutputHelper.getCompactLabel()` as the single source of truth for both `MainActivity` (player dropdown) and `AudioHubBottomSheet` (Playback tab header & route widget).
  3. Implement real-time Bluetooth A2DP codec detection (`LDAC`, `aptX HD`, `aptX`, `AAC`, `LC3`, `SBC`, `Opus`, `SSC`) via reflection on `BluetoothCodecStatus`, promoting resolution to 24-bit / 96 kHz for hi-res codecs.
  4. Implement **Automatic Background Codec Optimization** (`AudioOutputHelper.autoOptimizeBluetoothCodec`): MusicMate silently requests highest codec priority (`LDAC 24/96` or `aptX HD`) upon Bluetooth connection without requiring manual dialog button presses.
  5. Tapping the Step 3 output card in the Music Center directly opens Android's native Media Output panel or Bluetooth settings with zero modal dialog friction.
  6. Standardize output type names to Title Case (`"Bluetooth Audio"`, `"USB DAC"`, `"Wired Headphones"`, `"Phone Speaker"`).
  7. Fix Audio Route Path Step 3 target badge to correctly display `BLUETOOTH A2DP` and `Active Bluetooth A2DP Wireless Stream`.
- **Consequences:** Harmonized naming across all playback renderers, optimal single-line fit on high-DPI smartphone displays (e.g. Samsung Galaxy S25), rich audiophile telemetry, and a zero-friction, automatic path to the highest Bluetooth audio quality.

### ADR-009: Song Detail / Tag Editor (`TagsActivity`) Viewport Hierarchy & Metadata Deduplication
- **Status:** Accepted
- **Date:** 2026-08-14
- **Context:** In `TagsActivity`, when the `AppBarLayout` header was fully expanded in Preview mode (`mode == 0`), the `TabLayout` (`Song Info` / `Tech Info`) was pushed to the bottom of the screen, colliding directly with the floating bottom action capsule (`Delete` | `Organize` | `More...` / `Edit Song Info`). Additionally, when a track's `Album Artist` matched its `Artist`, the artist string was printed twice in succession (`Artist` and `Album Artist` above `Genre`), and song title overlays on album art lacked sufficient contrast on light covers.
- **Decision:**
  1. **Dynamic Tab Lifecycle:** In Preview mode with the header expanded, `tabLayout` is hidden (`GONE`). When the user taps `Edit Song Info` or scrolls to collapse the header, `tabLayout` smoothly transitions to the top of the viewport under the action bar.
  2. **Metadata Deduplication:** `Album Artist` is displayed in `genreView` only if it exists and differs from the primary `Artist` (e.g. `Various Artists` compilations). If identical, it is suppressed to display `Artist | Album` followed cleanly by `❖ Genre ❖`.
  3. **High-Contrast Top Scrim:** Enhanced `shape_background_main_header.xml` with a dark top-down vignette gradient (`#CC0A0A0A` $\rightarrow$ `#00000000`) for high text legibility across all album artwork brightness levels.
- **Consequences:** Eliminates visual clipping and tab bleed-through in preview mode, removes redundant text repetition, and elevates the visual presentation of song details.

### ADR-010: Build System Hygiene & Dependency Modularization
- **Status:** Accepted
- **Date:** 2026-08-14
- **Context:** Over successive releases, `gradle/libs.versions.toml` accumulated over 60 lines of dead/commented-out dependencies (Jackson, RxJava, Guava, Skydoves, old Cling/UPnP forks, unreferenced Jetty/HttpCore54 entries), `settings.gradle` contained dozens of commented module includes, and `core/build.gradle` contained duplicate dependency declarations. Local library modules (`androidtagview`, `crashreporter`) also carried unused transitive dependencies.
- **Decision:**
  1. Restructure `libs.versions.toml` into clean domain groups (*SDK & Toolchain*, *Core Architecture*, *Media & Playback*, *UI & Utilities*, *Networking & UPnP*, *Testing*) with 100% active dependencies and zero commented bloat.
  2. Clean `settings.gradle` to only include active project modules.
  3. Prune unused transitive dependencies from local submodules (`androidtagview`, `crashreporter`) and remove duplicate dependencies in `core/build.gradle`.
- **Consequences:** Cleaner dependency tree, faster compilation times, lower memory footprint during builds, and straightforward dependency auditing.

### ADR-011: Floating Bottom Dock Layout Hierarchy & Music Center Compose Architecture
- **Status:** Accepted
- **Date:** 2026-08-21
- **Context:** The floating dock previously placed the (M) Menu button on the left and the album art on the right, which inverted standard media player ergonomics (album art as leading visual anchor; menu in the primary one-handed right thumb zone). In addition, the Media Server tab required a full Jetpack Compose overhaul with a prominent Hero Status Card and reliable Start/Stop control execution, while the 3D flip Audio Anatomy card needed a dedicated info icon and compact typography to prevent vertical clipping on smaller viewports.
- **Decision:**
  1. **Dock Layout Inversion:** Swap positions in `activity_main.xml` so Mini Album Art (`bar_album_art`) is on the far left, Play/Pause/Next in the center, and Collections/Menu (`navigation_collections`) on the far right.
  2. **Media Server Compose Redesign:** Implement `MediaServerPage.kt` with a prominent Hero Status Card containing live Wi-Fi SSID chip, status LED, Gold Start / Crimson Stop buttons, interactive QR code zoom modal dialog, and segmented engine switcher with zero label truncation.
  3. **Direct Service Execution & Reactive Observation:** Ensure `AudioHubBottomSheet` directly invokes `msi.stopServers()` / `startServers()` and observes `MusicMateServiceImpl.getStatusLiveData()` alongside `MediaServerViewModel` to ensure instant UI reactivity.
  4. **Compact Audio Anatomy Card:** Add `ic_round_info_24` to the `AUDIO ANATOMY` header and restructure technical specs into compact horizontal rows with `verticalScroll` to eliminate vertical clipping across all aspect ratios.
### ADR-012: Flagship Audiophile Provenance Hierarchy, Tactile Micro-Haptics & Pure Compose Bottom Sheet
- **Status:** Accepted
- **Date:** 2026-08-22
- **Context:** Music track cards previously lacked a consistent audiophile provenance ordering, causing resolution badges and dynamic range meters to appear in inconsistent positions. Numeric time strings shifted horizontally when scrolling rapidly due to proportional font glyph widths. Furthermore, fast scrolling and transport controls lacked tactile haptic feedback, and the legacy Bottom Sheet container used ViewPager2 rather than pure Jetpack Compose sheet physics.
- **Decision:**
  1. **Option A Audiophile Provenance Hierarchy:** Enforce standard left-to-right studio flow on track cards: `[QualityBadge]` ➔ `[ResolutionBadge]` ➔ `[DynamicRangeMeter]` ➔ `[Duration]`.
  2. **Tabular Numerals:** Apply `fontFeatureSettings = "tnum"` to track duration text (`04:23`), ensuring identical character width across digits (`0` through `9`) to prevent horizontal jitter during scrolling.
  3. **Luminous Glass Aura:** Add an 8% accent-tinted background with a 38% alpha frosted glass border to `QualityBadge` and `ResolutionBadge`.
  4. **Tactile Micro-Haptics:** Add `LocalHapticFeedback` ticks (`TextHandleMove`) on alphabet transitions during fast-scrolling and on playback transport button presses (Play, Pause, Skip, Previous, Shuffle, Repeat).
  5. **Pure Compose Modal Bottom Sheet:** Implement `AudioHubSheet.kt` (`ModalBottomSheet` + `HorizontalPager`) for 120Hz gesture navigation across Playback, Queue, and Server tabs.
  6. **Bulletproof Drawer Interop:** Bind `drawerState` directly via `SideEffect` in `DrawerInterop.kt` and set `elevation = 4dp` on the right (M) menu button in `activity_main.xml`.
- **Consequences:** Cohesive studio provenance layout, rock-solid numerical stability during fast scrolling, tactile physical engagement, and silky 120Hz gesture physics.

### ADR-013: Tag Activity Accessible 2-Row Command Bar Architecture & Pro Curation Workflows
- **Status:** Accepted
- **Date:** 2026-09-01
- **Context:** An experimental 1-row command bar in `TagsActivity` compressed all actions into a single horizontal row, forcing secondary fragment actions into icon-only buttons or overflow menus. This compromised touch accessibility, created icon ambiguity (e.g. distinguishing formatting vs reading tags vs saving), and hid high-frequency curation shortcuts.
- **Decision:**
  1. **Restore 2-Row Functional Separation:** Establish a strict 2-tier command dock:
     - **Row 1 (Static Global File Operations):** Permanent `[Delete]`, `[⭐ Organize]`, `[More... ⋯]` dock, always accessible across all views.
     - **Row 2 (Dynamic Active Fragment Tier):** Contextual controls (`[✏️ Edit Song Info]` | `[💾 Save]` in preview, `[✨ Format] | [📄 From File] | [💾 Save]` in Song Info editor, `[🔄 Reload] | [🖼️ Extract] | [🗑️ Remove Art]` in Tech Info diagnostics).
  2. **Micro-Labels & Icon Hierarchy:** Pair vector icons with explicit, scannable micro-labels and generous horizontal touch padding (`10dp`–`16dp`, `minWidth="0dp"`).
  3. **Tactile Micro-Haptics & Tooltips:** Bind `performHapticFeedback` to all taps/long-presses and attach `TooltipCompat.setTooltipText` across all buttons.
  4. **Dynamic Batch Badging:** Real-time multi-selection counts dynamically badge action buttons (`Delete (N)`, `Organize (N)`, `Save (N)`).
  5. **Pro Long-Press Shortcuts:**
     - Long-press `[✨ Format]`: Executes **Full Clean Pipeline** (Junk noise removal + Title Case + Thai encoding repair in a single pass).
     - Long-press `[💾 Save]`: Executes **Save & Close** (commits metadata and finishes activity).
  6. **Direct File Sharing:** Integrated `[Share Audio File]` into the "More Actions" power menu for instant single/multi-file dispatch via `MusicFileProvider`.
- **Consequences:** Maximizes one-handed reachability, eliminates icon ambiguity, preserves clean separation between global file and fragment contexts, and gives power users lightning-fast bulk curation gestures.

### ADR-014: High-End Studio Provenance Capsules & In-Place Related Tracks Sheet
- **Context:** On the Tag Preview screen, discovering other songs with the same Artist, same Album, or in the same Directory previously required abruptly closing the tag editor and dumping the user back into the main library list with a filter, breaking user inspection flow. Single-line rendering of 3 capsules also caused severe text truncation on narrow mobile viewports.
- **Decision:**
  1. **2-Line Studio Provenance Layout:** Embed 3 frosted glass micro-capsules on `TagPreviewHeader` across 2 structured rows:
     - **Row 1 (Music Provenance):** `[ 👤 {Artist} • {N} ❯ ]` (Gold accented `#FFD700`) & `[ 💿 {Album} • {N} ❯ ]` (Acoustic Teal accented `#80CBC4`).
     - **Row 2 (Storage & Location):** `[ 📁 {Folder} • {N} ❯ ]` (Slate Blue accented `#90CAF9`).
     - Streamlined Auto-Tag: Eliminated conditional quick-fix chips to prevent vertical screen jumpiness; Auto-Tag is accessible cleanly via the `More...` power menu.
  2. **In-Place Frosted Context Sheet (`RelatedTracksSheet.kt`):** Tapping any capsule smoothly slides up a Compose Modal Bottom Sheet with:
     - Real-time track list with audiophile quality badges (`QualityBadge`, `ResolutionBadge`, `DynamicRangeMeter`).
     - 1-tap track playback (`onPlayTrack`).
     - Sticky bottom action dock: `[ ▶ Play All ]`, `[ ➕ Add All to Queue ]`, `[ 🔍 View in Library ]`.
### ADR-015: Elimination of On-Screen Volume Slider for Audiophile Bit-Perfect Clarity & Ergonomic Focus
- **Status:** Accepted
- **Date:** 2026-09-02
- **Context:** The Now Playing sheet previously contained an on-screen horizontal volume slider row sandwiched directly between the chromatic Seekbar and the primary transport controls. This caused multiple architectural and UX compromises:
  1. *Bit-Perfect Digital Integrity:* Digital software volume attenuation reduces bit depth and dynamic resolution before audio reaches external DACs. Audiophiles stream in fixed 100% output mode and control volume on analog amplifiers, DAC preamps, or physical DAP dials.
  2. *Hardware Button Redundancy:* Physical volume rocker buttons on the Android device already control media volume with tactile precision without consuming screen real estate.
  3. *Touch Target Crowding & Accidental Seeks:* Having two parallel horizontal sliders (Seekbar and Volume) stacked vertically in close proximity (~40dp) caused frequent accidental track seeking when attempting to adjust volume, and vice-versa.
  4. *UPnP Microstack Flooding:* Continuous sliding generated rapid bursts of UPnP SOAP `SetVolume` requests that overloaded single-threaded DAP microstacks (such as HiBy R3 and Shanling).
- **Decision:**
  1. Completely remove the persistent on-screen volume slider row from `NowPlayingPage.kt`.
  2. Rely on Android hardware volume rocker buttons for local audio and physical volume dials on external DACs / DAPs / amplifiers for network streaming.
  3. Dedicate the reclaimed vertical space (`10dp` breathing spacer) to expand touch margins and elevate visual focus on the Album Artwork, glowing chromatic Seekbar, and primary Transport buttons.
- **Consequences:** Clean, distraction-free, modern playback UI (matching reference audiophile apps like Apple Music, Qobuz, and Roon); zero touch-target collision with the Seekbar; guaranteed bit-perfect digital signal output; zero UPnP volume command congestion.

---

## 10. Non-Goals

Deliberately out of scope to protect the core tag-management focus:

- **No playlist editing in the single-track popup** — queueing beyond `Play Now` / `Play Next` / `Add to Queue` belongs in the Queue tab.
- **No playback controls in Multi-Select Action Mode** — batch mode is for tag and file operations only (see §3B).
- **No destructive actions (`Delete`, `Move`) in the single-track popup** — reserved for batch mode with mandatory preview dialogs (see §8B).
- **No full music-player feature parity** — MusicMate delegates rich playback UX to external players/renderers; the built-in transport is intentionally minimal.
- **No cloud sync / streaming-service integration** — the library model is local & LAN (DLNA/UPnP) only.

