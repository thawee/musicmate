# Plan: 3-Tab Architecture for Music Center Hub ([ Playback | Queue | Server ])

## Overview
Refactor `AudioHubBottomSheet` and its XML layouts into a dedicated 3-Tab Architecture (`[ Playback | Queue | Server ]`), expanding each page to full height and giving both Playback/Audio Route telemetry and Queue management maximum visual real estate.

---

## Tasks Checklist

- [ ] **Phase 1: Update XML Layout (`sheet_audio_hub.xml`)**
  - [ ] Update `MaterialButtonToggleGroup` to include 3 tabs:
    - `tab_now_playing` (Text: `"Playback"`)
    - `tab_queue` (Text: `"Queue"`)
    - `tab_media_server` (Text: `"Server"`)
  - [ ] Set `ViewPager2` (`audio_hub_view_pager`) to full weight (`layout_width="match_parent"`, `layout_height="0dp"`, `layout_weight="1"`).
  - [ ] Create dedicated `view_audio_hub_queue_page.xml` layout for Page 1 with toolbar (track count + total remaining duration), queue action buttons, and full-height RecyclerView list.

- [ ] **Phase 2: Update Java Controller (`AudioHubBottomSheet.java`)**
  - [ ] Update tab index constants: `TAB_NOW_PLAYING = 0`, `TAB_QUEUE = 1`, `TAB_MEDIA_SERVER = 2`.
  - [ ] Update `ViewPager2Adapter` to inflate and manage 3 pages (`viewNowPlayingPage`, `viewQueuePage`, `viewMediaServerPage`).
  - [ ] Synchronize tab toggle group with ViewPager2 `OnPageChangeCallback`.
  - [ ] Implement queue total duration & track count calculation in queue header toolbar.
  - [ ] Update `newInstance(initialTab)` calls across activities/views (`MainActivity`, `NowPlayingQueueSheet`, shortcut long-press triggers).

- [ ] **Phase 3: Update Related Views & Shortcuts**
  - [ ] Update long-press on floating dock / signal path shortcuts to open `TAB_NOW_PLAYING` / `TAB_QUEUE` appropriately.

- [ ] **Phase 4: Build Verification & Documentation**
  - [ ] Run `./gradlew assembleDebug` to verify clean build.
  - [ ] Update `README.md`, `USER_GUIDE.md`, and `CHANGELOG.md`.
