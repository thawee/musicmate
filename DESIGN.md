# MusicMate UI/UX Design System & Architectural Principles

> **Last Updated:** 2026-08-11 · **Owner:** @thawee
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
- **Micro-Pill Telemetry Badges:** Audio route source specs (`FLAC 24/96`) and target renderers are styled as micro-pill badges (`shape_telemetry_chip_source.xml`, `shape_telemetry_chip_target.xml`) with `12dp` rounded corners and colored borders. Each badge uses a dedicated vector icon (`ic_round_audio_file_24` tinted in Gold for Source input format, and `ic_round_speaker_24` tinted in Cyan for Target output player) instead of raw text arrows. Target renderer labels are dynamically formatted using `PlayerNameUtils.getDropdownPlayerLabel(...)` to include rich device metadata (e.g. `Sony WH-1000XM5 (Bluetooth A2DP)`, `HiBy R3 • 192.168.1.50`), matching the player selection popup.
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

Organized into three functional groups with icons and visual group dividers (API 28+):

```
┌─────────────────────────────────────────┐
│  ✨  Auto-Tag (MusicBrainz)  ┐ Tag      │
│  🔍  Search & Match Tags    ┘ Automation│
│  ───────────────────────────────────────│ ← Group Divider
│  📊  Verify Lossless Quality (Audio)    │
│  ───────────────────────────────────────│ ← Group Divider
│  📁  Show in File Manager    ┐ File &   │
│  🌐  Search Song on Web      ┘ Web Ops  │
└─────────────────────────────────────────┘
```

1. **Tag Automation Group (`group_tag_automation`):**
   - ✨ `Auto-Tag (MusicBrainz)` (`action_auto_tag`): Automated online metadata fetching and audio fingerprinting (AcoustID).
   - 🔍 `Search & Match Tags` (`action_search_match_tags`): Text-based search and tag matching dialog.
2. **Audio Quality Analysis Group (`group_audio_analysis`):**
   - 📊 `Verify Lossless Quality` (`action_spectrum`): High-resolution spectrum analysis and authenticity verification.
3. **File System & Web Utilities (`group_file_utils`):**
   - 📁 `Show in File Manager` (`action_open_folder`): Launch system file manager at file location.
   - 🌐 `Search Song on Web` (`action_web_search`): Search song online in default web browser.

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
│  - Discover (Recently Added) │              │  - Diagnostics & About       │
│  - Quality Grade Filtering   │              │                              │
│  menu_music_collection.xml   │              │  menu_music_mate.xml         │
└──────────────────────────────┘              └──────────────────────────────┘
```

1. **Left Slide Menu — Music Content & Filtering (`menu_music_collection.xml`):**
   - **Header (`view_header_left_menu.xml`):** Displays total song count, total library duration, and a visual multi-storage space usage bar (`UIUtils.buildStoragesStatus`).
   - **Browse Group:** All Songs, Artists, Genres.
   - **Curate Group:** Collections / Folders.
   - **Discover Group:** Recently Added, Similar Songs.
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
- **Geometry:** `MaterialCardView` with `20dp` corner radius, `12dp` horizontal / `8dp` bottom margins so the dock floats above the list edge.
- **Idle State:** Displays Library icon, app title ("MusicMate"), Media Server status icon, and menu.
- **Playing State:** Dynamically embeds mini album artwork, marquee scrolling title, and target player subtitle (e.g. `HiBy R3 • DLNA Renderer`).
- **Interactions:**
  - **Single Tap (Title/Art):** Opens the 3-Tab **`AudioHubBottomSheet`** at the last-viewed tab (sticky session state, see §8C). The Audio Route Path widget lives on the `Playback` tab — no separate long-press shortcut exists.

### B. Dedicated 3-Tab Architecture (`AudioHubBottomSheet`)
Transitioned from a single congested bottom sheet to a full-height **3-Tab Viewport**:

1. **`[ Playback ]` Tab:** Maximum vertical viewport for expanded artwork, transport controls (Prev / Play-Pause / Next), and the 1-line 3-node Audio Route Path widget.
2. **`[ Queue ]` Tab:** Dedicated full-height list of upcoming tracks, total remaining duration header (`X min total`), drag-to-reorder handles, and swipe-to-remove. The tab label shows a live count (`Queue (12)`).
3. **`[ Server ]` Tab:** Embeds live DLNA Media Server status, server address with QR code and copy-URL action, start/stop controls, and a runtime engine switcher (`SonicNIO` / `CoreHTTP` / `Netty`) that persists the preference and restarts the server on change.
   - *Out of scope (for now):* server port configuration (port is fixed at `WEB_SERVER_PORT = 9000`) and per-client connection telemetry — revisit if core APIs are added.

### C. Contextual Navigation
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
- **Consequences:** Predictable modal interactions across all dialog surfaces; zero ambiguous or trapped dialog states.

---

## 10. Non-Goals

Deliberately out of scope to protect the core tag-management focus:

- **No playlist editing in the single-track popup** — queueing beyond `Play Now` / `Play Next` / `Add to Queue` belongs in the Queue tab.
- **No playback controls in Multi-Select Action Mode** — batch mode is for tag and file operations only (see §3B).
- **No destructive actions (`Delete`, `Move`) in the single-track popup** — reserved for batch mode with mandatory preview dialogs (see §8B).
- **No full music-player feature parity** — MusicMate delegates rich playback UX to external players/renderers; the built-in transport is intentionally minimal.
- **No cloud sync / streaming-service integration** — the library model is local & LAN (DLNA/UPnP) only.
