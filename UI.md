# MusicMate UI/UX Design System & Human Interface Guidelines

> **Last Updated:** 2026-09-25 · **Owner:** @thawee
>
> **Scope:** This document is the authoritative specification for MusicMate's UI/UX design tokens, interaction models, gesture mappings, menus, theming, modal surfaces, and Jetpack Compose component architectures. For backend system architecture, audio engine internals, and multi-target playback routing, see [`DESIGN.md`](file:///Users/thawee.p/Workspaces/github/musicmate/DESIGN.md) and [`PLAYBACK_ARCHITECTURE.md`](file:///Users/thawee.p/Workspaces/github/musicmate/PLAYBACK_ARCHITECTURE.md).

---

## 1. Core Product Philosophy & Design Principles

MusicMate is fundamentally a **music library organization and tag management application**, integrated with high-fidelity local & network playback capabilities (DLNA/UPnP, Bluetooth, native Android player integrations).

### Design Principles:
1. **Primary Purpose First:** The most prominent, lowest-friction interactions (e.g., single tap on a song in Curator mode, or dedicated tag action buttons) must lead directly to tag management and audiophile metadata inspection.
2. **Single Responsibility per Gesture:** Every touch target and gesture must map to exactly one primary user intent without modal ambiguity.
3. **Context-Driven Affordances:** UI elements (such as play buttons, codec badges, and menu groups) should dynamically reflect system capabilities and active device states rather than presenting static or non-functional actions.
4. **Surface Decoupling:** Menu structures must never be lazily shared between single-item context popups and batch selection toolbars.
5. **Tactile Audiophile Aesthetics:** The design language celebrates physical hi-fi equipment heritage—utilizing deep OLED obsidian glassmorphism, authentic jewel status LEDs, precision hairline metallic borders, and calibrated analog ballistic needle dynamics.

---

## 2. Interaction Model & Gestures (Dual Persona Architecture)

MusicMate adapts to two distinct audiophile personas via a configurable **Interaction Mode** (`PREF_TAP_ACTION_MODE`):

```
+-----------------------------------------------------------------------------------------+
|  [Cover Art]      Title / Artist / Album (Specs: Quality • Resolution • DR)        [⋮]  |
|                                                                                         |
|  • Listener Mode:  Single Tap Row ➔ Quick Play   | Long Press ➔ Open TagsEditor        |
|  • Curator Mode:   Single Tap Row ➔ TagsActivity | Tap Art ➔ Quick Play | Long Press ➔  |
+-----------------------------------------------------------------------------------------+
```

| Gesture | Target | Listener Mode (`TAP_MODE_LISTEN`) | Curator Mode (`TAP_MODE_CURATE`) |
|---|---|---|---|
| **Single Tap** | Song Row (Title, Specs) | **Quick Play** track immediately (`onTrackQuickPlayClicked`) | Open **`TagsActivity`** (Tag Editor & Technical Specs) |
| **Single Tap** | Cover Art Thumbnail | **Quick Play** track / Pause toggle | **Quick Play** track |
| **Long Press** | Song Row | Open **`TagsActivity`** (Direct deep-dive inspection) | Enter **Multi-Select Action Mode** (Batch Tagging) |
| **Tap `⋮`** | Row More Button | Open **Single-Track Context Menu** | Open **Single-Track Context Menu** |

> **User Switching:** Configured in `Settings` ➔ `INTERACTION MODE` via an instant segmented switcher `[ 🎧 Listener Mode | 🏷 Curator Mode ]`. Default is `Listener Mode` for casual music enjoyment, with 1-tap switching to `Curator Mode` for collection management sessions.

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

## 3. Surface & Menu Architecture

### Surface Decoupling Architecture
Previously, `menu_main_actionmode.xml` was shared between the single-track `⋮` popup menu and the multi-select `ActionMode` toolbar. This created UX clutter and required code hacks (like hiding `Select All` at runtime).

The menu architecture is strictly decoupled into dedicated menus:

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

### E. Player Picker Menu Structure & Hierarchy (`PlayerPickerDialog`)

```
┌───────────────────────────────────────────────────────────┐
│  Audio Output Picker                                   ✕  │
│  ───────────────────────────────────────────────────────  │
│  NETWORK STREAMERS                                        │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ 📻 HiBy R3                                    ✓     │  │ ← Gold active card
│  │    192.168.1.50 • DLNA Renderer                     │  │
│  └─────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ 📻 WiiM Pro                                         │  │
│  │    192.168.1.45 • DLNA Renderer                     │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                           │
│  THIS DEVICE                                              │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ 🔊 Phone Speaker (or 🎛️ FiiO KA13 / 🎧 Bluetooth)   │  │
│  │    Internal Speaker / USB Bit-Perfect / LDAC        │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                           │
│  INSTALLED MUSIC APPS                                     │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ [🎨 Poweramp Icon] Poweramp                         │  │ ← Native App Icon
│  │                    v935 • External Player           │  │   (Squircle 6dp)
│  │ [🎨 UAPP Icon]     USB Audio Player PRO             │  │
│  │                    v6.1 • External Player           │  │
│  └─────────────────────────────────────────────────────┘  │
│  ───────────────────────────────────────────────────────  │
│  🔄  Rescan for DLNA players                              │ ┐ Group 1:
│  🔵  Bluetooth / System Output…                           │ ┘ Utilities
└───────────────────────────────────────────────────────────┘
```

- **Categorized Three-Tier Hierarchy:**
  - **`NETWORK STREAMERS`:** DLNA/UPnP network renderers (e.g. HiBy R3, WiiM Pro, Eversolo) placed first for instant casting access.
  - **`THIS DEVICE`:** The local phone audio output, dynamically displaying the actual hardware device (Phone Speaker `ic_round_speaker_24`, USB DAC bit-perfect `ic_baseline_usb_24`, or Bluetooth audio `ic_round_bluetooth_audio_24`).
  - **`INSTALLED MUSIC APPS`:** External media players (Poweramp, UAPP, Spotify, Neutron, etc.) placed in a dedicated section.
