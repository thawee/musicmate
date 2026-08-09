# Plan: 3-Tab Architecture for Music Center Hub ([ Playback | Queue | Server ])

## Overview
Refactored `AudioHubBottomSheet` and its XML layouts into a dedicated 3-Tab Architecture (`[ Playback | Queue | Server ]`), expanding each page to full height and giving both Playback/Audio Route telemetry and Queue management maximum visual real estate.

---

## Tasks Checklist

- [x] **Phase 1: Update XML Layout (`sheet_audio_hub.xml`)**
  - [x] Updated `MaterialButtonToggleGroup` to include 3 tabs:
    - `tab_now_playing` (Text: `"Playback"`)
    - `tab_queue` (Text: `"Queue"`)
    - `tab_media_server` (Text: `"Server"`)
  - [x] Set `ViewPager2` (`audio_hub_view_pager`) to full weight (`layout_width="match_parent"`, `layout_height="0dp"`, `layout_weight="1"`).
  - [x] Created dedicated `view_audio_hub_queue_page.xml` layout for Page 1 with toolbar (track count + total remaining duration), clear queue button, and full-height RecyclerView list.

- [x] **Phase 2: Update Java Controller (`AudioHubBottomSheet.java`)**
  - [x] Updated tab index constants: `TAB_NOW_PLAYING = 0`, `TAB_QUEUE = 1`, `TAB_MEDIA_SERVER = 2`.
  - [x] Updated `ViewPager2Adapter` to inflate and manage 3 pages (`viewNowPlayingPage`, `viewQueuePage`, `viewMediaServerPage`).
  - [x] Synchronized tab toggle group with ViewPager2 `OnPageChangeCallback`.
  - [x] Implemented queue total duration & track count calculation in queue header toolbar (`X tracks • Y min total`).

- [x] **Phase 3: Build Verification & Documentation**
  - [x] Verified build with `./gradlew assembleDebug` (`BUILD SUCCESSFUL in 6s`).
  - [x] Updated `README.md`, `CHANGELOG.md`, and `tasks/lessons.md`.
  - [x] Committed changes to Git (`dfa44063`).
