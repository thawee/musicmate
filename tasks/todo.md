# Plan: 3 Pro-Refinements for Music Center Hub

## Overview
Implement 3 high-value UI/UX refinements in `AudioHubBottomSheet`:
1. "Jump to Now Playing" quick-scroll button in Queue tab header.
2. Vertical centering & 1-Tap "Copy Server URL" button in Server tab.
3. Interactive Audio Route Path telemetry hint on Playback tab.

---

## Tasks Checklist

- [x] **Phase 1: Queue Tab "Jump to Now Playing" (`view_audio_hub_queue_page.xml` & `AudioHubBottomSheet.java`)**
  - [x] Add `btn_jump_now_playing` icon button to `view_audio_hub_queue_page.xml` toolbar header.
  - [x] Wire `btn_jump_now_playing` in `AudioHubBottomSheet.java` to find playing track index and scroll smoothly.

- [x] **Phase 2: Server Tab Centering & Copy URL (`view_action_server_management_bottom_sheet.xml` & `AudioHubBottomSheet.java`)**
  - [x] Apply `gravity="center_vertical"` / `match_parent` height to Server card container.
  - [x] Add Copy URL icon next to server address and wire `ClipboardManager` toast.

- [x] **Phase 3: Interactive Signal Path Telemetry Hint (`sheet_now_playing_queue.xml` & `AudioHubBottomSheet.java`)**
  - [x] Add info chevron / telemetry icon to Audio Route Path widget.

- [x] **Phase 4: Verification & Git Commit**
  - [x] Verify build with `./gradlew assembleDebug`.
  - [x] Commit changes to Git.