- **Authentic App Icons:** External music apps render their real native application icon via `packageManager.getApplicationIcon()` with smooth squircle clipping (`RoundedCornerShape(6.dp)`), displayed without color tinting to preserve brand identity. Fallback to `rounded_music_note_24` if uninstalled.
- **Priority Within Categories:** Each category sorts the active/selected target (`isSelected`) to the top with an Amber/Gold border (`0x80FFD700`) and checkmark (`✓`), followed by remaining devices sorted alphabetically.
- **Dynamic Local Device Telemetry:** Local player replaces generic labels and SD card icons with live hardware detection: `"Phone Speaker"` with `"Internal Speaker"`, `"FiiO KA13"` with `"USB Bit-Perfect Output"`, or `"WH-1000XM5"` with `"Bluetooth • LDAC"`.
- **Zero-Latency Auto-Discovery:** On opening the popup, `refreshPlayerDiscovery()` triggers both DLNA M-SEARCH and an installed package scan (`isPackageInstalled` against `SUPPORTED_PLAYERS`), populating targets immediately without requiring an explicit manual tap.
- **Ordering by Proximity to Effect:**
  - **Rescan** is placed directly below the player target list because its action directly modifies the list above it.
  - **Bluetooth / System Output…** is placed at the bottom because selecting it navigates away from the app into Android System Settings / Output Panel.
- **Informative Empty State:** When no remote renderers are found, displays a disabled `"Scanning for audio output devices…"` placeholder item rather than an interactive/confusing error item.

---

## 4. Style, Theme & Color System

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

### D. Quality, Resolution & Dynamic Range (DR) Badges

File metadata badges use frosted obsidian glass micro-capsules (`Color(0xD9101010)`) with 8% accent tint, 38% alpha glowing borders, and luminous LED status dots:

- **Quality Tier (`QualityBadge`):**
  - **DSD / 1-Bit Direct:** Luminous Cyan LED dot (`#00E5FF`)
  - **Hi-Res Audio (24-bit / ≥48kHz):** Luminous Gold LED dot (`#FFD700`)
  - **MQA Studio / Master:** Luminous Violet LED dot (`#E040FB`)
  - **CD Quality Lossless (16/44.1):** Luminous Sky Blue LED dot (`#64B5F6`)
  - **Standard Lossy (MP3/AAC):** Neutral Steel Grey LED dot (`#9E9E9E`)
  - *Expanded Mode:* In Now Playing hero cards, renders full audiophile grade tokens (`[● HI-RES LOSSLESS]`, `[● 24-BIT STUDIO]`, `[● CD QUALITY]`, `[● DSD AUDIO]`, `[● MQA MASTER]`).
- **Studio Resolution (`ResolutionBadge`):** Displays compact precision rate tokens (`24/96`, `16/44.1`, `DSD64`, `320k`).
- **Dynamic Range (`DynamicRangeMeters`):**
  - **High DR (≥12):** Luminous Green (`#00E676`)
  - **Medium DR (8–11):** Vibrant Amber (`#FFA000`)
  - **Low DR (≤7):** Overload Crimson (`#FF3B30`)
  - *Standard Mode:* Monospace numerical score + 30dp × 6dp rounded level bar.
  - *Compact Mode:* Clean numerical token (`DR12`) when horizontal space is constrained.
- **Responsive Badge Synthesis (`UnifiedAudioBadge` on Screens < 390dp):**
  - To eliminate song title and artist ellipsis truncation on compact viewports, `TrackListItem.kt` automatically evaluates `LocalConfiguration.current.screenWidthDp`.
  - **Screens < 390dp:** Fuses tier, depth, and sampling rate into a single unified badge (`[● HI-RES 24/96]`, `[● CD 16/44.1]`, `[● DSD64]`, `[● 320k]`) alongside compact `DR12` text, saving **~40dp of horizontal space**.
  - **Screens ≥ 390dp:** Renders the full multi-badge studio provenance sequence (`[QualityBadge]` ➔ `[ResolutionBadge]` ➔ `[DynamicRangeMeter]` ➔ `[Duration]`).

---

## 5. Layout & Sheet Architecture

MusicMate's layout hierarchy is anchored by a persistent main list paired with floating cards and bottom sheets:

```
┌─────────────────────────────────────────────────────────┐
│  Top Bar: Search & Cast Icon (Dynamic Gold Tint)       │
├─────────────────────────────────────────────────────────┤
│  Song Library RecyclerView (Paged 500s, Scroll Memory) │
│  - Single tap row ➔ TagsActivity (or Quick Play)       │
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
  - **Center:** Docked Playback Bar with marquee scrolling track title (`bar_track_title`), target output player subtitle (`bar_target_subtitle`), and Play/Pause & Next transport controls. Fullscreen Studio Console is available through the Audio Hub header rather than a separate dock icon.
  - **Far Right:** Collections / Navigation Menu button (`navigation_collections`, 48dp × 48dp), positioned on the right edge in the primary reach zone for effortless one-handed thumb navigation.
- **Idle State:** Displays idle audio icon, app title ("MusicMate"), and target player prompt.
- **Playing State:** Dynamically embeds live album artwork, marquee scrolling title, and target player subtitle (e.g. `HiBy R3 • DLNA Renderer`).
- **Interactions:**
  - **Single Tap (Title/Art):** Opens the 3-Tab **`AudioHubBottomSheet`** at the last-viewed tab (sticky session state, see §7C).
  - **Single Tap (Menu Button):** Opens the Library Collections drawer / navigation sheet (`doShowLeftMenus()`).

### B. Tag Activity Accessible 2-Row Bottom Action Dock (`shape_bottom_frosted_panel`)
- **Preview viewport:** The expanded cover header uses 82% of usable screen height. Both the Song Info / Tech Info switcher and editor pages are hidden, leaving cover artwork, a title surface, badges, and the bottom action dock. Opening the editor reveals the tabs and pages, reduces the header to 72% (animated over ~220ms), and sizes the scrollable page above the measured action dock, including its navigation-bar inset.
- **Preview ↔ Edit scroll hysteresis (`TagsActivity.OffSetChangeListener`):** Mode transitions use ratio thresholds rather than exact extremes so the tab pill cannot ride into the fixed dock mid-drag:
  - Enter edit mode when `scrollRatio >= 0.72` (header mostly scrolled away).
  - Return to preview when `scrollRatio <= 0.40` (header less than half expanded again).
  - The band between 0.40 and 0.72 holds the current mode (hysteresis).
- **Geometry:** Edge-to-edge true bottom anchor (`0dp` corner radius, `0dp` margins), pinned flush to the window bottom (`bottomMargin = 0`), extending the frosted obsidian background (`shape_bottom_frosted_panel`) to the physical screen edge with dynamic system navigation bar insets applied as bottom padding.
- **2-Tier Functional Architecture:**
  - **Row 1 (Static Global File Tier):** Permanent file operations (`[Delete]` in subtle error tone, `[⭐ Organize]` in primary Gold tonal pill, `[More... ⋯]`). Consistently accessible across Preview, Song Info, and Tech Info tabs.
  - **Row 2 (Dynamic Active Fragment Tier):** Contextual workflows populated based on the active viewport:
    - *Preview Mode:* `[✏️ Edit Song Info]` | `[💾 Save]` (Gold tonal pill). Always exposes an immediate Save button in preview mode so users can commit edits made via the "More..." power menu without switching tabs. Save mutes to 45% alpha when `isDirty` is false; it stays visible so power-menu edits remain committable without entering the editor.
    - *Song Info Editor Tab:* `[✨ Format]` | `[📄 From File]` | `[💾 Save]` (Gold tonal pill).
    - *Tech Info Tab:* `[🔄 Reload]` | `[🖼️ Extract]` | `[🗑️ Remove Art]`.
- **Unified Immersive Hero Cover Art Layout (`activity_tags.xml`):**
  - **Clean Cover Artwork Viewport:** Full 1:1 aspect ratio album artwork (`AspectRatioPhotoView`) free of top scrims or distracting visual clutter, keeping 90%+ of the album art 100% visible.
  - **Cover Overlay Affordances (theme surface icon buttons):**
    - `[◀]` **Back** (`btn_back`, top-start): Triggers `onBackPressedDispatcher` (unsaved-changes dialog still applies).
    - `[🖼]` **Change Cover** (`btn_change_cover_art`, top-end): A 48dp icon button opens the cover-art action sheet (search / pick / extract / remove). Top margin is `16dp`; status-bar insets are already applied to the AppBar. The separate Play overlay has been removed.
  - **Title surface below artwork:** `panel_title` uses bold 18sp theme text with clean ellipsis protection (`maxLines = 2`) on its own surface below the image, rather than a gradient overlay.
  - **Eliminated Redundancy:** Removed the legacy split `[ Artist | Album ]` box and duplicate `panel_artist` subtitle. Discography exploration stays on the Compose provenance capsules.
- **Unified 4-Tier Audiophile Header Hierarchy (`TagPreviewHeader` in Compose):**
  - The badge strip (`tags_header_badges`) is constrained directly under `cover_art_container` (not pinned to the header bottom), so fidelity → taxonomy → provenance read as one stack with no empty blur band on tall screens.
  1. **Tier 1 — Quality Tier & Visual Badges:** Expanded audiophile quality tier badges (`[● CD QUALITY]`, `[● HI-RES LOSSLESS]`, `[● 24-BIT STUDIO]`, `[● DSD AUDIO]`, `[● MQA MASTER]`, `[● STANDARD QUALITY]`) identical to the Now Playing playback sheet, paired with `ResolutionBadge` (`[16/44.1]`, `[24/96]`, `[DSD64]`), `DynamicRangeMeter` (`[DR 11]`), star rating, and New status badge. All micro-capsules use `CircleShape` for a consistent pill language.
  2. **Tier 2 — Interactive Musical Taxonomy Micro-Chips (`TaxonomyChipsRow`):** Positioned directly below the audio badges, establishing a natural narrative (Fidelity ➔ Musical Taxonomy ➔ Library Provenance). Renders compact frosted obsidian micro-pills with zero vertical space penalty when unpopulated:
     - `[ 🏷️ {Genre} › ]`: Acoustic Teal (`#80CBC4`), interactive 1-tap exploration sliding up `RelatedTracksSheet` filtered by `Constants.FILTER_TYPE_GENRE`.
     - Mood and Style remain editable in Song Info but are no longer repeated in the compact preview row.
  3. **Tier 3 — Fluid Discography Capsules (`StudioProvenanceSection`):** Compact frosted obsidian micro-capsules (`#D9101010`, `6dp` radius, `0.75dp` border) surfacing live library counts and direct in-place discovery via `RelatedTracksSheet`:
     - **Row 1 (Music Discography):** `[ 👤 {Artist} • {N} ❯ ]` (Gold `#FFD700`) & `[ 💿 {Album} • {N} ❯ ]` (Acoustic Teal `#80CBC4`) side-by-side with equal flex width and ellipsis protection.
     - **Row 2 (Storage Location):** `[ 📁 {Folder} • {N} ❯ ]` (Slate Blue `#90CAF9`) centered underneath.
- **Micro-Labels & Icon Styling:** Row 2 buttons feature compact, scannable text labels alongside Material vector icons (`minWidth="0dp"`, `10dp`–`16dp` horizontal touch padding) to eliminate icon-only ambiguity.
- **Tactile Micro-Haptics & Tooltips:**
  - `performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)` on all button clicks and `HapticFeedbackConstants.LONG_PRESS` on long presses.
  - Comprehensive `TooltipCompat.setTooltipText` on every action across Row 1 and Row 2.
- **Dynamic Multi-Track Batch Badging:** Buttons dynamically display active selection counts (`Delete (N)`, `Organize (N)`, `Save (N)`) when editing batches of songs.
- **Pro Long-Press Shortcuts:**
  - Long-press `[✨ Format]` ➔ Executes the **Full Clean Pipeline** (Junk noise removal + Title Case + Thai encoding repair in a single pass).
  - Long-press `[💾 Save]` ➔ Executes **Save & Close** (commits metadata and returns to track list).

### C. Dedicated 3-Tab Architecture (`AudioHubBottomSheet` in Jetpack Compose)
Transitioned from a single congested bottom sheet to a full-height **3-Tab Viewport** powered by the **Fluid Audiophile Glass Pill** switcher:

1. **`[ Playback ]` Tab (`NowPlayingPage.kt`):** Edge-to-edge album artwork, streamlined full-width bottom overlay title marquee, expanded streaming tier quality badge (`[● HI-RES LOSSLESS]`, `[● CD QUALITY]`, `[● 24-BIT STUDIO]`, `[● DSD AUDIO]`, `[● MQA MASTER]`, `[● STANDARD QUALITY]`), interactive output target selector pill, glowing seekbar, full transport controls, and interactive full-surface 3D Y-axis flip card revealing the **Audio Anatomy** spec sheet (with gold `ic_round_info_24` title badge, Codec, Resolution, Bitrate, Duration, Dynamic Range, Audio Channels, File Size, and Track #/Year/Genre in compact scrollable rows).
2. **`[ Queue ]` Tab (`QueuePage.kt`):** Dedicated list of upcoming tracks, compact summary header (`X tracks • Y min`), drag-to-reorder handles, and swipe-to-remove. Features streamlined ~50dp row density (`7dp` vertical padding, `13.5sp/11.5sp` typography, and `52f` reorder thresholds) displaying 6–7 tracks simultaneously within the 65% sheet viewport while strictly preserving Material 3 minimum 48dp touch bounds. The tab label displays an Apple-style monospace count badge chip (`[ 12 ]`).
3. **`[ Server ]` Tab (`MediaServerPage.kt`):** Jetpack Compose Media Server management featuring a top Hero Status Card (live Wi-Fi SSID chip, status LED, Gold Start / Crimson Stop power action button), 1-tap WebUI actions, tap-to-enlarge high-contrast QR code modal, and segmented engine switcher (`SonicNIO` / `CoreHTTP` / `Netty`) with dynamic architecture descriptions. The tab label features an authentic hardware emerald jewel LED diode (`Color(0xFF00E676)`) active indicator.

### D. Contextual Navigation
- **Queue Tab row tap:** Immediately **plays the tapped track** — the queue is a playback surface, not a navigation surface.
- **Now Playing card tap (Playback tab):** Dismisses the sheet and smoothly scrolls the main library list to the currently playing song's position.

---

## 6. Touch Targets, Accessibility (a11y) & Feedback

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

## 7. Dialog & Bottom Sheet Design System

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

### C. Music Center Bottom Sheets (`AudioHubBottomSheet` / `AudioHubSheet.kt`)

```
┌─────────────────────────────────────────────────────────┐
│ [ Playback ]        [ Queue  12 ]        [ Server ● ]   │
├─────────────────────────────────────────────────────────┤
│                                                         │
│          Fixed 65%-Height Viewport Content              │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

- **Top Edge Geometry:** `24dp` top corner radius (`RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`).
- **Viewport Sizing:** The sheet opens fully expanded at a **fixed 65% of screen height** (clamped between 420dp and 680dp), a single predictable size across all three tabs.
- **Fluid Audiophile Glass Pill Switcher:** Modernized 3-tab segmented navigation featuring real-time 1:1 finger-tracking motion physics bound directly to `(pagerState.currentPage.toFloat() + pagerState.currentPageOffsetFraction).coerceIn(0f, 2f)` inside `BoxWithConstraints`.
  - **Deep Obsidian Glassmorphism:** Outer container in dark obsidian glass (`Color(0xFF161616)`) with a precision hairline border (`0.75dp, Color(0x24FFFFFF)`).
  - **Sliding Indicator Pill:** Elevated sliding capsule with champagne gold vertical ambient gradient (`Color(0x38FFD700)` top highlight to `Color(0x1CFFD700)` base) and metallic gold hairline rim (`Color(0x66FFD700)`).
  - **Micro-Badges & Status LED:**
    - **Playback:** Crisp typography with champagne gold active state and muted titanium inactive state.
    - **Queue:** Monospace count pill chip (`[ 12 ]`) dynamically reflecting queue size without crude string parentheses.
    - **Server:** Dedicated emerald jewel LED diode (`Color(0xFF00E676)`) replacing raw `🟢` unicode emoji.
- **Sticky Session State:** Selected tab position (`Playback`, `Queue`, or `Server`) remains sticky when closing and reopening the Music Center during a session (`MainScaffoldState.get().audioHubInitialTab`).

---

## 8. UI Architectural Decision Records (ADRs)

> **Convention:** Each ADR records **Status** (`Proposed` / `Accepted` / `Superseded by ADR-XXX`), **Date**, **Context**, **Decision**, and **Consequences**. For system, playback, networking, and backend ADRs, see [`DESIGN.md`](file:///Users/thawee.p/Workspaces/github/musicmate/DESIGN.md#5-architectural-decision-records-adrs).

### ADR-001: Unconditional Single-Tap to Tag Editor
- **Status:** Superseded by ADR-016
- **Date:** 2026-08
- **Context:** Previously, tapping a song row conditionally opened `TagsActivity` only if the playback service was not connected; otherwise, it played the track. This caused unpredictable navigation behavior when a player connected in the background.
- **Decision:** Row single-tap always opens `TagsActivity`. Tapping cover art handles quick play.
- **Consequences:** Consistent mental model; users can reliably edit tags at any time. (Superseded by ADR-016 to introduce user-configurable Listener vs Curator modes).

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
- **Consequences:** Consistent, predictable modal experience across all device form factors.

### ADR-009: Song Detail / Tag Editor (`TagsActivity`) Viewport Hierarchy & Metadata Deduplication
- **Status:** Accepted
- **Date:** 2026-08-14 (updated 2026-09-24)
- **Context:** In `TagsActivity`, when the `AppBarLayout` header was fully expanded in Preview mode (`mode == 0`), the `TabLayout` (`Song Info` / `Tech Info`) was pushed to the bottom of the screen, colliding directly with the floating bottom action capsule (`Delete` | `Organize` | `More...` / `Edit Song Info`). Additionally, when a track's `Album Artist` matched its `Artist`, the artist string was printed twice in succession (`Artist` and `Album Artist` above `Genre`), and song title overlays on album art lacked sufficient contrast on light covers.
- **Decision:**
  1. **Dynamic Tab Lifecycle:** In Preview mode with the header expanded, `tabLayout` is hidden (`GONE`). When the user taps `Edit Song Info` or scrolls to collapse the header, `tabLayout` smoothly transitions to the top of the viewport under the action bar. The tab pill reference is resolved *before* the first `setupActionButtons` call so the initial preview frame cannot leak tabs (a null-`tabLayout` race previously left them visible until the next scroll/edit event).
  2. **Scroll Hysteresis Mode Switch:** Preview ↔ edit transitions fire at `scrollRatio >= 0.72` (enter edit) and `scrollRatio <= 0.40` (return to preview) instead of only at exact fully-expanded / fully-collapsed offsets, eliminating the intermediate band where tabs sat under the fixed action dock.
  3. **Metadata Deduplication:** `Album Artist` is displayed in `genreView` only if it exists and differs from the primary `Artist` (e.g. `Various Artists` compilations). If identical, it is suppressed to display `Artist | Album` followed cleanly by `❖ Genre ❖`.
  4. **High-Contrast Top Scrim:** Enhanced `shape_background_main_header.xml` with a dark top-down vignette gradient (`#CC0A0A0A` $\rightarrow$ `#00000000`) for high text legibility across all album artwork brightness levels.
- **Consequences:** Eliminates visual clipping and tab bleed-through in preview mode, removes redundant text repetition, and elevates the visual presentation of song details.

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
- **Status:** Accepted
- **Date:** 2026-09-01
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

### ADR-016: Dual Persona "Curator vs. Listener" Interaction Architecture
- **Status:** Accepted (Supersedes ADR-001)
- **Date:** 2026-09-10
- **Context:** Historically, single-tapping a track unconditionally opened `TagsActivity` (ADR-001) to reinforce MusicMate's primary purpose as a library tag editor. However, daily listening workflows felt high-friction for users who use MusicMate as their primary music player, requiring a precise tap on the small album art thumbnail to initiate playback.
- **Decision:**
  1. Introduce a user-configurable **Interaction Mode** via `PREF_TAP_ACTION_MODE` (`"listen"` vs `"curate"`), configured via an instant segmented card in `SettingsScreen.kt`.
  2. **Listener Mode (`TAP_MODE_LISTEN`):**
     - Single-tap on track row: Starts instant playback (`onTrackQuickPlayClicked`).
     - Long-press on track row: Opens `TagsActivity` for deep metadata curation.
  3. **Curator Mode (`TAP_MODE_CURATE`):**
     - Single-tap on track row: Opens `TagsActivity` (preserving existing power-curator workflows).
     - Tap cover art thumbnail: Starts playback.
     - Long-press on track row: Enters multi-select batch action mode.
- **Consequences:** Eliminates friction for daily music listening without compromising power tag curation workflows; users choose their preferred app persona with one tap.

### ADR-017: Responsive Badge Synthesis & Screen Density (< 390dp)
- **Status:** Accepted
- **Date:** 2026-09-10
- **Context:** Rich audio telemetry badges (Quality grade, Resolution, Dynamic Range bar, Duration) occupied up to 180dp of horizontal width in `TrackListItem.kt`. On compact mobile devices (< 390dp screen width), this squeezed the text column, causing severe ellipsis truncation on track titles and artist names (e.g., `"Symphony No. 5 in C..."` or `"The Dark Side of..."`).
- **Decision:**
  1. Implement `UnifiedAudioBadge` in `AudioBadges.kt` that fuses Quality Tier and Studio Resolution into a single micro-capsule (`[● HI-RES 24/96]`, `[● CD 16/44.1]`, `[● DSD64]`, `[● 320k]`).
  2. Implement compact mode in `DynamicRangeMeters.kt` (`compact = true`) which collapses the horizontal graphic level bar into concise monospace text (`DR12`).
  3. Dynamically detect screen width in `TrackListItem.kt` via `LocalConfiguration.current.screenWidthDp < 390`: automatically switch to `UnifiedAudioBadge` + compact DR meter on compact viewports, liberating ~40dp of breathing room.
- **Consequences:** Zero title and artist truncation on compact devices while preserving 100% of technical audio specs.

### ADR-019: Vintage Analog Needle VU Meter & Ballistic Studio Telemetry in Audio Anatomy
- **Status:** Accepted
- **Date:** 2026-09-10
- **Context:** The 3D flip side of the Now Playing card ("Audio Anatomy") displayed static textual audio specifications (Codec, Bitrate, Channels, DR), lacking visual energy, dynamic feedback, and tactile audiophile appeal.
- **Decision:**
  1. Implement `AnalogVUMeter.kt` using pure Jetpack Compose `Canvas`.
  2. Dual Stereo Channel Dials: Left and Right channels rendered side-by-side in a shared vintage chassis with decorrelated stereo phase harmonics.
  3. ANSI Standard Ballistics: True mechanical spring-damper equations ($300\text{ms}$ rise time, $\sim 1.5\%$ overshoot) computed per-frame with `withFrameNanos`.
  4. Logarithmic Decibel Scale: Calibrated $-20\text{ dB} \dots +3\text{ dB}$ scale with bold $0\text{ dB}$ reference and Red Overload Zone ($>0\text{ dB}$), peak hold indicators, and active overload LEDs.
  5. 3 Legendary Audiophile Themes (interactive tap-to-cycle):
     - *Accuphase Gold* (Champagne Gold dial face & amber incandescent glow)
     - *McIntosh Blue* (Electric Cyan-Blue dial face & carmine red needles)
     - *Studio Slate* (High-contrast dark reference)
  6. Driven by UPnP / Local playback state, track Dynamic Range (DR score), volume, and ReplayGain true-peak telemetry.
- **Consequences:** Transforms the Audio Anatomy flip screen into an authentic, living high-end analog hi-fi console that works across Local, USB Bit-Perfect, Bluetooth, and DLNA renderers.

### ADR-022: Dual-Mode Audio Telemetry: Real-Time PCM Stereo VU Meter & Vintage Reel-to-Reel Tape Deck
- **Status:** Accepted
- **Date:** 2026-09-18
- **Context:**
  1. The analog VU meter's needle physics were realistic, but its audio input was synthetic (sine oscillators modulated by DR score and ReplayGain peak).
  2. For local playback via ExoPlayer, decoded PCM samples are available in-process and can drive real-time sample-accurate Left/Right channel decibel telemetry.
  3. For remote DLNA streaming, the audio file is decoded directly on the external hardware streamer's DAC/DSP; local ExoPlayer is silent to avoid duplicate playback. Therefore, live PCM audio is unavailable on the phone during DLNA cast.
  4. An authentic visualizer for DLNA must be driven by transport state (play/pause, elapsed progress, total duration) rather than raw PCM.
- **Decision:**
  1. **Real-Time PCM AudioProcessor:** Implemented `AudioLevelProcessor.kt` extending Media3 `BaseAudioProcessor` in `AndroidPlayerController.java`. Calculates sample-accurate Left and Right RMS decibels ($20 \log_{10}(\text{RMS})$) and true peak levels. Operates purely in-memory on decoded PCM buffers without requiring Android `RECORD_AUDIO` permission or triggering the microphone privacy indicator.
  2. **Live Ballistic Dynamics:** Wired real PCM telemetry into `AnalogVUMeter.kt`, driving the ANSI ballistic spring-damper needles to live audio transients, bass hits, and vocal dynamics, with automatic fallback to procedural synthesis when PCM is idle.
  3. **Vintage Reel-to-Reel Tape Deck Widget (`ReelToReelTapeDeck.kt`):** Built a pure Jetpack Compose Canvas tape deck widget emulating iconic studio master tape machines (Studer A820, Revox B77). Features dual rotating 3-hole NAB precision aluminum reels, dynamic supply & take-up tape pack radii tracking track progress, mechanical differential angular velocity ($\omega = v/r$), tape ribbon path, tape counter, and glowing RUN/PAUSE status lamp.
  4. **Smart Dual-Mode Switching:** Automatically detects active playback target in `NowPlayingPage.kt` (`isDLNA == true` -> defaults to Reel-to-Reel Tape Deck; `isDLNA == false` -> defaults to Analog VU Meter), paired with an interactive 1-tap capsule switcher (`[VU METER]` ↔ `[TAPE DECK]`) to manually toggle widgets at any time.
- **Consequences:** Delivers genuine sample-accurate studio needle dynamics during local listening (headphones, Bluetooth, USB DAC) and an authentic mechanical tape deck visualizer during DLNA Wi-Fi casting.

### ADR-023: Fullscreen Landscape Studio Console ("Hi-Fi Desk Mode")
- **Status:** Accepted
- **Date:** 2026-09-18
- **Context:**
  1. When placing a phone or tablet on a desk listening stand, audio rack, or vehicle mount, standard vertical playback sheets provide insufficient layout width for comprehensive audio telemetry, dual-needle VU meters, and large album art.
  2. Users desired a dedicated horizontal / landscape console experience accessible directly from the floating mini-player dock or Audio Hub sheet without requiring Android system-wide auto-rotate to be unlocked.
  3. The Activity lifecycle must not destroy or recreate `MainActivity` when rotating to landscape, as activity recreation tears down background playback services and drops active UPnP/DLNA event subscriptions.
- **Decision:**
  1. **Activity Lifecycle Resilience:** Added `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize"` to `MainActivity` in `AndroidManifest.xml`. Configuration changes are handled seamlessly by Jetpack Compose without activity destruction or service reconnections.
  2. **Programmatic Orientation & Dynamic Screen Wake Lock:** Implemented in `FullscreenStudioConsole.kt` using `DisposableEffect`:
     - Sets `activity.requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE` on entry, and cleanly restores `SCREEN_ORIENTATION_PORTRAIT` on exit.
     - Dynamically manages `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` via `DisposableEffect(keepScreenOn)`.
     - Provides an illuminated 1-tap quick toggle button in the console header (`rounded_wb_sunny_24` / `rounded_bedtime_24`) allowing users to easily toggle whether the screen stays awake or sleeps via system display timeout, persisted via `Settings.setStudioKeepScreenOn()`.
     - Integrates `BackHandler` to exit fullscreen mode on Android system back gesture.
  3. **Audiophile 50/50 Split-Bay Architecture:**
     - **Left Bay (Visualizer Deck - 50%):** Houses a grand visualizer with smooth animated crossfading (`AnimatedContent`) between:
       - *Grand Analog VU Meter* (stereo dials driven by real-time PCM decibel telemetry).
       - *Grand Reel-to-Reel Tape Deck* (dynamic tape pack radii and mechanical rotation physics).
       - *Grand Vinyl/Cover Art* with hairline gold glass border and depth shadows.
       - Floating 1-tap switcher pill (`[VU METER]`, `[TAPE DECK]`, `[ALBUM ART]`).
     - **Right Bay (Master Studio Telemetry & Transport Deck - 50%):**
       - Quality verdict header (`HI-RES AUDIO`, `LOSSLESS`, `DSD`).
       - Interactive hardware target selector pill with dynamic LED indicator dot (`Bit-Perfect Direct USB`, `DLNA Cast`, `Bluetooth`, `Local Audio`).
       - Quick-action buttons: 1-tap "Keep Screen Awake" toggle button (`rounded_wb_sunny_24`) and Exit Fullscreen button (`rounded_fullscreen_exit_24`).
       - Full-width title marquee (`basicMarquee`) and artist/album subtitle.
       - Monospace technical diagnostics strip (Codec, Resolution `24-bit 96.0 kHz`, Dynamic Range `DR12`, ReplayGain `+0.4 dB`).
       - High-precision time scrubbing slider with tabular monospace time readouts.
       - Tactile studio transport controls (Shuffle, Previous, Play/Pause, Next, Repeat).
       - Master volume fader bar with volume down/up icons.
       - "Up Next" queue preview capsule.
  4. **Direct Entry Points:**
     - Tap the floating dock's artwork or title to open the Audio Hub, then use the expand `[⛶]` icon button in its header.
- **Entry-point update (2026-09-24):** Removed the dock's dedicated fullscreen icon to leave more width for track details and transport controls. The Audio Hub header remains the fullscreen entry point.
- **Consequences:** Provides a luxury desktop/rack listening console with configurable display wakefulness that stays responsive without disrupting ongoing playback or DLNA sessions.

### ADR-024: Fluid Audiophile Glass Pill Tab Switcher & Full-Surface Card Flip
- **Status:** Accepted
- **Date:** 2026-09-20
- **Context:**
  1. The segmented tab switcher in `AudioHubSheet.kt` utilized static in-place background toggling without sliding motion physics. When users swiped between pages in the `HorizontalPager`, the tab indicator remained frozen until page settlement, breaking direct manipulation principles.
  2. Tab titles relied on raw unicode emojis (`Server 🟢`) and parentheses (`Queue (12)`), causing inconsistent font rendering across Android OEM skins and creating visual clutter.
  3. On the Now Playing card in `NowPlayingPage.kt`, an explicit info icon beside the track title duplicated the existing card tap gesture (`flipped = !flipped`) and compressed the horizontal space available for long track titles.
- **Decision:**
  1. **Fluid Motion Physics:** Bound sliding indicator pill offset directly to `(pagerState.currentPage.toFloat() + pagerState.currentPageOffsetFraction).coerceIn(0f, 2f)` inside `BoxWithConstraints` in `AudioHubSheet.kt`. The indicator glides 1:1 with user finger drag and animates seamlessly on tap.
  2. **Obsidian Glassmorphism & Status Micro-Components:** Styled the switcher rail in deep obsidian (`Color(0xFF161616)`) with hairline metallic borders. Built dedicated Compose micro-components: an authentic hardware emerald jewel LED diode (`Color(0xFF00E676)`) for active media servers and a monospace count pill badge chip (`[ 12 ]`) for queue items.
  3. **Streamlined Now Playing Card Title Row:** Removed the redundant info icon from the track title row in `NowPlayingPage.kt`, granting long titles full width while retaining full-surface 3D card flip gestures (`flipped = !flipped`) to access the Audio Anatomy technical spec sheet.
- **Consequences:** Elevates the Music Center navigation to modern luxury hi-fi standards, eliminates crude string/emoji hacks, provides 120Hz fluid gesture responsiveness, and maximizes title readability.

### ADR-025: Tag Editor & Auxiliary Dialog Modernization to Obsidian Glass Design System
- **Status:** Accepted
- **Date:** 2026-09-20
- **Context:**
  1. `TagsActivity` relied on legacy XML `TabLayout` with static tab indicators, harsh 1dp vertical hairline dividers between bottom action buttons, and squared button styling.
  2. Auxiliary dialogs—including Online Tag Search & Match, Operation & Save Progress, Audio Spectrum Lossless Verifier, and Trash Confirmation—used 2014-era Android View layouts (e.g. `ListView`, fixed 256dp/164dp nested frame boxes, hardcoded green spinners, low-contrast text builders).
  3. Obsolete storage visualization layouts and unused helper methods lingered in `UIUtils.java`.
- **Decision:**
  1. **Fluid Audiophile Glass Pill Tab Switcher (`TagsTabPillSwitcher.kt`):**
     - Replaced legacy `TabLayout` in `activity_tags.xml` with a `ComposeView` host (`tags_tab_pill_container`).
     - Built `TagsTabPillSwitcher` featuring a sliding glass pill indicator with 1:1 `ViewPager2` drag tracking via `pagerPosition + pagerOffsetFraction`.
     - Styled with an obsidian glass base (`Color(0xFF141416)`), subtle champagne gold glow gradient (`Color(0x33FFD700)`), metallic hairline border (`0.75dp` `Color(0x24FFFFFF)`), and tactile haptic feedback on tab selection.
     - Provided zero-boilerplate Java interop via `TagsTabPillBridge.setup(composeView, viewPager)`.
  2. **Modernized Action Dock (`activity_tags.xml`):**
     - Removed obsolete 1dp vertical hairline dividers (`divider_1` through `divider_3`).
     - Styled bottom dock buttons as rounded pills (`app:cornerRadius="20dp"`) with comfortable spacing and clear visual hierarchy.
  3. **Compose Online Tag Search & Match Dialogs (`SearchMatchDialog.kt`):**
     - Replaced legacy `ListView` and XML cards with pure Jetpack Compose sheets: `SearchQueryDialog` and `SearchResultsDialog`.
     - Features `LazyColumn` for high-performance scrolling, Coil 3 asynchronous cover art loading, match quality chips, and metadata difference highlighting.
     - Wired into `TagsActivity.java` via `DialogInterop.kt` helpers (`showSearchQueryDialog`, `showSearchResultsDialog`), deleting `SearchResultAdapter` and obsolete XML layouts (`view_action_search_query_dialog.xml`, `view_action_search_results_dialog.xml`, `view_list_item_search_result.xml`).
  4. **Obsidian Glass Progress Modal (`animated_progress_dialog_layout.xml`):**
     - Modernized progress modal into a floating obsidian glass card (`bg_dialog_dark_blur`) with rounded geometry and clean typography.
     - Replaced hardcoded green spinner with `CircularProgressIndicator` tinted with `@color/colorGold`. Deleted legacy `progress_dialog_layout.xml`.
  5. **Lossless Verifier Studio Inspector (`view_action_spectrum.xml`):**
     - Upgraded layout to a studio inspector with a two-column telemetry glass card (Format details vs. Analytics details), champagne gold verdict badge, and integrated progress spinner.
  6. **Obsidian Glass Trash Confirmation Sheet (`view_action_trash_bottom_sheet_dialog.xml`):**
     - Modernized into an obsidian glass bottom sheet with a red tonal warning container, delete icon, and pill-shaped action buttons.
  7. **Dead Code & Resource Pruning:**
     - Removed unused storage calculation methods from `UIUtils.java` (`buildStoragesUsed`, `buildStoragesUsedOld`, `buildStoragesStatus`, `formatCompactStorageText`, `setTextViewShading`).
     - Deleted obsolete layout files: `view_storage_space.xml` and `view_storage_space_estimated.xml`.
- **Consequences:** Unifies all remaining auxiliary flows and the Tag Editor under the flagship Obsidian-Glass Design System, guaranteeing 120Hz gesture response, high contrast, clean typography, and zero legacy View adapter overhead.

### ADR-027: Tag Preview Taxonomy Micro-Chips & In-Place Genre Exploration
- **Status:** Accepted
- **Date:** 2026-09-21
- **Context:**
  1. On the `TagsActivity` preview viewport, `Genre`, `Mood`, and `Style` were previously hidden entirely inside the editor fragment (`TagsEditorPage.kt`), forcing users to tap *"Edit Song Info"* and scroll down just to discover a track's musical classification.
  2. While `Genre` is populated on ~85%+ of audio tracks, `Mood` and `Style` exhibit high sparsity (~20–30%). Static rows with placeholder dashes create vertical clutter, push action docks off-screen, and compromise visual elegance.
- **Decision:**
   1. **Taxonomy Micro-Chips Flow (`TaxonomyChipsRow` in `AudioBadges.kt`):**
     - Embed compact frosted glass micro-pills (`height ≈ 22dp`, `typography 9.5sp monospace`, `widthIn(max = 160.dp)`) directly between `TagHeaderBadges` and `StudioProvenanceSection`.
     - Layout wrapped and centered cleanly using `FlowRow` (`horizontalArrangement = Arrangement.spacedBy(5.dp)`, `verticalArrangement = Arrangement.spacedBy(4.dp)`).
   2. **Strict Zero-Clutter Conditional Rendering:**
      - Blank, whitespace, `UNKNOWN`, `NONE`, or `"-"` values are stripped. The compact preview row now shows only Genre; Mood and Style remain editable in Song Info. If Genre is empty, the row consumes **0dp height**.
   3. **Color Tokens & Semantic Micro-Icons:**
      - **Genre:** Acoustic Teal (`#80CBC4` border/tint) with `🏷️` glyph.
  4. **1-Tap In-Place Genre Exploration:**
     - Tapping the `Genre` chip invokes `onOpenRelated(Constants.FILTER_TYPE_GENRE, genre, "Genre: $genre")` with tactile haptic feedback (`HapticFeedbackType.TextHandleMove`), sliding up `RelatedTracksSheet` to view, play, or queue all library tracks sharing that genre.
### ADR-028: Tag Editor Overhaul: Contextual Tabs, Filename Pattern Parser, Batch Editing Safety & Compact Command Dock
- **Status:** Accepted
- **Date:** 2026-09-24
- **Context:**
  1. Showing the Song Info / Tech Info switcher and editor fields in the expanded artwork preview crowded the cover and placed fields behind the fixed action dock. The preview already has a clear "Edit Song Info" entry point to the detail workspace.
  2. The `[📄 From File]` (`action_read_tag`) bottom action displayed an unimplemented stub Toast.
  3. The fixed 2-row bottom command bar occupied ~160dp plus 72dp Compose bottom spacer, causing severe viewport compression and IME keyboard occlusion when editing form fields.
  4. In batch multi-track editing, mixed values displayed literal `" - "` text inside input fields, creating ambiguity and risking data loss if saved without modification.
  5. The album art header lacked an edit affordance badge, obscuring cover art search, extraction, and gallery pick flows.
- **Decision:**
  1. **Preview and Detail Separation:** Hide both `TagsTabPillSwitcher` and the editor `ViewPager2` in the expanded cover preview. Reveal them in the detail workspace entered through "Edit Song Info"; the button opens Song Info first, with Tech Info one tab away. Returning to the fully expanded cover restores the uncluttered preview.
  2. **Filename Tag Parser Sheet (`TagsFromFilenameSheet.kt`):** Built a Compose modal bottom sheet with pattern presets (`%track% - %title%`, `%artist% - %title%`, `%track% - %artist% - %title%`, etc.), interactive token builder chips, live preview across selected tracks, and safe application across single or batch track selections via `FilenameTagParser.kt`.
  3. **Compact Bottom Command Bar & Editor Viewport:** Compacted vertical padding and button heights in `activity_tags.xml` (reducing bar height from ~140dp to ~88dp), sized the editor page above the measured command dock and navigation inset, and eliminated redundant `72dp` spacers in Compose editor and tech pages.
  4. **Batch Editing Safeguards & Mixed-Value UX:**
     - Replaced literal `" - "` text with clean placeholder `"< Multiple Values >"`, golden `• Mixed` badge, and helper text *"Leave blank to preserve individual track values"*.
     - Refactored `buildTag()` in `TagsEditorFragment.kt` to ensure un-modified mixed fields safely retain `oldVal`.
  5. **Cover Art Edit Affordance & Diagnostics Export:**
     - Added a frosted `[ Change Cover ]` tonal button overlay on the album art thumbnail.
     - Added a 1-tap "Copy Diagnostics" action in `TagsTechnicalPage.kt` exporting comprehensive audio telemetry (format specs, ReplayGain, cover art dimensions, reflection field dump, FFmpeg diagnostics) to the system clipboard.
  6. **Recoverable Editor State:** Keep unsaved tag edits through metadata refreshes and configuration recreation. Failed writes remain visible as drafts with an actionable error instead of discarding user input.
  7. **Partial Batch Edits:** Blank fields in batch operations mean “leave each track's current value unchanged”; users can update only selected metadata fields without replacing the rest.
   8. **Uncluttered Cover Title & Interactive Provenance Discovery:**
      - Purged duplicate `panel_artist` TextView from `title_container` in `activity_tags.xml`. The track title now sits on a separate surface below the cover instead of overlaying the artwork; artist and album discovery remain in the Compose `StudioProvenanceSection` (`ProvenanceCapsule`) chips below. The preview cover has Back and Change Cover controls but no separate Play button.
  9. **Compose Dialog Lifecycle Interop (`DialogInterop.kt`):**
     - Auxiliary Compose dialogs (`SearchQueryContent`, `SearchResultsContent`) require valid `LifecycleOwner` and `SavedStateRegistryOwner` providers on the window decor view. Standardized on `androidx.activity.ComponentDialog(context, R.style.AlertDialogTheme)` with explicit `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner`, and `ViewTreeViewModelStoreOwner` propagation to guarantee zero parent recomposer resolution crashes.
  10. **Target Picker Icon Caching (`PlayerPickerDialog.kt`):**
      - Backed third-party music app icon bitmaps with an in-memory `LruCache(32)` to eliminate `PackageManager` query latency and bitmap reallocation during audio routing selection.
- **Consequences:** Transforms the Tag Editor into an intuitive, high-efficiency metadata workshop with reduced keyboard occlusion, reliable batch editing safety, recoverable drafts, crash-free auxiliary Compose dialogs, and full tag-from-filename automation.

### 3.19.8 interaction and accessibility maintenance (2026-09-25)
- Search & Match and auto-tag results become unsaved drafts when they change metadata, so Back presents the discard confirmation. Save persists the changes.
- All Songs, folder and playlist browsing request 500-item pages; full-result folder/playlist queries are sliced before reaching the list so scrolling cannot re-append the first page. Switching drawer destinations clears a previous related-track filter.
- The New Smart Playlist action is tied to being at the Playlists overview, not to the displayed count text, so it remains available when no playlists exist. Browse Library in the empty Queue closes Music Center and navigates to All Songs.
- Artwork/title regions in the mini-player expose an Open Music Center action to accessibility services; the Now Playing flip exposes Show audio details / Show album artwork actions. The Studio Console seek and volume rails expose adjustable range actions and left/right keyboard steps alongside existing touch gestures.
- An explicit Server Stop persists across main-screen recreation and reopening until Start is selected; it does not disable a later intentional server start for a streaming target.

---

### Cross-Reference: System & Backend Decision Records
The following Architectural Decision Records govern system architecture, audio engines, and data pipelines. Full details can be found in [`DESIGN.md`](file:///Users/thawee.p/Workspaces/github/musicmate/DESIGN.md#5-architectural-decision-records-adrs):

* [`ADR-007`](file:///Users/thawee.p/Workspaces/github/musicmate/DESIGN.md#adr-007-robust-audio-tag-readwrite--dual-chunk-wav-support): Robust Audio Tag Read/Write & Dual-Chunk WAV Support
* [`ADR-008`](file:///Users/thawee.p/Workspaces/github/musicmate/DESIGN.md#adr-008-automatic-bluetooth-audio-optimization--real-time-codec-telemetry): Automatic Bluetooth Audio Optimization & Real-Time Codec Telemetry
* [`ADR-010`](file:///Users/thawee.p/Workspaces/github/musicmate/DESIGN.md#adr-010-build-system-hygiene--dependency-modularization): Build System Hygiene & Dependency Modularization
* [`ADR-018`](file:///Users/thawee.p/Workspaces/github/musicmate/DESIGN.md#adr-018-visual-audiophile-query-studio--dynamic-rule-based-smart-playlists): Visual "Audiophile Query Studio" & Dynamic Rule-Based Smart Playlists
* [`ADR-020`](file:///Users/thawee.p/Workspaces/github/musicmate/DESIGN.md#adr-020-active-replaygain-20--ebu-r128-playback-leveling-engine-with-anti-clipping-true-peak-guard): Active ReplayGain 2.0 / EBU R128 Playback Leveling Engine with Anti-Clipping True-Peak Guard
* [`ADR-021`](file:///Users/thawee.p/Workspaces/github/musicmate/DESIGN.md#adr-021-http-streaming-reliability-rfc-7233-range-clamping--dlna-completion-latching): HTTP Streaming Reliability, RFC 7233 Range Clamping & DLNA Completion Latching
* [`ADR-026`](file:///Users/thawee.p/Workspaces/github/musicmate/DESIGN.md#adr-026-multi-target-playback-architecture-companion-controller-pattern--bit-perfect-usb-pipeline): Multi-Target Playback Architecture, Companion Controller Pattern & Bit-Perfect USB Pipeline
